(ns lipas.ui.analysis.heatmap.db)

(def default-db
  {:dimension :density
   :weight-by :count
   :precision nil ; nil means use auto-precision based on zoom
   :use-bbox-filter? false ; true = current map view, false = whole Finland
   :filters {:status-codes ["active" "out-of-service-temporarily"]}
   :visual {:radius 20
            :blur 15
            :opacity 0.8
            :gradient :default
            :weight-fn :linear
            :max-intensity nil}
   :loading? false
   :error nil
   :facets nil
   :heatmap-data nil})

(defn init-db [db]
  (assoc db :heatmap default-db))

(defn set-dimension [db dimension]
  (assoc-in db [:heatmap :dimension] dimension))

(defn set-weight-by [db weight-by]
  (assoc-in db [:heatmap :weight-by] weight-by))

(defn set-precision [db precision]
  (assoc-in db [:heatmap :precision] precision))

(defn set-use-bbox-filter [db use-bbox?]
  (assoc-in db [:heatmap :use-bbox-filter?] use-bbox?))

(defn set-filter [db filter-key value]
  (assoc-in db [:heatmap :filters filter-key] value))

(defn clear-filter [db filter-key]
  (update-in db [:heatmap :filters] dissoc filter-key))

(defn set-visual-param [db param value]
  (assoc-in db [:heatmap :visual param] value))

(defn set-loading [db loading?]
  (assoc-in db [:heatmap :loading?] loading?))

(defn set-error [db error]
  (assoc-in db [:heatmap :error] error))

(defn set-heatmap-data [db data]
  (assoc-in db [:heatmap :heatmap-data] data))

(defn set-facets [db facets]
  (assoc-in db [:heatmap :facets] facets))
