(ns lipas.backend.route
  "Route-related operations including segment ordering following LIPAS CQRS patterns"
  (:require
   [lipas.backend.gis :as gis]
   [clojure.set :as set]
   [taoensso.timbre :as log]))

(defn- extract-feature-id
  "Extract the feature ID from a feature"
  [feature]
  (or (get-in feature [:properties :fid])
      (get-in feature [:properties :id])
      (get-in feature [:id])))

(defn- coords-equal?
  "Compare two coordinates with tolerance for floating point comparison"
  [c1 c2]
  (and (< (Math/abs (- (double (first c1)) (double (first c2)))) 0.00001)
       (< (Math/abs (- (double (second c1)) (double (second c2)))) 0.00001)))

(defn- detect-direction
  "Detect if a feature is in forward or reverse direction based on its position in the sequence.
  Compares the original coordinates with the sequenced coordinates to determine direction."
  [original-feature sequenced-feature]
  (let [orig-coords (get-in original-feature [:geometry :coordinates])
        seq-coords (get-in sequenced-feature [:geometry :coordinates])
        ;; Get first and last coordinates
        orig-first (first orig-coords)
        orig-last (last orig-coords)
        seq-first (first seq-coords)
        seq-last (last seq-coords)]
    ;; If the first coordinate of sequenced matches first of original, it's forward
    ;; If the first coordinate of sequenced matches last of original, it's backward
    (cond
      (and (coords-equal? seq-first orig-first)
           (coords-equal? seq-last orig-last))
      "forward"

      (and (coords-equal? seq-first orig-last)
           (coords-equal? seq-last orig-first))
      "backward"

      ;; Default to forward if we can't determine
      :else "forward")))

