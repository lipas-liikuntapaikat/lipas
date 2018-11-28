(ns lipas.ui.db
  (:require [clojure.spec.alpha :as s]
            [lipas.data.admins :as admins]
            [lipas.data.cities :as cities]
            [lipas.data.ice-stadiums :as ice-stadiums]
            [lipas.data.materials :as materials]
            [lipas.data.owners :as owners]
            [lipas.data.swimming-pools :as swimming-pools]
            [lipas.data.types :as types]
            [lipas.schema.core :as schema]
            [lipas.i18n.core :as i18n]
            [lipas.ui.utils :as utils]))

(def default-db
  {:active-panel :main-panel
   :backend-url  "/api"
   :logged-in?   false
   :drawer-open? false
   :translator   (i18n/->tr-fn :fi)

   ;; Sports sites
   :sports-sites {}

   :admins                admins/all
   :owners                owners/all
   :cities                (utils/index-by :city-code cities/active)
   :types                 (utils/index-by :type-code types/all)
   :materials             materials/all
   :building-materials    materials/building-materials
   :supporting-structures materials/supporting-structures
   :ceiling-structures    materials/ceiling-structures
   :base-floor-structures materials/base-floor-structures

   ;; Ice stadiums
   :ice-stadiums
   {:active-tab                0
    :editing                   nil
    :editing?                  false
    :dialogs
    {:rink {:open? false}}
    :size-categories           ice-stadiums/size-categories
    :condensate-energy-targets ice-stadiums/condensate-energy-targets
    :refrigerants              ice-stadiums/refrigerants
    :refrigerant-solutions     ice-stadiums/refrigerant-solutions
    :heat-recovery-types       ice-stadiums/heat-recovery-types
    :dryer-types               ice-stadiums/dryer-types
    :dryer-duty-types          ice-stadiums/dryer-duty-types
    :heat-pump-types           ice-stadiums/heat-pump-types
    :ice-resurfacer-fuels      ice-stadiums/ice-resurfacer-fuels}

   ;; Swimming pools
   :swimming-pools
   {:active-tab        0
    :pool-types        swimming-pools/pool-types
    :sauna-types       swimming-pools/sauna-types
    :filtering-methods swimming-pools/filtering-methods
    :heat-sources      swimming-pools/heat-sources
    :pool-structures   materials/pool-structures
    :editing           nil
    :editing?          false
    :dialogs
    {:pool   {:open? false}
     :slide  {:open? false}
     :energy {:open? false}
     :sauna  {:open? false}}}

   ;; Energy stats
   :energy-stats
   {:chart-energy-type :energy-mwh}

   ;; User
   :user
   {:login-form        {}
    :registration-form {}}

   ;; Search
   :search
   {:filters {:type-codes #{}
              :city-codes #{}}}

   ;; Map
   :map
   {:drawer-open? true
    :center       {:lon 435047 :lat 6901408}
    :zoom         2
    :mode         {:name :default}
    :basemap      :taustakartta}})
