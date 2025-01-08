(ns lipas.ui.components.lists
  (:require ["@mui/material/ListItem$default" :as ListItem]
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

(defui virtualized-list [{:keys [items key-fn landing-bay?] :as list-props}]
  (let [;; Measure just the available content height for the list.
        ;; We don't need to care about the width.
        [measure-ref measure] (useMeasure)
        ;; Measure a real two line ListItem to get the
        ;; pixel height for virtualized list.
        ;; The height should usually be around 70px, but
        ;; if user is changing browser default font size,
        ;; it could be something else.
        [measure-item-ref measure-item] (useMeasure)
        item-height (if (pos? (.-height measure))
                       (.-height measure-item)
                       70)
        ;; When opening the "Rajaa hakua" box, there often will be
        ;; no space left for the search results flex item.
        ;; Best solution would be to rework the whole sidebar contents
        ;; to create a new solution for the filters (maybe dropdown
        ;; instead of the expanding box?).
        ;; Maybe hiding the results completely when the filter box is
        ;; open is confusing for the users? Adding an 5 item min-height
        ;; keeps some items always visible. HOWEVER, now there
        ;; will be a scrollbar for the sidebar & scrollbar for the
        ;; search results which is also confusing.
        min-height (* 5 item-height)
        container-height (max (.-height measure) min-height)]
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
             {:height       container-height
              :itemSize     item-height
              :itemCount    (cond-> (count items)
                              landing-bay? inc)}
             (fn [^js props]
               (let [item (get items (.-index props))]
                 ($ row-item
                    {:key (key-fn item)
                     :style (.-style props)
                     :item item
                     :list-props list-props}))))))))
