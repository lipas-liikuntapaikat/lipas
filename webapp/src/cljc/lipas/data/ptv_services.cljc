(ns lipas.data.ptv-services
  "The Palvelut tab of the PTV tool: one table row per PTV service of the
   org. Everything a row shows (its sync status, the sync review, the audit
   verdict, its service locations and the Lipas sites linked to it) is
   derived here once, for both the row and its open panel, so the two
   can't disagree."
  (:require [clojure.string :as str]
            [lipas.data.ptv :as ptv-data]
            [lipas.data.ptv-review :as ptv-review]))

(defn service-edit-key
  "The key of a service's unsaved edits in app-db: the source-id for a
  service LIPAS created (shared with the creation wizard), the PTV id for
  one made in PTV, which may have no source-id yet and keeps its id across
  the first save."
  [service]
  (if (= :lipas (:management service))
    (:source-id service)
    (:service-id service)))

(defn sync-status
  "The service's sync status: the sites' (ptv-review/sync-statuses) minus
   :not-synced, plus :other-system for a service another system maintains
   (read-only in Lipas). :blocked also when the edits hold texts PTV would
   reject (`invalid-texts?`), as the sync can't go through then either.
   :ready also when there is nothing to send but PTV's version to record
   as Lipas's (`take-ptv-version?`). nil for a service made in PTV that
   Lipas hasn't written and holds no edits for: Lipas doesn't track it
   (yet)."
  [{:keys [other-system? modified? open-rows invalid-texts? has-local-edits? take-ptv-version?
           lipas-managed?]}]
  (cond
    other-system? :other-system
    modified? :blocked
    (seq open-rows) :awaiting-choice
    invalid-texts? :blocked
    (or has-local-edits? take-ptv-version?) :ready
    lipas-managed? :ok))

(def ^:private text-fields [:name :summary :description :user-instruction])

(defn- text-locales
  "The locales `texts` ({field {locale text}}) have some text in."
  [texts]
  (into #{} (for [[_ by-locale] texts
                  [locale v] by-locale
                  :when (not (str/blank? v))]
              locale)))

