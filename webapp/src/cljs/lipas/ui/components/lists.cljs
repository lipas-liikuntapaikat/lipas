(ns lipas.ui.components.lists
  (:require
   ["react-virtualized/dist/commonjs/AutoSizer$default" :as AutoSizer]
   ["react-virtualized/dist/commonjs/List$default" :as List]
   [lipas.ui.mui :as mui]
   [reagent.core :as r]))

(defn row-renderer
  [{:keys [items label-fn label2-fn on-item-click]} ^js props]
  (let [key   (.-key props)
        idx   (.-index props)
        style (.-style props)
        item  (get items idx)]
    (r/as-element
     ^{:key key}
     [mui/stack {:style style}
      [mui/list-item
       {:button  (some? item)
        :divider (some? item)
        :on-click (fn [_e]
                    (when item
                      (on-item-click item)))}
       [mui/list-item-text
        {:primary                    (label-fn item)
         :secondary                  (label2-fn item)
         :primary-typography-props   {:no-wrap true}
         :secondary-typography-props {:no-wrap true}}]]])))

(defn virtualized-list [{:keys [items] :as props}]
  [:> AutoSizer
   (fn [^js measure]
     (let [row-height 64]
       (r/as-element
        [:> List
         {:row-width    (.-width measure)
          :width        (.-width measure)
          :height       (.-height measure)
          :row-height   row-height
          :row-renderer (partial row-renderer props)
          ;; TODO: I think the inc is here to reserve one extra row at the end for the floating
          ;; toolbar buttons. Could be done with a margin or something more obvious.
          :row-count    (inc (count items))}])))])
