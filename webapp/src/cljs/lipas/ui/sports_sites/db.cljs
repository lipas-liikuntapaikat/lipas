(ns lipas.ui.sports-sites.db
  (:require [lipas.data.admins :as admins]
            [lipas.data.materials :as materials]
            [lipas.data.owners :as owners]
            [lipas.data.prop-types :as prop-types]
            [lipas.data.sports-sites :as sports-sites]
            [lipas.data.types :as types]
            [lipas.ui.sports-sites.activities.db :as activities]
            [lipas.ui.sports-sites.floorball.db :as floorball]
            [lipas.ui.sports-sites.football.db :as football]
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
   :surface-materials     materials/sports-site-surface-materials

   :delete-dialog
   {:open?           false
    :selected-status nil
    :selected-year   utils/this-year}

   :football   football/default-db
   :floorball  floorball/default-db
   :activities activities/default-db})
