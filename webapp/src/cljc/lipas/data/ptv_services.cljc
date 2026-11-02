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
   nil for a service made in PTV that Lipas hasn't written and holds no
   edits for: there is nothing to say about it yet."
  [{:keys [other-system? modified? open-rows invalid-texts? has-local-edits? lipas-managed?]}]
  (cond
    other-system? :other-system
    modified? :blocked
    (seq open-rows) :awaiting-choice
    invalid-texts? :blocked
    has-local-edits? :ready
    lipas-managed? :ok))

(defn service-state
  "What the Palvelut tab shows of `service` (a ::subs/services entry) given
   the user's unsaved edits `descriptions` ({field {locale text}}, plus
   :name and :remove-languages), the org's languages, Lipas's mirror of the
   service after its last write `base` (nil when Lipas hasn't written it),
   the `choices` made in its review and its `audit`:

   - :data              the body a sync sends
   - :text-errors       the texts PTV would reject, which block the sync
   - :versions-to-drop  the language versions the sync removes from PTV
                        (a service Lipas created)
   - :has-local-edits?  whether a sync has anything to send
   - :invalid-texts?    whether it has, and :text-errors block it
   - :review, :open-rows the sync review, and its rows still to decide
   - :audit-status      ptv-data/audit-display-status
   - :sync-status       see sync-status

   and what the panel needs to edit it: :edit-key, :edited-name, :name-data,
   :edit-languages (a native's: the org's languages and every version it
   has) and :removals (language versions marked for removal)."
  [{:keys [org-id service descriptions org-languages base choices audit]}]
  (let [source-id (:source-id service)
        management (:management service)
        native? (= :native management)
        other-system? (= :other-system management)
        edit-key (service-edit-key service)
        current-texts {:summary (merge (:summary service) (:summary descriptions))
                       :description (merge (:description service) (:description descriptions))
                       :user-instruction (merge (:user-instruction service) (:user-instruction descriptions))}
        ptv-texts {:summary (:summary service)
                   :description (:description service)
                   :user-instruction (:user-instruction service)}
        edited-name (when-not native? (not-empty (get-in descriptions [:name :fi])))
        name-changed? (boolean (and edited-name (not= edited-name (:label service))))
        ;; A native service: names per language, the org's languages plus
        ;; every version it already has, and versions marked for removal
        name-data (merge (:name service) (:name descriptions))
        edit-languages (when native?
                         (into (set org-languages) (map name) (:language-versions service)))
        removals (set (:remove-languages descriptions))
        native-texts (assoc current-texts :name name-data)
        data (if native?
               (merge {:org-id org-id
                       :mode "native"
                       :service-id (:service-id service)
                       :expected-modified (:service-modified service)
                       :city-codes (:city-codes service)
                       :languages org-languages
                       :remove-languages (mapv name removals)}
                      native-texts)
               (cond-> (merge {:org-id org-id
                               :source-id source-id
                               :service-id (:service-id service)
                               :city-codes (:city-codes service)
                               :sub-category-id (ptv-data/parse-service-source-id source-id)
                               :languages org-languages
                               ;; PTV's names, kept unless edited, as the
                               ;; texts are (current-texts)
                               :name (:name service)
                               ;; the service as these edits were made on:
                               ;; one changed in PTV since is refused, as
                               ;; it holds PTV's texts as they were then
                               :expected-modified (:service-modified service)}
                              current-texts)
                 edited-name (assoc :service-name edited-name)))
        ;; A native's patch, built as the backend builds it
        native-body (when native?
                      (ptv-data/native-patch-body org-id (:service-id service)
                                                  (:ptv-entries service)
                                                  native-texts
                                                  (keep ptv-data/locale->lang removals)))
        problems (when native?
                   (ptv-data/language-version-problems native-body))
        ;; What blocks the sync, checked on the body the sync will send
        text-errors (case management
                      :native (ptv-data/service-text-errors {:management :native
                                                             :texts native-texts
                                                             :version-problems problems
                                                             :body native-body})
                      :lipas (ptv-data/lipas-service-text-errors data (:ptv-entries service))
                      nil)
        versions-to-drop (when (= :lipas management)
                           (ptv-data/service-versions-to-drop (:language-versions service)
                                                              current-texts
                                                              org-languages))
        ;; Compare only the languages the org publishes: a generated text
        ;; in any other language (the generator writes fi/se/en) never
        ;; reaches PTV, and would read as a difference forever.
        has-local-edits? (boolean
                           (if native?
                             (or (not (ptv-data/texts-match? native-texts
                                                             (assoc ptv-texts :name (:name service))
                                                             [:name :summary :description :user-instruction]
                                                             (map keyword edit-languages)))
                                 (seq removals))
                             (or (not (ptv-data/texts-match? current-texts ptv-texts
                                                             [:summary :description :user-instruction]
                                                             (map keyword org-languages)))
                                 name-changed?)))
        ;; The edit against PTV and Lipas's last write: only an edited field
        ;; can overwrite PTV, and one PTV changed too is a choice to make
        review (when-not other-system?
                 (ptv-review/service-review
                   {:edit (if native?
                            native-texts
                            (assoc current-texts :name (cond-> (:name service)
                                                         edited-name (assoc :fi edited-name))))
                    :ptv (assoc ptv-texts :name (:name service))
                    :base base
                    :locales (map keyword (or edit-languages org-languages))
                    :last-sync (:last-sync base)
                    :ptv-modified (:service-modified service)}))
        state {:edit-key edit-key
               :native? native?
               :other-system? other-system?
               :lipas-managed? (boolean (some-> source-id (str/starts-with? "lipas-")))
               :modified? (= "Modified" (:publishing-status service))
               :descriptions descriptions
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
               :open-rows (ptv-review/open-review-rows (:rows review) choices)
               :audit audit
               :audit-status (ptv-data/audit-display-status
                               audit (ptv-data/service-audit-fields ptv-texts))}]
    (assoc state :sync-status (sync-status state))))

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
  "The service's channels in PTV, split into :locations, its service
   locations (the places Suomi.fi shows for it), each with the :lipas-ids
   of the Lipas sites whose location it is, and :other-channels, the rest
   with their PTV :type (nil for another org's channel: the org's own
   listing, `org-channels`, can't tell its type). Both by name."
  [service org-channels channel->lipas-ids]
  (let [by-name (comp str/lower-case str :name)
        channels (for [{:keys [id name]} (:service-channels service)
                       :let [channel (get org-channels id)]]
                   {:id id
                    :name (or (some-> channel ptv-data/resolve-service-channel-name) name)
                    :type (:serviceChannelType channel)
                    :lipas-ids (get channel->lipas-ids id #{})})
        location? #(= "ServiceLocation" (:type %))]
    {:locations (vec (sort-by by-name (filter location? channels)))
     :other-channels (vec (sort-by (juxt (comp str :type) by-name) (remove location? channels)))}))

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
