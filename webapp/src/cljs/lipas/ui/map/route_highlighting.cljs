(ns lipas.ui.map.route-highlighting
  "Functions for handling route highlighting in both editing and default (read-only) modes"
  (:require [lipas.ui.map.utils :as map-utils]))

(defn update-route-highlights!
  "Updates route highlights based on the current mode and selected features.
   Works in both editing and default (read-only) modes."
  [{:keys [layers] :as map-ctx} {:keys [highlight-source selected-features name sub-mode]}]
  (let [^js highlights-layer (-> layers :overlays :highlights)
        highlights-source (.getSource highlights-layer)
        ^js edits-layer (-> layers :overlays :edits)
        edits-source (.getSource edits-layer)
        ;; Ensure selected-features is a set for proper contains? checking
        selected-features-set (set selected-features)]

    ;; Clear existing highlights
    (.clear highlights-source)

    ;; Ensure the highlights layer is visible
    (.setVisible highlights-layer true)

    (cond
      ;; In editing mode with selected features - use edits layer
      (and (= name :editing) selected-features-set (seq selected-features-set))
      (do
        (doseq [fid selected-features-set]
          (when-let [f (.getFeatureById edits-source fid)]
            (.addFeature highlights-source (.clone f)))))

      ;; In default mode with highlight source - use highlight source data
      (and (not= name :editing)
           highlight-source
           selected-features-set
           (seq selected-features-set))
      (let [{:keys [geometries]} highlight-source]
        ;; Check that geometries is a valid FeatureCollection before processing
        (when (and geometries
                   (= (:type geometries) "FeatureCollection")
                   (:features geometries)
                   (seq (:features geometries)))
          (try
            ;; Convert ClojureScript data to JavaScript before passing to OpenLayers
            (let [js-geoms (clj->js geometries)
                  features (map-utils/->ol-features js-geoms)]

              ;; features is an array of OpenLayers Feature objects
              (dotimes [i (.-length features)]
                (let [^js feature (aget features i)
                      fid (.getId feature)]
                  (when (contains? selected-features-set fid)
                    (.addFeature highlights-source (.clone feature))))))
            (catch js/Error e
              ;; Silently handle errors - could log to a proper logging system if needed
              nil)))))

    map-ctx))