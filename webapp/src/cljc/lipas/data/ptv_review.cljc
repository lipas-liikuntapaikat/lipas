(ns lipas.data.ptv-review
  "The PTV sync review: what a sync of a site would change in its PTV
   service location, and who changed it since the last sync.

   All three sides are compared as PTV data. The Lipas side is the payload a
   sync of the site sends (lipas.data.ptv/->ptv-service-location), PTV's side
   the location as PTV has it (GET), and the base the payload the last sync
   sent. `location-values` reduces each to comparable values per review key,
   so the review covers exactly what a sync writes. `review-fields` says
   which keys are reviewed and how; `not-reviewed-payload-keys` lists the
   rest of the payload and why.

   Also the PTV tool's view of a site (`sports-site->ptv-input`), whose sync
   status follows the review."
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [lipas.data.ptv :as ptv-data]))

;;; Fields ;;;

(def review-fields
  "The fields the review compares, in the order their rows are shown.
   - :field         the first element of a row's :key
   - :group         which part of the review shows the row
   - :kind          :short (a short value), :text (a text with a word diff)
                    or :set (a set of ids, a row per difference)
   - :languages     :published (a row per published language) or the PTV
                    language codes it has rows for
   - :takeable-by   who may take PTV's version into Lipas: :site-editor (it
                    changes the site's own data) or :ptv-manager (it changes
                    the site's PTV texts or connections)
   - :reads         the payload keys it is derived from"
  [{:field :name :group :texts :kind :short :languages :published
    :takeable-by :site-editor :reads #{:serviceChannelNames}}
   {:field :marketing-name :group :texts :kind :short :languages ["fi"]
    :takeable-by :site-editor :reads #{:serviceChannelNames}}
   {:field :summary :group :texts :kind :text :languages :published
    :takeable-by :ptv-manager :reads #{:serviceChannelDescriptions}}
   {:field :description :group :texts :kind :text :languages :published
    :takeable-by :ptv-manager :reads #{:serviceChannelDescriptions}}
   {:field :services :group :services :kind :set
    :takeable-by :ptv-manager :reads #{:services}}
   {:field :languages :group :languages :kind :languages
    :reads #{:languages}}])

(def field-spec
  "field -> its entry in `review-fields`."
  (into {} (map (juxt :field identity)) review-fields))

(def not-reviewed-payload-keys
  "Keys of the payload a sync sends (->ptv-service-location) that no review
   field reads, and why. A test fails when the payload has a key that is
   neither here nor in a review field's :reads."
  {:organizationId "fixed: the organisation the location belongs to"
   :sourceId "Lipas's own id for the location"
   :displayNameType "follows the published languages"
   :publishingStatus "the state of the link, not a value to choose"
   :addresses "not yet reviewed: visiting address, municipality, point"
   :emails "not yet reviewed"
   :deleteAllEmails "not yet reviewed (email)"
   :phoneNumbers "not yet reviewed"
   :deleteAllPhoneNumbers "not yet reviewed (phone)"
   :webPages "not yet reviewed"
   :deleteAllWebPages "not yet reviewed (web pages)"})

;;; Projection ;;;

(defn- review-text
  "A text as the review sees it: nil when blank, or when it is the
   placeholder a sync sends for a missing Finnish text."
  [v]
  (when-not (or (str/blank? v) (= ptv-data/placeholder v))
    v))

(def ^:private name-types {"Name" :name "AlternativeName" :marketing-name})

(def ^:private description-types {"Summary" :summary "Description" :description})

(defn location-values
  "Review key -> value of a PTV service location: the payload a sync sends
   (->ptv-service-location) or the location as PTV has it (GET); the two
   share a shape. Texts are keyed [field language], with PTV's language
   codes, and left out when blank. [:languages] is the set of language
   versions and [:services] the ids of the services the location is
   connected to."
  [location]
  (let [texts (fn [k types]
                (for [{:keys [type language value]} (get location k)
                      :let [field (types type)
                            v (review-text value)]
                      :when (and field v)]
                  [[field language] v]))
        services (:services location)]
    (-> {}
        (into (texts :serviceChannelNames name-types))
        (into (texts :serviceChannelDescriptions description-types))
        (assoc [:languages] (set (:languages location))
               ;; the payload lists ids, a GET the services themselves
               [:services] (if (every? string? services)
                             (set services)
                             (set (ptv-data/channel-service-ids location)))))))

(defn site->location
  "The payload a sync of `site` sends, for comparing it: `langs` are the
   languages it publishes (LIPAS codes, see effective-languages)."
  [site langs]
  (ptv-data/->ptv-service-location
    nil
    ;; coordinates aren't reviewed yet
    (constantly nil)
    ""
    (-> site
        (assoc-in [:ptv :languages] langs)
        ;; not compared; a missing one would be made up (and logged)
        (assoc-in [:ptv :source-id] "review"))))

;;; Base ;;;

(def review-base-ptv-keys
  "The :ptv keys of a sync's own revision that the sync review compares
   against: what the sync sent and which location it went to."
  [:summary :description :service-ids :service-channel-ids :languages :last-sync])

(def ^:private review-site-paths
  "The site's own data a sync derives the location from, but the geometry."
  [[:name] [:name-localized] [:marketing-name]
   [:email] [:phone-number] [:www] [:reservations-link]
   [:location :address] [:location :postal-code] [:location :postal-office]
   [:location :city :city-code]])

(defn- select-paths
  [m paths]
  (reduce (fn [acc path]
            (let [v (get-in m path)]
              (if (some? v) (assoc-in acc path v) acc)))
          {}
          paths))

(defn ->review-base
  "The part of `site`, the revision a PTV sync wrote, that the sync review
   needs: what that sync sent. Fetched for every synced site of an org, so
   kept small."
  [site]
  (-> (select-paths site review-site-paths)
      (assoc :lipas-id (:lipas-id site))
      (assoc :ptv (select-keys (:ptv site) review-base-ptv-keys))))

(defn- usable-review-base
  "`base` when it is what the last sync of `site` sent to `channel`; nil when
   it isn't (another sync since, or the site now points at another
   location)."
  [site base channel]
  (when (and base
             (= (-> base :ptv :last-sync) (-> site :ptv :last-sync))
             (= (-> base :ptv :service-channel-ids first) (:id channel)))
    base))

;;; Review ;;;

(defn- review-origin
  "Who changed a value since the last sync: :lipas, :ptv, or :both (each to
   something else). nil when Lipas and PTV agree."
  [base lipas ptv]
  (cond
    (= lipas ptv) nil
    (= base ptv) :lipas
    (= base lipas) :ptv
    :else :both))

(defn review-text-path
  "Where a text row's Lipas value lives in a site."
  [{:keys [field locale]}]
  (case field
    :name (if (= :fi locale) [:name] [:name-localized locale])
    :marketing-name [:marketing-name]
    (:summary :description) [:ptv field locale]))

(defn- resolve-service-name
  "Resolve a service ID to a human-readable Finnish name from the
   services-by-id map. Falls back to the ID when the service isn't
   in the cache (e.g. external services or stale cache)."
  [services service-id]
  (or (some-> (get services service-id)
              :serviceNames
              ptv-data/select-service-name)
      service-id))

(def ^:private ptv-language-order ["fi" "sv" "en"])

(defn- text-keys
  "[field language locale] of every text row the review compares, in
   order, for the `published` languages (PTV codes): those of the payload."
  [published]
  (for [{:keys [field kind languages]} review-fields
        :when (#{:short :text} kind)
        language (if (= :published languages)
                   (filter (set published) ptv-language-order)
                   languages)]
    [field language (ptv-data/lang->locale language)]))

(defn- row-spec
  [row]
  (select-keys (field-spec (:field row)) [:group :kind :takeable-by]))

(defn sync-review
  "Compares a site with its PTV service location three ways: `site` (the
   saved site: what a sync of it sends), `channel` (PTV now, GET shape) and
   `base` (what the last sync sent, see ->review-base). nil without a
   channel, else

   {:rows [...] :matching [...] :services {...} :base? bool :first-link? bool
    :langs [...]}

   A row is a difference between Lipas and PTV, keyed by :key, with its
   field's :group, :kind and :takeable-by (review-fields):
   - a text: :field (:name, :marketing-name, :summary or :description),
     :language (PTV code), :locale, and :lipas, :ptv and :base, the values
     as they are or were sent (:base nil when the last sync didn't send it),
     and :saved, the site's own value at the row's path (no fallback);
   - a connection: :field :services, :id, :name, and :lipas?, :ptv? and
     :base?, whether each is connected to the service.
   Its :origin says who changed it since the last sync: :lipas, :ptv or
   :both. Without a usable base it is :first-link (LIPAS hasn't synced the
   site to this location) or :unknown (it has, but the base wasn't found).
   :language-added? marks a text in a language that neither the last sync
   nor PTV has: Lipas adds the language version.

   :matching lists the {:field :language} pairs (texts) and {:field
   :services} that agree, for telling the user what is up to date.
   :services has every service each side connects the location to,
   {:lipas [{:id :name}] :ptv [...]}, for showing a difference in context.
   :langs are `langs`, for annotate-review.

   `services` resolves service names (services-by-id); `langs` are the
   languages the next sync publishes (effective-languages)."
  [site base channel services langs]
  (when channel
    (let [base (usable-review-base site base channel)
          no-base (if (ptv-data/first-link? (:ptv site) channel) :first-link :unknown)
          base-langs (or (-> base :ptv :languages) langs)
          base-locales (set (map keyword base-langs))
          lipas (location-values (site->location site langs))
          ;; the languages a sync of the site publishes; a language PTV has
          ;; besides them is removed by the sync, not compared text by text
          published (get lipas [:languages])
          ptv (location-values channel)
          sent (when base (location-values (site->location base base-langs)))
          compared (for [[field language locale] (text-keys published)
                         :let [k [field language]
                               text {:field field :language language :locale locale}
                               l (get lipas k)
                               p (get ptv k)
                               b (get sent k)
                               nl (ptv-data/normalize-ws l)
                               np (ptv-data/normalize-ws p)]]
                     (merge (assoc text
                                   :key k
                                   :lipas l
                                   :ptv p
                                   :base b
                                   :saved (get-in site (review-text-path text))
                                   :origin (cond
                                             (= nl np) nil
                                             base (review-origin (ptv-data/normalize-ws b) nl np)
                                             :else no-base)
                                   :language-added? (boolean (and base
                                                                  (not (contains? base-locales locale))
                                                                  (nil? np))))
                            (row-spec text)))
          lipas-ids (get lipas [:services])
          ptv-ids (get ptv [:services])
          base-ids (get sent [:services] #{})
          ;; The location names the services it is connected to, also those
          ;; of other organisations that `services` doesn't have.
          channel-names (into {} (keep (fn [{:keys [service]}]
                                         (when (:name service)
                                           [(:id service) (:name service)])))
                              (:services channel))
          service-name (fn [id]
                         (let [n (resolve-service-name services id)]
                           (if (= n id) (get channel-names id id) n)))
          named (fn [ids]
                  (->> ids
                       (map (fn [id] {:id id :name (service-name id)}))
                       (sort-by (comp str/lower-case :name))
                       vec))
          connections (for [id (sort (set/union lipas-ids ptv-ids))
                            :let [l? (contains? lipas-ids id)
                                  p? (contains? ptv-ids id)]
                            :when (not= l? p?)]
                        (merge {:key [:services id]
                                :field :services
                                :id id
                                :name (service-name id)
                                :lipas? l?
                                :ptv? p?
                                :base? (when base (contains? base-ids id))
                                :origin (if base
                                          (review-origin (contains? base-ids id) l? p?)
                                          no-base)}
                               (row-spec {:field :services})))]
      {:rows (vec (concat (filter :origin compared) connections))
       :matching (vec (concat
                        (for [{:keys [field language origin lipas]} compared
                              :when (and (nil? origin) (ptv-data/normalize-ws lipas))]
                          {:field field :language language})
                        (when (and (empty? connections) (seq lipas-ids))
                          [{:field :services}])))
       :services {:lipas (named lipas-ids) :ptv (named ptv-ids)}
       :base? (some? base)
       :first-link? (= :first-link no-base)
       :langs langs})))

(defn review-choice-required?
  "Whether `row` needs the user's decision before a sync overwrites PTV with
   Lipas: PTV changed it, or who did can't be told. On a first link the
   location keeps its connections (link-service-ids), so a connection row
   needs no decision there."
  [row]
  (case (:origin row)
    (:ptv :both :unknown) true
    :first-link (not= :services (:field row))
    false))

(defn- assoc-text
  [site path v]
  (if (str/blank? v)
    (if (= 1 (count path))
      (dissoc site (first path))
      (update-in site (butlast path) dissoc (last path)))
    (assoc-in site path v)))

(defn- assoc-text*
  "assoc-text that also takes non-text values (a vector of service ids)."
  [site path v]
  (if (string? v)
    (assoc-text site path v)
    (if (nil? v)
      (assoc-text site path nil)
      (assoc-in site path v))))

;; The review is made against the saved site; the user decides by making
;; the edit (draft) hold one side's version, or a text of their own.

(defn review-saved-site
  "`draft` with what the review compares as `saved` has it (->review-base
   of the saved site): the site the review is made against. `draft` itself
   without `saved`."
  [draft saved]
  (if-not saved
    draft
    (let [put (fn [m path v]
                (cond
                  (some? v) (assoc-in m path v)
                  (= 1 (count path)) (dissoc m (first path))
                  (map? (get-in m (butlast path))) (update-in m (butlast path) dissoc (last path))
                  :else m))]
      (-> (reduce (fn [site path] (put site path (get-in saved path)))
                  draft
                  review-site-paths)
          (update :ptv (fn [ptv]
                         (reduce (fn [ptv k] (put ptv [k] (get-in saved [:ptv k])))
                                 ptv
                                 [:summary :description :service-ids])))))))

(defn- row-current
  "Which version of `row` the edit holds, given its `values`
   (location-values of the edit): :lipas (the saved one), :ptv, or :edited
   (a text of the user's own)."
  [values row]
  (if (= :services (:field row))
    (if (= (contains? (get values [:services]) (:id row)) (:ptv? row))
      :ptv
      :lipas)
    (let [v (ptv-data/normalize-ws (get values (:key row)))]
      (cond
        (= v (ptv-data/normalize-ws (:ptv row))) :ptv
        (= v (ptv-data/normalize-ws (:lipas row))) :lipas
        :else :edited))))

(defn annotate-review
  "`review` (sync-review of the saved site) with what the edit `draft` now
   holds on each row: :current (:lipas, :ptv or :edited) and, for a text,
   :draft, its text."
  [review draft]
  (if-not review
    review
    (let [values (location-values (site->location draft (:langs review)))]
      (update review :rows (fn [rows]
                             (mapv (fn [row]
                                     (cond-> (assoc row :current (row-current values row))
                                       (not= :services (:field row))
                                       (assoc :draft (get values (:key row)))))
                                   rows))))))

(defn review-choice-seen
  "The PTV side of `row` a decision is made against. A decision counts
   only while PTV still has it (see review-choice)."
  [row]
  (if (= :services (:field row))
    (:ptv? row)
    (ptv-data/normalize-ws (:ptv row))))

(defn review-choice
  "The decision to keep Lipas's version of `row` recorded in `choices` (row
   :key -> {:choice :lipas :seen}), or nil when there is none or it was made
   against a PTV value that has changed since. Taking PTV's version or
   editing the text needs no record: the edit shows it."
  [choices row]
  (when-let [choice (get choices (:key row))]
    (when (= (:seen choice) (review-choice-seen row))
      choice)))

(defn review-decided?
  "Whether the user has decided `row` (annotated, see annotate-review): the
   edit holds PTV's version or their own text, or they chose to keep
   Lipas's."
  [choices row]
  (boolean (or (#{:ptv :edited} (:current row))
               (review-choice choices row))))

(defn open-review-rows
  "The rows of `rows` (annotated) that need a decision and don't have one."
  [rows choices]
  (filterv #(and (review-choice-required? %)
                 (not (review-decided? choices %)))
           rows))

(defn review-writes
  "What to write into the edit `draft` to make it hold `side`'s version of
   `row` (:ptv, or :lipas for the saved one): {path value}, a nil value
   removing the key."
  [draft row side]
  (if (= :services (:field row))
    (let [connected? (if (= :ptv side) (:ptv? row) (:lipas? row))
          ids (vec (-> draft :ptv :service-ids))]
      {[:ptv :service-ids] (if connected?
                             (vec (distinct (conj ids (:id row))))
                             (vec (remove #{(:id row)} ids)))})
    (let [v (if (= :ptv side) (:ptv row) (:saved row))]
      {(review-text-path row) (when-not (str/blank? v) v)})))

(defn apply-review-writes
  "`draft` with `writes` (review-writes) made."
  [draft writes]
  (reduce-kv assoc-text* draft writes))

(def ^:private site-name-paths
  [[:name] [:name-localized :se] [:name-localized :en] [:marketing-name]])

(defn review-site-names
  "The site's own names that `draft` changes from `saved` (->review-base),
   shaped like the site ({:name …, :name-localized {:se …}, :marketing-name
   …}; nil removes one). A sync from the PTV tool sends these for the
   backend to write into the site, since it syncs the saved site. nil when
   no name changed."
  [saved draft]
  (not-empty
    (reduce (fn [names path]
              (let [v (not-empty (get-in draft path))]
                (if (= v (not-empty (get-in saved path)))
                  names
                  (assoc-in names path v))))
            {}
            site-name-paths)))

(defn apply-site-names
  "`site` with `names` (review-site-names) written in. A blank value
   removes a localized or marketing name."
  [site names]
  (cond-> site
    (not (str/blank? (:name names))) (assoc :name (:name names))
    (contains? names :marketing-name) (assoc-text [:marketing-name] (:marketing-name names))
    (:name-localized names) (as-> site
                                  (reduce-kv (fn [site locale v]
                                               (assoc-text site [:name-localized locale] v))
                                             site
                                             (:name-localized names)))))

;;; The PTV tool's view of a site ;;;

(defn sports-site->ptv-input [{:keys [types org-id org-langs]} service-channels services site]
  (let [service-id (-> site :ptv :service-ids first)
        service-channel-id (-> site :ptv :service-channel-ids first)

        summary (-> site :ptv :summary)
        description (-> site :ptv :description)
        user-instruction (-> site :ptv :user-instruction)

        last-sync (-> site :ptv :last-sync)

        ptv-channel (get service-channels service-channel-id)
        org-langs (or org-langs ptv-data/fallback-languages)
        ;; The languages the next sync would publish, from the org's live
        ;; config — not the site's stored :languages, which is a snapshot of
        ;; the last sync and would hide e.g. English texts written after the
        ;; org enabled English.
        langs (ptv-data/effective-languages site org-langs)
        ;; What differs from PTV and who changed it since the last sync, for
        ;; the saved site; the edit (`site`) shows what the user decided.
        ;; `:ptv-base` is what that sync sent and `:review-saved` the saved
        ;; site (both ->review-base), joined in by the caller; without the
        ;; base nobody can be told and every difference needs a decision.
        saved (review-saved-site site (:review-saved site))
        review (-> (sync-review saved (:ptv-base site) ptv-channel services
                                (ptv-data/effective-languages saved org-langs))
                   (annotate-review site))
        changed-in-ptv? (boolean (some review-choice-required? (:rows review)))
        ;; The edit differs from PTV (a review choice, or texts edited here):
        ;; the next sync has something to send.
        has-drift? (boolean (or (seq (:rows review))
                                (and (not= saved site)
                                     (seq (:rows (sync-review site (:ptv-base site) ptv-channel
                                                              services langs))))))

        ;; The languages the channel is published in now: PTV's own record when
        ;; the channel is cached, else what the last sync stored. A difference
        ;; means the next sync adds or removes a language version, so the site
        ;; needs a sync even when no text compared in `langs` changed.
        published-langs (if ptv-channel
                          (keep (comp #(some-> % name) ptv-data/lang->locale) (:languages ptv-channel))
                          (-> site :ptv :languages))
        languages-changed? (boolean (and last-sync
                                         (seq published-langs)
                                         (not= (set published-langs) (set langs))))

        ;; A site that was synced before (last-sync present) but now has no
        ;; service-channel-id was deliberately unlinked by the user — a
        ;; successful sync always writes service-channel-ids back, so this
        ;; combination can't arise any other way. (Use str/blank? rather than
        ;; empty?: clearing the autocomplete can leave service-channel-ids as
        ;; [nil]/[""], not [].) Treat it as out-of-date so the sync button
        ;; re-activates and the backend create-path (channel id nil) builds a
        ;; fresh PTV service-location. Without this, has-drift? is false (no
        ;; channel left to diff) and event-date still equals last-sync, so
        ;; sync-status would wrongly read :ok and the button would stay
        ;; disabled ("up to date").
        link-removed? (and last-sync (str/blank? service-channel-id))

        ;; A channel archived in PTV ("Deleted") that the user has re-enabled
        ;; needs a sync to resurrect it (the upsert PUTs publishingStatus
        ;; "Published" on the same channel). Without this, event-date still
        ;; equals last-sync (from the archive) and the archived channel can't be
        ;; diffed, so status reads :ok and "Synkronoi nyt" stays disabled.
        archived-resync? (and (= "Deleted" (-> site :ptv :publishing-status))
                              (get-in site [:ptv :sync-enabled] false))]

    {:valid (boolean (and (some-> description :fi count (> 5))
                          (some-> summary :fi count (> 5))))
     :lipas-id (:lipas-id site)
     :name (:name site)
     ;; differs from :name while a review choice renames the site
     :saved-name (:name saved)
     :event-date (:event-date site)
     :name-conflict (ptv-data/detect-name-conflict site (vals service-channels))
     :marketing-name (:marketing-name site)
     :type (-> site :search-meta :type :name :fi)
     :type-code (-> site :type :type-code)
     :sub-category (-> site :search-meta :type :sub-category :name :fi)
     :sub-category-id (-> site :type :type-code types :sub-category)
     :org-id org-id
     :admin (-> site :search-meta :admin :name :fi)
     :owner (-> site :search-meta :owner :name :fi)
     :summary summary
     :description description
     :user-instruction user-instruction
     :languages langs

     :sync-enabled (get-in site [:ptv :sync-enabled] false)
     :last-sync last-sync

     :sync-status (cond
                    (not last-sync) :not-synced
                    link-removed? :out-of-date
                    archived-resync? :out-of-date
                    changed-in-ptv? :content-drift
                    has-drift? :out-of-date
                    languages-changed? :out-of-date
                    (= (:event-date site) last-sync) :ok
                    :else :out-of-date)

     :review review
     :unpublished-ptv-languages (ptv-data/unpublished-ptv-languages site ptv-channel org-langs)

     :service-ids (-> site :ptv :service-ids)
     :service-name (-> services
                       (get service-id)
                       :serviceNames
                       (ptv-data/select-service-name))
     :service-channel-id service-channel-id
     :service-channel-ids (-> site :ptv :service-channel-ids)
     :service-channel-name (-> (get service-channels service-channel-id)
                               (ptv-data/resolve-service-channel-name))
     ;; Live status from the cached PTV channel (nil once archived — PTV drops
     ;; Deleted channels from /list/organization).
     :service-channel-publishing-status (:publishingStatus ptv-channel)
     ;; LIPAS-persisted status from the last sync/archive. Stays "Deleted" for
     ;; an archived channel, so the UI can label it instead of showing a bare
     ;; UUID it can no longer resolve to a name.
     :publishing-status (-> site :ptv :publishing-status)

     ;; The audit record itself, so the listing can caption its status
     ;; symbol (date + which field). This map replaces the site for the
     ;; listing, so reading it back off :ptv there finds nothing.
     :audit (-> site :ptv :audit)
     :audit-status (ptv-data/determine-audit-status (ptv-data/with-persisted-audit-content site))}))
