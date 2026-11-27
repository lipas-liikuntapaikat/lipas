(ns lipas.polygon-checks
  "Checks for the polygons people draw and edit in the map editor.

  Accidental clicks used to end up saved as \"polygons\": the same point
  four times, or two points (2026-07 onwards; double-clicking to zoom while
  drawing does it). Such a site can't be indexed, and every retry of the
  failing save created another one.

  Consecutive duplicate points never mean anything, so `clean` drops them
  silently. What remains with fewer than 3 distinct points, or without
  area, is broken: `problem` says why. Used by the editor only; the
  backend doesn't reject what's already saved.")

(defn dedupe-ring
  "`ring` without consecutive duplicate positions. The closing position
  stays: it repeats the first position, not its neighbour."
  [ring]
  (into [] (dedupe) ring))

(def min-area-m2
  "Smaller than this, a polygon has no area to speak of (1 dm²)."
  0.01)

(defn ring-area-m2
  "Approximate area of a WGS84 `ring` in m²: plenty to tell no area from
  some. Coordinates are taken relative to the first position, so that
  rounding doesn't swamp small areas."
  [ring]
  (if (< (count ring) 3)
    0
    (let [[lon0 lat0] (first ring)
          phi (* lat0 (/ Math/PI 180))
          ;; metres per degree on the WGS84 ellipsoid at this latitude
          kx (+ (* 111412.84 (Math/cos phi)) (* -93.5 (Math/cos (* 3 phi))) (* 0.118 (Math/cos (* 5 phi))))
          ky (+ 111132.954 (* -559.822 (Math/cos (* 2 phi))) (* 1.175 (Math/cos (* 4 phi))))
          pts (mapv (fn [[lon lat]] [(* (- lon lon0) kx) (* (- lat lat0) ky)]) ring)]
      (/ (Math/abs (reduce + (map (fn [[x1 y1] [x2 y2]] (- (* x1 y2) (* x2 y1)))
                                  pts (rest pts))))
         2))))

(defn problem
  "Why a polygon with these WGS84 `rings` is broken (`:too-few-points` or
  `:no-area`), or nil. Looks at the exterior ring: `clean` drops holes that
  have no area."
  [rings]
  (let [exterior (first rings)]
    (cond
      (< (count (distinct exterior)) 3) :too-few-points
      (< (ring-area-m2 exterior) min-area-m2) :no-area)))

(defn- polygon? [f] (= "Polygon" (get-in f [:geometry :type])))

(defn clean
  "`fcoll` with consecutive duplicate points dropped from its Polygon rings,
  and holes without area dropped. Other features are left as they are."
  [fcoll]
  (if (seq (:features fcoll))
    (update fcoll :features
            (fn [fs]
              (mapv (fn [f]
                      (if (polygon? f)
                        (update-in f [:geometry :coordinates]
                                   (fn [[exterior & holes]]
                                     (into [(dedupe-ring exterior)]
                                           (comp (map dedupe-ring)
                                                 (remove #(problem [%])))
                                           holes)))
                        f))
                    fs)))
    fcoll))

(defn problems
  "The broken polygons of `fcoll`, in feature order:
  [{:index i :id feature-id :problem kw}]."
  [fcoll]
  (into []
        (keep-indexed (fn [i f]
                        (when (polygon? f)
                          (when-let [p (problem (get-in f [:geometry :coordinates]))]
                            {:index i :id (:id f) :problem p}))))
        (:features fcoll)))

(defn new-problems
  "The `problems` of `edited` that `saved` doesn't already have. Polygons
  saved before these checks existed may be broken too; the editor only
  stops what the user breaks while editing. A saved polygon is recognised
  by its id, or by its cleaned coordinates (the editor gives id-less
  features ids when the geometry changes)."
  [saved edited]
  (let [saved (clean saved)
        edited (clean edited)
        saved-fs (vec (:features saved))
        edited-fs (vec (:features edited))
        broken (map #(nth saved-fs (:index %)) (problems saved))
        known-ids (into #{} (keep :id) broken)
        known-coords (into #{} (map #(get-in % [:geometry :coordinates])) broken)
        known? (fn [{:keys [index id]}]
                 (or (contains? known-ids id)
                     (contains? known-coords
                                (get-in (nth edited-fs index) [:geometry :coordinates]))))]
    (into [] (remove known?) (problems edited))))