(defn service-state
  "What the Palvelut tab shows of `service` (a ::subs/services entry) given
   the user's unsaved edits `descriptions` ({field {locale text}}, plus
   :remove-languages), the org's languages, Lipas's mirror of the service
   after its last write `base` (nil when Lipas hasn't written it), the
   `choices` made in its review and its `audit`:

   - :texts             the edit: Lipas's version of the names and texts
                        (ptv-review/service-lipas-texts) with the user's
                        edits over it, what the form shows and a sync sends
   - :data              the body a sync sends
   - :text-errors       the texts PTV would reject, which block the sync
   - :versions-to-drop  the language versions the sync removes from PTV
                        (a service Lipas created)
   - :has-local-edits?  whether a sync has anything to send
   - :invalid-texts?    whether it has, and :text-errors block it
   - :review, :open-rows the sync review, and its rows still to decide
   - :take-ptv-version? nothing to send, every change PTV made taken as it
                        is: the sync records PTV's version as Lipas's,
                        else the review would come back
   - :last-modified-in  :lipas when PTV's :modified is that of Lipas's last
                        write, else :ptv
   - :audit-status      ptv-data/audit-display-status
   - :sync-status       see sync-status

   and what the panel needs to edit it: :edit-key, :edited-name, :name-data,
   :edit-languages (a native's: the org's languages and every version it
   or Lipas's version has) and :removals (language versions marked for
   removal)."
  [{:keys [org-id service descriptions org-languages base choices audit]}]
  (let [source-id (:source-id service)
        management (:management service)
        native? (= :native management)
        other-system? (= :other-system management)
        edit-key (service-edit-key service)
        ptv-texts {:name (:name service)
                   :summary (:summary service)
                   :description (:description service)
                   :user-instruction (:user-instruction service)}
        ;; PTV has changed the service since Lipas's last write: the edit
        ;; starts from Lipas's version, and PTV's changes are a review
        changed-in-ptv? (ptv-review/service-changed-since-write? base (:service-modified service))
        lipas-texts (ptv-review/service-lipas-texts base ptv-texts changed-in-ptv?)
        texts (merge-with merge lipas-texts (select-keys descriptions text-fields))
        edited-name (when-not native? (not-empty (get-in descriptions [:name :fi])))
        ;; A native service: names per language, the org's languages plus
        ;; every version it or Lipas's version has, and versions marked for
        ;; removal
        name-data (:name texts)
        edit-languages (when native?
                         (into (set org-languages)
                               (map name)
                               (concat (:language-versions service) (text-locales lipas-texts))))
        removals (set (:remove-languages descriptions))
        data (if native?
               (merge {:org-id org-id
                       :mode "native"
                       :service-id (:service-id service)
                       :expected-modified (:service-modified service)
                       :city-codes (:city-codes service)
                       :languages org-languages
                       :remove-languages (mapv name removals)}
                      texts)
               (cond-> (merge {:org-id org-id
                               :source-id source-id
                               :service-id (:service-id service)
                               :city-codes (:city-codes service)
                               :sub-category-id (ptv-data/parse-service-source-id source-id)
                               :languages org-languages
                               ;; the service as the review saw it: one
                               ;; changed in PTV since is refused, as the
                               ;; review didn't show that change
                               :expected-modified (:service-modified service)}
                              texts)
                 edited-name (assoc :service-name edited-name)))
        ;; A native's patch, built as the backend builds it
        native-body (when native?
                      (ptv-data/native-patch-body org-id (:service-id service)
                                                  (:ptv-entries service)
                                                  texts
                                                  (keep ptv-data/locale->lang removals)))
        problems (when native?
                   (ptv-data/language-version-problems native-body))
        ;; What blocks the sync, checked on the body the sync will send
        text-errors (case management
                      :native (ptv-data/service-text-errors {:management :native
                                                             :texts texts
                                                             :version-problems problems
                                                             :body native-body})
                      :lipas (ptv-data/lipas-service-text-errors data (:ptv-entries service))
                      nil)
        versions-to-drop (when (= :lipas management)
                           (ptv-data/service-versions-to-drop (:language-versions service)
                                                              texts
                                                              org-languages))
        ;; The edit against PTV and Lipas's version, in the languages a sync
        ;; publishes: a generated text in any other language (the generator
        ;; writes fi/se/en) never reaches PTV, and would read as a
        ;; difference forever
        review (when-not other-system?
                 (ptv-review/service-review
                   {:edit texts
                    :ptv ptv-texts
                    :base lipas-texts
                    :base? (some? base)
                    :changed-in-ptv? changed-in-ptv?
                    :locales (map keyword (or edit-languages org-languages))
                    :last-sync (:last-sync base)
                    :ptv-modified (:service-modified service)}))
        rows (:rows review)
        has-local-edits? (boolean (or (some #(= :lipas (:current %)) rows)
                                      (seq removals)))
        state {:edit-key edit-key
               :native? native?
               :other-system? other-system?
               :lipas-managed? (boolean (some-> source-id (str/starts-with? "lipas-")))
               :modified? (= "Modified" (:publishing-status service))
               :descriptions descriptions
               :texts texts
               :edited-name edited-name
               :name-data name-data
               :edit-languages edit-languages
               :removals removals
               :data data
               :text-errors text-errors
               :versions-to-drop versions-to-drop
               :has-local-edits? has-local-edits?
               ;; without edits nothing is sent, whatever PTV holds
               :invalid-texts? (boolean (and has-local-edits? (seq text-errors)))
               :review review
               :open-rows (ptv-review/open-review-rows rows choices)
               :take-ptv-version? (boolean (and (seq rows) (not has-local-edits?)))
               :last-modified-in (if (and base (not changed-in-ptv?) (not (:read-from-ptv base)))
                                   :lipas
                                   :ptv)
               :audit audit
               :audit-status (ptv-data/audit-display-status
                               audit (ptv-data/service-audit-fields ptv-texts))}]
    (assoc state :sync-status (sync-status state))))

(defn review-writes
  "What to write into a service's edits to make the edit hold `side`'s
   version of each of `rows` (its service-review rows): {[field locale]
   text}, nil dropping the edit so that the field shows Lipas's version.
   Taking PTV's version writes PTV's text (\"\" for none, as a cleared
   field). Keeping Lipas's drops the edit where it holds PTV's, and leaves
   the user's own text be: it is Lipas's side."
  [rows side]
  (into {}
        (for [{:keys [field locale ptv current]} rows
              :when (or (= :ptv side) (= :ptv current))]
          [[field locale] (when (= :ptv side) (or ptv ""))])))

(defn apply-review-writes
  "`descriptions`, a service's edits, with `writes` (review-writes) made."
  [descriptions writes]
  (reduce-kv (fn [m [field locale] v]
               (if (some? v)
                 (assoc-in m [field locale] v)
                 (let [m (update m field dissoc locale)]
                   (cond-> m (empty? (get m field)) (dissoc field)))))
             descriptions
             writes))

(defn review-choices
  "`choices` with what taking `side`'s version of `rows` records: keeping
   Lipas's version is recorded against PTV's text of the time
   (ptv-review/service-kept-choice); taking PTV's needs no record, the edit
   shows it."
  [choices rows side]
  (reduce (fn [choices row]
            (if (= :lipas side)
              (assoc choices (:key row) (ptv-review/service-kept-choice row))
              (dissoc choices (:key row))))
          choices
          rows))

(def base-keys
  "What the review needs of a document of Lipas's mirror of a service
   (ptv_service)."
  [:name :summary :description :user-instruction :languages :last-sync :ptv-modified
   :read-from-ptv])

(defn written-base
  "The base Lipas's mirror holds of `service` (PTV shape) once Lipas has
   written it, or recorded it as read from PTV (`read?`): for the panel
   until the mirror itself is fetched again."
  [service read?]
  (cond-> (-> (ptv-data/->service-document nil service)
              (assoc :ptv-modified (:modified service))
              (select-keys base-keys))
    read? (assoc :read-from-ptv true)))

(defn drop-written-edits
  "The user's edits of a service, `descriptions`, without what a write left
   in PTV: each name and text now equal to the written `service`'s own goes.
   Otherwise an edit already sent would stay, and come back as the user's
   edit (a conflict even) once the text is changed in PTV. What was typed
   while the write was on its way differs, and stays. nil when nothing is
   left."
  [descriptions service]
  (let [ptv (assoc (ptv-data/ptv-descriptions->texts (:serviceDescriptions service))
                   :name (ptv-data/service-names service))
        norm ptv-data/normalize-ws]
    (not-empty
      (reduce-kv (fn [m field v]
                   (cond
                     (= :remove-languages field) m
                     (map? v) (let [left (into {}
                                               (remove (fn [[locale text]]
                                                         (= (norm text) (norm (get-in ptv [field locale])))))
                                               v)]
                                (cond-> m (seq left) (assoc field left)))
                     :else (assoc m field v)))
                 {}
                 descriptions))))

(defn service-channels
  "The service's service locations in PTV, the places Suomi.fi shows for
   it, by name, each with the :lipas-ids of the Lipas sites whose location
   it is: {:locations [...]}. The integration concerns service locations
   only, so the service's other channels (web pages, phone, printable
   forms, e-services) are left out, and so is another organisation's
   channel: the org's own listing, `org-channels`, can't tell its type, and
   a Lipas site's location is always the org's own."
  [service org-channels channel->lipas-ids]
  {:locations (->> (for [{:keys [id name]} (:service-channels service)
                         :let [channel (get org-channels id)]
                         :when (= "ServiceLocation" (:serviceChannelType channel))]
                     {:id id
                      :name (or (ptv-data/resolve-service-channel-name channel) name)
                      :lipas-ids (get channel->lipas-ids id #{})})
                   (sort-by (comp str/lower-case str :name))
                   vec)})

(defn concerns-lipas?
  "Whether the Palvelut tab lists `service` before the org's other PTV
   services (libraries, health care, permits...): one Lipas created, one
   made in PTV that Lipas has written (and so gave a sourceId), or any
   service a Lipas site is linked to, in Lipas (`linked?`) or in PTV, where
   a Lipas site's service location is among its channels (`lipas-location?`)."
  [{:keys [management source-id]} {:keys [linked? lipas-location?]}]
  (boolean
    (or (= :lipas management)
        (and (= :native management) (not (str/blank? source-id)))
        linked?
        lipas-location?)))

(defn display-name
  "The service's name in Finnish, or else in another language it has."
  [{:keys [label name]}]
  (or (not-empty label) (some #(not-empty (get name %)) [:se :en])))

(def ^:private management-rank {:lipas 0 :native 1 :other-system 2})

(defn sort-key
  "Where a row sorts in the table, before its name: what needs the user
   first (a choice to make, a blocked sync, a change request, edits to
   send), then the services Lipas created. `rank` overrides the first, for
   a row that keeps the place it had when opened."
  ([row] (sort-key row nil))
  ([{:keys [sync-status audit-status management]} rank]
   [(or rank
        (cond
          (= :awaiting-choice sync-status) 0
          (= :blocked sync-status) 1
          (= :changes-requested audit-status) 2
          (= :ready sync-status) 3
          :else 4))
    (get management-rank management 3)]))

(def status-filters
  "The status chips of the table: a sync status, or :no-locations for a
   service without a single service location in PTV."
  [:all :awaiting-choice :ready :blocked :ok :no-locations])

(defn matches?
  "Whether `row` matches the search text and the status chip."
  [row search status]
  (and (or (str/blank? search)
           (str/includes? (str/lower-case (or (display-name row) ""))
                          (str/lower-case (str/trim search))))
       (case status
         :all true
         :no-locations (empty? (:locations row))
         (= status (:sync-status row)))))
