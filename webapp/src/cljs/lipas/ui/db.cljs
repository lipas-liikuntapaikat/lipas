(ns lipas.ui.db
  (:require
   [clojure.spec.alpha :as s]
   [lipas.data.cities :as cities]
   [lipas.i18n.core :as i18n]
   [lipas.schema.core :as specs]
   [lipas.ui.admin.db :as admin]
   [lipas.ui.analysis.db :as analysis]   
   [lipas.ui.energy.db :as energy]
   [lipas.ui.front-page.db :as front-page]
   [lipas.ui.ice-stadiums.db :as ice-stadiums]
   [lipas.ui.map.db :as map]
   [lipas.ui.reminders.db :as reminders]
   [lipas.ui.reports.db :as reports]
   [lipas.ui.search.db :as search]
   [lipas.ui.sports-sites.db :as sports-sites]
   [lipas.ui.stats.db :as stats]
   [lipas.ui.swimming-pools.db :as swimming-pools]
   [lipas.ui.user.db :as user]))

(def default-db
  {;; General
   :active-panel  :front-page-panel
   :backend-url   "/api"
   :logged-in?    false
   :drawer-open?  false
   :screen-size   "lg"
   :current-route nil
   :translator    (i18n/->tr-fn :fi)

   ;; Admin
   :admin admin/default-db

   ;; Cities
   :cities                cities/by-city-code
   :cities-by-avi-id      cities/by-avi-id
   :cities-by-province-id cities/by-province-id
   :provinces             cities/provinces
   :avi-areas             cities/avi-areas
   :abolished-cities      cities/abolished-by-city-code

   ;; Energy
   :energy energy/default-db

   ;; Front page
   :front-page front-page/default-db
   
   ;; Ice stadiums
   :ice-stadiums ice-stadiums/default-db

   ;; Map
   :map map/default-db

   ;; Sports sites
   :sports-sites sports-sites/default-db

   ;; Swimming pools
   :swimming-pools swimming-pools/default-db

   ;; User
   :user user/default-db

   ;; Reminders
   :reminders reminders/default-db

   ;; Reports
   :reports reports/default-db

   ;; Search
   :search search/default-db

   ;; Stats
   :stats stats/default-db

   ;; Analysis
   :analysis analysis/default-db})
