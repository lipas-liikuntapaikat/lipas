(ns lipas.ui.components.lists
  (:require
    ["@mui/material/ListItem$default" :as ListItem]
    ["@mui/material/ListItemText$default" :as ListItemText]
    ["@mui/material/Stack$default" :as Stack]
    ["react-use/lib/useMeasure$default" :as useMeasure]
    ["react-window" :refer [FixedSizeList]]
    [uix.core :refer [$ defui]]))

(defui row-item
  [{:keys [style item list-props]}]
  (let [{:keys [on-item-click label-fn label2-fn]} list-props]
    ($ Stack {:style style}
       ($ ListItem
          {:button  (some? item)
           :divider (some? item)
           :on-click (fn [_e]
                       (when item
                         (on-item-click item)))}
          ($ ListItemText
             {:primary                    (label-fn item)
              :secondary                  (label2-fn item)
              :primary-typography-props   #js {:no-wrap true}
              :secondary-typography-props #js {:no-wrap true}})))))

(defui virtualized-list [{:keys [items key-fn] :as list-props}]
  (let [;; Measure just the available content height for the list.
        ;; We don't need to care about the width.
        [measure-ref measure] (useMeasure)
        ;; Measure a real two line ListItem to get the
        ;; pixel height for virtualized list.
        ;; The height should usually be around 70px, but
        ;; if user is changing browser default font size,
        ;; it could be something else.
        [measure-item-ref measure-item] (useMeasure)
        height (if (pos? (.-height measure))
                 (.-height measure-item)
                 70)]
    ($ :<>
       ($ :div
          {:style {:position "absolute"
                   :top -1000
                   :left -1000
                   :opacity 0
                   :pointers-events "none"}
           :ref measure-item-ref}
          ($ ListItem
             ($ ListItemText
                {:primary "measure"
                 :secondary "measure"})))
       ($ :div
          {:ref measure-ref
           :style {:flex "1 1 auto"}}
          ($ FixedSizeList
             {:height       (.-height measure)
              :itemSize     height
              :itemCount    (count items)}
             (fn [^js props]
               (let [item (get items (.-index props))]
                 ($ row-item
                    {:key (key-fn item)
                     :style (.-style props)
                     :item item
                     :list-props list-props}))))))))
