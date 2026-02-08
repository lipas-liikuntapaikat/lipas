(ns lipas.schema.sports-sites.fields
  "Schema definitions for sports facility playing fields.
  
  Fields are playing surfaces within a sports facility. This namespace provides:
  - A base field schema with generic attributes applicable to all field types
  - Sport-specific field schemas (e.g., floorball) with specialized attributes
  - A multi-schema that dispatches on field :type for validation"
  (:require [lipas.data.floorball :as floorball]
            [lipas.data.materials :as materials]
            [lipas.schema.common :as common]
            [malli.util :as mu]))

;; Standalone schemas for frontend form validation
(def field-name [:string {:min 2 :max 100}])
(def lighting-lux [:int {:min 0 :max 10000}])
(def capacity-person [:int {:min 0 :max 100000}])
(def field-length-m [:and common/number [:fn #(<= 0 % 200)]])
(def field-width-m [:and common/number [:fn #(<= 0 % 200)]])
(def field-surface-area-m2 [:and common/number [:fn #(<= 0 % 20000)]])
(def safety-area-m [:and common/number [:fn #(<= 0 % 10)]])

;; Generic field attributes applicable to all field types
;; These attributes enable meaningful cross-sport analysis such as:
;; - Total field surface area in a facility
;; - Field dimension comparisons
;; - Capacity planning across different sports

(def base-field
  "Base schema for all field types with generic attributes.
  
  These attributes are meaningful across different sports and enable
  aggregate analysis like 'total playing surface area' in a facility."
  [:map
   {:description "Base playing field attributes applicable to all sports"}
   [:type {:description "Type of the field"}
    [:enum "floorball-field" "football-field"]]
   [:field-id {:description "Unique identifier for the field (UUID)"}
    [:string {:min 36 :max 36}]]
   [:name {:optional true :description "Name of the field"}
    [:string {:min 2 :max 100}]]
   [:length-m {:optional true :description "Length of the field in meters"}
    #'common/number]
   [:width-m {:optional true :description "Width of the field in meters"}
    #'common/number]
   [:surface-area-m2 {:optional true :description "Surface area of the field in square meters"}
    #'common/number]])

;; Floorball-specific field schema

(def floorball-field
  "Floorball field schema with sport-specific attributes.
  
  Extends the base field schema with floorball-specific attributes including:
  - Height requirements for indoor play
  - Surface materials and characteristics specific to floorball
  - Detailed lighting measurements per floorball regulations
  - Safety area measurements
  - Rink specifications
  - Spectator capacity and visibility requirements"
  (mu/merge
   #'base-field
   [:map
    {:description "Floorball-specific field attributes"}
    [:type {:description "Must be floorball-field"}
     [:= "floorball-field"]]
    [:minimum-height-m {:optional true :description "Minimum height of the field in meters (indoor requirement)"}
     #'common/number]
    [:surface-material {:optional true :description "Surface material of the field"}
     (into [:enum] (keys materials/field-surface-materials))]
    [:surface-material-product {:optional true :description "Brand/product name of the surface material"}
     [:string {:min 2 :max 100}]]
    [:surface-material-color {:optional true :description "Color of the surface material"}
     [:string {:min 2 :max 100}]]
    [:floor-elasticity {:optional true :description "Floor elasticity characteristics"}
     (into [:enum] (keys floorball/floor-elasticity))]

    ;; Lighting measurements per floorball regulations
    [:lighting-corner-1-1-lux {:optional true :description "Lighting at corner 1/1 in lux"}
     [:int {:min 0 :max 10000}]]
    [:lighting-corner-1-2-lux {:optional true :description "Lighting at corner 1/2 in lux"}
     [:int {:min 0 :max 10000}]]
    [:lighting-corner-2-1-lux {:optional true :description "Lighting at corner 2/1 in lux"}
     [:int {:min 0 :max 10000}]]
    [:lighting-corner-2-2-lux {:optional true :description "Lighting at corner 2/2 in lux"}
     [:int {:min 0 :max 10000}]]
    [:lighting-goal-1-lux {:optional true :description "Lighting at goal 1 in lux"}
     [:int {:min 0 :max 10000}]]
    [:lighting-goal-2-lux {:optional true :description "Lighting at goal 2 in lux"}
     [:int {:min 0 :max 10000}]]
    [:lighting-center-point-lux {:optional true :description "Lighting at center point in lux"}
     [:int {:min 0 :max 10000}]]
    [:lighting-average-lux {:optional true :description "Average lighting in lux"}
     [:int {:min 0 :max 10000}]]

    ;; Safety areas
    [:safety-area-end-1-m {:optional true :description "Safety area at end 1 in meters"}
     #'common/number]
    [:safety-area-end-2-m {:optional true :description "Safety area at end 2 in meters"}
     #'common/number]
    [:safety-area-side-1-m {:optional true :description "Safety area at side 1 in meters"}
     #'common/number]
    [:safety-area-side-2-m {:optional true :description "Safety area at side 2 in meters"}
     #'common/number]

    ;; Rink specifications
    [:rink-product {:optional true :description "Brand/product name of the rink"}
     [:string {:min 2 :max 100}]]
    [:rink-color {:optional true :description "Color of the rink"}
     [:string {:min 2 :max 100}]]

    ;; Spectator capacity
    [:stands-total-capacity-person {:optional true :description "Total capacity of stands"}
     [:int {:min 0 :max 100000}]]
    [:seating-area-capacity-person {:optional true :description "Capacity of seating area"}
     [:int {:min 0 :max 100000}]]
    [:standing-area-capacity-person {:optional true :description "Capacity of standing area"}
     [:int {:min 0 :max 100000}]]
    [:accessible-seating-capacity-person {:optional true :description "Capacity of accessible seating"}
     [:int {:min 0 :max 100000}]]

    ;; Visibility and accessibility
    [:scoreboard-visible-to-benches? {:optional true :description "Is scoreboard visible to benches"}
     :boolean]
    [:scoreboard-visible-to-officials? {:optional true :description "Is scoreboard visible to officials"}
     :boolean]
    [:field-accessible-without-strairs? {:optional true :description "Is field accessible without stairs"}
     :boolean]
    [:audience-stand-access {:optional true :description "How audience accesses the stands"}
     (into [:enum] (keys floorball/audience-stand-access))]]))

;; Multi-schema for field validation

(def field
  "Multi-schema for field validation that dispatches on :type.
  
  This allows for sport-specific validation while maintaining a common
  base structure. New field types can be added by:
  1. Creating a sport-specific schema extending base-field
  2. Adding a new case to this multi-schema"
  [:multi {:dispatch :type
           :description "A playing field within a sports facility"}
   ["floorball-field" #'floorball-field]
   ;; Future field types would be added here:
   ;; ["football-field" football-field]
   ;; ["tennis-court" tennis-court]
   ])

(def fields
  "Vector of fields in a facility"
  [:vector {:description "Collection of playing fields in the facility"}
   #'field])
