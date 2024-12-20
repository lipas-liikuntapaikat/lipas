(ns lipas.ui.components.tables
  (:require ["@hello-pangea/dnd" :refer [DragDropContext Draggable Droppable] :as dnd]
            [lipas.ui.components.buttons :as buttons]
            [lipas.ui.components.checkboxes :as checkboxes]
            [lipas.ui.mui :as mui]
            [lipas.ui.utils :as utils]
            [reagent.core :as r]))

(defn table
  [{:keys [headers items on-select key-fn sort-fn sort-asc? sort-cmp
           action-icon hide-action-btn? on-sort-change in-progress? on-mouse-enter
           on-mouse-leave]
    :or   {sort-cmp         compare
           sort-asc?        false
           action-icon      "keyboard_arrow_right"
           hide-action-btn? false
           in-progress?     false
           on-sort-change   :default}}]
  (r/with-let [key-fn*         (or key-fn (constantly nil))
               sort-fn*        (r/atom sort-fn)
               sort-asc?       (r/atom sort-asc?)
               on-sort-change* #(on-sort-change {:sort-fn @sort-fn*
                                                 :asc?    @sort-asc?})]

    [mui/grid {:container true}
     [mui/grid {:item true :xs 12}
      [:div {:style {:overflow-x "auto"}} ; Fixes overflow outside screen

       [mui/table

        ;; Head
        [mui/table-head
         (into [mui/table-row (when (and on-select (not hide-action-btn?))
                                [mui/table-cell ""])]
               (doall
                 (for [[key header hidden?] headers]
                   [mui/table-cell {:style    (when hidden? {:display :none})
                                    :on-click #(do (reset! sort-fn* key)
                                                   (on-sort-change*))}
                    [mui/table-sort-label
                     {:active    (= key @sort-fn*)
                      :direction (if @sort-asc? "asc" "desc")
                      :on-click  #(swap! sort-asc? not)}
                     header]])))]

        ;; Body
        (when-not in-progress?
          [mui/table-body

           ;; Rows
           (doall
             (for [item (if @sort-fn*
                          (sort-by @sort-fn* (if @sort-asc?
                                               sort-cmp
                                               utils/reverse-cmp)
                                   items)
                          items)
                   :let [id (or (key-fn* item) (:id item) (:lipas-id item) (gensym))]]
               [mui/table-row
                {:key            id
                 :on-click       (when on-select #(on-select item))
                 :hover          true
                 :style          (when on-select {:cursor "pointer"})
                 :on-mouse-enter (when on-mouse-enter #(on-mouse-enter item))
                 :on-mouse-leave (when on-mouse-leave #(on-mouse-leave item))}

                (when (and on-select (not hide-action-btn?))
                  [mui/table-cell {:padding "checkbox"}
                   [mui/icon-button {:on-click #(on-select item)}
                    [mui/icon {:color "primary"} action-icon]]])

               ;; Cells
                (doall
                  (for [[k _ hidden?] headers
                        :let          [v (get item k)]]
                    [mui/table-cell
                     {:style (when hidden? {:display :none})
                      :key   (str id k)}
                     [mui/typography
                      {:style   {:font-size "1em"}
                       :variant "body1" :no-wrap false}
                      (utils/display-value v)]]))]))])]

       (when in-progress?
         [mui/grid {:container true :direction "column" :align-items "center"}
          [mui/grid {:item true}
           [mui/circular-progress {:style {:margin-top "1em"}}]]])]]]))

(defn- resolve-key [key-fn item]
  (or (key-fn item) (:id item) (:lipas-id item) (gensym)))

(defn table-v2
  [{:keys [headers items on-select key-fn sort-fn sort-asc? sort-cmp
           action-icon hide-action-btn? action-label on-sort-change
           in-progress?  allow-editing? on-item-save edit-label
           save-label discard-label allow-saving? multi-select?
           on-edit-start]
    :or   {sort-cmp         compare
           sort-asc?        false
           action-icon      "keyboard_arrow_right"
           hide-action-btn? false
           in-progress?     false
           on-sort-change   :default
           on-item-save     #(prn "Item save clicked!" %)
           allow-editing?   (constantly false)
           allow-saving?    (constantly false)
           multi-select?    false}}]

  (r/with-let [key-fn*         (or key-fn (constantly nil))
               selected        (r/atom (into {} (map
                                                  (juxt (partial resolve-key key-fn*)
                                                        (constantly false)))
                                             items))
               sort-fn*        (r/atom sort-fn)
               sort-asc?       (r/atom sort-asc?)
               on-sort-change* #(on-sort-change {:sort-fn @sort-fn*
                                                 :asc?    @sort-asc?})
               editing?        (r/atom nil)]

    (let [any-editable? (some allow-editing? items)]

      [mui/paper
       {:style
        {:width "100%" :overflow-x "scroll" :margin-top "0.5em" :margin-bottom "1em"}}

       (when in-progress?
         [:div
          {:style
           {:position         "absolute" :width "100%" :height "100%"
            :background-color "rgba(0, 0, 0, 0.2)"}}
          [mui/circular-progress
           {:size  "120px"
            :style {:display "block" :margin-left "auto" :margin-right "auto"}}]])

       [mui/table

        ;; Head
        [mui/table-head
         (into [mui/table-row

                ;; "Select all" checkbox
                (when (or (and on-select (not hide-action-btn?)) any-editable?)
                  [mui/table-cell {:padding "checkbox"}
                   (if multi-select?
                     [checkboxes/checkbox
                      {:value     (= (count items) (count (->> @selected vals (filter true?))))
                       :on-change (fn []
                                    (let [b (not (->> @selected vals (every? true?)))
                                          m (swap! selected #(reduce-kv
                                                               (fn [res k _]
                                                                 (assoc res k b))
                                                               {}
                                                               %))]
                                      (on-select (keys m))))}]
                     "")])]

               (for [[key {:keys [label hidden?]}] headers]
                 [mui/table-cell {:style    (when hidden? {:display :none})
                                  :on-click #(do (reset! sort-fn* key)
                                                 (on-sort-change*))}
                  [mui/table-sort-label
                   {:active    (= key @sort-fn*)
                    :direction (if @sort-asc? "asc" "desc")
                    :on-click  #(swap! sort-asc? not)}
                   label]]))]

        ;; Body
        [mui/table-body

         ;; Rows
         (doall
           (for [item (if @sort-fn*
                        (sort-by @sort-fn* (if @sort-asc?
                                             sort-cmp
                                             utils/reverse-cmp)
                                 items)
                        items)
                 :let [id (resolve-key key-fn* item)
                       editing-this? (contains? @editing? id)]]

             [mui/table-row
              {:key id :hover true :style (when on-select {:cursor "pointer"})}

             ;; First cell
              (when (or (and on-select (not hide-action-btn?)) any-editable?)
                [mui/table-cell {:padding "checkbox"}
                 (if editing-this?
                   [mui/grid {:container true :align-items "center" :wrap "nowrap"}

                    [mui/grid {:item true}
                     [mui/tooltip {:title save-label}
                      [mui/icon-button {:disabled (not (allow-saving? (@editing? id)))
                                        :on-click (fn []
                                                    (on-item-save (@editing? id))
                                                    (swap! editing? dissoc id))}
                       [mui/icon "save"]]]]

                    [mui/grid {:item true}
                     [mui/tooltip {:title discard-label}
                      [mui/icon-button {:on-click #(swap! editing? dissoc id)}
                       [mui/icon "undo"]]]]]

                   [mui/grid {:container true :wrap "nowrap"}

                    (if multi-select?

                      [mui/grid {:item true}
                       [checkboxes/checkbox
                        {:value     (@selected id)
                         :on-change (fn []
                                      (let [vs (swap! selected update id not)]
                                        (on-select (reduce
                                                     (fn [res [k v]]
                                                       (if v (conj res k) res))
                                                     #{}
                                                     vs))))}]]

                      (when (and on-select (not hide-action-btn?))
                        [mui/grid {:item true}
                         [mui/tooltip {:title action-label}
                          [mui/icon-button
                           {:on-click #(on-select item)}
                           [mui/icon action-icon]]]]))

                    (when (allow-editing? item)
                      [mui/grid {:item true}
                       [mui/tooltip {:title edit-label}
                        [mui/icon-button
                         {:on-click (fn []
                                      (when on-edit-start
                                        (on-edit-start item))
                                      (swap! editing? assoc id item))}
                         [mui/icon "edit"]]]])])])

             ;; Remaining Cells
              (doall
                (for [[k {:keys [hidden? form]}] headers
                      :let                       [v (get item k)]]

                  [mui/table-cell
                   {:style    (when hidden? {:display :none})
                    :on-click #(when (and on-select (not editing-this?))
                                 (on-select item))
                    :key      (str id k editing-this?)}

                   (if (and editing-this? (:component form))

                   ;; form field
                     (let [value-key (or (:value-key form) k)
                           path      [id value-key]]
                       [mui/grid {:container true :align-items "center" :wrap "nowrap"}
                        [mui/grid {:item true}
                         [(:component form)
                          (-> form
                              :props
                              (assoc :value (get-in @editing? [id value-key])
                                     :on-change #(swap! editing? assoc-in path %)))]]])

                   ;; display value
                     [mui/grid {:container true :align-items "center" :wrap "nowrap"}
                      [mui/grid {:item true}
                       [mui/typography
                        {:style {:font-size "1em"} :variant "body1" :no-wrap false}
                        (utils/display-value v)]]])]))]))]]])))

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
      [mui/typography (or empty-label "-")]
      [table props])

    ;; Table with 'edit' 'delete' and 'add'
    ;; actions
    ;; NOTE: Component users need to setup React Key to update the table
    ;; contents when the items update (at least after new items etc.)
    (r/with-let [idx->item (r/atom (into {} (map-indexed vector items)))
                 key-fn (or key-fn (constantly nil))]
      [mui/grid
       {:container       true
        :spacing         1
        :justify-content "flex-end"
        :align-items     "center"}

       ;; Table
       (when-not (empty? (vals @idx->item))
         [mui/grid {:item  true :xs 12
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
            [mui/table

             ;; Headear row
             (when-not hide-header-row?
               [mui/table-head
                [mui/table-row {:hover true}
                 [mui/table-cell ""]
                 (for [[k header] headers]
                   ^{:key k}
                   [mui/table-cell header])
                 [mui/table-cell ""]]])

             ;; Body
             [:> Droppable
              {:droppableId "droppable"}
              (fn [provided]
                (let [t-props (.-droppableProps provided)
                      _       (set! (.-ref t-props) (.-innerRef provided))]

                  (r/as-element
                    [mui/table-body (js->clj t-props)

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
                                  [mui/table-row (merge r-props
                                                        (js->clj (.-draggableProps provided))
                                                        (js->clj (.-dragHandleProps provided)))

                                   [mui/table-cell
                                    {:padding "checkbox"}
                                    [mui/stack
                                     {:direction "row"
                                      :align-items "center"}
                                     [mui/icon "drag_indicator"]]]

                                   ;; Cells
                                   (doall
                                     (for [[k _] headers
                                           :let  [v (get item k)]]
                                       [mui/table-cell
                                        {:key k
                                         :padding "normal"}
                                        (utils/display-value v)]))

                                   [mui/table-cell
                                    {:padding "checkbox"
                                     :class-name :no-print}
                                    [mui/stack
                                     {:direction "row"
                                      :align-items "center"}
                                     [mui/tooltip
                                      {:title (or edit-tooltip "")
                                       :placement "top"}
                                      [mui/icon-button
                                       {:on-click #(on-edit item)}
                                       [mui/icon "edit"]]]
                                     [buttons/confirming-delete-button
                                      {:tooltip         delete-tooltip
                                       :confirm-tooltip confirm-tooltip
                                       :on-delete       #(on-delete item)}]]]])))])))

                     (.-placeholder provided)])))]]]]])

       ;; Add button
       [mui/grid
        {:item       true :xs 2
         :style      {:text-align "right"}
         :class-name :no-print}
        [mui/tooltip
         {:title (or add-tooltip "")
          :placement "left"}
         [mui/fab
          {:style    {:margin-top "1em"}
           :on-click on-add
           :size     add-btn-size
           :color    "secondary"}
          [mui/icon "add"]]]]])))
