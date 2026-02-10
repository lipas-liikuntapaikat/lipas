(ns lipas.schema.swimming-pools
  (:require [lipas.data.materials :as materials]
            [lipas.data.swimming-pools :as swimming-pools]
            [lipas.schema.common :as common]
            [malli.core :as m]))

(def pool-type (m/schema (into [:enum] (keys swimming-pools/pool-types))))

(def accessibility-feature (m/schema (into [:enum] (keys swimming-pools/accessibility))))

;; Standalone schemas for form field validation (used in view :spec props)
(def pool-length-m (m/schema [:and common/number [:fn #(<= 0 % 200)]]))
(def pool-width-m (m/schema [:and common/number [:fn #(<= 0 % 100)]]))
(def pool-area-m2 (m/schema [:and common/number [:fn #(<= 0 % 2000)]]))
(def pool-depth-m (m/schema [:and common/number [:fn #(<= 0 % 10)]]))
(def pool-temperature-c (m/schema [:and common/number [:fn #(<= 0 % 50)]]))

;; These have different bounds from pool-schema (exclusive upper from original spec's number-in)
(def depth-min-m (m/schema [:and common/number [:fn #(<= 0 % 9)]]))
(def volume-m3 (m/schema [:and common/number [:fn #(<= 0 % 4999)]]))

;; Facility platform counts — (int-in 0 100) exclusive upper
(def platforms-count (m/schema [:int {:min 0 :max 99}]))

(def pool-schema
  (m/schema
   [:map
    [:type {:optional true} pool-type]
    [:outdoor-pool? {:optional true} :boolean]
    [:temperature-c {:optional true} pool-temperature-c]
    [:volume-m3 {:optional true} [:and common/number [:fn #(<= 0 % 5000)]]]
    [:area-m2 {:optional true} pool-area-m2]
    [:length-m {:optional true} pool-length-m]
    [:width-m {:optional true} pool-width-m]
    [:depth-min-m {:optional true} pool-depth-m]
    [:depth-max-m {:optional true} pool-depth-m]
    [:accessibility {:optional true} [:vector {:distinct true} accessibility-feature]]]))

(def slide-structure (m/schema (into [:enum] (keys materials/slide-structures))))

(def slide-schema
  (m/schema
   [:map
    [:length-m pool-length-m]
    [:structure {:optional true} slide-structure]]))
