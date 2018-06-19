(ns lipas.data.owners)

(def all #{:city
           :registered-association
           :company-ltd
           :city-main-owner
           :foundation
           :state
           :other
           :unknown})

(type (set (keys {:a 1 :b 2})))
