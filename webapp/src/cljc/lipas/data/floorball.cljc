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

;; NOTE: the site-level :surface-material prop is deliberately NOT derived.
;; The floorball per-field surface material has a different definition
;; (what you play on, including movable overlays like carpet) than the
;; LIPAS prop (the permanent primary surface of the site), so no valid
;; mapping exists — e.g. for a carpet floor LIPAS wants to know what is
;; under the carpet, which the fields data does not record.

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
