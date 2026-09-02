(ns lipas.gpx
  "GeoJSON → GPX 1.1 conversion. Replaces the `togpx` npm package, whose
  transitive dependency `jxon` is GPL-3.0 licensed and therefore
  incompatible with distributing the frontend bundle under MIT."
  (:require [clojure.string :as str]))

(defn- xml-escape [x]
  (-> (str x)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")
      (str/replace "\"" "&quot;")))

(defn- emit-xml
  "Serializes a hiccup-style node [tag attrs? & children] to an XML string.
  Children are nodes or text; text is escaped here."
  [[tag & more]]
  (let [[attrs children] (if (map? (first more))
                           [(first more) (rest more)]
                           [nil more])
        attrs-str (apply str (for [[k v] attrs]
                               (str " " k "=\"" (xml-escape v) "\"")))]
    (if (seq children)
      (str "<" tag attrs-str ">"
           (apply str (map #(if (vector? %) (emit-xml %) (xml-escape %)) children))
           "</" tag ">")
      (str "<" tag attrs-str "/>"))))

(defn- feature-name [props]
  (let [title (or (:name props) (:ref props) (:id props))]
    (when (and (some? title) (not= "" title))
      (str title))))

(defn- feature-desc [props]
  (let [desc (->> props
                  (remove (fn [[_ v]] (or (nil? v) (coll? v))))
                  (map (fn [[k v]] (str (name k) "=" v)))
                  (str/join "\n"))]
    (not-empty desc)))

(defn- name-desc-elems [props]
  (cond-> []
    (feature-name props) (conj ["name" (feature-name props)])
    (feature-desc props) (conj ["desc" (feature-desc props)])))

(defn- wpt [props [lon lat ele]]
  (into ["wpt" {"lat" lat "lon" lon}]
        (concat
          (when (some? ele) [["ele" ele]])
          (name-desc-elems props))))

(defn- trkpt [[lon lat ele]]
  (cond-> ["trkpt" {"lat" lat "lon" lon}]
    (some? ele) (conj ["ele" ele])))

(defn- trkseg [coords]
  (into ["trkseg"] (map trkpt coords)))

(defn- trk
  "One GPX trk from a seq of coordinate seqs (one trkseg each)."
  [props segs]
  (into ["trk"]
        (concat (name-desc-elems props)
                (map trkseg segs))))

(defn- feature->elems
  "Returns {:wpts [...] :trks [...]} of hiccup nodes for one GeoJSON feature.
  Points become waypoints; lines and polygon rings become track segments."
  [{:keys [geometry properties]}]
  (let [{:keys [type coordinates geometries]} geometry]
    (case type
      "Point"           {:wpts [(wpt properties coordinates)]}
      "MultiPoint"      {:wpts (mapv (partial wpt properties) coordinates)}
      "LineString"      {:trks [(trk properties [coordinates])]}
      "MultiLineString" {:trks [(trk properties coordinates)]}
      "Polygon"         {:trks [(trk properties coordinates)]}
      "MultiPolygon"    {:trks [(trk properties (apply concat coordinates))]}
      "GeometryCollection"
      (apply merge-with into {:wpts [] :trks []}
             (map #(feature->elems {:geometry % :properties properties})
                  geometries))
      {})))

(defn geojson->gpx
  "Converts GeoJSON (Clojure data, keyword keys) to a GPX 1.1 XML string.
  Accepts a FeatureCollection, a Feature or a bare geometry. A third
  coordinate, when present, is written as elevation."
  ([geojson] (geojson->gpx geojson {}))
  ([geojson {:keys [creator] :or {creator "LIPAS"}}]
   (let [features (case (:type geojson)
                    "FeatureCollection" (:features geojson)
                    "Feature"           [geojson]
                    [{:type "Feature" :properties {} :geometry geojson}])
         {:keys [wpts trks]} (apply merge-with into {:wpts [] :trks []}
                                    (map feature->elems features))]
     (str "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
          (emit-xml
            (into ["gpx" {"xmlns"              "http://www.topografix.com/GPX/1/1"
                          "xmlns:xsi"          "http://www.w3.org/2001/XMLSchema-instance"
                          "xsi:schemaLocation" "http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd"
                          "version"            "1.1"
                          "creator"            creator}]
                  (concat wpts trks)))))))
