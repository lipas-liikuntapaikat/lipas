(ns lipas.ui.db
  (:require [lipas.data.cities :as cities]
            [lipas.i18n.core :as i18n]
            [lipas.ui.admin.db :as admin]
            [lipas.ui.analysis.db :as analysis]
            [lipas.ui.bulk-operations.db :as bulk-ops]
            [lipas.ui.energy.db :as energy]
            [lipas.ui.feedback.db :as feedback]
            [lipas.ui.front-page.db :as front-page]
            [lipas.ui.help.db :as help]
            [lipas.ui.ice-stadiums.db :as ice-stadiums]
            [lipas.ui.loi.db :as loi]
            [lipas.ui.map.db :as map]
            [lipas.ui.ptv.db :as ptv]
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
   ;; This is just a wrapper fn calling latest translate fn (var) in the i18n.core
   ;; so if core ns is reloaded, app uses the latest translation dicts.
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
   :analysis analysis/default-db

   ;; Feedback
   :feedback feedback/default-db

   ;; LOI
   :loi loi/default-db

   ;; PTV
   :ptv ptv/default-db

   ;; Help
   :help help/default-db

   ;; Bulk operations
   :bulk-operations bulk-ops/default-db})
