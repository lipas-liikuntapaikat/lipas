(ns lipas.schema.ice-stadiums
  (:require [lipas.schema.common :as common]
            [malli.core :as m]))

;; Rink dimension schemas — (number-in :min 0 :max N) uses (<= 0 % (dec N))
(def rink-width-m (m/schema [:and common/number [:fn #(<= 0 % 99)]]))
(def rink-length-m (m/schema [:and common/number [:fn #(<= 0 % 99)]]))
(def rink-area-m2 (m/schema [:and common/number [:fn #(<= 0 % 4999)]]))
