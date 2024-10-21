(ns lipas.ui.ptv.subs
  (:require [clojure.string :as str]
            [lipas.ui.utils :as utils]
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
  (fn [ptv _]
    (:selected-tab ptv)))

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
  :<- [::selected-org-id]
  (fn [[ptv org-id] _]
    (get-in ptv [:org org-id :default-settings])))

(rf/reg-sub ::default-settings
  :<- [::ptv]
  :<- [::org-default-settings]
  (fn [[ptv org-defaults] _]
    (merge (:default-settings ptv) org-defaults)))

(def lang
  {"fi" "fi" "sv" "se" "en" "en"})

(rf/reg-sub ::org-languages
  :<- [::ptv]
  :<- [::selected-org-id]
  (fn [[ptv org-id] _]
   ;; There are (undocumented) business rules regarding in which lang
   ;; data can be entered. One of the rules seems to be that the org
   ;; must be described with all the desired languages before other
   ;; data can be entered. AFAIK there's no direct way to get the org
   ;; 'supported languages' so we infer them from org name
   ;; translations. wtf
    (->> (get-in ptv [:org org-id :data :org org-id :organizationNames])
         (keep #(get lang (:language %))))))

(rf/reg-sub ::selected-org-data
  :<- [::ptv]
  :<- [::selected-org-id]
  (fn [[ptv org-id] _]
    (when org-id
      (let [{:keys [services _service-channels]} (get-in ptv [:org org-id :data])]
        (for [s  services
              sc (:serviceChannels s)]
          (let [service-name (->> s :serviceNames first :value)
                channel-name (-> sc :serviceChannel :name)]
            {:label                       (str service-name " > " channel-name)
             :service-id                  (:id s)
             :service-name                service-name
             :service-description-summary (->> s
                                               :serviceDescriptions
                                               (some (comp #{"summary"} :type)))
             :service-modified            (:modified s)

             :service-channel-id   (-> sc :serviceChannel :id)
             :service-channel-name channel-name
            ;; TODO rest of the gunk, service hours, descriptions etc.
             }))))))

(rf/reg-sub ::services-by-id
  :<- [::ptv]
  :<- [::selected-org-id]
  (fn [[ptv org-id] _]
    (when org-id
      (get-in ptv [:org org-id :data :services]))))

(def langs {"sv" "se" "fi" "fi" "en" "en"})

(rf/reg-sub ::services
  :<- [::services-by-id]
  (fn [services _]
    (for [[id service] services]
      (let [service-name (->> service :serviceNames (some #(when (= "fi" (:language %))
                                                             (:value %))))
            descriptions (->> service :serviceDescriptions (filter #(= (:type %) "Description")))
            summaries    (->> service :serviceDescriptions (filter #(= (:type %) "Summary")))]
        {:label               service-name
         :service-id          id
         :source-id           (:sourceId service)
         :last-modified       (:modified service)
         :last-modified-human (utils/->human-date-time-at-user-tz (:modified service))
         :service-classes     (->> service
                                   :serviceClasses
                                   (map (fn [m]
                                          (utils/index-by (comp langs :language) :value (:name m)))))
         :languages           (map langs (:languages service))
         :summary             (utils/index-by (comp langs :language) :value summaries)
         :description         (utils/index-by (comp langs :language) :value descriptions)
         :service-name        service-name
         :service-modified    (:modified service)
         :service-channels    (->> service :serviceChannels (map :serviceChannel))
         :ontology-terms      (->> service
                                   :ontologyTerms
                                   (map (fn [m]
                                          (utils/index-by (comp langs :language) :value (:name m)))))}))))

(rf/reg-sub ::services-filter
  :<- [::ptv]
  (fn [ptv _]
    (:services-filter ptv)))

(rf/reg-sub ::services-filtered
  :<- [::services]
  :<- [::services-filter]
  (fn [[services filter'] _]
    (sort-by :label
             (case filter'
               "lipas-managed" (filter (fn [m] (some-> m :source-id (str/starts-with? "lipas-"))) services)
               services))))

(defn ->source-id
  [org-id sub-category-id]
  (str "lipas-" org-id "-" sub-category-id))

(defn resolve-missing-services
  "Infer services (sub-categories) that need to be created in PTV and
  attached to sports-sites."
  [org-id services types sports-sites]
  (let [source-ids (->> services vals (keep :sourceId) set)]
    (->> sports-sites
         (filter (fn [{:keys [ptv]}] (empty? (:service-ids ptv))))
         (map (fn [site] {:source-id       (->source-id org-id (:sub-category-id site))
                          :sub-category    (-> site :sub-category)
                          :sub-category-id (-> site :sub-category-id)}))
         distinct
         (remove (fn [m] (contains? source-ids (:source-id m)))))))

(rf/reg-sub ::missing-services
  :<- [::selected-org-id]
  :<- [::services-by-id]
  :<- [::sports-sites]
  :<- [:lipas.ui.sports-sites.subs/all-types]
  (fn [[org-id services sports-sites types] _]
    (resolve-missing-services org-id services types sports-sites)))

(rf/reg-sub ::service-candidate-descriptions
  :<- [::selected-org-id]
  :<- [::ptv]
  (fn [[org-id ptv] _]
    (get-in ptv [:org org-id :data :service-candidates])))

(rf/reg-sub ::service-candidates
  :<- [::missing-services]
  :<- [::service-candidate-descriptions]
  :<- [::org-languages]
  (fn [[missing-services descriptions org-langs] _]
    (->> missing-services
         (map (fn [{:keys [source-id] :as m}]
                (let [description (get-in descriptions [source-id :description])
                      summary     (get-in descriptions [source-id :summary])]
                  (-> m
                      (assoc :languages (or (get-in descriptions [source-id :languages])
                                            org-langs))
                      (assoc :description description)
                      (assoc :summary summary)
                      (assoc :valid (boolean (and
                                               (some-> description :fi count (> 5))
                                               (some-> summary :fi count (> 5))))))))))))

(rf/reg-sub ::service-channels-by-id
  :<- [::ptv]
  :<- [::selected-org-id]
  (fn [[ptv org-id] _]
    (when org-id
      (get-in ptv [:org org-id :data :service-channels]))))

(rf/reg-sub ::service-channels
  :<- [::service-channels-by-id]
  (fn [channels _]
    (vals channels)))

(defn resolve-service-channel-name
  "Sometimes these seem to have the name under undocumented :name
  property and sometimes under documented :serviceChannelNames
  array. Wtf."
  [service-channel]
  (or (:name service-channel)
      (some (fn [m]
              (when (= "fi" (:language m))
                (:value m)))
            (:serviceChannelNames service-channel))))

(rf/reg-sub ::service-channels-list
  :<- [::service-channels]
  (fn [channels _]
    (for [m channels]
      {:service-channel-id (:id m)
       :name               (resolve-service-channel-name m)})))

(defn parse-summary
  "Returns first line-delimited paragraph."
  [s]
  (when (string? s)
    (first (str/split s #"\r?\n"))))

(defn detect-name-conflict
  [sports-site service-channels]
  (let [s1                (some-> sports-site :name str/trim str/lower-case)
        attached-channels (-> sports-site :ptv :service-channel-ids set)]
    (some (fn [service-channel]
            (let [ssname (resolve-service-channel-name service-channel)
                  s2     (some-> ssname str/trim str/lower-case)]
              (when (and
                      (not (contains? attached-channels (:id service-channel)))
                      (= s1 s2))
                {:service-channel-id (:id service-channel)})))
          service-channels)))

(rf/reg-sub ::sports-sites
  :<- [::ptv]
  :<- [::selected-org-id]
  :<- [::services-by-id]
  :<- [::service-channels-by-id]
  :<- [::default-settings]
  :<- [:lipas.ui.subs/translator]
  :<- [:lipas.ui.sports-sites.subs/all-types]
  :<- [::org-languages]
  (fn [[ptv org-id services service-channels org-defaults tr types org-langs] _]
    (let [lipas-id->site (get-in ptv [:org org-id :data :sports-sites])]
      (for [site (vals lipas-id->site)]
        (let [service-id               (-> site :ptv :service-ids first)
              service-channel-id       (-> site :ptv :service-channel-ids first)
              descriptions-integration (or (-> site :ptv :descriptions-integration)
                                           (:descriptions-integration org-defaults))

              summary (case descriptions-integration
                        "lipas-managed-comment-field"
                        (-> site :comment parse-summary)

                        "lipas-managed-ptv-fields"
                        (-> site :ptv :summary)

                        "ptv-managed"
                        (tr :ptv.integration.description/ptv-managed-helper))
              description (case descriptions-integration
                            "lipas-managed-comment-field"
                            (-> site :comment)

                            "lipas-managed-ptv-fields"
                            (-> site :ptv :description)

                            "ptv-managed"
                            (tr :ptv.integration.description/ptv-managed-helper))]
          {:valid           (boolean (and (some-> description :fi count (> 5))
                                          (some-> summary :fi count (> 5))))
           :lipas-id        (:lipas-id site)
           :name            (:name site)
           :name-conflict   (detect-name-conflict site (vals service-channels))
           :marketing-name  (:marketing-name site)
           :type            (-> site :search-meta :type :name :fi)
           :sub-category    (-> site :search-meta :type :sub-category :name :fi)
           :sub-category-id (-> site :type :type-code types :sub-category)
           :org-id          org-id
           :admin           (-> site :search-meta :admin :name :fi)
           :owner           (-> site :search-meta :owner :name :fi)
           :summary         summary
           :description     description
           :languages       (or (-> site :ptv :languages) org-langs)

           :descriptions-integration    descriptions-integration
           :sync-enabled                (get-in site [:ptv :sync-enabled] true)
           :last-sync                   (-> site :ptv :last-sync)
           :last-sync-human                   (or (some-> site
                                                          :ptv
                                                          :last-sync
                                                          utils/->human-date-time-at-user-tz)
                                                  "Ei koskaan")
           :service-ids                 (-> site :ptv :service-ids)
           :service-name                (-> services (get service-id) :serviceNames
                                            (->> (some #(when (= "fi" (:language %)) (:value %)))))
           :service-integration         (or (-> site :ptv :service-integration)
                                            (:service-integration org-defaults))
           :service-channel-id          service-channel-id
           :service-channel-ids         (-> site :ptv :service-channel-ids)
           :service-channel-name        (-> (get service-channels service-channel-id)
                                            (resolve-service-channel-name))
           :service-channel-integration (or (-> site :ptv :service-channel-integration)
                                            (:service-channel-integration org-defaults))})))))

(rf/reg-sub ::sports-sites-count
  :<- [::sports-sites]
  (fn [sports-sites _]
    (count sports-sites)))

(rf/reg-sub ::sync-all-enabled?
  :<- [::sports-sites]
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
       :in-progress?        in-progress?
       :halt?               halt?
       :total-count         size
       :processed-count     processed-count
       :processed-percent   (if (pos? size)
                              (if (zero? processed-count)
                                0
                                (* 100 (- 1 (/ (- size processed-count) size))))
                              100)})))

(rf/reg-sub ::sports-site-setup-done
  :<- [::sports-sites]
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
  :<- [::sports-sites]
  :<- [::sports-sites-filter]
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
  :<- [::sports-sites-filtered]
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
      {:processed-ids     (set processed-ids)
       :in-progress?      in-progress?
       :halt?             halt?
       :total-count       size
       :processed-count   processed-count
       :processed-percent (if (pos? size)
                            (if (zero? processed-count)
                              0
                              (* 100 (- 1 (/ (- size processed-count) size))))
                            100)})))

(rf/reg-sub ::service-locations-creation
  :<- [::ptv]
  (fn [m _]
    (:service-location-creation m)))

(rf/reg-sub ::service-location-creation-progress
  :<- [::service-locations-creation]
  (fn [{:keys [processed-lipas-ids size halt? in-progress?]} _]
    (let [processed-count (count processed-lipas-ids)]
      {:processed-lipas-ids (set processed-lipas-ids)
       :in-progress?        in-progress?
       :halt?               halt?
       :total-count         size
       :processed-count     processed-count
       :processed-percent   (if (pos? size)
                              (if (zero? processed-count)
                                0
                                (* 100 (- 1 (/ (- size processed-count) size))))
                              100)})))
