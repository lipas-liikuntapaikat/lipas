(ns lipas.schema.swimming-pools
  (:require [lipas.data.materials :as materials]
            [lipas.data.swimming-pools :as swimming-pools]
            [lipas.schema.common :as common]
            [malli.core :as m]))

(def pool-type (m/schema (into [:enum] (keys swimming-pools/pool-types))))

(def accessibility-feature (m/schema (into [:enum] (keys swimming-pools/accessibility))))

(def pool-schema
  (m/schema
   [:map
    [:type {:optional true} pool-type]
    [:outdoor-pool? {:optional true} :boolean]
    [:temperature-c {:optional true} [:and common/number [:fn #(<= 0 % 50)]]]
    [:volume-m3 {:optional true} [:and common/number [:fn #(<= 0 % 5000)]]]
    [:area-m2 {:optional true} [:and common/number [:fn #(<= 0 % 2000)]]]
    [:length-m {:optional true} [:and common/number [:fn #(<= 0 % 200)]]]
    [:width-m {:optional true} [:and common/number [:fn #(<= 0 % 100)]]]
    [:depth-min-m {:optional true} [:and common/number [:fn #(<= 0 % 10)]]]
    [:depth-max-m {:optional true} [:and common/number [:fn #(<= 0 % 10)]]]
    [:accessibility {:optional true} [:vector {:distinct true} accessibility-feature]]]))

;; Standalone schemas for pool/slide form validation (used in view :spec props)
(def pool-length-m (m/schema [:and common/number [:fn #(<= 0 % 200)]]))
(def pool-width-m (m/schema [:and common/number [:fn #(<= 0 % 100)]]))
(def pool-area-m2 (m/schema [:and common/number [:fn #(<= 0 % 2000)]]))
(def pool-depth-m (m/schema [:and common/number [:fn #(<= 0 % 10)]]))
(def pool-temperature-c (m/schema [:and common/number [:fn #(<= 0 % 50)]]))

;; Standalone schemas for pool fields — (number-in :min 0 :max N) uses (<= 0 % (dec N))
(def depth-min-m (m/schema [:and common/number [:fn #(<= 0 % 9)]]))
(def volume-m3 (m/schema [:and common/number [:fn #(<= 0 % 4999)]]))

;; Facility platform counts — (int-in 0 100) exclusive upper
(def platforms-count (m/schema [:int {:min 0 :max 99}]))

(def slide-structure (m/schema (into [:enum] (keys materials/slide-structures))))

(def slide-schema
  (m/schema
   [:map
    [:length-m [:and common/number [:fn #(<= 0 % 200)]]]
    [:structure {:optional true} slide-structure]]))
