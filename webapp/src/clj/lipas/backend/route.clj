(ns lipas.backend.route
  "Route-related operations including segment ordering"
  (:require
   [lipas.backend.gis :as gis]
   [taoensso.timbre :as log]))

(defn- extract-feature-id
  "Extract the feature ID from a feature"
  [feature]
  (or (get-in feature [:properties :fid])
      (get-in feature [:properties :id])
      (get-in feature [:id])))

(defn- detect-direction
  "Detect if a feature is in forward or reverse direction based on its position in the sequence.
  This is a simplified version - in a full implementation we'd compare coordinates."
  [original-idx sequenced-idx feature]
  ;; For now, just return forward - can be enhanced later
  "forward")

(defn suggest-order
  "Suggest ordering for route segments based on spatial connectivity.
  
  Args:
    sports-site - The sports site data (for context, not used in basic implementation)
    feature-ids - List of feature IDs to order
    features - The actual feature collection containing the segments
  
  Returns:
    {:success boolean
     :ordered-segments [{:fid string :direction string :order number}]
     :error string (optional)}"
  [sports-site feature-ids features]
  (try
    (let [;; Filter to only the requested features
          feature-collection (if (map? features)
                               features
                               {:type "FeatureCollection" :features features})
          all-features (:features feature-collection)

          ;; Create a map for quick lookup by ID
          id-to-feature (into {}
                              (map (fn [f] [(extract-feature-id f) f])
                                   all-features))

          ;; Get only the features we're interested in
          selected-features (keep #(get id-to-feature %) feature-ids)

          ;; Check if we found all requested features
          _ (when (not= (count selected-features) (count feature-ids))
              (log/warn "Some feature IDs not found"
                        {:requested (count feature-ids)
                         :found (count selected-features)}))

          ;; Validate all features are LineStrings before processing
          _ (doseq [feature selected-features]
              (let [geom-type (get-in feature [:geometry :type])]
                (when-not (= "LineString" geom-type)
                  (throw (ex-info "Invalid geometry type"
                                  {:fid (extract-feature-id feature)
                                   :expected "LineString"
                                   :actual geom-type})))))

          ;; Create feature collection for sequencing
          selected-fcoll {:type "FeatureCollection"
                          :features selected-features}

          ;; Use the GIS sequencing function
          sequenced-fcoll (gis/sequence-features selected-fcoll)
          sequenced-features (:features sequenced-fcoll)

          ;; Build the ordered segments result
          ordered-segments (map-indexed
                            (fn [idx feature]
                              (let [fid (extract-feature-id feature)
                                    original-idx (.indexOf feature-ids fid)]
                                {:fid fid
                                 :direction (detect-direction original-idx idx feature)
                                 :order idx}))
                            sequenced-features)]

      {:success true
       :ordered-segments ordered-segments})

    (catch Exception e
      (log/error e "Error in suggest-order")
      {:success false
       :error (.getMessage e)})))

(comment
  ;; Test data - simple connected segments
  (def test-features
    [{:type "Feature"
      :id "seg-1"
      :properties {:fid "seg-1" :name "Segment 1"}
      :geometry {:type "LineString"
                 :coordinates [[0 0] [1 1]]}}
     {:type "Feature"
      :id "seg-2"
      :properties {:fid "seg-2" :name "Segment 2"}
      :geometry {:type "LineString"
                 :coordinates [[1 1] [2 1]]}}
     {:type "Feature"
      :id "seg-3"
      :properties {:fid "seg-3" :name "Segment 3"}
      :geometry {:type "LineString"
                 :coordinates [[2 1] [3 0]]}}])

  ;; Test the function
  (suggest-order
   {:id "test-site"}
   ["seg-3" "seg-1" "seg-2"] ;; Wrong order
   test-features)
  ;; Should return segments in order: seg-1, seg-2, seg-3

  ;; Test with disconnected segments
  (def test-features-disconnected
    [{:type "Feature"
      :id "seg-1"
      :properties {:fid "seg-1"}
      :geometry {:type "LineString"
                 :coordinates [[0 0] [1 1]]}}
     {:type "Feature"
      :id "seg-2"
      :properties {:fid "seg-2"}
      :geometry {:type "LineString"
                 :coordinates [[5 5] [6 6]]}} ;; Disconnected
     {:type "Feature"
      :id "seg-3"
      :properties {:fid "seg-3"}
      :geometry {:type "LineString"
                 :coordinates [[1 1] [2 1]]}}])

  (suggest-order
   {:id "test-site"}
   ["seg-1" "seg-2" "seg-3"]
   test-features-disconnected)
  ;; Should group connected segments first
  )