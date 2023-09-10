(ns lipas.ui.map.db)

(def default-db
  {:drawer-open?                 true
   :center                       {:lon 435047 :lat 7201408}
   :zoom                         2
   :mode                         {:name :default}
   :basemap                      :taustakartta
   :selected-overlays            #{:vectors
                                   :lois
                                   :edits
                                   :highlights
                                   :markers
                                   :analysis
                                   :schools
                                   :population}
   :selected-sports-site-tab     0
   :selected-new-sports-site-tab 0
   :import
   {:dialog-open?      false
    :selected-encoding "ISO-8859-1"
    :selected-items    #{}
    :replace-existing? true}
   :simplify
   {:dialog-open? false
    :tolerance    0}
   :address-search
   {:base-url     "https://api.digitransit.fi/geocoding/v1"
    :dialog-open? false}})
