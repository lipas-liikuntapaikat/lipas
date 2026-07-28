(ns lipas.ui.analysis.map-integration
  "Entry namespace of the lazy :analysis module (listed in its :entries in
   shadow-cljs.edn, so it loads whenever the module does).

   Wires the analysis feature into the map, which cannot statically
   require analysis code:
   - installs the reachability buffer drawing fn into lipas.ui.map.hooks
   - registers the :heatmap method of the map popup multimethod
   - requiring the analysis event namespaces registers their re-frame
     handlers before any dispatch can reach them."
  (:require
   ;; clj-kondo false positive: `List` collides with the cljs.core/List
   ;; deftype, so the [:> List ...] hiccup uses below aren't recognized as
   ;; uses of this alias.
    #_{:clj-kondo/ignore [:unused-namespace]}
    ["@mui/material/List$default" :as List]
    ["@mui/material/ListItem$default" :as ListItem]
    ["@mui/material/Paper$default" :as Paper]
    ["@mui/material/Stack$default" :as Stack]
    ["@mui/material/Typography$default" :as Typography]
    [lipas.ui.analysis.buffer :as buffer]
    [lipas.ui.analysis.diversity.events]
    [lipas.ui.analysis.events]
    [lipas.ui.analysis.heatmap.events]
    [lipas.ui.analysis.heatmap.subs :as heatmap-subs]
    [lipas.ui.analysis.reachability.events]
    [lipas.ui.map.hooks :as hooks]
    [lipas.ui.map.views :as map-views]
    [lipas.ui.utils :refer [<==]]))

(defmethod map-views/popup-body :heatmap [popup]
  (let [tr (<== [:lipas.ui.subs/translator])
        locale (tr)
        data (-> popup :data :features first :properties)
        dimension (<== [::heatmap-subs/dimension])
        ;; Get type labels for type-distribution dimension
        types-db (<== [:lipas.ui.sports-sites.subs/all-types])]

    [:> Paper
     {:style
      {:padding "0.5em"
       :min-width "200px"}}

     [:> Stack {:direction "column"}
      ;; Facility count
      [:> Typography {:variant "body2" :style {:font-weight "bold"}}
       (str (:doc_count data) " " (if (= 1 (:doc_count data))
                                    (tr :analysis/heatmap-popup-facility-singular)
                                    (tr :analysis/heatmap-popup-facility-plural)))]

      ;; Type distribution for type-distribution dimension
      (when (and (= :type-distribution dimension) (:types data))
        [:<>
         [:> Typography {:variant "caption" :style {:margin-top "0.5em" :font-weight "bold"}}
          (str (tr :analysis/heatmap-popup-top-types) (count (:types data)) ")")]
         (into [:> List {:dense true :style {:padding 0}}]
               (for [{:keys [key doc_count]} (take 5 (sort-by :doc_count > (:types data)))
                     :let [type-label (get-in types-db [key :name locale] (str (tr :analysis/heatmap-popup-type-fallback) key))]]
                 [:> ListItem {:style {:padding "2px 0"}}
                  [:> Typography {:variant "caption"}
                   (str type-label ": " doc_count)]]))])

      ;; Activities for activities dimension
      (when (and (= :activities dimension) (:activities data))
        [:<>
         [:> Typography {:variant "caption" :style {:margin-top "0.5em" :font-weight "bold"}}
          (tr :analysis/heatmap-popup-activities)]
         (into [:> List {:dense true :style {:padding 0}}]
               (for [{:keys [key doc_count]} (take 5 (sort-by :doc_count > (:activities data)))]
                 [:> ListItem {:style {:padding "2px 0"}}
                  [:> Typography {:variant "caption"}
                   (str key ": " doc_count)]]))])]]))

(hooks/install!
  {:draw-analytics-buffer! buffer/draw-analytics-buffer!})
