(ns lipas.ui.components.form-table
  "Editable drag-sortable form table. Split out of
   lipas.ui.components.tables because @hello-pangea/dnd is ~85KB and
   every user of this component lives in the lazy :map module — plain
   tables stay dnd-free in the base module."
  (:require ["@hello-pangea/dnd" :refer [DragDropContext Draggable Droppable]]
            ["@mui/material/Fab$default" :as Fab]
            ["@mui/material/GridLegacy$default" :as Grid]
            ["@mui/material/IconButton$default" :as IconButton]
            ["@mui/material/Icon$default" :as Icon]
            ["@mui/material/Stack$default" :as Stack]
            ["@mui/material/Table$default" :as Table]
            ["@mui/material/TableBody$default" :as TableBody]
            ["@mui/material/TableCell$default" :as TableCell]
            ["@mui/material/TableHead$default" :as TableHead]
            ["@mui/material/TableRow$default" :as TableRow]
            ["@mui/material/Tooltip$default" :as Tooltip]
            ["@mui/material/Typography$default" :as Typography]
            [lipas.ui.components.buttons :as buttons]
            [lipas.ui.components.tables :as tables]
            [lipas.ui.utils :as utils]
            [reagent.core :as r]))

(defn form-table
  [{:keys [headers items key-fn add-tooltip
           edit-tooltip delete-tooltip confirm-tooltip
           read-only? on-add on-edit on-delete add-btn-size
           max-width empty-label hide-header-row? on-custom-hover-in on-custom-hover-out
           on-user-sort]
    :or   {add-btn-size     "large"
           hide-header-row? false}
    :as   props}]
  (if read-only?

    ;; Normal read-only table
    (if (empty? items)
      [:> Typography (or empty-label "-")]
      [tables/table props])

    ;; Table with 'edit' 'delete' and 'add'
    ;; actions
    ;; NOTE: Component users need to setup React Key to update the table
    ;; contents when the items update (at least after new items etc.)
    (r/with-let [idx->item (r/atom (into {} (map-indexed vector items)))
                 key-fn (or key-fn (constantly nil))]
      [:> Grid
       {:container       true
        :spacing         1
        :justify-content "flex-end"
        :align-items     "center"}

       ;; Table
       (when-not (empty? (vals @idx->item))
         [:> Grid {:item  true :xs 12
                   :style (merge {} (when max-width
                                       ;; Hacky place to do this here
                                       ;; TODO: move to smarter place
                                      {:width (str "calc(" max-width " - 24px)")}))}

          ;; Handle horizontal overflow with scrollbar
          [:div {:style {:overflow-x "auto"}}
           [:> DragDropContext
            {:onDragEnd (fn [res]
                          (when (and (= (.-reason res) "DROP")
                                     (> (count (vals @idx->item)) 1))
                            (let [source-idx (-> res .-source .-index)
                                  target-idx (-> res .-destination .-index)]
                              (swap! idx->item (fn [curr]
                                                 (let [m (get curr source-idx)
                                                       a (-> curr
                                                             (->> (sort-by first)
                                                                  (map second))
                                                             (into-array))]
                                                   (.splice a source-idx 1)
                                                   (.splice a target-idx 0 m)
                                                   (into {} (map-indexed vector) a))))
                              (when on-user-sort
                                (on-user-sort (vals @idx->item))))))}
            [:> Table

             ;; Headear row
             (when-not hide-header-row?
               [:> TableHead
                [:> TableRow {:hover true}
                 [:> TableCell ""]
                 (for [[k header] headers]
                   ^{:key k}
                   [:> TableCell header])
                 [:> TableCell ""]]])

             ;; Body
             [:> Droppable
              {:droppableId "droppable"}
              (fn [provided]
                (let [t-props (.-droppableProps provided)
                      _       (set! (.-ref t-props) (.-innerRef provided))]

                  (r/as-element
                    [:> TableBody (js->clj t-props)

                     ;; Rows
                     (doall
                       (for [[idx item] (sort-by first @idx->item)]
                         (let [id (or (key-fn item)
                                      idx
                                      (:id item)
                                      (:lipas-id item))]

                           [:> Draggable
                            {:draggableId (str "draggable-" id)
                             :index idx
                             :key id}
                            (fn [provided]
                              (let [r-props {:key            id
                                             :ref            (.-innerRef provided)
                                             :hover          true
                                             :on-mouse-enter (when on-custom-hover-in
                                                               #(on-custom-hover-in % item))
                                             :on-mouse-leave (when on-custom-hover-out
                                                               #(on-custom-hover-out % item))}]
                                (r/as-element
                                  [:> TableRow (merge r-props
                                                      (js->clj (.-draggableProps provided))
                                                      (js->clj (.-dragHandleProps provided)))

                                   [:> TableCell
                                    {:padding "checkbox"}
                                    [:> Stack
                                     {:direction "row"
                                      :align-items "center"}
                                     [:> Icon "drag_indicator"]]]

                                   ;; Cells
                                   (doall
                                     (for [[k _] headers
                                           :let  [v (get item k)]]
                                       [:> TableCell
                                        {:key k
                                         :padding "normal"}
                                        (utils/display-value v)]))

                                   [:> TableCell
                                    {:padding "checkbox"
                                     :class-name :no-print}
                                    [:> Stack
                                     {:direction "row"
                                      :align-items "center"}
                                     [:> Tooltip
                                      {:title (or edit-tooltip "")
                                       :placement "top"}
                                      [:> IconButton
                                       {:on-click #(on-edit item)}
                                       [:> Icon "edit"]]]
                                     [buttons/confirming-delete-button
                                      {:tooltip         delete-tooltip
                                       :confirm-tooltip confirm-tooltip
                                       :on-delete       #(on-delete item)}]]]])))])))

                     (.-placeholder provided)])))]]]]])

       ;; Add button
       [:> Grid
        {:item       true :xs 2
         :style      {:text-align "right"}
         :class-name :no-print}
        [:> Tooltip
         {:title (or add-tooltip "")
          :placement "left"}
         [:> Fab
          {:style    {:margin-top "1em"}
           :on-click on-add
           :size     add-btn-size
           :color    "secondary"}
          [:> Icon "add"]]]]])))
