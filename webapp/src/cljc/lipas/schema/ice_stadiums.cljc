(ns lipas.schema.ice-stadiums
  (:require [lipas.schema.common :as common]))

;; Rink dimension schemas — (number-in :min 0 :max N) uses (<= 0 % (dec N))
(def rink-width-m [:and common/number [:fn #(<= 0 % 99)]])
(def rink-length-m [:and common/number [:fn #(<= 0 % 99)]])
(def rink-area-m2 [:and common/number [:fn #(<= 0 % 4999)]])
