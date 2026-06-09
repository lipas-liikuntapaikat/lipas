(ns lipas.wfs.sld
  "Generates GeoServer SLD (Styled Layer Descriptor) styles from the canonical
  LIPAS vector style definitions in `lipas.data.styles`.

  GeoServer publishes the legacy WFS/WMS layers (see `lipas.wfs.core`). Every
  per-type-code layer of a given geometry references one of three shared,
  named styles:

    - point layers      -> lipas:tyyli_pisteet
    - linestring layers -> lipas:tyyli_reitit
    - polygon layers    -> lipas:tyyli_alueet_2

  Historically these SLDs were hand-rolled and drifted from the webapp colors;
  newer type codes were missing rules entirely and rendered as nothing in WMS
  clients. This namespace makes `lipas.data.styles/symbols` the single source
  of truth: each named style is one SLD document containing one <Rule> per type
  code, selected by a `tyyppikoodi = <code>` filter.

  Pure generation only — no I/O. Publishing lives in `lipas.wfs.core`."
  (:require [clojure.string :as str]
            [lipas.data.styles :as styles]
            [lipas.data.types :as types]))

;;; Minimal hiccup -> XML emitter ;;;
;;
;; SLD is a small, fixed-shape XML document, so a tiny emitter beats pulling in
;; a dependency. Nodes are `[:ns/Tag {attr val ...} & children]`; children may
;; be strings (escaped as text), nested nodes, or nil (skipped). Namespaced
;; keyword tags like :sld/Rule render as `sld:Rule`.

