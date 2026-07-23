(ns lipas.ui.map.hooks
  "Runtime registry the lazy :analysis module installs itself into.

   The OpenLayers map (:map module) needs analysis-specific behaviour —
   drawing reachability buffers, feeding the heatmap layer — but the
   analysis code lives in the lazy :analysis module. Instead of static
   requires (which would pull @turf/buffer + jsts + the analysis views
   into :map), lipas.ui.analysis.map-integration registers its functions
   here when the module loads. Analysis map modes can only activate
   through the analysis UI, so by the time any of these is called the
   registry is populated; before that the accessors no-op.")

(defonce analysis-fns (atom nil))

(defn install! [fns]
  (reset! analysis-fns fns))

(defn installed? []
  (some? @analysis-fns))

(defn draw-analytics-buffer!
  [map-ctx reachability]
  (if-let [f (:draw-analytics-buffer! @analysis-fns)]
    (f map-ctx reachability)
    map-ctx))

(defn update-heatmap!
  [map-ctx heatmap]
  (if-let [f (:update-heatmap! @analysis-fns)]
    (f map-ctx heatmap)
    map-ctx))
