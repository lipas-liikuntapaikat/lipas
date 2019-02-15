(ns lipas.ui.components.tables
  (:require
   [lipas.ui.components.buttons :as buttons]
   [lipas.ui.mui :as mui]
   [lipas.ui.utils :as utils]
   [reagent.core :as r]))

(defn table
  [{:keys [headers items on-select key-fn sort-fn sort-asc? sort-cmp
           action-icon hide-action-btn? on-sort-change in-progress? ]
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
               (for [[key header hidden?] headers]
                 [mui/table-cell {:style    (when hidden? {:display :none})
                                  :on-click #(do (reset! sort-fn* key)
                                                 (on-sort-change*))}
                  [mui/table-sort-label
                   {:active    (= key @sort-fn*)
                    :direction (if @sort-asc? "asc" "desc")
                    :on-click  #(swap! sort-asc? not)}
                   header]]))]

        ;; Body
        (when-not in-progress?
          [mui/table-body

           ;; Rows
           (for [item (if @sort-fn*
                        (sort-by @sort-fn* (if @sort-asc?
                                             sort-cmp
                                             utils/reverse-cmp)
                                 items)
                        items)
                 :let [id (or (key-fn* item) (:id item) (:lipas-id item) (gensym))]]
             [mui/table-row {:key      id
                             :on-click (when on-select #(on-select item))
                             :hover    true}
              (when (and on-select (not hide-action-btn?))
                [mui/table-cell {:padding "checkbox"}
                 [mui/icon-button {:on-click #(on-select item)}
                  [mui/icon {:color "primary"} action-icon]]])

              ;; Cells
              (for [[k _ hidden?] headers
                    :let          [v (get item k)]]
                [mui/table-cell
                 {:style (when hidden? {:display :none})
                  :key   (str id k)}
                 [mui/typography
                  {:style   {:font-size "1em"}
                   :variant "body1" :no-wrap false}
                  (utils/display-value v)]])])])]

       (when in-progress?
         [mui/grid {:container true :direction "column" :align-items "center"}
          [mui/grid {:item true}
           [mui/circular-progress {:style {:margin-top "1em"}}]]])]]]))

(defn table-v2
  [{:keys [headers items on-select key-fn sort-fn sort-asc? sort-cmp
           action-icon hide-action-btn? action-label on-sort-change
           in-progress?  allow-editing? on-item-save edit-label
           save-label discard-label allow-saving?]
    :or   {sort-cmp         compare
           sort-asc?        false
           action-icon      "keyboard_arrow_right"
           hide-action-btn? false
           in-progress?     false
           on-sort-change   :default
           on-item-save     #(prn "Item save clicked!" %)
           allow-editing?   (constantly false)
           allow-saving?    (constantly false)}}]

  (r/with-let [key-fn*         (or key-fn (constantly nil))
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

                (when (or (and on-select (not hide-action-btn?)) any-editable?)
                  [mui/table-cell ""])]

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
        ;; (when-not in-progress?)
        [mui/table-body

         ;; Rows
         (doall
          (for [item (if @sort-fn*
                       (sort-by @sort-fn* (if @sort-asc?
                                            sort-cmp
                                            utils/reverse-cmp)
                                items)
                       items)
                :let [id (or (key-fn* item) (:id item) (:lipas-id item) (gensym))
                      editing-this? (contains? @editing? id)]]

            [mui/table-row {:key id :hover true}

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

                   (when (and on-select (not hide-action-btn?))
                     [mui/grid {:item true}
                      [mui/tooltip {:title action-label}
                       [mui/icon-button
                        {:on-click #(on-select item)}
                        [mui/icon action-icon]]]])

                   (when (allow-editing? item)
                     [mui/grid {:item true}
                      [mui/tooltip {:title edit-label}
                       [mui/icon-button
                        {:on-click #(swap! editing? assoc id item)}
                        [mui/icon "edit"]]]])])])

             ;; Remaining Cells
             (doall
              (for [[k {:keys [hidden? form]}] headers
                    :let                       [v (get item k)]]

                [mui/table-cell
                 {:style    (when hidden? {:display :none})
                  :on-click #(when-not editing-this? (on-select item))
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

(defn form-table [{:keys [headers items key-fn add-tooltip
                          edit-tooltip delete-tooltip confirm-tooltip
                          read-only? on-add on-edit on-delete]
                   :as   props}]
  (if read-only?

    ;; Normal read-only table
    [table props]

    ;; Table with selectable rows and 'edit' 'delete' and 'add'
    ;; actions
    (r/with-let [selected-item (r/atom nil)
                 key-fn (or key-fn (constantly nil))]
      [mui/grid
       {:container   true
        :spacing     8
        :justify     "flex-end"
        :align-items "center"}

       ;; Table
       [mui/grid {:item true :xs 12}

        ;; Handle horizontal overflow with scrollbar
        [:div {:style {:overflow-x "auto"}}
         [mui/table

          ;; Headear row
          [mui/table-head
           (into [mui/table-row {:hover true}
                  [mui/table-cell ""]]
                 (for [[_ header] headers]
                   [mui/table-cell header]))]

          ;; Body
          [mui/table-body
           (doall
            ;; Rows
            (for [item items
                  :let [id (or (key-fn item) (:id item) (:lipas-id item))]]
              [mui/table-row {:key id :hover true}
               [mui/table-cell {:padding "checkbox"}
                [mui/checkbox
                 {:checked   (= item @selected-item)
                  :on-change (fn [_ checked?]
                               (let [v (when checked? item)]
                                 (reset! selected-item v)))}]]

               ;; Cells
               (for [[k _] headers
                     :let  [v (get item k)]]
                 [mui/table-cell {:key (str id k) :padding "dense"}
                  (utils/display-value v)])]))]]]]

       ;; Editing tools
       [mui/grid {:item true :xs 10 :class-name :no-print}

        ;; Edit button
        (when @selected-item
          [mui/tooltip {:title (or edit-tooltip "") :placement "top"}
           [mui/icon-button {:on-click #(on-edit @selected-item)}
            [mui/icon "edit"]]])

        ;; Delete button
        (when @selected-item
          [buttons/confirming-delete-button
           {:tooltip         delete-tooltip
            :confirm-tooltip confirm-tooltip
            :on-delete       #(do
                                (on-delete @selected-item)
                                (reset! selected-item nil))}])]

       ;; Add button
       [mui/grid
        {:item       true :xs 2
         :style      {:text-align "right"}
         :class-name :no-print}
        [mui/tooltip {:title (or add-tooltip "") :placement "left"}
         [mui/button
          {:style    {:margin-top "1em"}
           :on-click on-add
           :variant  "fab"
           :color    "secondary"}
          [mui/icon "add"]]]]])))

(defn info-table [{:keys [data]}]
  [mui/table
   (into
    [mui/table-body]
    (for [row data]
      [mui/table-row
       [mui/table-cell
        [mui/typography {:variant "caption"}
         (first row)]]
       [mui/table-cell (-> row second utils/display-value)]]))])

(defn table-form [{:keys [read-only?]} & fields]
  [:div {:style {:overflow-x "auto" :max-width "600px"}}
   [mui/table
    (into
     [mui/table-body]
     (for [row  (remove nil? fields)
           :let [{:keys [label value form-field]} row]]
       [mui/table-row
        [mui/table-cell
         [mui/typography {:variant "caption"}
          label]]
        [mui/table-cell {:numeric true :style {:text-overflow :ellipsis}}
         (if read-only?
           (utils/display-value value)
           [mui/form-group
            form-field])]]))]])
