(ns lipas.backend.geom-utils
  "Utility functions for working with geometry data structures.
   
   This namespace contains shared utility functions for transforming
   and manipulating geometric data, particularly for GeoJSON and 
   ElasticSearch geometry formats.")

(defn feature-coll->geom-coll
  "Transforms GeoJSON FeatureCollection to ElasticSearch
   geometrycollection."
  [{:keys [features]}]
  {:type "geometrycollection"
   :geometries (mapv :geometry features)})
