(ns lipas.ui.ptv.subs
  (:require [clojure.string :as str]
            [lipas.data.ptv :as ptv-data]
            [lipas.data.types :as types]
            [lipas.roles :as roles]
            [lipas.ui.map.utils :as map-utils]
            [lipas.ui.utils :as utils :refer [prod?]]
            [re-frame.core :as rf]))

(rf/reg-sub ::ptv
  (fn [db _]
    (:ptv db)))

(rf/reg-sub ::dialog-open?
  :<- [::ptv]
  (fn [ptv _]
    (get-in ptv [:dialog :open?])))

(rf/reg-sub ::selected-tab
  :<- [::ptv]
  :<- [::default-tab]
  (fn [[ptv default-tab] _]
    (or (:selected-tab ptv) default-tab)))

(rf/reg-sub ::selected-step
  :<- [::ptv]
  (fn [ptv _]
    (or (:selected-step ptv)
        0)))

(rf/reg-sub ::candidates-search
  :<- [::ptv]
  (fn [ptv _]
    (:candidates-search ptv)))

(rf/reg-sub ::loading-from-ptv?
  :<- [::ptv]
  (fn [ptv _]
    (->> ptv :loading-from-ptv vals (some true?))))

(rf/reg-sub ::loading-from-lipas?
  :<- [::ptv]
  (fn [ptv _]
    (->> ptv :loading-from-lipas vals (some true?))))

(rf/reg-sub ::generating-descriptions?
  :<- [::ptv]
  (fn [ptv _]
    (->> ptv :loading-from-lipas :descriptions)))

(rf/reg-sub ::loading?
  :<- [::loading-from-ptv?]
  :<- [::loading-from-lipas?]
  (fn [[loading-from-ptv? loading-from-lipas?] _]
    (or loading-from-ptv? loading-from-lipas?)))

