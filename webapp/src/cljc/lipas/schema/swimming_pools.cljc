(ns lipas.schema.swimming-pools
  (:require [lipas.data.materials :as materials]
            [lipas.data.swimming-pools :as swimming-pools]
            [lipas.schema.common :as common]))

(def pool-type (into [:enum] (keys swimming-pools/pool-types)))

(def accessibility-feature (into [:enum] (keys swimming-pools/accessibility)))

(def pool-schema
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
   [:accessibility {:optional true} [:vector {:distinct true} accessibility-feature]]])

;; Standalone schemas for pool fields — (number-in :min 0 :max N) uses (<= 0 % (dec N))
(def depth-min-m [:and common/number [:fn #(<= 0 % 9)]])
(def volume-m3 [:and common/number [:fn #(<= 0 % 4999)]])

;; Facility platform counts — (int-in 0 100) exclusive upper
(def platforms-count [:int {:min 0 :max 99}])

(def slide-structure (into [:enum] (keys materials/slide-structures)))

(def slide-schema
  [:map
   [:length-m [:and common/number [:fn #(<= 0 % 200)]]]
   [:structure {:optional true} slide-structure]])
