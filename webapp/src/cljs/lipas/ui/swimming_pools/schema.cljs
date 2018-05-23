(ns lipas.ui.swimming-pools.schema
  (:require [clojure.spec.alpha :as s]
            [lipas.ui.schemas :as lipas]))

(def this-year (.getFullYear (js/Date.)))

;;; General ;;;

(comment (s/valid? ::email "kissa@koira.fi"))
(comment (s/valid? ::email "@koira.fi"))
(s/def ::email ::lipas/email-type)

(s/def ::relevant-year (s/int-in 1800 (inc this-year)))

(comment (s/valid? ::construction-year 2018))
(s/def ::construction-year ::relevant-year)

(comment (s/valid? ::material :concrete))
(comment (s/valid? ::material :kebab))
(s/def ::material #{:concrete :brick :tile :steel :wood :glass})

;;; Building ;;;

(s/def ::main-designers string?)
(s/def ::total-surface-area-m2 (s/int-in 100 (inc 50000)))
(s/def ::total-volume-m3 (s/int-in 100 (inc 200000)))
(s/def ::pool-room-total-area-m2 (s/int-in 100 (inc 10000)))
(s/def ::total-water-area-m2 (s/int-in 100 (inc 10000)))
(s/def ::heat-sections boolean?)
(comment (s/valid? ::main-construction-materials [:concrete :brick]))
(s/def ::main-construction-materials (s/coll-of ::material))
(s/def ::piled? boolean?)
(s/def ::supporting-structures-description string?)
(s/def ::ceiling-description string?)
(s/def ::staff-count (s/int-in 0 (inc 1000)))
(s/def ::seating-capacity (s/int-in 0 (inc 1000)))
(s/def ::heat-source #{:private-power-station :district-heating})
(comment (s/valid? ::ventilation-units-count 100))
(s/def ::ventilation-units-count (s/int-in 0 (inc 100)))

(s/def ::building (s/keys :opt-un [::construction-year
                                   ::main-designers
                                   ::total-surface-area-m2
                                   ::total-volume-m3
                                   ::pool-room-total-area-m2
                                   ::total-water-area-m2
                                   ::heat-sections?
                                   ::main-construction-materials
                                   ::piled?
                                   ::supporting-structures-description
                                   ::ceiling-description
                                   ::staff-count
                                   ::seating-capacity
                                   ::heat-source
                                   ::ventilation-units-count]))

(comment (s/valid? ::building {:construction-year 1995 :main-designer "Tipokatti"}))

;;; Renovations ;;;

(s/def ::year ::relevant-year)
(s/def ::comment string?)

(s/def ::renovation (s/keys :opt-un [::year
                                     ::comment
                                     ::main-designers]))

;;; Water treatment ;;;

(s/def ::ozonation boolean?)
(s/def ::uv-treatment boolean?)
(s/def ::activated-carbon boolean?)

(s/def ::filtering-method #{:pressure-suction      ; Paineimu
                            :pressure-sand         ; Painehiekka
                            :suction-sand          ; Imuhiekka ?
                            :open-sand             ; Avohiekka
                            :other                 ; Muu
                            :multi-layer-filtering ; Monikerrossuodatus
                            :coal                  ; Hiili ?
                            :precipitation         ; Saostus ?
                            :activated-carbon      ; aktiivihiili
                            })

;;; Pools ;;;

(s/def ::type #{:main-pool         ; Pääallas
                :diving-pool       ; Hyppyallas
                :multipurpose-pool ; Monitoimiallas
                :teaching-pool     ; Opetusallas
                :paddling-pool     ; Kahluuallas
                :childrens-pool    ; Lastenallas
                :cold-pool         ; Kylmäallas
                :whirlpool-bath    ; Poreallas
                :therapy-pool      ; Terapia-allas
                })
