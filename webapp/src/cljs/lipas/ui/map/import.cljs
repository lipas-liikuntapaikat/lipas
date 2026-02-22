(ns lipas.ui.map.import
  (:require [clojure.string :as string]
            [lipas.ui.components :as lui]
            [lipas.ui.map.events :as events]
            [lipas.ui.map.subs :as subs]
            ["@mui/material/Button$default" :as Button]
            ["@mui/material/Dialog$default" :as Dialog]
            ["@mui/material/DialogActions$default" :as DialogActions]
            ["@mui/material/DialogContent$default" :as DialogContent]
            ["@mui/material/DialogTitle$default" :as DialogTitle]
            ["@mui/material/GridLegacy$default" :as Grid]
            ["@mui/material/Link$default" :as Link]
            ["@mui/material/Paper$default" :as Paper]
            ["@mui/material/Tooltip$default" :as Tooltip]
            ["@mui/material/Typography$default" :as Typography]
            [lipas.ui.utils :refer [<== ==>] :as utils]))

(def import-formats [".zip" ".kml" ".gpx" ".json" ".geojson"])
(def import-formats-str (string/join " " import-formats))

(defn helper [{:keys [label tooltip]}]
  [:> Tooltip {:title tooltip}
   [:> Link
    {:style     {:font-family "Lato" :font-size "0.9em" :margin "0.5em"}
     :underline "always"}
    label]])

(defn import-geoms-view
  [{:keys [on-import show-replace? geom-type]
    :or   {show-replace? true}}]
  (let [tr       (<== [:lipas.ui.subs/translator])
        open?    (<== [::subs/import-dialog-open?])
        encoding (<== [::subs/selected-import-file-encoding])
        data     (<== [::subs/import-candidates])
        batch-id (<== [::subs/import-batch-id])
        headers  (<== [::subs/import-candidates-headers])
        selected (<== [::subs/selected-import-items])
        replace? (<== [::subs/replace-existing-geoms?])
        error    (<== [::subs/import-error])

        on-close #(==> [::events/toggle-import-dialog])]

    [:> Dialog
     {:open       open?
      :full-width true
      :max-width  "xl"
      :on-close   on-close}

     [:> DialogTitle (tr :map.import/headline)]

     [:> DialogContent

      [:> Grid {:container true :spacing 2}

       ;; File selector, helpers and encoding selector
       [:> Grid {:item true :xs 12}
        [:> Grid
         {:container       true
          :spacing         4
          :align-items     "flex-end"
          :justify-content "space-between"}

         ;; File selector
         [:> Grid {:item true}
          [:input
           {:type      "file"
            :accept    (string/join "," import-formats)
            :on-change #(==> [::events/load-geoms-from-file
                              (-> % .-target .-files)
                              geom-type])}]]

         ;; Helper texts
         [:> Grid {:item true}
          [:> Typography {:display "inline"} (str (tr :help/headline) ":")]
          [helper {:label "Shapefile" :tooltip (tr :map.import/shapefile)}]
          [helper {:label "GeoJSON" :tooltip (tr :map.import/geoJSON)}]
          [helper {:label "GPX" :tooltip (tr :map.import/gpx)}]
          [helper {:label "KML" :tooltip (tr :map.import/kml)}]]

         ;; File encoding selector
         [:> Grid {:item true}
          [lui/select
           {:items     ["utf-8" "ISO-8859-1"]
            :label     (tr :map.import/select-encoding)
            :style     {:min-width "120px"}
            :value     encoding
            :value-fn  identity
            :label-fn  identity
            :on-change #(==> [::events/select-import-file-encoding %])}]]]]

       (when error
         [:> Grid {:item true :xs 12}
          [:> Paper
           [:> Typography {:color "error"}
            (tr (keyword :map.import (name (get error :type "unknown-error"))))]]])

       (when (and batch-id (not (seq data)))
         [:> Grid {:item true :xs 12}
          [:> Paper
           [:> Typography {:color "error"}
            (tr :map.import/no-geoms-of-type geom-type)]]])

       (when (seq data)
         [:> Grid {:item true :xs 12}

          ^{:key batch-id}
          [lui/table-v2
           {:items         (-> data vals (->> (map :properties) (map #(update-vals % str))))
            :key-fn        :id
            :multi-select? true
            :on-select     #(==> [::events/select-import-items %])
            :headers       headers}]])]]

     [:> DialogActions

      ;; Replace existing feature checkbox
      (when show-replace?
        [lui/checkbox
         {:label     (tr :map.import/replace-existing?)
          :value     replace?
          :on-change #(==> [::events/toggle-replace-existing-selection])}])

      ;; Cancel button
      [:> Button {:on-click on-close}
       (tr :actions/cancel)]

      ;; Import button
      [:> Button {:on-click on-import :disabled (empty? selected)}
       (tr :map.import/import-selected)]]]))
