(ns lipas.ui.components.lists
  (:require
   [goog.object :as gobj]
   [lipas.ui.mui :as mui]
   [reagent.core :as r]))


(defn row-renderer
  [{:keys [items label-fn label2-fn on-item-click]} js-opts]
  (let [key   (gobj/get js-opts "key")
        idx   (gobj/get js-opts "index")
        style (gobj/get js-opts "style")
        item  (get items idx)]
    (r/as-element
     ^{:key key}
     [mui/grid {:item true :xs 12 :style style}
      [mui/list-item
       {:button true :divider true :on-click #(on-item-click item)}
       [mui/list-item-text
        {:primary   (label-fn item)
         :secondary (label2-fn item)}]]])))

(defn virtualized-list2 [{:keys [items] :as props}]
  [:> js/reactVirtualized.List
   {:row-width    390
    :width        390
    :height       (* 10 64)
    :row-height   64
    :row-renderer (partial row-renderer props)
    :row-count    (count items)}])

(defn virtualized-list [{:keys [items] :as props}]
  [:> js/reactVirtualized.AutoSizer
   (fn [m]
     (let [row-height 64
           width      (gobj/get m "width")
           height     (gobj/get m "height")]
       (r/as-element
        [:> js/reactVirtualized.List
         {:row-width    width
          :width        width
          :height       height
          :row-height   row-height
          :row-renderer (partial row-renderer props)
          :row-count    (count items)}])))])

(defn inifinite-list [{:keys [items] :as props}]
  [:> js/reactVirtualized.InfiniteLoader
   (fn [m]
     (let [row-height 64
           width      (gobj/get m "width")
           height     (gobj/get m "height")]
       (r/as-element
        [:> js/reactVirtualized.List
         {:row-width    width
          :width        width
          :height       height
          :row-height   row-height
          :row-renderer (partial row-renderer props)
          :row-count    (count items)}])))])
