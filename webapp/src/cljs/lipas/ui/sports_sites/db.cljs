(ns lipas.ui.sports-sites.db
  (:require [lipas.data.admins :as admins]
            [lipas.data.materials :as materials]
            [lipas.data.owners :as owners]
            [lipas.data.sports-sites :as sports-sites]
            [lipas.data.types :as types]
            [lipas.ui.sports-sites.activities.db :as activities]
            [lipas.ui.sports-sites.floorball.db :as floorball]
            [lipas.ui.sports-sites.football.db :as football]
            [lipas.ui.sports-sites.hall-equipment :as hall-equipment]
            [lipas.ui.utils :as utils]))

;; FIXME: What is the benefit of moving static data to re-frame app-db?
;; This breaks editing the data ns and getting the result visible right away.

(def default-db
  {:statuses          sports-sites/statuses
   :document-statuses sports-sites/document-statuses ;; unused?
   :field-types       sports-sites/field-types

   :admins       admins/all
   :owners       owners/all
   :types        types/all
   :active-types types/active

   :prop-types types/used-prop-types

   :materials             materials/all
   :building-materials    materials/building-materials
   :supporting-structures materials/supporting-structures
   :ceiling-structures    materials/ceiling-structures
   :base-floor-structures materials/base-floor-structures
   ;; Full enum — this backs the :surface-material prop selector, and the
   ;; prop's schema/opts accept every value here. The narrower
   ;; sports-site-surface-materials lacks resin/carpet (floorball floors),
   ;; which rendered stored values as blank chips and made them unpickable.
   :surface-materials     materials/surface-materials

   :delete-dialog
   {:open?           false
    :selected-status nil
    :selected-year   utils/this-year}

   :football       football/default-db
   :floorball      floorball/default-db
   :activities     activities/default-db
   :hall-equipment hall-equipment/default-db})
