(ns lipas.data.floorball
  (:require
    [clojure.string :as str]
    [lipas.data.materials :as materials]))

(def audit-type "floorball-circumstances-audit")

(def floor-elasticity
  {"point" {:fi "Piste"
            :se "Punkt"
            :en "Point"}
   "area"  {:fi "Alue"
            :se "Areal"
            :en "Area"}
   "unknown" {:fi "Ei tietoa"
              :se "Okänt"
              :en "Unknown"}})

(def player-entrance
  {"private-entrance" {:fi "Oma sisäänkäynti"
                       :sv ""
                       :en "Private entrance"}
   "audience-entrance" {:fi "Katsojien kanssa samasta"
                        :sv ""
                        :en "Same as audience entrance"}})

(def audience-stand-access
  {"from-field-level" {:fi "Kenttätasolta"
                       :se ""
                       :en "From field level"}
   "from-upper-level" {:fi "Yläkautta"
                       :se ""
                       :en "From upper level"}})

(def car-parking-economics-model
  {"paid" {:fi "Maksullinen"
           :se ""
           :en "Paid"}
   "free" {:fi "Maksuton"
           :se ""
           :en "Free"}})

(def roof-trussess-operation-model
  {"can-be-lowered" {:fi "Saa laskettua alas"
                     :se ""
                     :en "Can be lowered"}
   "lift-required"  {:fi "Tarvitsee nostimen"
                     :se ""
                     :en "Lift is required"}})

(def field-surface-materials
  (select-keys materials/field-surface-materials ["resin" "wood" "carpet"]))

;;; Deriving traditional props from structured fields data ;;;

;; Values the site-level :surface-material prop accepts (the full
;; surface-materials enum backs both prop-types opts and its schema).
;; NOTE: not sports-site-surface-materials — that narrower set lacks
;; resin/carpet, and filtering against it silently dropped the two most
;; common floorball floor materials.
(def prop-surface-materials
  (into #{} (keys materials/surface-materials)))

(defn- fields-seq
  "Fields live as an indexed map {0 {...}} in the editing state and as a
  vector in stored documents; normalize both to a seq of field maps."
  [fields]
  (cond
    (map? fields) (vals fields)
    (sequential? fields) (seq fields)
    :else nil))

(defn- pos-num [x]
  (when (and (number? x) (pos? x)) x))

(defn- main-field
  "The largest field (by surface area, falling back to length × width) —
  the hall's field-size props are taken from it."
  [fields]
  (->> fields
       (sort-by (fn [{:keys [surface-area-m2 length-m width-m]}]
                  (or (pos-num surface-area-m2)
                      (when (and (number? length-m) (number? width-m))
                        (* length-m width-m))
                      0))
                >)
       first))

(def prop-k->derive-fn
  "How each traditional prop is computed from a seq of field maps. Every fn
  returns nil when the fields data cannot yield a value — a derive must
  never produce an empty-but-present value (\"\", 0, [])."
  {:field-length-m         (comp :length-m main-field)
   :field-width-m          (comp :width-m main-field)
   :height-m               (fn [fields]
                             (when-let [hs (seq (keep :minimum-height-m fields))]
                               (apply min hs)))
   :area-m2                (fn [fields]
                             (pos-num (apply + 0 (keep :surface-area-m2 fields))))
   :surface-material       (fn [fields]
                             (->> fields
                                  (keep :surface-material)
                                  distinct
                                  (filter prop-surface-materials)
                                  vec
                                  not-empty))
   :surface-material-info  (fn [fields]
                             (->> fields
                                  (keep :surface-material-product)
                                  (remove str/blank?)
                                  distinct
                                  (str/join ", ")
                                  not-empty))
   :floorball-fields-count (fn [fields]
                             (pos-num (count fields)))
   :stand-capacity-person  (fn [fields]
                             (pos-num (apply + 0 (keep :stands-total-capacity-person fields))))})

(defn derive-props
  "Compute traditional prop values from structured floorball fields data
  (stored vector or editing-state indexed map). Returns a map containing
  only the props for which a value could be derived; nil when there is no
  fields data at all."
  [fields]
  (when-let [fs (fields-seq fields)]
    (reduce-kv (fn [m k derive-fn]
                 (if-some [v (derive-fn fs)]
                   (assoc m k v)
                   m))
               {}
               prop-k->derive-fn)))