(defn- assess-connectivity
  "Assess how well connected the segments are.
  Returns a map with connectivity metrics."
  [features]
  (let [total-segments (count features)
        ;; Check if segments form a connected path
        connected-segments (atom #{})
        warnings (atom [])]

    ;; Simple connectivity check - can be enhanced with actual coordinate checking
    (doseq [[idx feature] (map-indexed vector features)]
      (when (> idx 0)
        (let [prev-feature (nth features (dec idx))
              prev-end (last (get-in prev-feature [:geometry :coordinates]))
              curr-start (first (get-in feature [:geometry :coordinates]))
              connected? (= prev-end curr-start)]
          (when connected?
            (swap! connected-segments conj idx)))))

    ;; Assess connectivity ratio
    (let [connectivity-ratio (if (> total-segments 1)
                               (/ (count @connected-segments) (dec total-segments))
                               1.0)]
      {:connectivity-ratio connectivity-ratio
       :connected-count (count @connected-segments)
       :total-segments total-segments
       :warnings @warnings})))

(defn- calculate-confidence
  "Calculate confidence level based on sequencing quality metrics."
  [features connectivity-metrics requested-count found-count segments]
  (let [{:keys [connectivity-ratio]} connectivity-metrics
        completeness-ratio (if (> requested-count 0)
                             (/ found-count requested-count)
                             1.0) ; Empty request means we found everything requested
        ;; Count reversed segments
        reversed-count (count (filter #(= "reverse" (:direction %)) segments))
        reversal-ratio (if (> (count segments) 0)
                         (/ reversed-count (count segments))
                         0)]
    (cond
      ;; Special case: empty request
      (= requested-count 0) "high"
      ;; High confidence: all segments found, well connected, and few reversals
      (and (>= completeness-ratio 1.0)
           (>= connectivity-ratio 0.8)
           (<= reversal-ratio 0.3)) "high"
      ;; Medium confidence: most segments found or mostly connected, or some reversals
      (or (and (>= completeness-ratio 0.8) (>= connectivity-ratio 0.6))
          (and (>= completeness-ratio 1.0) (>= connectivity-ratio 0.8) (<= reversal-ratio 0.5))) "medium"
      ;; Low confidence: missing segments, poor connectivity, or many reversals
      :else "low")))

(defn- generate-warnings
  "Generate warnings based on the analysis of route segments."
  [connectivity-metrics requested-count found-count segments]
  (let [{:keys [connectivity-ratio connected-count total-segments]} connectivity-metrics
        warnings (atom [])
        reversed-count (count (filter #(= "backward" (:direction %)) segments))
        reversal-ratio (if (> (count segments) 0)
                         (/ reversed-count (count segments))
                         0)]

    ;; Warning if not all requested segments were found
    (when (< found-count requested-count)
      (swap! warnings conj (format "Only %d of %d requested segments were found"
                                   found-count requested-count)))

    ;; Warning if segments are not well connected
    (when (and (> total-segments 1) (< connectivity-ratio 0.8))
      (swap! warnings conj (format "Route has disconnected segments (%d of %d connections missing)"
                                   (- (dec total-segments) connected-count)
                                   (dec total-segments))))

    ;; Warning if multiple valid orderings might exist
    (when (and (> total-segments 3) (< connectivity-ratio 0.5))
      (swap! warnings conj "Multiple valid route orderings may be possible"))

    ;; Warning if many segments need to be reversed
    (when (> reversal-ratio 0.5)
      (swap! warnings conj (format "%d of %d segments need to be traversed in reverse direction"
                                   reversed-count (count segments))))

    @warnings))

(defn suggest-route-order
  "CQRS action handler for suggesting route segment ordering.
  
  This function analyzes the spatial connectivity of route segments and suggests
  an optimal ordering while providing confidence metrics and warnings.
  
  Args:
    sports-site - The complete sports site data
    activity-type - The activity type (e.g., \"cycling\", \"hiking\")
    fids - Vector of feature IDs to order
    features - The feature collection containing all segments
  
  Returns:
    {:success boolean
     :segments [{:fid string :direction \"forward\"|\"backward\"}]
     :confidence :high|:medium|:low
     :warnings [string] or nil
     :error string (when success is false)}"
  [sports-site activity-type fids features]
  (try
    ;; Check for nil features first
    (when (nil? features)
      (throw (ex-info "Features cannot be nil"
                      {:activity-type activity-type})))

    (let [;; Ensure we have a proper feature collection
          feature-collection (cond
                               (map? features)
                               features
                               :else
                               {:type "FeatureCollection" :features features})
          all-features (:features feature-collection)

          ;; Create lookup map by ID (handle both string and int IDs)
          id-to-feature (reduce (fn [acc f]
                                  (let [fid (extract-feature-id f)]
                                  ;; Store with both the original FID and its string representation
                                    (assoc acc
                                           fid f
                                           (str fid) f)))
                                {}
                                all-features)

          ;; Get only the features we're interested in
          selected-features (keep #(or (get id-to-feature %)
                                       (get id-to-feature (str %))) fids)
          found-count (count selected-features)
          requested-count (count fids)

          ;; Log missing features
          _ (when (< found-count requested-count)
              (let [found-ids (set (map (fn [f]
                                          (let [fid (extract-feature-id f)]
                                             ;; Normalize to string for comparison
                                            (str fid)))
                                        selected-features))
                    missing-ids (set/difference (set (map str fids)) found-ids)]
                (log/warn "Some feature IDs not found"
                          {:requested requested-count
                           :found found-count
                           :missing missing-ids
                           :activity-type activity-type
                           :lipas-id (:lipas-id sports-site)})))

          ;; Validate all features are LineStrings
          _ (doseq [feature selected-features]
              (let [geom-type (get-in feature [:geometry :type])]
                (when-not (= "LineString" geom-type)
                  (throw (ex-info "Invalid geometry type for route segment"
                                  {:fid (extract-feature-id feature)
                                   :expected "LineString"
                                   :actual geom-type
                                   :activity-type activity-type})))))

          ;; Create feature collection for sequencing
          selected-fcoll {:type "FeatureCollection"
                          :features selected-features}

          ;; Use the GIS sequencing function
          sequenced-fcoll (gis/sequence-features selected-fcoll)
          sequenced-features (:features sequenced-fcoll)

          ;; Create ID to original feature map for direction detection
          fid-to-original (into {}
                                (map (fn [f] [(extract-feature-id f) f])
                                     selected-features))

          ;; Build the ordered segments with direction detection
          segments (mapv
                    (fn [seq-feature]
                      (let [fid (extract-feature-id seq-feature)
                            ;; Always return FID as string to match API expectations
                            fid-str (str fid)
                            original-feature (get fid-to-original fid)
                            direction (if original-feature
                                        (detect-direction original-feature seq-feature)
                                        "forward")]
                        {:fid fid-str
                         :direction direction}))
                    sequenced-features)

          ;; Assess connectivity and calculate confidence
          connectivity-metrics (assess-connectivity sequenced-features)
          confidence (calculate-confidence sequenced-features
                                           connectivity-metrics
                                           requested-count
                                           found-count
                                           segments)

          ;; Generate warnings
          warnings (generate-warnings connectivity-metrics
                                      requested-count
                                      found-count
                                      segments)

          ;; Convert empty warnings to nil
          warnings (when (seq warnings) warnings)]

      ;; Return success response
      {:success true
       :segments segments
       :confidence confidence
       :warnings warnings})

    (catch Exception e
      (log/error e "Error in suggest-route-order"
                 {:activity-type activity-type
                  :fid-count (count fids)})
      {:success false
       :error (or (.getMessage e) "Unknown error occurred")})))

(comment
  ;; Test data - simple connected segments
  (def test-features
    [{:type "Feature"
      :id "1"
      :properties {:fid "1" :name "Segment 1"}
      :geometry {:type "LineString"
                 :coordinates [[0 0] [1 1]]}}
     {:type "Feature"
      :id "2"
      :properties {:fid "2" :name "Segment 2"}
      :geometry {:type "LineString"
                 :coordinates [[1 1] [2 1]]}}
     {:type "Feature"
      :id "3"
      :properties {:fid "3" :name "Segment 3"}
      :geometry {:type "LineString"
                 :coordinates [[2 1] [3 0]]}}])

  ;; Test the production function
  (suggest-route-order
   {:lipas-id 12345}
   "cycling"
   [3 1 2] ;; Wrong order
   test-features)
  ;; Should return:
  ;; {:success true
  ;;  :segments [{:fid 1 :direction "forward"}
  ;;             {:fid 2 :direction "forward"}
  ;;             {:fid 3 :direction "forward"}]
  ;;  :confidence :high
  ;;  :warnings nil}

  ;; Test with disconnected segments
  (def test-features-disconnected
    [{:type "Feature"
      :id "1"
      :properties {:fid "1"}
      :geometry {:type "LineString"
                 :coordinates [[0 0] [1 1]]}}
     {:type "Feature"
      :id "2"
      :properties {:fid "2"}
      :geometry {:type "LineString"
                 :coordinates [[5 5] [6 6]]}} ;; Disconnected
     {:type "Feature"
      :id "3"
      :properties {:fid "3"}
      :geometry {:type "LineString"
                 :coordinates [[1 1] [2 1]]}}])

  (suggest-route-order
   {:lipas-id 12345}
   "cycling"
   [1 2 3]
   test-features-disconnected)
  ;; Should return with warnings about disconnected segments
  ;; and medium/low confidence

  ;; Test with missing segments
  (suggest-route-order
   {:lipas-id 12345}
   "hiking"
   [1 2 3 4 5] ;; Request IDs that don't exist
   test-features)
  ;; Should return warnings about missing segments
  )