(defn- esc [s]
  (-> (str s)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")))

(defn- esc-attr [s]
  (-> (esc s) (str/replace "\"" "&quot;")))

(defn- nm
  "Renders a tag/attr key. Keywords with a namespace become `ns:name`."
  [k]
  (if (keyword? k)
    (if-let [ns (namespace k)] (str ns ":" (name k)) (name k))
    (str k)))

(defn emit
  "Renders a hiccup-like node into an XML string."
  [node]
  (cond
    (nil? node)    ""
    (string? node) (esc node)
    (vector? node)
    (let [[tag & more]      node
          [attrs children]  (if (map? (first more))
                              [(first more) (next more)]
                              [nil more])
          t                 (nm tag)
          attr-str          (apply str (for [[k v] attrs]
                                         (str " " (nm k) "=\"" (esc-attr v) "\"")))
          kids              (remove nil? children)]
      (if (seq kids)
        (str "<" t attr-str ">" (apply str (map emit kids)) "</" t ">")
        (str "<" t attr-str "/>")))
    :else (esc node)))

;;; Symbolizers (one per geometry family) ;;;

(defn- point-symbolizer
  "Point layers (circle/square marks)."
  [{:keys [shape radius fill stroke]}]
  [:sld/PointSymbolizer
   [:sld/Graphic
    (cond-> [:sld/Mark
             [:sld/WellKnownName shape]]
      (:color fill)   (conj [:sld/Fill [:sld/CssParameter {:name "fill"} (:color fill)]])
      (:color stroke) (conj [:sld/Stroke [:sld/CssParameter {:name "stroke"} (:color stroke)]]))
    [:sld/Size (str (or radius 9))]]])

(defn- line-symbolizer
  "Linestring layers (colored, rounded, dashed)."
  [{:keys [stroke]}]
  (let [{:keys [color width line-cap line-join line-dash]} stroke]
    [:sld/LineSymbolizer
     (cond-> [:sld/Stroke]
       color            (conj [:sld/CssParameter {:name "stroke"} color])
       line-cap         (conj [:sld/CssParameter {:name "stroke-linecap"} line-cap])
       line-join        (conj [:sld/CssParameter {:name "stroke-linejoin"} line-join])
       width            (conj [:sld/CssParameter {:name "stroke-width"} (str width)])
       (seq line-dash)  (conj [:sld/CssParameter {:name "stroke-dasharray"}
                               (str/join " " line-dash)]))]))

(defn- polygon-symbolizer
  "Polygon layers (filled, thin outline)."
  [{:keys [fill stroke]}]
  [:sld/PolygonSymbolizer
   (when (:color fill)
     [:sld/Fill [:sld/CssParameter {:name "fill"} (:color fill)]])
   (cond-> [:sld/Stroke]
     (:color stroke) (conj [:sld/CssParameter {:name "stroke"} (:color stroke)])
     (:width stroke) (conj [:sld/CssParameter {:name "stroke-width"} (str (:width stroke))]))])

;;; Rule + document assembly ;;;

(defn- slug
  "ascii-folds a Finnish type name into a rule-name-friendly token."
  [s]
  (when s
    (-> (str/lower-case s)
        (str/replace "ä" "a") (str/replace "å" "a") (str/replace "ö" "o")
        (str/replace #"[^a-z0-9]+" "_")
        (str/replace #"^_+|_+$" ""))))

(defn- rule-name [type-code]
  (let [n (slug (get-in types/all [type-code :name :fi]))]
    (str "lipas_" type-code (when n (str "_" n)))))

(defn- rule [symbolizer-fn type-code sym]
  [:sld/Rule
   [:sld/Name (rule-name type-code)]
   [:ogc/Filter
    [:ogc/PropertyIsEqualTo
     [:ogc/PropertyName "tyyppikoodi"]
     [:ogc/Literal (str type-code)]]]
   (symbolizer-fn sym)])

(defn ->sld
  "Builds a complete SLD 1.0.0 document string. `entries` is a seq of
  [type-code symbol-def] pairs, one <Rule> each."
  [user-style symbolizer-fn entries]
  (str "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
       (emit
         [:sld/StyledLayerDescriptor
          {"xmlns:sld" "http://www.opengis.net/sld"
           "xmlns"     "http://www.opengis.net/sld"
           "xmlns:gml" "http://www.opengis.net/gml"
           "xmlns:ogc" "http://www.opengis.net/ogc"
           "version"   "1.0.0"}
          [:sld/NamedLayer
           [:sld/Name (str "lipas:" user-style)]
           [:sld/UserStyle
            [:sld/Name (str "lipas:" user-style)]
            (into [:sld/FeatureTypeStyle [:sld/Name "name"]]
                  (for [[code sym] entries]
                    (rule symbolizer-fn code sym)))]]])))

;;; Style definitions ;;;

(def ^:private shape->family
  {"circle"     :point
   "square"     :point
   "linestring" :line
   "polygon"    :polygon})

(def style-defs
  "Each shared GeoServer named style and how to build it.
  :style-name is the GeoServer style; :user-style the internal UserStyle name
  (kept identical to the historical hand-rolled styles)."
  [{:style-name "tyyli_pisteet"  :user-style "pisteet" :family :point   :symbolizer point-symbolizer}
   {:style-name "tyyli_reitit"   :user-style "reitit"  :family :line    :symbolizer line-symbolizer}
   {:style-name "tyyli_alueet_2" :user-style "alueet"  :family :polygon :symbolizer polygon-symbolizer}])

(defn- entries-for
  "type-code -> symbol-def pairs for one geometry family, sorted by type code.

  Only emits rules for type codes that exist in `lipas.data.types/all` - this
  keeps the SLD and its WMS legend matched to the live type catalog. Orphan
  style entries (codes in styles.cljc with no type definition and no data, e.g.
  2710, 5155) are dropped."
  [family]
  (->> styles/symbols
       (filter (fn [[code sym]]
                 (and (contains? types/all code)
                      (= family (shape->family (:shape sym))))))
       (sort-by key)))

(defn sld-for
  "Generates the SLD XML string for one named style (e.g. \"tyyli_pisteet\")."
  [style-name]
  (let [{:keys [user-style family symbolizer]}
        (first (filter #(= style-name (:style-name %)) style-defs))]
    (->sld user-style symbolizer (entries-for family))))

(defn all-slds
  "Returns {style-name -> sld-xml} for all three shared named styles."
  []
  (into {} (for [{:keys [style-name]} style-defs]
             [style-name (sld-for style-name)])))

(comment
  ;; Coverage / sanity checks.

  ;; Type codes present in styles.cljc but missing a geometry-type in types/all
  ;; (would still get a rule, just an odd one):
  (->> styles/symbols keys (remove #(get-in types/all [% :geometry-type])))

  ;; Disagreements between the styles.cljc :shape family and the canonical
  ;; geometry-type in types/all (should be empty):
  (->> styles/symbols
       (keep (fn [[code {:keys [shape]}]]
               (let [fam     (shape->family shape)
                     geom    (get-in types/all [code :geometry-type])
                     geom-fam ({"Point" :point "LineString" :line "Polygon" :polygon} geom)]
                 (when (and geom-fam (not= fam geom-fam))
                   [code shape geom]))))
       (into {}))

  ;; Type codes in types/all that have NO style at all (the WMS gap):
  (->> (keys types/all) (remove (set (keys styles/symbols))) sort)

  (println (sld-for "tyyli_pisteet")))
