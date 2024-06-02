(ns lipas.ui.ptv.subs
  (:require
   [clojure.string :as str]
   [lipas.ui.utils :as utils]
   [re-frame.core :as re-frame]))

(re-frame/reg-sub
 ::ptv
 (fn [db _]
   (:ptv db)))

(re-frame/reg-sub
 ::dialog-open?
 :<- [::ptv]
 (fn [ptv _]
   (get-in ptv [:dialog :open?])))

(re-frame/reg-sub
 ::selected-tab
 :<- [::ptv]
 (fn [ptv _]
   (:selected-tab ptv)))

(re-frame/reg-sub
 ::loading-from-ptv?
 :<- [::ptv]
 (fn [ptv _]
   (->> ptv :loading-from-ptv vals (some true?))))

(re-frame/reg-sub
 ::loading-from-lipas?
 :<- [::ptv]
 (fn [ptv _]
   (->> ptv :loading-from-lipas vals (some true?))))

(re-frame/reg-sub
 ::loading?
 :<- [::loading-from-ptv?]
 :<- [::loading-from-lipas?]
 (fn [[loading-from-ptv? loading-from-lipas?] _]
   (or loading-from-ptv? loading-from-lipas? )))

(re-frame/reg-sub
 ::selected-org
 :<- [::ptv]
 (fn [ptv _]
   (:selected-org ptv)))

(re-frame/reg-sub
 ::selected-org-id
 :<- [::selected-org]
 (fn [org _]
   (:id org)))

(re-frame/reg-sub
 ::org-default-settings
 :<- [::ptv]
 :<- [::selected-org-id]
 (fn [[ptv org-id] _]
   (get-in ptv [:org org-id :default-settings])))

(re-frame/reg-sub
 ::default-settings
 :<- [::ptv]
 :<- [::org-default-settings]
 (fn [[ptv org-defaults] _]
   (merge (:default-settings ptv) org-defaults)))

(re-frame/reg-sub
 ::selected-org-data
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

(re-frame/reg-sub
 ::services
 :<- [::ptv]
 :<- [::selected-org-id]
 (fn [[ptv org-id] _]
   (when org-id
     (let [services (get-in ptv [:org org-id :data :services])]
       (for [s services]
         (let [service-name (->> s :serviceNames first :value)]
           {:label                       service-name
            :service-id                  (:id s)
            :service-name                service-name
            :service-description-summary (->> s
                                              :serviceDescriptions
                                              (some (comp #{"summary"} :type)))
            :service-modified            (:modified s)}))))))

(re-frame/reg-sub
 ::services-by-id
 :<- [::services]
 (fn [services _]
   (utils/index-by :service-id services)))

(re-frame/reg-sub
 ::service-channels-old
 :<- [::ptv]
 :<- [::selected-org-id]
 (fn [[ptv org-id] _]
   (when org-id
     (let [{:keys [services _service-channels]} (get-in ptv [:org org-id :data])]
       (for [s  services
             sc (:serviceChannels s)]
         (let [service-name (->> s :serviceNames first :value)
               channel-name (-> sc :serviceChannel :name)]
           {:label                       channel-name
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

(re-frame/reg-sub
 ::service-channels
 :<- [::ptv]
 :<- [::selected-org-id]
 (fn [[ptv org-id] _]
   (when org-id
     (get-in ptv [:org org-id :data :service-channels]))))

(re-frame/reg-sub
 ::service-channels-by-id
 :<- [::service-channels]
 (fn [channels _]
   (utils/index-by :id channels)))

(defn parse-summary
  "Returns first line-delimited paragraph."
  [s]
  (when (string? s)
    (first (str/split s #"\r?\n"))))

(re-frame/reg-sub
 ::sports-sites
 :<- [::ptv]
 :<- [::selected-org-id]
 :<- [::services-by-id]
 :<- [::service-channels-by-id]
 (fn [[ptv org-id services service-channels] _]
   (let [lipas-id->site (get-in ptv [:org org-id :data :sports-sites])]
     (for [site (vals lipas-id->site)]
       (let [service-id               (-> site :ptv :service-id)
             service-channel-id       (-> site :ptv :service-channel-id)
             descriptions-integration (or (-> site :ptv :descriptions-integration)
                                          "lipas-managed")]
         {:lipas-id                    (:lipas-id site)
          :name                        (:name site)
          :marketing-name              (:marketing-name site)
          :type                        (-> site :search-meta :type :name :fi)
          :admin                       (-> site :search-meta :admin :name :fi)
          :owner                       (-> site :search-meta :owner :name :fi)
          :summary                     (case descriptions-integration
                                         "lipas-managed" (-> site :comment parse-summary)
                                         "manual"        (-> site :ptv :summary :fi))
          :description                 (case descriptions-integration
                                         "lipas-managed" (:comment site)
                                         "manual"        (-> site :ptv :description :fi))

          :descriptions-integration    descriptions-integration
          :sync-enabled                (get-in site [:ptv :sync-enabled] false)
          :last-sync                   (or (-> site :ptv :last-sync) "-")
          :service-id                  service-id
          :service-name                (get-in services [service-id :name])
          :service-integration         (or (-> site :ptv :service-integration)
                                           "lipas-managed")
          :service-channel-id          service-channel-id
          :service-channel-name        (get-in service-channels [service-channel-id :name])
          :service-channel-integration (or (-> site :ptv :service-channel-integration)
                                           "lipas-managed")})))))