(def orgs
  (if (prod?)
    (filterv :prod ptv-data/orgs)
    (filterv #(not (:prod %)) ptv-data/orgs)))

(rf/reg-sub ::users-orgs
  :<- [:lipas.ui.user.subs/user-data]
  (fn [user-data _]
    (cond
      ;; If user has audit privilege (global), show all orgs
      (roles/check-privilege user-data {} :ptv/audit)
      orgs

      ;; Otherwise, filter by manage privilege per city-code
      :else
      (filterv (fn [{:keys [city-codes :as _org]}]
                 (some
                   (fn [city-code]
                     (roles/check-privilege user-data {:city-code city-code} :ptv/manage))
                   city-codes))
               orgs))))

(rf/reg-sub ::selected-org
  :<- [::ptv]
  (fn [ptv _]
    (:selected-org ptv)))

(rf/reg-sub ::selected-org-id
  :<- [::selected-org]
  (fn [org _]
    (:id org)))

(rf/reg-sub ::org-default-settings
  :<- [::ptv]
  (fn [ptv [_ org-id]]
    (get-in ptv [:org org-id :default-settings])))

(rf/reg-sub ::default-settings
  (fn [[_ org-id]]
    [(rf/subscribe [::ptv])
     (rf/subscribe [::org-default-settings org-id])])
  (fn [[ptv org-defaults] _]
    (merge (:default-settings ptv) org-defaults)))

(rf/reg-sub ::selected-org-data
  :<- [::ptv]
  (fn [ptv [_ org-id]]
    (when org-id
      (let [{:keys [services _service-channels]} (get-in ptv [:org org-id :data])]
        (for [s services
              sc (:serviceChannels s)]
          (let [service-name (->> s
                                  :serviceNames
                                  (ptv-data/select-service-name))
                channel-name (-> sc :serviceChannel :name)]
            {:label (str service-name " > " channel-name)
             :service-id (:id s)
             :service-name service-name
             :service-description-summary (->> s
                                               :serviceDescriptions
                                               (some (comp #{"summary"} :type)))
             :service-modified (:modified s)

             :service-channel-id (-> sc :serviceChannel :id)
             :service-channel-name channel-name
            ;; TODO rest of the gunk, service hours, descriptions etc.
             }))))))

(rf/reg-sub ::services-by-id
  :<- [::ptv]
  (fn [ptv [_ org-id]]
    (when org-id
      (get-in ptv [:org org-id :data :services]))))

(def langs {"sv" "se" "fi" "fi" "en" "en"})

(rf/reg-sub ::services
  (fn [[_ org-id]]
    (rf/subscribe [::services-by-id org-id]))
  (fn [services _]
    (for [[id service] services]
      (let [service-name (->> service :serviceNames (ptv-data/select-service-name))
            descriptions (->> service :serviceDescriptions (filter #(= (:type %) "Description")))
            summaries (->> service :serviceDescriptions (filter #(= (:type %) "Summary")))]
        {:label service-name
         :service-id id
         :source-id (:sourceId service)
         :last-modified (:modified service)
         :last-modified-human (utils/->human-date-time-at-user-tz (:modified service))
         :service-classes (->> service
                               :serviceClasses
                               (map (fn [m]
                                      (utils/index-by (comp langs :language) :value (:name m)))))
         :languages (map langs (:languages service))
         :summary (utils/index-by (comp langs :language) :value summaries)
         :description (utils/index-by (comp langs :language) :value descriptions)
         :service-name service-name
         :service-modified (:modified service)
         :service-channels (->> service :serviceChannels (map :serviceChannel))
         :city-codes (->> service
                          :areas
                          (mapcat (fn [{:keys [type municipalities]}]
                                    (when (= "Municipality" type)
                                      municipalities)))
                          (map (comp parse-long :code))
                          vec)
         :ontology-terms (->> service
                              :ontologyTerms
                              (map (fn [m]
                                     (utils/index-by (comp langs :language) :value (:name m)))))}))))

(rf/reg-sub ::services-filter
  :<- [::ptv]
  (fn [ptv _]
    (:services-filter ptv)))

(rf/reg-sub ::services-filtered
  (fn [[_ org-id]]
    [(rf/subscribe [::services org-id])
     (rf/subscribe [::services-filter])])
  (fn [[services filter'] _]
    (sort-by :label
             (case filter'
               "lipas-managed" (filter (fn [m] (some-> m :source-id (str/starts-with? "lipas-"))) services)
               services))))

(rf/reg-sub ::missing-services
  (fn [[_ org-id]]
    [(rf/subscribe [::services-by-id org-id])
     (rf/subscribe [::sports-sites org-id])])
  (fn [[services sports-sites] [_ org-id]]
    (ptv-data/resolve-missing-services org-id services sports-sites)))

(rf/reg-sub ::manual-services
  :<- [::ptv]
  (fn [ptv [_ org-id]]
    (vals (get-in ptv [:org org-id :data :manual-services]))))

(rf/reg-sub ::manual-services-keys
  :<- [::ptv]
  (fn [ptv [_ org-id]]
    (keys (get-in ptv [:org org-id :data :manual-services]))))

(rf/reg-sub ::service-details-tab
  :<- [::ptv]
  (fn [ptv [_]]
    (:service-details-tab ptv)))

(rf/reg-sub ::service-preview
  :<- [::ptv]
  (fn [ptv [_ source-id sub-category-id]]
    (let [org-id (get-in ptv [:selected-org :id])
          descriptions (get-in ptv [:org org-id :data :service-candidates source-id])
          org-params (ptv-data/org-id->params org-id)]
      (ptv-data/->ptv-service
        {:org-id org-id
         :city-codes (:city-codes org-params)
         :source-id source-id
         :sub-category-id sub-category-id
         :languages (ptv-data/org-id->languages org-id)
         :description (:description descriptions)
         :summary (:summary descriptions)}))))

(rf/reg-sub ::service-location-preview
  :<- [::ptv]
  (fn [ptv [_ org-id lipas-id]]
    (let [site (get-in ptv [:org org-id :data :sports-sites lipas-id])]
      #_(println site)
      (ptv-data/->ptv-service-location org-id
                                       (comp map-utils/wgs84->epsg3067 clj->js)
                                       (utils/timestamp)
                                       site))))

(rf/reg-sub ::service-candidate-descriptions
  :<- [::ptv]
  (fn [ptv [_ org-id]]
    (get-in ptv [:org org-id :data :service-candidates])))

(rf/reg-sub ::service-candidates
  (fn [[_ org-id]]
    [(rf/subscribe [::missing-services org-id])
     (rf/subscribe [::manual-services org-id])
     (rf/subscribe [::service-candidate-descriptions org-id])])
  (fn [[missing-services manual-services descriptions] [_ org-id]]
    (->> (concat missing-services
                 manual-services)
         (map (fn [{:keys [source-id] :as m}]
                (let [description (get-in descriptions [source-id :description])
                      summary (get-in descriptions [source-id :summary])
                      languages (ptv-data/org-id->languages org-id)]
                  (-> m
                      (assoc :languages languages)
                      (assoc :description description)
                      (assoc :summary summary)
                      (assoc :valid (boolean (and
                                               (some-> description :fi count (> 5))
                                               (some-> summary :fi count (> 5))))))))))))

(rf/reg-sub ::service-channels-by-id
  :<- [::ptv]
  (fn [ptv [_ org-id]]
    (when org-id
      (get-in ptv [:org org-id :data :service-channels]))))

(rf/reg-sub ::service-channels
  (fn [[_ org-id]]
    (rf/subscribe [::service-channels-by-id org-id]))
  (fn [channels _]
    (vals channels)))

(rf/reg-sub ::service-channels-list
  (fn [[_ org-id]]
    (rf/subscribe [::service-channels org-id]))
  (fn [channels [_ _org-id]]
    (for [m channels]
      {:service-channel-id (:id m)
       :name (ptv-data/resolve-service-channel-name m)})))

(rf/reg-sub ::sports-sites
  (fn [[_ org-id]]
    [(rf/subscribe [::ptv])
     (rf/subscribe [::services-by-id org-id])
     (rf/subscribe [::service-channels-by-id org-id])
     (rf/subscribe [::default-settings org-id])
     (rf/subscribe [:lipas.ui.sports-sites.subs/all-types])])
  (fn [[ptv services service-channels org-defaults types] [_ org-id]]
    (let [lipas-id->site (get-in ptv [:org org-id :data :sports-sites])]
      (for [site (vals lipas-id->site)
            :let [org-langs (ptv-data/org-id->languages org-id)]]
        (ptv-data/sports-site->ptv-input {:org-id org-id
                                          :types types
                                          :org-defaults org-defaults
                                          :org-langs org-langs}
                                         service-channels
                                         services
                                         site)))))

(rf/reg-sub ::sports-sites-count
  (fn [[_ org-id]]
    (rf/subscribe [::sports-sites org-id]))
  (fn [sports-sites _]
    (count sports-sites)))

(rf/reg-sub ::sports-sites-count-sync
  (fn [[_ org-id]]
    (rf/subscribe [::sports-sites org-id]))
  (fn [sports-sites _]
    (count (filter :sync-enabled sports-sites))))

(rf/reg-sub ::sync-all-enabled?
  (fn [[_ org-id]]
    (rf/subscribe [::sports-sites org-id]))
  (fn [sports-sites _]
    (every? true? (map :sync-enabled sports-sites))))

(rf/reg-sub ::batch-descriptions-generation
  :<- [::ptv]
  (fn [m _]
    (:batch-descriptions-generation m)))

(rf/reg-sub ::batch-descriptions-generation-progress
  :<- [::batch-descriptions-generation]
  (fn [{:keys [processed-lipas-ids size halt? in-progress?]} _]
    (let [processed-count (count processed-lipas-ids)]
      {:processed-lipas-ids (set processed-lipas-ids)
       :in-progress? in-progress?
       :halt? halt?
       :total-count size
       :processed-count processed-count
       :processed-percent (if (pos? size)
                            (if (zero? processed-count)
                              0
                              (* 100 (- 1 (/ (- size processed-count) size))))
                            100)})))

(rf/reg-sub ::sports-site-setup-done
  (fn [[_ org-id]]
    (rf/subscribe [::sports-sites org-id]))
  (fn [ms _]
    (every? (fn [{:keys [last-sync sync-enabled] :as _m}]
              (or (utils/iso-date-time-string? (or last-sync ""))
                  (false? sync-enabled)))
            ms)))

(rf/reg-sub ::sports-sites-filter
  :<- [::batch-descriptions-generation]
  (fn [m _]
    (:sports-sites-filter m)))

(rf/reg-sub ::sports-sites-filtered
  (fn [[_ org-id]]
    [(rf/subscribe [::sports-sites org-id])
     (rf/subscribe [::sports-sites-filter])])
  (fn [[sites filter*] _]
    (case filter*
      "all"
      sites

      "sync-enabled"
      (filter :sync-enabled sites)

      "no-existing-description"
      (filter (fn [{:keys [summary description]}]
                (or (empty? summary) (empty? description)))
              sites)

      "sync-enabled-no-existing-description"
      (filter (fn [{:keys [sync-enabled summary description]}]
                (and sync-enabled
                     (or (empty? summary) (empty? description))))
              sites))))

(rf/reg-sub ::sports-sites-filtered-count
  (fn [[_ org-id]]
    (rf/subscribe [::sports-sites-filtered org-id]))
  (fn [sports-sites _]
    (count sports-sites)))

(rf/reg-sub ::service-descriptions-generation
  :<- [::ptv]
  (fn [m _]
    (:service-descriptions-generation m)))

(rf/reg-sub ::service-descriptions-generation-progress
  :<- [::service-descriptions-generation]
  (fn [{:keys [processed-ids size halt? in-progress?]} _]
    (let [processed-count (count processed-ids)]
      {:processed-ids (set processed-ids)
       :in-progress? in-progress?
       :halt? halt?
       :total-count size
       :processed-count processed-count
       :processed-percent (if (pos? size)
                            (if (zero? processed-count)
                              0
                              (* 100 (- 1 (/ (- size processed-count) size))))
                            100)})))

(rf/reg-sub ::service-locations-creation
  :<- [::ptv]
  (fn [m _]
    (:service-locations-creation m)))

(rf/reg-sub ::service-location-creation-progress
  :<- [::service-locations-creation]
  (fn [{:keys [processed-ids size halt? in-progress?]} _]
    (let [processed-count (count processed-ids)]
      {:processed-lipas-ids (set processed-ids)
       :in-progress? in-progress?
       :halt? halt?
       :total-count size
       :processed-count processed-count
       :processed-percent (if (pos? size)
                            (if (zero? processed-count)
                              0
                              (* 100 (- 1 (/ (- size processed-count) size))))
                            100)})))

(rf/reg-sub ::services-creation
  :<- [::ptv]
  (fn [m _]
    (:services-creation m)))

(rf/reg-sub ::services-creation-progress
  :<- [::services-creation]
  (fn [{:keys [processed-ids size halt? in-progress?]} _]
    (let [processed-count (count processed-ids)]
      {:in-progress? in-progress?
       :halt? halt?
       :total-count size
       :processed-count processed-count
       :processed-percent (if (pos? size)
                            (if (zero? processed-count)
                              0
                              (* 100 (- 1 (/ (- size processed-count) size))))
                            100)})))

(rf/reg-sub ::missing-subcategories
  (fn [[_ org-id]]
    (rf/subscribe [::services org-id]))
  (fn [created-services [_ org-id]]
    (let [sub-cats types/sub-categories
          source-id->service (into {} (map (juxt :source-id identity) created-services))]
      (reduce (fn [acc [sub-cat-id sub-cat]]
                (let [source-id (ptv-data/->service-source-id org-id sub-cat-id)]
                  (if (get source-id->service source-id)
                    acc
                    (conj acc {:source-id source-id
                               :sub-category-id sub-cat-id
                               :sub-category (get-in sub-cat [:name :fi])
                               :label (get-in sub-cat [:name :fi])}))))
              []
              sub-cats))))

;; PTV Audit subscriptions
(rf/reg-sub ::audit
  :<- [::ptv]
  (fn [ptv _]
    (:audit ptv)))

(rf/reg-sub ::audit-tab
  :<- [::audit]
  (fn [audit _]
    (get audit :selected-tab :summary)))

(rf/reg-sub ::saving-audit?
  :<- [::audit]
  (fn [audit _]
    (:saving? audit)))

(rf/reg-sub ::audit-feedback
  :<- [::audit]
  (fn [audit [_ field]]
    (get-in audit [:feedback field])))

(rf/reg-sub ::audit-status
  :<- [::audit]
  (fn [audit [_ field]]
    (get-in audit [:status field])))

(rf/reg-sub ::site-audit-data
  (fn [db [_ lipas-id]]
    (let [org-id (get-in db [:ptv :selected-org :id])]
      (get-in db [:ptv :org org-id :data :sports-sites lipas-id :ptv :audit]))))

(rf/reg-sub ::site-audit-field-feedback
  (fn [db [_ lipas-id field]]
    (let [org-id (get-in db [:ptv :selected-org :id])]
      (get-in db [:ptv :org org-id :data :sports-sites lipas-id :ptv :audit field :feedback]))))

(rf/reg-sub ::site-audit-field-status
  (fn [db [_ lipas-id field]]
    (let [org-id (get-in db [:ptv :selected-org :id])]
      (get-in db [:ptv :org org-id :data :sports-sites lipas-id :ptv :audit field :status]))))

(rf/reg-sub ::has-audit-privilege?
  :<- [:lipas.ui.user.subs/user-data]
  (fn [user-data _]
    (roles/check-privilege user-data {} :ptv/audit)))

(rf/reg-sub ::has-manage-privilege?
  :<- [:lipas.ui.user.subs/user-data]
  (fn [user-data _]
    (roles/check-privilege user-data {} :ptv/manage)))

(rf/reg-sub ::default-tab
  :<- [::has-manage-privilege?]
  :<- [::has-audit-privilege?]
  (fn [[has-manage? has-audit?] _]
    (cond
      has-manage? "wizard"
      has-audit? "audit"
      :else nil)))

(rf/reg-sub ::selected-audit-site
  :<- [::ptv]
  (fn [ptv _]
    (:selected-audit-site ptv)))

(rf/reg-sub ::selected-audit-tab
  :<- [::ptv]
  (fn [ptv _]
    (get-in ptv [:audit :selected-tab] "todo")))

(rf/reg-sub ::auditable-sites
  (fn [[_ org-id status]]
    [(rf/subscribe [::ptv])])
  (fn [[ptv] [_ org-id status]]
    (let [sites (get-in ptv [:org org-id :data :sports-sites] {})

          filter-fn (case status
                      ;; Sites with content but not audited yet
                      :todo (fn [site]
                              (let [ptv (:ptv site)]
                                (and
                                 ;; Has summary and description content
                                  (some-> ptv :summary :fi count (> 5))
                                  (some-> ptv :description :fi count (> 5))
                                 ;; Not already audited
                                  (or (nil? (get-in ptv [:audit :summary :status]))
                                      (nil? (get-in ptv [:audit :description :status]))))))

                      ;; Sites that have been fully audited
                      :completed (fn [site]
                                   (let [ptv (:ptv site)]
                                     (and
                                      ;; Has audit data for both summary and description
                                       (some? (get-in ptv [:audit :summary :status]))
                                       (some? (get-in ptv [:audit :description :status])))))

                      ;; All sites with PTV content
                      (fn [site]
                        (let [ptv (:ptv site)]
                          (and
                            (some-> ptv :summary :fi count (> 5))
                            (some-> ptv :description :fi count (> 5))))))]

      ;; Apply filter and sort by name
      (->> (vals sites)
           (filter filter-fn)
           (sort-by :name)))))
