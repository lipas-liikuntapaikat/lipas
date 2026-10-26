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
            [lipas.data.ptv :as ptv-data]
            [lipas.tm35fin :as tm35fin]))

;;; Fields ;;;

(def review-fields
  "The fields the review compares, in the order their rows are shown.
   - :field         the first element of a row's :key
   - :group         which part of the review shows the row (review-groups)
   - :kind          how it is compared and decided:
                    :short    a short text (a name), side by side
                    :text     a long text, with a word diff
                    :one-slot one value in Lipas; PTV may hold several, of
                              which taking one replaces the rest
                    :extra    values PTV has that Lipas has no slot for, which
                              can be taken into a slot
                    :address  the visiting address (street and postal code)
                    :info     information only, never a choice
                    :distance the map point, a row only beyond
                              point-threshold-m; information only
                    :languages a language version PTV has that the sync
                              removes; information only
                    :set      a set of ids, a row per difference
   - :languages     :published (a row per published language) or the PTV
                    language codes it has rows for (texts only)
   - :takeable-by   who may take PTV's version into Lipas: :site-editor (it
                    changes the site's own data) or :ptv-manager (it changes
                    the site's PTV texts or connections); none when it can't
                    be taken
   - :reads         the payload keys it is derived from"
  [{:field :name :group :texts :kind :short :languages :published
    :takeable-by :site-editor :reads #{:serviceChannelNames}}
   {:field :marketing-name :group :texts :kind :short :languages ["fi"]
    :takeable-by :site-editor :reads #{:serviceChannelNames}}
   {:field :summary :group :texts :kind :text :languages :published
    :takeable-by :ptv-manager :reads #{:serviceChannelDescriptions}}
   {:field :description :group :texts :kind :text :languages :published
    :takeable-by :ptv-manager :reads #{:serviceChannelDescriptions}}
   {:field :phone :group :contacts :kind :one-slot
    :takeable-by :site-editor :reads #{:phoneNumbers :deleteAllPhoneNumbers}}
   {:field :email :group :contacts :kind :one-slot
    :takeable-by :site-editor :reads #{:emails :deleteAllEmails}}
   {:field :www :group :contacts :kind :one-slot
    :takeable-by :site-editor :reads #{:webPages :deleteAllWebPages}}
   {:field :reservations-link :group :contacts :kind :one-slot
    :takeable-by :site-editor :reads #{:webPages :deleteAllWebPages}}
   {:field :other-web-pages :group :contacts :kind :extra
    :takeable-by :site-editor :reads #{:webPages}}
   {:field :address :group :location :kind :address
    :takeable-by :site-editor :reads #{:addresses}}
   {:field :municipality :group :location :kind :info :reads #{:addresses}}
   {:field :point :group :location :kind :distance :reads #{:addresses}}
   {:field :languages :group :languages :kind :languages :reads #{:languages}}
   {:field :services :group :services :kind :set
    :takeable-by :ptv-manager :reads #{:services}}])

(def review-groups
  "The review's groups, in the order they are shown."
  [:texts :contacts :location :languages :services])

(def field-spec
  "field -> its entry in `review-fields`."
  (into {} (map (juxt :field identity)) review-fields))

(def info-kinds
  "Kinds whose rows are information only: they never need a choice."
  #{:info :distance :languages})

(def not-reviewed-payload-keys
  "Keys of the payload a sync sends (->ptv-service-location) that no review
   field reads, and why. A test fails when the payload has a key that is
   neither here nor in a review field's :reads."
  {:organizationId "fixed: the organisation the location belongs to"
   :sourceId "Lipas's own id for the location"
   :displayNameType "follows the published languages"
   :publishingStatus "the state of the link, not a value to choose"})

(def point-threshold-m
  "How far apart Lipas's and PTV's points may be before the review mentions
   it. On prod no point differed by 1–50 m (2026-10-23): PTV keeps what
   Lipas sent, or geocodes the address far off."
  50)

;;; Projection ;;;

(defn- review-text
  "A text as the review sees it: nil when blank, or when it is the
   placeholder a sync sends for a missing Finnish text."
  [v]
  (when-not (or (str/blank? v) (= ptv-data/placeholder v))
    v))

(def ^:private name-types {"Name" :name "AlternativeName" :marketing-name})

(def ^:private description-types {"Summary" :summary "Description" :description})

(def ^:private web-page-slots
  "A web page's name, in any language, -> the Lipas field Lipas names it for
   (ptv-data/web-page-names)."
  (into {} (for [[slot names] ptv-data/web-page-names
                 [_ page-name] names]
             [page-name slot])))

(defn- phone-value
  [{:keys [prefixNumber number isFinnishServiceNumber]}]
  (when-not (str/blank? number)
    {:prefix (when-not (str/blank? prefixNumber) prefixNumber)
     :number number
     :service? (boolean isFinnishServiceNumber)}))

(defn format-phone
  "A phone value (location-values) as Lipas keeps it, e.g. \"+358 858755700\";
   a Finnish service number has no prefix. ptv-data/parse-phone-number
   reads it back to the same value."
  [{:keys [prefix number]}]
  (str/trim (str prefix " " number)))

(defn- language-value
  "The value of `items` ({:value :language}) in PTV language `lang`."
  [items lang]
  (some #(when (= lang (:language %)) (:value %)) items))

(defn- street-text
  "The street of a street address as Lipas keeps it: the Finnish street
   (or the first one), plus the street number PTV keeps apart."
  [{:keys [street streetNumber]}]
  (let [s (or (language-value street "fi") (some :value street))]
    (when-not (str/blank? s)
      (cond-> (str/trim s)
        (not (str/blank? streetNumber)) (str " " (str/trim streetNumber))))))

(defn- parse-number
  [v]
  (cond
    (number? v) v
    (str/blank? v) nil
    :else #?(:clj (try (Double/parseDouble v) (catch Exception _ nil))
             :cljs (let [x (js/parseFloat v)] (when-not (js/isNaN x) x)))))

(defn- address-value
  "The visiting address of `location` (its first Location address) for the
   review: :street and :postal-code are compared; :post-office (PTV's
   Finnish one), :other-streets (streets in other languages that differ
   from the Finnish one) and :additional-info say what a sync would replace."
  [location]
  (when-let [street (some #(when (= "Location" (:type %)) (:streetAddress %))
                          (:addresses location))]
    (let [fi (street-text street)]
      (when (or fi (not (str/blank? (:postalCode street))))
        {:street fi
         :postal-code (:postalCode street)
         :post-office (language-value (:postOffice street) "fi")
         :other-streets (not-empty
                          (into {} (for [{:keys [value language]} (:street street)
                                         :when (and (not= "fi" language)
                                                    (not (str/blank? value))
                                                    (not= (str/lower-case value)
                                                          (some-> (language-value (:street street) "fi")
                                                                  str/lower-case)))]
                                     [language value])))
         :additional-info (not-empty (vec (remove (comp str/blank? :value)
                                                  (:additionalInformation street))))}))))

(defn- location-point
  "[easting northing] (TM35FIN) of `location`'s visiting address."
  [location]
  (let [street (some #(when (= "Location" (:type %)) (:streetAddress %))
                     (:addresses location))
        e (parse-number (:longitude street))
        n (parse-number (:latitude street))]
    (when (and e n) [e n])))

(defn- location-municipality
  "The municipality code of `location`'s visiting address; nil without one
   (a payload for a site without a city pads nil into \"null\")."
  [location]
  (let [m (some #(when (= "Location" (:type %)) (-> % :streetAddress :municipality))
                (:addresses location))
        code (if (map? m) (:code m) m)]
    (when (and (string? code) (re-matches #"\d+" code))
      code)))

(defn- text-languages
  "The languages `location` has a name or a description in."
  [location]
  (into #{} (comp (remove (comp str/blank? :value)) (keep :language))
        (concat (:serviceChannelNames location) (:serviceChannelDescriptions location))))

(defn location-values
  "Review key -> value of a PTV service location: the payload a sync sends
   (->ptv-service-location) or the location as PTV has it (GET); the two
   share a shape. Texts are keyed [field language], with PTV's language
   codes, and left out when blank. The rest are keyed [field]:
   - one-slot fields and [:other-web-pages] are sets (of URLs, emails, or
     phones {:prefix :number :service?}). A web page goes to the slot its
     name is Lipas's name for, others to [:other-web-pages];
   - [:address] {:street :postal-code …} (see address-value);
   - [:municipality] the code, [:point] [easting northing] (TM35FIN);
   - [:languages] the language versions (those with texts when the location
     lists none) and [:services] the ids of the connected services."
  [location]
  (let [texts (fn [k types]
                (for [{:keys [type language value]} (get location k)
                      :let [field (types type)
                            v (review-text value)]
                      :when (and field v)]
                  [[field language] v]))
        services (:services location)
        pages (group-by #(get web-page-slots (:value %) :other-web-pages) (:webPages location))
        urls (fn [slot] (into #{} (keep #(some-> (:url %) str/trim not-empty)) (get pages slot)))]
    (-> {}
        (into (texts :serviceChannelNames name-types))
        (into (texts :serviceChannelDescriptions description-types))
        (assoc [:languages] (if (seq (:languages location))
                              (set (:languages location))
                              (text-languages location))
               ;; the payload lists ids, a GET the services themselves
               [:services] (if (every? string? services)
                             (set services)
                             (set (ptv-data/channel-service-ids location)))
               [:phone] (into #{} (keep phone-value) (:phoneNumbers location))
               [:email] (into #{} (keep #(some-> (:value %) str/trim not-empty)) (:emails location))
               [:www] (urls :www)
               [:reservations-link] (urls :reservations-link)
               [:other-web-pages] (urls :other-web-pages)
               [:address] (address-value location)
               [:municipality] (location-municipality location)
               [:point] (location-point location)))))

(defn site->location
  "The payload a sync of `site` sends, for comparing it: `langs` are the
   languages it publishes (LIPAS codes, see effective-languages)."
  [site langs]
  (ptv-data/->ptv-service-location
    nil
    tm35fin/from-wgs84
    ""
    (-> site
        (assoc-in [:ptv :languages] langs)
        ;; not compared; a missing one would be made up (and logged)
        (assoc-in [:ptv :source-id] "review"))))

(defn- slot-key
  "What one value of a one-slot or extra field is compared by."
  [field v]
  (case field
    :email (str/lower-case v)
    v))

(defn- comparable
  "What the review compares of `field`'s value `v`: equal comparables are no
   difference. Blank and empty are nil."
  [field v]
  (case (:kind (field-spec field))
    (:short :text) (ptv-data/normalize-ws v)
    (:one-slot :extra) (not-empty (into #{} (map #(slot-key field %)) v))
    :address (when v
               (let [street (some-> (:street v) ptv-data/normalize-ws str/lower-case)
                     postal (some-> (:postal-code v) str/trim not-empty)]
                 (when (or street postal) [street postal])))
    :set (not-empty v)
    v))

(defn- distance-m
  [[e1 n1] [e2 n2]]
  (Math/sqrt (+ (Math/pow (- e1 e2) 2) (Math/pow (- n1 n2) 2))))

(defn same-value?
  "Whether `a` and `b`, values of `field` (location-values), are no
   difference: the review compares them as `comparable` (whitespace and
   case where PTV rewrites them; a point within point-threshold-m)."
  [field a b]
  (if (= :distance (:kind (field-spec field)))
    (or (nil? a) (nil? b) (<= (distance-m a b) point-threshold-m))
    (= (comparable field a) (comparable field b))))

;;; Base ;;;

(def review-base-ptv-keys
  "The :ptv keys of a sync's own revision that the sync review compares
   against: what the sync sent, which location it went to and when."
  [:summary :description :service-ids :service-channel-ids :languages :last-sync
   :ptv-modified])

(def ^:private review-site-paths
  "The site's own data a sync derives the location from. The geometry only
   by its point (ptv-data/site-wgs84-point), to keep the base small."
  [[:name] [:name-localized] [:marketing-name]
   [:email] [:phone-number] [:www] [:reservations-link]
   [:location :address] [:location :postal-code] [:location :postal-office]
   [:location :city :city-code]
   [:search-meta :location :wgs84-point]])

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
  (let [point (ptv-data/site-wgs84-point site)]
    (-> (select-paths site review-site-paths)
        (cond-> point (assoc-in [:search-meta :location :wgs84-point] point))
        (assoc :lipas-id (:lipas-id site))
        (assoc :ptv (select-keys (:ptv site) review-base-ptv-keys)))))

(defn- usable-review-base
  "`base` when it is what the last sync of `site` sent to `channel`; nil when
   it isn't (another sync since, or the site now points at another
   location)."
  [site base channel]
  (when (and base
             (= (-> base :ptv :last-sync) (-> site :ptv :last-sync))
             (= (-> base :ptv :service-channel-ids first) (:id channel)))
    base))

;;; Evidence of a change made in PTV ;;;

(def ^:private sync-margin-ms
  "How long after a sync's :last-sync PTV's own :modified of the write may
   be, for a site synced before Lipas stored that :modified. :last-sync is
   taken before the write, which normally takes 1–5 s."
  (* 2 60 1000))

(defn- instant-ms
  "Epoch milliseconds of the ISO-8601 time `s`; one without a zone is UTC,
   as PTV's :modified is. nil when it can't be read."
  [s]
  (when (string? s)
    (let [s (cond-> (str/replace s #"(\.\d{3})\d+" "$1")
              (not (re-find #"(Z|[+-]\d{2}:?\d{2})$" s)) (str "Z"))]
      #?(:clj (try (.toEpochMilli (java.time.Instant/parse s))
                   (catch Exception _ nil))
         :cljs (let [ms (js/Date.parse s)]
                 (when-not (js/isNaN ms) ms))))))

(defn ptv-changed-since-sync?
  "Whether someone has demonstrably changed `channel` in PTV since the last
   sync of `site`: PTV's :modified differs from the one PTV reported right
   after that sync ([:ptv :ptv-modified]) or, for a site synced before that
   was stored, is over 2 minutes after the sync. When it can't be told (a
   time missing or unreadable), true: a difference PTV may have made is
   asked about rather than overwritten.

   A change in how Lipas derives the payload since the last sync makes the
   base differ from PTV with nobody touching PTV; this tells that apart."
  [site channel]
  (let [modified (:modified channel)
        stored (-> site :ptv :ptv-modified)]
    (cond
      (str/blank? modified) true
      (not (str/blank? stored)) (not= modified stored)
      :else (let [m (instant-ms modified)
                  s (instant-ms (-> site :ptv :last-sync))]
              (or (nil? m) (nil? s) (> m (+ s sync-margin-ms)))))))

;;; Review ;;;

(defn review-text-path
  "Where a text row's Lipas value lives in a site."
  [{:keys [field locale]}]
  (case field
    :name (if (= :fi locale) [:name] [:name-localized locale])
    :marketing-name [:marketing-name]
    (:summary :description) [:ptv field locale]))

(defn- language-paths
  "Where a language version's name, summary and description live in a
   site."
  [locale]
  [(if (= :fi locale) [:name] [:name-localized locale])
   [:ptv :summary locale]
   [:ptv :description locale]])

(defn row-site-paths
  "The site paths `row` stands for: where taking a version writes it."
  [{:keys [field locale] :as row}]
  (case field
    (:name :marketing-name :summary :description) [(review-text-path row)]
    :phone [[:phone-number]]
    :email [[:email]]
    :www [[:www]]
    :reservations-link [[:reservations-link]]
    :other-web-pages [[:www] [:reservations-link]]
    :address [[:location :address] [:location :postal-code] [:location :postal-office]]
    ;; a language Lipas doesn't support (no locale) can't be taken
    :languages (if locale (language-paths locale) [])
    :services [[:ptv :service-ids]]
    []))

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

(defn- language-sort-key
  [lang]
  [(get (zipmap ptv-language-order (range)) lang (count ptv-language-order)) lang])

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

(defn- site-values
  "{path value} of `site` at `paths`."
  [site paths]
  (into {} (for [p paths] [p (get-in site p)])))

(defn- with-sent-service-ids
  "`site` with the service ids its sync sends: after the type-change swap
   (ptv-data/rebound-service-ids), when `services` (by id) has the org's
   LIPAS services to swap."
  [site services]
  (let [source-id->service (into {} (keep (fn [[id s]]
                                            (when-let [sid (:sourceId s)]
                                              [sid (assoc s :id id)])))
                                 services)]
    (cond-> site
      (seq source-id->service)
      (assoc-in [:ptv :service-ids] (ptv-data/rebound-service-ids source-id->service site)))))

(defn- assign-pages-by-url
  "PTV's `values` with its [:other-web-pages] matched to Lipas's slots by
   URL: a page PTV keeps unnamed (synced before Lipas named them) or under
   another name is still the slot's when its URL is the one Lipas has
   there."
  [values lipas]
  (reduce (fn [values slot]
            (let [urls (set/intersection (get values [:other-web-pages] #{}) (get lipas [slot]))]
              (-> values
                  (update [slot] (fnil into #{}) urls)
                  (update [:other-web-pages] #(set/difference % urls)))))
          values
          [:www :reservations-link]))

(defn sync-review
  "Compares a site with its PTV service location three ways: `site` (the
   saved site: what a sync of it sends), `channel` (PTV now, GET shape) and
   `base` (what the last sync sent, see ->review-base). nil without a
   channel, else

   {:rows [...] :matching [...] :services {...} :base? bool :first-link? bool
    :evidence? bool :langs [...] :last-sync … :ptv-modified …
    :postal-addresses {:kept [...] :dropped [...]} :service-hours? bool}

   A row is a difference between Lipas and PTV, keyed by :key, with its
   field's :group, :kind and :takeable-by (review-fields), and :lipas, :ptv
   and :base, the values as the payloads have them (location-values; :base
   nil when the last sync didn't send it). :saved is {path value} of the
   site where taking a version writes (row-site-paths). By kind:
   - a text: also :field, :language (PTV code) and :locale;
   - :one-slot, :extra: sets of values; :address: an address-value;
   - :distance: [easting northing], and :distance-m between them;
   - :languages: a language version PTV has that the sync removes, with
     :language, :locale and :texts, PTV's {field text} in it;
   - a connection: :field :services, :id, :name, and :lipas?, :ptv? and
     :base?, whether each is connected to the service.
   Its :origin says who changed it since the last sync: :lipas, :ptv or
   :both. A value counts as changed in PTV only with evidence that PTV was
   edited since (ptv-changed-since-sync?); without it the difference is
   Lipas's, e.g. from a change in how Lipas derives the payload.
   Connections need no evidence. Without a usable base the origin is
   :first-link (LIPAS hasn't synced the site to this location) or :unknown
   (it has, but the base wasn't found). :language-added? marks a text in a
   language PTV doesn't have: Lipas adds the language version, its own
   change.

   :matching lists the {:field :language} pairs (texts) and {:field} that
   agree, for telling the user what is up to date. :services has every
   service each side connects the location to, {:lipas [{:id :name}] :ptv
   [...]}, for showing a difference in context. :langs are `langs`, for
   annotate-review. :postal-addresses and :service-hours? say what PTV keeps
   that Lipas has no field for (a sync leaves them be, but drops a postal
   address PTV's API rejects).

   `services` resolves service names (services-by-id) and, when it holds
   the org's LIPAS services with their :sourceId, the swap a type change
   makes; `langs` are the languages the next sync publishes
   (effective-languages)."
  [site base channel services langs]
  (when channel
    (let [base (usable-review-base site base channel)
          no-base (if (ptv-data/first-link? (:ptv site) channel) :first-link :unknown)
          evidence? (ptv-changed-since-sync? site channel)
          base-langs (or (-> base :ptv :languages) langs)
          lipas (location-values (site->location (with-sent-service-ids site services) langs))
          ;; the languages a sync of the site publishes; a language PTV has
          ;; besides them is removed by the sync, not compared text by text
          published (get lipas [:languages])
          ptv (assign-pages-by-url (location-values channel) lipas)
          ptv-langs (get ptv [:languages])
          sent (when base (location-values (site->location base base-langs)))
          origin (fn [field b l p]
                   (cond
                     (same-value? field l p) nil
                     (not base) no-base
                     :else (let [o (cond
                                     (same-value? field b p) :lipas
                                     (same-value? field b l) :ptv
                                     :else :both)]
                             ;; without evidence PTV still has what was sent
                             (if (and (#{:ptv :both} o) (not evidence?)) :lipas o))))
          compared (for [[field language locale] (text-keys published)
                         :let [k [field language]
                               text {:field field :language language :locale locale}
                               l (get lipas k)
                               p (get ptv k)
                               ;; a language version PTV doesn't have at all
                               added? (and (not (contains? ptv-langs language)) (nil? p))]]
                     (merge (assoc text
                                   :key k
                                   :lipas l
                                   :ptv p
                                   :base (get sent k)
                                   :saved (site-values site [(review-text-path text)])
                                   :origin (when-not (same-value? field l p)
                                             (if added? :lipas (origin field (get sent k) l p)))
                                   :language-added? (boolean (and added? l)))
                            (row-spec text)))
          values (for [{:keys [field kind]} review-fields
                       :when (#{:one-slot :extra :address :info :distance} kind)
                       :let [k [field]
                             l (get lipas k)
                             p (get ptv k)
                             b (get sent k)
                             o (origin field b l p)]
                       :when o]
                   (let [row {:key k :field field :lipas l :ptv p :base b :origin o}]
                     (merge (assoc row :saved (site-values site (row-site-paths row)))
                            (when (= :distance kind) {:distance-m (Math/round (double (distance-m l p)))})
                            (row-spec row))))
          removed-langs (sort-by language-sort-key (set/difference ptv-langs published))
          languages (for [language removed-langs
                          :let [locale (ptv-data/lang->locale language)
                                row {:key [:languages language]
                                     :field :languages
                                     :language language
                                     :locale locale
                                     :texts (into {} (for [f [:name :summary :description]
                                                           :let [v (get ptv [f language])]
                                                           :when v]
                                                       [f v]))
                                     :origin (cond
                                               (not base) no-base
                                               (contains? (set (map ptv-data/lipas-lang->ptv-lang base-langs))
                                                          language) :lipas
                                               :else :ptv)}]]
                      (merge (assoc row :saved (site-values site (row-site-paths row)))
                             (row-spec row)))
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
                                ;; connections need no evidence: connecting a
                                ;; service from its side doesn't change the
                                ;; location's :modified
                                :origin (if base
                                          (cond
                                            (= (contains? base-ids id) p?) :lipas
                                            (= (contains? base-ids id) l?) :ptv
                                            :else :both)
                                          no-base)}
                               (row-spec {:field :services})))]
      {:rows (vec (concat (filter :origin compared) values languages connections))
       :matching (vec (concat
                        (for [{:keys [field language origin lipas]} compared
                              :when (and (nil? origin) (ptv-data/normalize-ws lipas))]
                          {:field field :language language})
                        (for [field [:phone :email :www :reservations-link :address]
                              :when (and (comparable field (get lipas [field]))
                                         (same-value? field (get lipas [field]) (get ptv [field])))]
                          {:field field})
                        (when (and (empty? connections) (seq lipas-ids))
                          [{:field :services}])))
       :services {:lipas (named lipas-ids) :ptv (named ptv-ids)}
       :base? (some? base)
       :first-link? (= :first-link no-base)
       :evidence? evidence?
       :last-sync (-> site :ptv :last-sync)
       :ptv-modified (:modified channel)
       :postal-addresses (ptv-data/ptv-postal-addresses channel published)
       :service-hours? (boolean (seq (:serviceHours channel)))
       :langs langs
       ;; for annotate-review's type-change swap
       :services-by-id services})))

(defn review-choice-required?
  "Whether `row` needs the user's decision before a sync overwrites PTV with
   Lipas: PTV changed it, or who did can't be told. Information rows never
   do. On a first link the location keeps its connections
   (link-service-ids), so a connection row needs no decision there."
  [row]
  (and (not (contains? info-kinds (:kind row)))
       (case (:origin row)
         (:ptv :both :unknown) true
         :first-link (not= :services (:field row))
         false)))

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
   (something of the user's own)."
  [values {:keys [field kind] :as row}]
  (case kind
    :set (if (= (contains? (get values [:services]) (:id row)) (:ptv? row))
           :ptv
           :lipas)

    :languages (if (contains? (get values [:languages]) (:language row)) :ptv :lipas)

    (:info :distance) :lipas

    :extra (let [held (into #{} (mapcat #(get values [%])) [:www :reservations-link])]
             (if (seq (set/intersection held (set (:ptv row)))) :ptv :lipas))

    (let [v (comparable field (get values (:key row)))
          p (comparable field (:ptv row))]
      (cond
        (= v p) :ptv
        ;; one of PTV's several values taken into the one slot
        (and (= :one-slot kind) (= 1 (count v)) (contains? p (first v))) :ptv
        (= v (comparable field (:lipas row))) :lipas
        :else :edited))))

(defn annotate-review
  "`review` (sync-review of the saved site) with what the edit `draft` now
   holds on each row: :current (:lipas, :ptv or :edited) and :draft, its
   value. The edit is projected as its sync sends it, with the same
   type-change swap as the review's Lipas side."
  [review draft]
  (if-not review
    review
    (let [values (location-values (site->location (with-sent-service-ids draft (:services-by-id review))
                                                  (:langs review)))]
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
    (comparable (:field row) (:ptv row))))

(defn review-choice
  "The decision to keep Lipas's version of `row` recorded in `choices` (row
   :key -> {:choice :lipas :seen}), or nil when there is none or it was made
   against a PTV value that has changed since. Taking PTV's version or
   editing the value needs no record: the edit shows it."
  [choices row]
  (when-let [choice (get choices (:key row))]
    (when (= (:seen choice) (review-choice-seen row))
      choice)))

(defn review-decided?
  "Whether the user has decided `row` (annotated, see annotate-review): the
   edit holds PTV's version or their own, or they chose to keep Lipas's."
  [choices row]
  (boolean (or (#{:ptv :edited} (:current row))
               (review-choice choices row))))

(defn open-review-rows
  "The rows of `rows` (annotated) that need a decision and don't have one."
  [rows choices]
  (filterv #(and (review-choice-required? %)
                 (not (review-decided? choices %)))
           rows))

(defn- title-case
  "\"UTAJÄRVI\" -> \"Utajärvi\", word by word (also after a hyphen)."
  [s]
  (when-not (str/blank? s)
    (let [s (str/lower-case (str/trim s))]
      (apply str (map-indexed (fn [i c]
                                (if (or (zero? i) (contains? #{" " "-"} (str (nth s (dec i)))))
                                  (str/upper-case (str c))
                                  (str c)))
                              s)))))

(defn- slot-text
  "A one-slot value as Lipas keeps it."
  [field v]
  (if (= :phone field) (format-phone v) v))

(defn ptv-values
  "PTV's values of a one-slot or extra `row`, in a stable order."
  [row]
  (sort-by str (:ptv row)))

(defn takeable-in-bulk?
  "Whether \"take all of PTV's changes\" can take `row`: one value to take.
   A one-slot row with several PTV values, an extra row (which slot?) and
   information rows need a pick of their own."
  [row]
  (case (:kind row)
    :one-slot (<= (count (:ptv row)) 1)
    (:extra :info :distance :languages) false
    true))

(defn review-writes
  "What to write into the edit `draft` to make it hold `side`'s version of
   `row` (:ptv, or :lipas for the saved one): {path value}, a nil value
   removing the key. A one-slot row takes PTV's only value (several:
   review-value-writes)."
  [draft row side]
  (if (= :services (:field row))
    (let [connected? (if (= :ptv side) (:ptv? row) (:lipas? row))
          ids (vec (-> draft :ptv :service-ids))]
      {[:ptv :service-ids] (if connected?
                             (vec (distinct (conj ids (:id row))))
                             (vec (remove #{(:id row)} ids)))})
    (if (= :lipas side)
      (:saved row)
      (let [{:keys [field kind ptv]} row]
        (case kind
          (:short :text) {(review-text-path row) (when-not (str/blank? ptv) ptv)}
          :one-slot {(first (row-site-paths row)) (some->> (first (ptv-values row)) (slot-text field))}
          :address (cond-> {}
                     (:street ptv) (assoc [:location :address] (:street ptv))
                     (:postal-code ptv) (assoc [:location :postal-code] (:postal-code ptv))
                     (:post-office ptv) (assoc [:location :postal-office] (title-case (:post-office ptv))))
          :languages (into {} (for [[path f] (map vector (row-site-paths row)
                                                  [:name :summary :description])
                                    :let [v (get-in row [:texts f])]
                                    :when v]
                                [path v]))
          {})))))

(defn review-value-writes
  "What to write into the edit to make it hold one of PTV's values: `value`
   of a one-slot `row`, or for an extra row (other web pages) {:slot field
   :url url}, the URL taken into that slot."
  [row value]
  (case (:kind row)
    :one-slot {(first (row-site-paths row)) (slot-text (:field row) value)}
    :extra {[(:slot value)] (:url value)}))

(defn apply-review-writes
  "`draft` with `writes` (review-writes) made."
  [draft writes]
  (reduce-kv assoc-text* draft writes))

;;; Writing the site's own data from the PTV tool ;;;

(def site-patch-paths
  "The site's own data that taking PTV's version in the review can change:
   the paths of the fields a site editor may take (review-fields
   :takeable-by :site-editor). The PTV tool and the wizard sync the saved
   site, so they send these as a patch for the backend to write first."
  [[:name] [:name-localized :se] [:name-localized :en] [:marketing-name]
   [:phone-number] [:email] [:www] [:reservations-link]
   [:location :address] [:location :postal-code] [:location :postal-office]])

(def ^:private required-site-paths
  "Of site-patch-paths, those a site can't be without: a patch never
   removes them."
  #{[:name] [:location :address] [:location :postal-code]})

(defn review-site-patch
  "The site's own data that `draft` changes from `saved` (->review-base),
   shaped like the site ({:name …, :location {:address …}}; nil removes a
   value). nil when nothing changed."
  [saved draft]
  (not-empty
    (reduce (fn [patch path]
              (let [v (not-empty (get-in draft path))]
                (if (= v (not-empty (get-in saved path)))
                  patch
                  (assoc-in patch path v))))
            {}
            site-patch-paths)))

(defn site-patch-path?
  "Whether `path` is one a site patch may write."
  [path]
  (boolean (some #{path} site-patch-paths)))

(defn site-patch-paths-of
  "The paths `patch` (shaped like a site) sets, nested maps followed down."
  [patch]
  (letfn [(walk [prefix m]
            (mapcat (fn [[k v]]
                      (let [path (conj prefix k)]
                        (if (and (map? v) (not (site-patch-path? path)))
                          (walk path v)
                          [path])))
                    m))]
    (walk [] patch)))

(defn apply-site-patch
  "`site` with `patch` (review-site-patch) written in. A blank value removes
   an optional field; a required one keeps its value."
  [site patch]
  (reduce (fn [site path]
            (let [v (get-in patch path)]
              (if (str/blank? v)
                (if (contains? required-site-paths path)
                  site
                  (assoc-text site path nil))
                (assoc-in site path v))))
          site
          (filter site-patch-path? (site-patch-paths-of patch))))

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
        ;; An edit made in the PTV tool but not saved that leaves no difference
        ;; to review, e.g. turning sync back on for a site saved with sync off.
        ;; Without this the site read :ok and its sync was disabled.
        unsaved-edits? (ptv-data/unsaved-ptv-edits? site)

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
                    unsaved-edits? :out-of-date
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
