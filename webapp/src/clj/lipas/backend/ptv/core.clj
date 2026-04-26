(ns lipas.backend.ptv.core
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.set :as set]
            [clojure.string :as str]
            [lipas.backend.core :as core]
            [lipas.backend.db.db :as db]
            [lipas.backend.email :as email]
            [lipas.backend.gis :as gis]
            [lipas.backend.org :as backend-org]
            [lipas.backend.ptv.ai :as ai]
            [lipas.backend.ptv.integration :as ptv]
            [lipas.backend.search :as search]
            [lipas.data.ptv :as ptv-data]
            [lipas.data.types :as types]
            [lipas.utils :as utils]
            [taoensso.timbre :as log]))

(defn get-ptv-integration-candidates
  [search criteria]
  (ptv/get-eligible-sites search criteria))

(defn generate-ptv-descriptions
  [{:keys [client indices] :as _search}
   lipas-id & [{:keys [reference]}]]
  (let [idx (get-in indices [:sports-site :search])
        doc (-> (search/fetch-document client idx lipas-id)
                :body
                :_source)]
    (-> (ai/generate-ptv-descriptions doc {:reference reference})
        :message
        :content)))

(defn generate-ptv-descriptions-batch
  "Generate PTV descriptions for multiple same-type sports sites in one Gemini call.

  opts keys:
    :lipas-ids  — seq of integer lipas ids (same type, ≤10 recommended)
    :reference  — optional {:summary :description} (Finnish) anchoring style across
                  partitioned batches

  Returns {:sites [{:lipas-id :summary :description :user-instruction} ...]}"
  [{:keys [client indices] :as _search}
   {:keys [lipas-ids reference]}]
  (let [idx   (get-in indices [:sports-site :search])
        docs  (mapv (fn [lipas-id]
                      (-> (search/fetch-document client idx lipas-id)
                          :body
                          :_source))
                    lipas-ids)]
    (-> (ai/generate-ptv-descriptions-batch docs {:reference reference})
        :message
        :content)))

(defn generate-ptv-descriptions-from-data
  [doc & [{:keys [reference]}]]
  (let [doc (core/enrich doc)]
    (-> (ai/generate-ptv-descriptions doc {:reference reference})
        :message
        :content)))

(defn translate-to-other-langs
  [doc]
  (-> (ai/translate-to-other-langs doc)
      :message
      :content
      ;; Ensure the original from texts are kept as-is
      (assoc-in [:summary (keyword (:from doc))] (:summary doc))
      (assoc-in [:description (keyword (:from doc))] (:description doc))))

(defn make-overview
  [sites]
  {:city-name (->> sites first :search-meta :location :city :name)
   :service-name (->> sites first :search-meta :type :sub-category :name)
   :sports-facilities (for [site sites]
                        {:type (-> site :search-meta :type :name :fi)})})

(defn- nil-safe-frequencies
  "Like frequencies but replaces nil keys with \"unknown\"."
  [coll]
  (into {} (map (fn [[k v]] [(if (nil? k) "unknown" k) v]))
        (frequencies coll)))

