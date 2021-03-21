(ns lipas.ui.map.db)

(def default-db
  {:drawer-open?      true
   :center            {:lon 435047 :lat 7201408}
   :zoom              2
   :mode              {:name :default}
   :basemap           :taustakartta
   :selected-overlays #{:vectors :edits :markers :population :schools}
   :import
   {:dialog-open?      false
    :selected-encoding "ISO-8859-1"
    :selected-items    #{}
    :replace-existing? true}
   :address-search
   {:base-url     "https://api.digitransit.fi/geocoding/v1"
    :dialog-open? false}
   :analysis
   {:selected-tab "sports-sites"
    :zones
    [{:min 0 :max 2 :id :zone1}
     {:min 2 :max 5 :id :zone2}
     {:min 5 :max 10 :id :zone3}]
    :sports-sites
    {:view "list"}
    :schools
    {:view "list"}}})
