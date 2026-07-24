(ns lipas.ui.components.tables
  (:require [lipas.ui.components.buttons :as buttons]
            [lipas.ui.components.checkboxes :as checkboxes]
            ["@mui/material/CircularProgress$default" :as CircularProgress]
            ["@mui/material/Fab$default" :as Fab]
            ["@mui/material/GridLegacy$default" :as Grid]
            ["@mui/material/Icon$default" :as Icon]
            ["@mui/material/IconButton$default" :as IconButton]
            ["@mui/material/Paper$default" :as Paper]
            ["@mui/material/Stack$default" :as Stack]
            ["@mui/material/Table$default" :as Table]
            ["@mui/material/TableBody$default" :as TableBody]
            ["@mui/material/TableCell$default" :as TableCell]
            ["@mui/material/TableHead$default" :as TableHead]
            ["@mui/material/TableRow$default" :as TableRow]
            ["@mui/material/TableSortLabel$default" :as TableSortLabel]
            ["@mui/material/Tooltip$default" :as Tooltip]
            ["@mui/material/Typography$default" :as Typography]
            [lipas.ui.utils :as utils]
            [reagent.core :as r]))

(defn table
  [{:keys [headers items on-select key-fn sort-fn sort-asc? sort-cmp
           action-icon hide-action-btn? on-sort-change in-progress? on-mouse-enter
           on-mouse-leave on-custom-hover-in on-custom-hover-out]
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

    [:> Grid {:container true}
     [:> Grid {:item true :xs 12}
      [:div {:style {:overflow-x "auto"}} ; Fixes overflow outside screen

       [:> Table

        ;; Head
        [:> TableHead
         (into [:> TableRow (when (and on-select (not hide-action-btn?))
                              [:> TableCell ""])]
               (doall
                 (for [[key header hidden?] headers]
                   [:> TableCell {:style    (when hidden? {:display :none})
                                  :on-click #(do (reset! sort-fn* key)
                                                 (on-sort-change*))}
                    [:> TableSortLabel
                     {:active    (= key @sort-fn*)
                      :direction (if @sort-asc? "asc" "desc")
                      :on-click  #(swap! sort-asc? not)}
                     header]])))]

        ;; Body
        (when-not in-progress?
          [:> TableBody

           ;; Rows
           (doall
             (for [item (if @sort-fn*
                          (sort-by @sort-fn* (if @sort-asc?
                                               sort-cmp
                                               utils/reverse-cmp)
                                   items)
                          items)
                   :let [id (or (key-fn* item) (:id item) (:lipas-id item) (gensym))]]
               [:> TableRow
                {:key            id
                 :on-click       (when on-select #(on-select item))
                 :hover          true
                 :style          (when on-select {:cursor "pointer"})
                 :on-mouse-enter (cond
                                   on-custom-hover-in #(on-custom-hover-in % item)
                                   on-mouse-enter     #(on-mouse-enter item))
                 :on-mouse-leave (cond
                                   on-custom-hover-out #(on-custom-hover-out % item)
                                   on-mouse-leave      #(on-mouse-leave item))}

                (when (and on-select (not hide-action-btn?))
                  [:> TableCell {:padding "checkbox"}
                   [:> IconButton {:on-click #(on-select item)}
                    [:> Icon {:color "primary"} action-icon]]])

               ;; Cells
                (doall
                  (for [[k _ hidden?] headers
                        :let          [v (get item k)]]
                    [:> TableCell
                     {:style (when hidden? {:display :none})
                      :key   (str id k)}
                     [:> Typography
                      {:style   {:font-size "1em"}
                       :variant "body1" :no-wrap false}
                      (utils/display-value v)]]))]))])]

       (when in-progress?
         [:> Grid {:container true :direction "column" :align-items "center"}
          [:> Grid {:item true}
           [:> CircularProgress {:style {:margin-top "1em"}}]]])]]]))

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

      [:> Paper
       {:style
        {:width "100%" :overflow-x "scroll" :margin-top "0.5em" :margin-bottom "1em"}}

       (when in-progress?
         [:div
          {:style
           {:position         "absolute" :width "100%" :height "100%"
            :background-color "rgba(0, 0, 0, 0.2)"}}
          [:> CircularProgress
           {:size  "120px"
            :style {:display "block" :margin-left "auto" :margin-right "auto"}}]])

       [:> Table

        ;; Head
        [:> TableHead
         (into [:> TableRow

                ;; "Select all" checkbox
                (when (or (and on-select (not hide-action-btn?)) any-editable?)
                  [:> TableCell {:padding "checkbox"}
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
                 [:> TableCell {:style    (when hidden? {:display :none})
                                :on-click #(do (reset! sort-fn* key)
                                               (on-sort-change*))}
                  [:> TableSortLabel
                   {:active    (= key @sort-fn*)
                    :direction (if @sort-asc? "asc" "desc")
                    :on-click  #(swap! sort-asc? not)}
                   label]]))]

        ;; Body
        [:> TableBody

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

             [:> TableRow
              {:key id :hover true :style (when on-select {:cursor "pointer"})}

             ;; First cell
              (when (or (and on-select (not hide-action-btn?)) any-editable?)
                [:> TableCell {:padding "checkbox"}
                 (if editing-this?
                   [:> Grid {:container true :align-items "center" :wrap "nowrap"}

                    [:> Grid {:item true}
                     [:> Tooltip {:title save-label}
                      [:> IconButton {:disabled (not (allow-saving? (@editing? id)))
                                      :on-click (fn []
                                                  (on-item-save (@editing? id))
                                                  (swap! editing? dissoc id))}
                       [:> Icon "save"]]]]

                    [:> Grid {:item true}
                     [:> Tooltip {:title discard-label}
                      [:> IconButton {:on-click #(swap! editing? dissoc id)}
                       [:> Icon "undo"]]]]]

                   [:> Grid {:container true :wrap "nowrap"}

                    (if multi-select?

                      [:> Grid {:item true}
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
                        [:> Grid {:item true}
                         [:> Tooltip {:title action-label}
                          [:> IconButton
                           {:on-click #(on-select item)}
                           [:> Icon action-icon]]]]))

                    (when (allow-editing? item)
                      [:> Grid {:item true}
                       [:> Tooltip {:title edit-label}
                        [:> IconButton
                         {:on-click (fn []
                                      (when on-edit-start
                                        (on-edit-start item))
                                      (swap! editing? assoc id item))}
                         [:> Icon "edit"]]]])])])

             ;; Remaining Cells
              (doall
                (for [[k {:keys [hidden? form]}] headers
                      :let                       [v (get item k)]]

                  [:> TableCell
                   {:style    (when hidden? {:display :none})
                    :on-click #(when (and on-select (not editing-this?))
                                 (on-select item))
                    :key      (str id k editing-this?)}

                   (if (and editing-this? (:component form))

                   ;; form field
                     (let [value-key (or (:value-key form) k)
                           path      [id value-key]]
                       [:> Grid {:container true :align-items "center" :wrap "nowrap"}
                        [:> Grid {:item true}
                         [(:component form)
                          (-> form
                              :props
                              (assoc :value (get-in @editing? [id value-key])
                                     :on-change #(swap! editing? assoc-in path %)))]]])

                   ;; display value
                     [:> Grid {:container true :align-items "center" :wrap "nowrap"}
                      [:> Grid {:item true}
                       [:> Typography
                        {:style {:font-size "1em"} :variant "body1" :no-wrap false}
                        (utils/display-value v)]]])]))]))]]])))