(defn make-aggregate-overview
  [sites {:keys [free-use? surface-materials? lighting?]}]
  (let [base {:city-name    (->> sites first :search-meta :location :city :name)
              :service-name (->> sites first :search-meta :type :sub-category :name)
              :total-count  (count sites)
              :by-type      (nil-safe-frequencies (map #(-> % :search-meta :type :name :fi) sites))}]
    (cond-> base
      free-use?
      (assoc :free-use (nil-safe-frequencies (map #(get-in % [:properties :free-use?]) sites)))

      surface-materials?
      (assoc :surface-materials (nil-safe-frequencies (mapcat #(get-in % [:properties :surface-material]) sites)))

      lighting?
      (assoc :lighting (nil-safe-frequencies (map #(get-in % [:properties :ligthing?]) sites))))))

(defn generate-ptv-service-descriptions
  [search
   {:keys [sub-category-id city-codes overview]}]
  (let [doc (or overview
                (let [type-codes (->> (types/by-sub-category sub-category-id)
                                      (map :type-code))
                      sites (ptv/get-eligible-sites search {:type-codes type-codes
                                                            :city-codes city-codes
                                                            :owners ["city" "city-main-owner"]})]
                  (make-overview sites)))]
    (-> (ai/generate-ptv-service-descriptions doc)
        :message
        :content)))

(defn- ptv-list-has-content?
  "Check if a PTV-style list of {:type :language :value} maps has any
   non-placeholder content. Returns false for lists where all values
   are empty/placeholder."
  [items]
  (boolean (some (fn [item]
                   (let [v (:value item)]
                     (and (string? v)
                          (not (str/blank? v))
                          (not= v "-"))))
                 items)))

(defn- localized-list?
  "Localized lists are `[{:value <str> :language <str> ...} ...]`. Used by
   PTV for descriptions, names, requirements, etc."
  [v]
  (and (sequential? v)
       (seq v)
       (every? map? v)
       (every? #(contains? % :value) v)))

(defn- strip-blank-localized-entries
  "PTV's PUT endpoint rejects localized list entries with blank :value
   (400 'The Value field is required.'). The GET response can include
   such entries — e.g. :requirements with empty per-language slots — so
   strip them before round-tripping."
  [m]
  (reduce-kv (fn [acc k v]
               (cond-> acc
                 (localized-list? v)
                 (assoc k (filterv #(not (str/blank? (:value %))) v))))
             m
             m))

(defn- normalize-ptv-service-for-update
  "Convert PTV GET response enriched objects back to the input format
   expected by the PUT endpoint. The GET returns rich objects (with names,
   descriptions, parents), but PUT expects simplified input (URIs, codes)."
  [service]
  (-> service
      ;; ontologyTerms: GET returns [{:uri ... :name [...] ...}], PUT expects just URIs
      (update :ontologyTerms (fn [terms] (mapv :uri terms)))
      ;; serviceClasses: GET returns [{:uri ... :name [...] ...}], PUT expects just URIs
      (update :serviceClasses (fn [classes] (mapv :uri classes)))
      ;; targetGroups: GET returns [{:uri ... :name [...] ...}], PUT expects just URIs
      (update :targetGroups (fn [groups] (mapv :uri groups)))
      ;; areas: GET returns [{:type :municipalities [{:code "425" :name [...]}]}]
      ;; PUT expects [{:type "Municipality" :areaCodes ["425"]}]
      (update :areas (fn [areas]
                       (mapv (fn [area]
                               {:type (:type area)
                                :areaCodes (mapv :code (:municipalities area))})
                             areas)))
      strip-blank-localized-entries))

(defn- merge-ptv-lists
  "Merge two PTV-style lists keyed by [:type :language]. Existing items
   are kept; new items from LIPAS are added for missing type+language combos.
   Items with actual content from LIPAS overwrite existing items."
  [existing-items lipas-items]
  (let [existing-by-key (into {} (map (fn [item]
                                        [((juxt :type :language) item) item])
                                      existing-items))
        lipas-by-key (into {} (map (fn [item]
                                     [((juxt :type :language) item) item])
                                   lipas-items))]
    (vals (reduce-kv (fn [acc k lipas-item]
                       (if (contains? acc k)
                         ;; Existing item: only overwrite if LIPAS has real content
                         (let [v (:value lipas-item)]
                           (if (and (string? v) (not (str/blank? v)) (not= v "-"))
                             (assoc acc k lipas-item)
                             acc))
                         ;; Missing language: only add if LIPAS has real content
                         (let [v (:value lipas-item)]
                           (if (and (string? v) (not (str/blank? v)) (not= v "-"))
                             (assoc acc k lipas-item)
                             acc))))
                     existing-by-key
                     lipas-by-key))))

(defn- merge-service-data
  "Merge LIPAS-generated service data on top of existing PTV service.
   Only overwrites fields that LIPAS actually provides meaningful content for.
   This preserves PTV-managed data (ontology, classes, names) for
   adopted services while allowing LIPAS to update descriptions."
  [existing lipas-data]
  (reduce-kv (fn [acc k v]
               (cond
                 (nil? v) acc
                 ;; Empty collections: don't overwrite
                 (and (coll? v) (empty? v)) acc
                 ;; PTV-style lists (serviceNames, serviceDescriptions):
                 ;; merge at item level to preserve existing + add missing languages
                 (and (sequential? v)
                      (seq v)
                      (map? (first v))
                      (contains? (first v) :value))
                 (assoc acc k (merge-ptv-lists (get acc k) v))
                 :else (assoc acc k v)))
             existing
             lipas-data))

(def ^:private lipas-managed-service-fields
  "Fields that LIPAS actively manages on a service. Other fields
   (ontologyTerms, serviceClasses, targetGroups, etc.) are either
   derived from sub-category or are defaults and should not overwrite
   existing PTV data when adopting a service."
  #{:sourceId :serviceDescriptions :serviceNames :publishingStatus :languages})

(defn upsert-ptv-service!
  [ptv {:keys [org-id source-id service-id sub-category-id] :as m}]
  (let [lipas-data (ptv-data/->ptv-service m)]
    (if service-id
      ;; Updating existing PTV service: fetch, normalize to input format, merge
      (let [existing (-> (ptv/get-service ptv org-id service-id)
                         (normalize-ptv-service-for-update))
            ;; When adopting without sub-category, only merge fields LIPAS manages
            ;; to avoid overwriting PTV-managed metadata with defaults
            lipas-data (if sub-category-id
                         lipas-data
                         (select-keys lipas-data lipas-managed-service-fields))
            merged (merge-service-data existing lipas-data)
            ;; PTV requires names for all supported languages — use Finnish as fallback
            fi-name (or (some #(when (and (= "fi" (:language %)) (not (str/blank? (:value %))))
                                 (:value %))
                              (:serviceNames merged))
                        "")
            required-langs (set (map name (:languages merged)))
            existing-name-langs (set (map :language (:serviceNames merged)))
            missing-name-langs (clojure.set/difference required-langs existing-name-langs)
            data (-> merged
                     (update :serviceDescriptions (fn [items] (filterv #(not (str/blank? (:value %))) items)))
                     ;; Fill empty name values with Finnish fallback
                     (update :serviceNames (fn [items]
                                             (let [filled (mapv #(if (str/blank? (:value %))
                                                                   (assoc % :value fi-name)
                                                                   %)
                                                                items)]
                                               ;; Add entries for completely missing languages
                                               (into filled (for [lang missing-name-langs]
                                                              {:type "Name" :language lang :value fi-name}))))))]
        (ptv/update-service-by-id ptv service-id data))
      ;; New service: try update by source-id, create if not found
      (try
        (ptv/update-service ptv source-id lipas-data)
        (catch clojure.lang.ExceptionInfo e
          (if (= 404 (:status (:resp (ex-data e))))
            (ptv/create-service ptv lipas-data)
            (throw e)))))))

(defn fetch-ptv-org
  [ptv org-id]
  (ptv/get-org ptv org-id))

(defn fetch-ptv-service-collections
  [ptv org-id]
  (ptv/get-org-service-collections ptv org-id))

(defn fetch-ptv-services
  [ptv org-id]
  (ptv/get-org-services ptv org-id))

(defn fetch-ptv-service-channels
  "Fetch the org's service channels from PTV with full entity data.
   Uses the /list/organization endpoint which returns complete channel
   entities (descriptions, sourceId, publishingStatus etc.) needed for
   drift detection and status warnings."
  [ptv org-id]
  (ptv/get-org-service-channels ptv org-id))

(defn fetch-ptv-service-channel
  [ptv org-id service-channel-id]
  (ptv/get-org-service-channel ptv org-id service-channel-id))

(def persisted-ptv-keys [:languages
                         :summary
                         :description
                         :user-instruction
                         :last-sync
                         :org-id
                         :sync-enabled
                         :service-integration
                         :descriptions-integration
                         :service-channel-integration
                         :service-ids
                         :service-channel-ids])

(defn upsert-ptv-service-location!*
  [ptv-component {:keys [org-id site ptv archive?] :as _m}]
  (let [id (-> ptv :service-channel-ids first)
        ;; Languages are determined by the org's live PTV config at sync time —
        ;; we don't trust the site's persisted :languages because it may be a
        ;; snapshot from an older org config. See calc-derived-fields archaeology.
        org-config (ptv/get-org-ptv-config-with-fallback ptv-component org-id)
        org-langs (or (:supported-languages org-config)
                      ptv-data/fallback-languages)
        ptv (assoc ptv :languages org-langs)
        ;; merge or just replace?
        site (update site :ptv merge ptv)
        ;; Use the same TS for sourceId, ptv last-sync and site event-date
        now (utils/timestamp)
        data (ptv-data/->ptv-service-location org-id gis/wgs84->tm35fin-no-wrap now (core/enrich site))
        data (cond-> data
               archive? (assoc :publishingStatus "Deleted"))
        ;; Note: Update request doesn't update Service connections!

        ;; Fetch the stored channel for service-connection diffing below.
        ;; Drift detection in `sports-site->ptv-input` uses a separately
        ;; cached channel snapshot, not this fetch.
        old-service-location (when id
                               (ptv/get-org-service-channel ptv-component org-id id))

        ptv-resp (if id
                   (ptv/update-service-location ptv-component id data)
                   (ptv/create-service-location ptv-component data))
        _ (when id
            ;; Update service connection changes
            (let [old-services (->> old-service-location
                                    :services
                                    (map (comp :id :service))
                                    set)
                  new-services (set (:service-ids (:ptv site)))
                  removed-services (set/difference old-services new-services)
                  new-services (set/difference new-services old-services)
                  service-channel-id (first (:service-channel-ids (:ptv site)))]
              (log/infof "Update service-location connections: +%s -%s" new-services removed-services)
              (doseq [service-id removed-services]
                (ptv/update-service-connections ptv-component org-id service-id #(disj % service-channel-id)))
              (doseq [service-id new-services]
                (ptv/update-service-connections ptv-component org-id service-id #(conj % service-channel-id)))))
        ;; Store the new PTV info to Lipas DB
        new-ptv-data (-> ptv
                         (select-keys persisted-ptv-keys)
                         (assoc :org-id (or (:org-id ptv)
                                            org-id)
                                :last-sync now
                                ;; Store the current type-code into ptv data, so this can be
                                ;; used to comapre if the services need to recalculated on site data update.
                                :previous-type-code (:type-code (:type site))
                                :source-id (:sourceId ptv-resp)
                                ;; Store the PTV status so we can ignore Lipas archived places that we already archived in PTV.
                                :publishing-status (:publishingStatus ptv-resp)
                                ;; NOTE: The ptv map might not have this value in some cases...?
                                ;; but the value merged with data from site should have it always?
                                :service-ids (:service-ids (:ptv site))
                                ;; Take the created ID from ptv response and store to Lipas DB right away.
                                ;; TODO: Is there a case where this could be multiple ids?
                                :service-channel-ids [(:id ptv-resp)])
                         (cond->
                           archive? (dissoc :source-id
                                            :service-channel-ids
                                            :delete-existing)))]

    (log/infof "Upserted service-location %s (status: %s, update: %s, channel: %s)"
               (:lipas-id site) (:status site) (boolean id) (:id ptv-resp))

    [ptv-resp new-ptv-data]))

(defn upsert-ptv-service-location!
  [db ptv-component search user {:keys [lipas-id org-id ptv archive?]}]
  ;; FIXME: This is called from inside tx in save-sports-site! is that a problem?
  ;; FIXME: Separate version from this fn for use in sync-ptv! which doesn't load the
  ;; sports site from db etc.?
  (jdbc/with-db-transaction [tx db]
    (let [site (db/get-sports-site db lipas-id)
          _ (assert (some? site) (str "Sports site " lipas-id " not found in DB"))

          [ptv-resp new-ptv-data] (upsert-ptv-service-location!* ptv-component
                                                                 {:org-id org-id
                                                                  :site site
                                                                  :ptv ptv
                                                                  :archive? archive?})]

      (let [resp (core/upsert-sports-site! tx
                                           user
                                           (assoc site
                                                  :event-date (:last-sync new-ptv-data)
                                                  :ptv new-ptv-data)
                                           false)]
        (core/index! search resp :sync))

      ;; No need to re-index for search after ptv change

      {;; Return the updated :ptv meta for sports-site, to for the app-db
       :ptv new-ptv-data
       ;; Return full PTV response so frontend can update service-channels cache
       ;; (needed for drift detection and PTV link)
       :ptv-resp ptv-resp})))

(comment
  (require '[integrant.repl.state :as state])

  (let [ptv-component (:lipas/ptv state/system)
        org-id "7fdd7f84-e52a-4c17-a59a-d7c2a3095ed5"
        services (:itemList (ptv/get-org-services ptv-component org-id))]
    (->> services
         (utils/index-by :sourceId)
         keys)))

;; Used through resolve due to circular dep
;; TODO: Check if code can be moved around to avoid this
^{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn sync-ptv! [tx search ptv-component user {:keys [sports-site ptv org-id lipas-id]}]
  (try
    (let [type-code (-> sports-site :type :type-code)

          previous-sent? (ptv-data/is-sent-to-ptv? sports-site)
          candidate-now? (and (ptv-data/ptv-candidate? sports-site)
                              (ptv-data/ptv-ready? sports-site))

          ;; If it looks like site that was previously sent to PTV is no longer
          ;; a candidate, mark for it archival.
          ;; The other function will mark the document Deleted when archive flag is true
          to-archive? (and previous-sent?
                           (or (not candidate-now?)
                               (:delete-existing ptv)))

          type-code-changed? (not= type-code (:previous-type-code ptv))
          ptv (if type-code-changed?
                (let [types types/all
                       ;; Figure out what services are available in PTV for the site organization
                      services (:itemList (ptv/get-org-services ptv-component org-id))
                      source-id->service (->> services
                                              (utils/index-by :sourceId))

                       ;; Check if services for the current/new site type-code exist
                      missing-services-input [{:service-ids #{}
                                               :sub-category-id (-> sports-site :type :type-code types :sub-category)
                                               :sub-cateogry (-> sports-site :search-meta :type :sub-category :name :fi)}]
                      missing-services (ptv-data/resolve-missing-services org-id source-id->service missing-services-input)

                       ;; FE doesn't update the :ptv :service-ids, that is still handled here.
                       ;; This code just presumes the user has created the possibly missing Sercices
                       ;; in the FE first.

                      _ (when (seq missing-services)
                          (throw (ex-info "Site needs a PTV Service that doesn't exists"
                                          {:missing-services missing-services})))

                       ;; Remove old service-ids from :ptv data and add the new.
                       ;; Don't touch other service-ids in the data, those could have be added manually in UI or in PTV.
                       ;; NOTE: OK, PTV updates are likely lost, because our :ptv :service-ids is what the create/update from
                       ;; Lipas previously returned, so if PTV ServiceLocation was modified after that in PTV, we lose those changes.
                      old-sports-site (assoc-in sports-site [:type :type-code] (:previous-type-code ptv))
                      old-service-ids (ptv-data/sports-site->service-ids types source-id->service old-sports-site)
                      new-service-ids (ptv-data/sports-site->service-ids types source-id->service sports-site)]
                  (log/infof "Site %d type changed %s => %s, service-ids %s => %s"
                             lipas-id (:previous-type-code ptv) type-code
                             old-service-ids new-service-ids)
                  (update ptv :service-ids (fn [ids]
                                             (let [x (set ids)
                                                   x (apply disj x old-service-ids)
                                                   x (into x new-service-ids)]
                                               (vec x)))))
                ptv)

          [_ptv-resp new-ptv-data] (upsert-ptv-service-location!* ptv-component
                                                                  {:org-id org-id
                                                                   :ptv ptv
                                                                   :site sports-site
                                                                   :archive? to-archive?})]

      (let [resp (core/upsert-sports-site! tx
                                           user
                                           (assoc sports-site
                                                  :event-date (:last-sync new-ptv-data)
                                                  :ptv new-ptv-data)
                                           false)]
        (core/index! search resp :sync)
        ;; Return both :ptv and :event-date so the caller's outer index!
        ;; reindexes the same revision we just wrote.
        {:ptv new-ptv-data :event-date (:event-date resp)}))
    (catch Exception e
      (let [new-ptv-data (assoc ptv :error {:message (.getMessage e)
                                            :data (ex-data e)})]
        (log/errorf e "Sports site %d updated but PTV sync failed" lipas-id)
        (let [resp (core/upsert-sports-site! tx
                                             user
                                             (-> sports-site
                                                 (assoc :event-date (utils/timestamp))
                                                 (assoc :ptv new-ptv-data))
                                             false)]
          (core/index! search resp :sync)
          ;; Failure path also wrote a new revision (with :error captured
          ;; on :ptv). Return the same :event-date so DB and ES match.
          {:ptv (:ptv resp) :event-date (:event-date resp)})))))

(defn save-ptv-integration-definitions
  "Saves ptv definitions under key :ptv. Does not notify webhooks,
  integrations or analysis queues since they're not likely interested
  in this."
  [db search user lipas-id->ptv-meta]
  (jdbc/with-db-transaction [tx db]
    (doseq [[lipas-id ptv] lipas-id->ptv-meta]
      ;; TODO take when-let -> let and add assert
      (when-let [site (-> (core/get-sports-site tx lipas-id)
                          (assoc :event-date (utils/timestamp))
                          (assoc :ptv ptv))]
        (core/upsert-sports-site! tx user site)
        (core/index! search site :sync))))
  {:status "OK"})

(defn save-ptv-audit
  "Saves PTV audit information for a sports site."
  [db search user {:keys [lipas-id audit]}]
  (jdbc/with-db-transaction [tx db]
    (when-let [site (core/get-sports-site tx lipas-id)]
      ;; Add timestamp and auditor information to the audit data
      (let [now (utils/timestamp)
            user-id (str (or (:id user) (get-in user [:login :user :id])))

            ;; Add timestamp and auditor-id to the audit data
            audit-with-meta (assoc audit
                                   :timestamp now
                                   :auditor-id user-id)

            ;; Update the site's PTV data with the audit info
            ;; IMPORTANT: Preserve the original author when updating
            updated-site (-> site
                             (assoc :event-date now)
                             (assoc-in [:ptv :audit] audit-with-meta))

            ;; Use the original author for the database update
            original-author {:id (:author-id (meta site))}]

        ;; Save and index the updated site with original author preserved
        (core/upsert-sports-site!* tx original-author updated-site)
        (core/index! search updated-site :sync)

        ;; Return the updated audit data
        (get-in updated-site [:ptv :audit])))))

(defn get-ptv-managers
  "Returns users who have :ptv/manage privilege for any of the organization's cities."
  [db org-id]
  (let [org (backend-org/get-org db org-id)
        org-city-codes (-> org :ptv-data :city-codes set)
        org-users (backend-org/get-org-users db org-id)]
    (filter (fn [user]
              (some (fn [role]
                      (and (= :ptv-manager (keyword (:role role)))
                           (seq (set/intersection org-city-codes (set (:city-code role))))))
                    (-> user :permissions :roles)))
            org-users)))

(defn send-audit-notification!
  "Send email notification to all PTV managers in the organization.
   Stats are calculated by frontend and passed here."
  [db emailer org-id stats]
  (when-let [org (backend-org/get-org db org-id)]
    (let [managers (get-ptv-managers db org-id)]
      (if (seq managers)
        (do
          (doseq [manager managers]
            (email/send-ptv-audit-complete-email!
              emailer
              (:email manager)
              {:org-name (:name org)
               :site-count (str (:total-sites stats))
               :summary-approved (str (get-in stats [:summary :approved]))
               :summary-changes (str (get-in stats [:summary :changes-requested]))
               :desc-approved (str (get-in stats [:description :approved]))
               :desc-changes (str (get-in stats [:description :changes-requested]))}))

          {:sent (count managers)
           :recipients (map :email managers)})

        (do
          (log/warnf "No PTV managers found for organization %s" org-id)
          {:sent 0
           :recipients []
           :warning "No PTV managers found"})))))

(comment
  (generate-ptv-service-descriptions
    (user/search)
    {:sub-category-id 2200
     :city-codes [992 #_92]})

  (def s1 (core/get-sports-site (repl/db) 612967))
  (def s2 (core/get-sports-site (repl/db) 506032))
  (core/enrich s1)
  (generate-ptv-descriptions-from-data s1)
  (generate-ptv-descriptions-from-data s2)

  (generate-ptv-descriptions-from-data (assoc s1 :comment "Luistinrata ylläpidetään lumisena aikana viikottain. Pukukopit käytössä aukioloaikoina. Kentälle on saatavissa pieniä jääkiekkomaaleja pelien järjestämistä ja lajitaitojen harjoittelua varten."))
  (generate-ptv-descriptions-from-data s2)

  *1)
