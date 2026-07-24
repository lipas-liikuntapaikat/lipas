(ns lipas.ui.map.hooks
  "Runtime registry the lazy :analysis module installs itself into.

   The OpenLayers map (:map module) needs analysis-specific behaviour —
   currently drawing reachability buffers — but that code lives in the
   lazy :analysis module. Instead of a static require (which would pull
   @turf/buffer + the 260KB jsts engine into :map),
   lipas.ui.analysis.map-integration registers its functions here when
   the module loads. (The heatmap layer glue is NOT routed through this
   registry: lipas.ui.analysis.heatmap.map is OL-only, so lipas.ui.map.map
   requires it statically and it hoists into :map.)

   Analysis map modes can only activate through the analysis UI, so by
   the time any of these is called the registry is populated; before
   that the accessors no-op.")

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
