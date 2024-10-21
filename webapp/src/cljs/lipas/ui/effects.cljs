(ns lipas.ui.effects
  (:require ["file-saver" :as filesaver]
            ["zipcelx$default" :as zipcelx]
            [lipas.ui.config :as config]
            [lipas.ui.routes :as routes]
            [re-frame.core :as rf]))

(rf/reg-fx
  ::reset-scroll!
  (fn  [_]
    (js/window.scrollTo 0 0)))

(rf/reg-fx
  ::download-excel!
  (fn  [config]
    (zipcelx (clj->js config))))

(rf/reg-fx
  ::save-as!
  (fn [{:keys [blob filename]}]
    (filesaver/saveAs blob filename)))

(rf/reg-fx
  ::request-geolocation!
  (fn  [cb]
    (when-let [geolocation (-> js/navigator .-geolocation)]
      (.getCurrentPosition geolocation cb))))

(rf/reg-fx
  ::navigate!
  (fn [args]
    (apply routes/navigate! args)))

(rf/reg-fx
  ::open-link-in-new-window!
  (fn [url]
    (.open js/window url)))

(rf/reg-fx
  :tracker/page-view!
  (fn [[path]]
    #_(println "Track!" path)
    (when-not config/debug?
      (.push (.-_paq js/window) #js ["trackPageView" (str path)]))))

(rf/reg-fx
  :tracker/event!
  (fn [[category action k v]]
    #_(println "Event!" category action k v)
    (when-not config/debug?
      (.push (.-_paq js/window) #js ["trackEvent" category action k v]))))

(rf/reg-fx
  :tracker/search!
  (fn [[k category results-count]]
    #_(println "Search!" k category results-count)
    (when-not config/debug?
      (.push (.-_paq js/window) #js ["trackSiteSearch" k category results-count]))))

(def dimensions
  {"user-type" 1})

(rf/reg-fx
  :tracker/set-dimension!
  (fn [[k v]]
    #_(println "Custom dimension!" k v)
    (if-let [id (dimensions (name k))]
      (.push (.-_paq js/window) #js ["setCustomDimension" id v])
      (println "Unknown dimension" k))))

#_(def variables {})

#_(re-frame/reg-fx
    :tracker/set-variable!
    (fn [[k v scope]]
      #_(println "Custom variable!" k v scope)
      (if-let [idx (variables (name k))]
        (.push (.-_paq js/window) #js ["setCustomVariable" idx (name k) (name v) (name scope)])
        (println "Unknown variable" k))))
