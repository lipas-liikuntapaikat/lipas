(ns lipas.ui.components
  (:require [clojure.reader :refer [read-string]]
            [clojure.spec.alpha :as s]
            [clojure.string :refer [trim]]
            [lipas.schema.core :as schema]
            [lipas.ui.mui :as mui]
            [reagent.core :as r]))

(def CHECK_MARK "✓")

;;; Transitions ;;;

(defn slide [props]
  [mui/slide props])

(defn fade [props]
  [mui/fade props])

(defn zoom [props]
  [mui/zoom props])

(defn grow [props]
  [mui/grow props])

;;; Components ;;;

(defn edit-button [{:keys [on-click active? tooltip]}]
  [mui/tooltip {:title     (or tooltip "")
                :placement "top"}
   [mui/button {:on-click on-click
                :color    (if active? "secondary" "primary")}
    [mui/icon "edit_icon"]]])

(defn save-button [{:keys [on-click tooltip] :as props}]
  [mui/tooltip {:title     ""
                :placement "top"}
   [mui/button (merge props {:variant  "contained"
                             :on-click on-click
                             :color    "secondary"})
    tooltip
    [mui/icon {:style {:margin-left "0.25em"}}
     "save_icon"]]])

(defn publish-button [{:keys [on-click tooltip] :as props}]
  [mui/tooltip {:title     ""
                :placement "top"}
   [mui/button (merge props {:variant  "contained"
                             :on-click on-click
                             :color    "secondary"})
    tooltip
    [mui/icon {:style {:margin-left "0.25em"}}
     "cloud_upload"]]])

(defn discard-button [{:keys [on-click tooltip] :as props}]
  [mui/tooltip {:title     (or tooltip "")
                :placement "top"}
   [mui/button (merge props {:on-click on-click
                             :color    "primary"})
    [mui/icon "undo"]]])

;; Returns actually a list of components.
;; TODO think something more intuitive here.
(defn edit-actions-list [{:keys [uncommitted-edits? editing?
                                 logged-in?  user-can-publish?
                                 on-discard on-save-draft
                                 save-draft-tooltip discard-tooltip
                                 edit-tooltip publish-tooltip
                                 on-edit-start on-edit-end
                                 on-publish]}]
  [(when (and uncommitted-edits? user-can-publish? (not editing?))
     [publish-button
      {:on-click on-publish
       :tooltip  publish-tooltip}])
   (when (and uncommitted-edits? (not user-can-publish?) (not editing?))
     [save-button
      {:on-click on-save-draft
       :tooltip  save-draft-tooltip}])
   (when (and uncommitted-edits? (not editing?))
     [discard-button
      {:on-click on-discard
       :tooltip  discard-tooltip}])
   (when logged-in?
     [edit-button
      {:active?  editing?
       :on-click #(if editing?
                    (on-edit-end %)
                    (on-edit-start %))
       :tooltip  edit-tooltip}])])

(defn checkbox [{:keys [label value on-change]}]
  [mui/form-control-label
   {:label label
    :control (r/as-element
              [mui/checkbox
               {:value (or (str value) "-")
                :checked value
                :on-change #(on-change %2)}])}]) ; %2 = checked?

(defn display-value [v]
  (cond
    (true? v) CHECK_MARK
    :else v))

(defn table-cell
  ([value]
   (table-cell {} value))
  ([props value]
   [mui/table-cell props (display-value value)]))

(defn table [{:keys [headers items on-select key-fn]}]
  (let [key-fn (or key-fn (constantly nil))]
    [mui/grid {:container true}
     [mui/grid {:item true :xs 12}
      [:div {:style {:overflow-x "auto"}}
       [mui/table
        [mui/table-head
         (into [mui/table-row (when on-select
                                [mui/table-cell ""])]
               (for [[_ header] headers]
                 [mui/table-cell header]))]
        [mui/table-body
         (for [item items
               :let [id (or (key-fn item) (:id item) (:lipas-id item))]]
           [mui/table-row {:key id
                           :hover true}
            (when on-select
              [mui/table-cell {:padding "checkbox"}
               [mui/icon-button {:on-click #(on-select item)}
                [mui/icon {:color "secondary"} "folder_open"]]])
            (for [[k _] headers
                  :let [v (get item k)]]
              [mui/table-cell {:key (str id k)}
               (display-value v)])])]]]]]))

(defn form-table [{:keys [headers
                          items
                          key-fn
                          add-tooltip
                          edit-tooltip
                          delete-tooltip
                          read-only?
                          on-add
                          on-edit
                          on-delete] :as props}]
  (if read-only?
    [table props]
    (r/with-let [selected-item (r/atom nil)
                 key-fn (or key-fn (constantly nil))]
      [mui/grid {:container true
                 :spacing 8
                 :justify "flex-end"
                 :align-items "center"}
       [mui/grid {:item true :xs 12}
        [:div {:style {:overflow-x "auto"}}
         [mui/table
          [mui/table-head
           (into [mui/table-row {:hover true}
                  [mui/table-cell ""]]
                 (for [[_ header] headers]
                   [mui/table-cell header]))]
          [mui/table-body
           (doall
            (for [item items
                  :let [id (or (key-fn item) (:id item) (:lipas-id item))]]
              [mui/table-row {:key id
                              :hover true}
               [mui/table-cell {:padding "checkbox"}
                [mui/checkbox {:checked (= item @selected-item)
                               :on-change (fn [_ checked?]
                                            (let [v (when checked? item)]
                                              (reset! selected-item v)))}]]
               (for [[k _] headers
                     :let [v (get item k)]]
                 [mui/table-cell {:key (str id k)
                                  :padding "dense"}
                  (display-value v)])]))]]]]
       [mui/grid {:item true :xs 10}
        (when @selected-item
          [:span
           [mui/tooltip {:title (or edit-tooltip "")
                         :placement "top"}
            [mui/icon-button {:on-click #(on-edit @selected-item)}
             [mui/icon "edit"]]]
           [mui/tooltip {:title (or delete-tooltip "")
                         :placement "top"}
            [mui/icon-button {:on-click #(on-delete @selected-item)}
             [mui/icon "delete"]]]])]
       [mui/grid {:item true :xs 2
                  :style {:text-align "right"}}
        [mui/tooltip {:title (or add-tooltip "")
                      :placement "left"}
         [mui/button {:style {:margin-top "0.5em"}
                      :on-click on-add
                      :variant "fab"
                      :color "secondary"}
          [mui/icon "add"]]]]])))

(defn dialog [{:keys [title
                      on-save
                      on-close
                      save-label
                      cancel-label]} content]
  [mui/dialog {:open true
               :full-width true
               :on-close on-close}
   [mui/dialog-title title]
   [mui/dialog-content content]
   [mui/dialog-actions
    [mui/button {:on-click on-close}
     cancel-label]
    [mui/button {:on-click on-save}
     save-label]]])

(defn form-card [{:keys [title]} & content]
  [mui/grid {:item true
             :xs 12
             :md 12}
   [mui/card {:square true
              :style {:height "100%"}}
    [mui/card-header {:title title}]
    (into [mui/card-content] content)]])

(defn notification [{:keys [notification on-close]}]
  [mui/snackbar {:key (gensym)
                 :auto-hide-duration 5000
                 :open true
                 :anchor-origin {:vertical "top"
                                 :horizontal "right"}
                 :on-close on-close}
   [mui/snackbar-content {:message (:message notification)
                          :action (r/as-element
                                   [mui/icon-button {:key "close"
                                                     :on-click on-close
                                                     :color "secondary"}
                                    (if (:success? notification)
                                      [mui/icon "done"]
                                      [mui/icon "warning"])])}]])

(defn trim-safe [s]
  (if (string? s)
    (trim s)
    s))

(defn error? [spec value required]
  (if (and spec (or value required))
    ((complement s/valid?) spec value)
    false))

(defn ->adornment [s]
  {:end-adornment
   (r/as-element
    [mui/input-adornment s])})

(defn coerce [type s]
  (if (= type "number")
    (read-string s)
    (not-empty s)))

(defn patched-input [props]
  [:input (dissoc props :inputRef)])

(defn text-field-controlled [{:keys [value type on-change spec required
                                     Input-props adornment]
                              :as props} & children]
  (let [props (-> props
                  (assoc :error (error? spec value required))
                  (assoc :Input-props
                         (merge Input-props
                                {:input-component (r/reactify-component
                                                   patched-input)}
                                (when adornment
                                  (->adornment adornment))))
                  (assoc :value (or value ""))
                  (assoc :on-change #(->> %
                                          .-target
                                          .-value
                                          (coerce type)
                                          on-change))
                  (assoc :on-blur #(when (string? value)
                                     (on-change (trim value)))))]
    (into [mui/text-field props] children)))

(def text-field text-field-controlled)

(defn select [{:keys [label value items on-change value-fn label-fn]
               :or   {value-fn :value
                      label-fn :label}
               :as   props}]
  (let [props (-> props
                  (dissoc :value-fn :label-fn :label)
                  (assoc :value (pr-str value))
                  (assoc :on-change #(on-change (-> %
                                                    .-target
                                                    .-value
                                                    read-string))))]
    [mui/form-control
     (when label [mui/input-label label])
     (into [mui/select props]
           (for [i items]
             (let [value (value-fn i)
                   label (label-fn i)]
               [mui/menu-item {:key (pr-str value)
                               :value (pr-str value)}
                label])))]))

(defn multi-select [{:keys [label value items on-change value-fn label-fn]
                     :or {value-fn :value
                          label-fn :label}
                     :as props}]
  [mui/form-control
   (when label [mui/input-label label])
   [mui/select
    (merge (dissoc props :label :value-fn :label-fn)
           {:multiple true
            :value value
            :on-change #(on-change (-> % .-target .-value))})
    (for [i items]
      [mui/menu-item
       {:key (value-fn i)
        :value (value-fn i)}
       (label-fn i)])]])

(defn year-selector [{:keys [label value on-change required years] :as props}]
  (let [years (or years
                  (range 1900 (inc (.getFullYear (js/Date.)))))]
    [select (merge props {:label label
                          :items (map #(hash-map :label % :value %) years)
                          :on-change on-change
                          :value value
                          :required required})]))

(defn date-picker [{:keys [label value on-change]}]
  [mui/text-field
   {:type "date"
    :label label
    :value (or value "")
    :Input-label-props
    {:shrink true} ; This makes the label show actually
    :on-change #(on-change (-> % .-target .-value))}])

(defn info-table [{:keys [data]}]
  [mui/table
   (into [mui/table-body]
         (for [row data]
           [mui/table-row
            [table-cell
             [mui/typography {:variant "caption"}
              (first row)]]
            [table-cell (second row)]]))])

(defn table-form [{:keys [read-only?]} & fields]
  [mui/table
   (into [mui/table-body]
         (for [row (remove nil? fields)
               :let [{:keys [label value form-field]} row]]
           [mui/table-row
            [table-cell
             [mui/typography {:variant "caption"}
              label]]
            [table-cell {:numeric true}
             (if read-only?
               value
               [mui/form-group
                form-field])]]))])

(def form table-form)

(defn sports-site-form [{:keys [tr display-data edit-data types size-categories
                                admins owners on-change read-only?]}]
  (let [locale (tr)]
    [form {:read-only? read-only?}

     ;; Name
     {:label      (tr :sports-place/name-fi)
      :value      (-> display-data :name)
      :form-field [text-field
                   {:spec      ::schema/name
                    :value     (-> edit-data :name :fi)
                    :on-change #(on-change :name :fi %)}]}

     ;; Type
     {:label      (tr :type/name)
      :value      (-> display-data :type :name)
      :form-field [select
                   {:value     (-> edit-data :type :type-code)
                    :items     types
                    :label-fn  (comp locale :name)
                    :value-fn  :type-code
                    :on-change #(on-change :type :type-code %)}]}

     ;; Ice-stadiums get special treatment
     (when (= 2520 (-> display-data :type :type-code))
       {:label      (tr :ice/size-category)
        :value      (-> display-data :type :size-category)
        :form-field [select
                     {:value     (-> edit-data :type :size-category)
                      :items     size-categories
                      :value-fn  first
                      :label-fn  (comp locale second)
                      :on-change #(on-change :type :size-category %)}]})

     ;; Owner
     {:label      (tr :sports-place/owner)
      :value      (-> display-data :owner)
      :form-field [select
                   {:value     (-> edit-data :owner)
                    :items     owners
                    :value-fn  first
                    :label-fn  (comp locale second)
                    :on-change #(on-change :owner %)}]}

     ;; Admin
     {:label      (tr :sports-place/admin)
      :value      (-> display-data :admin)
      :form-field [select
                   {:value     (-> edit-data :admin)
                    :items     admins
                    :value-fn  first
                    :label-fn  (comp locale second)
                    :on-change #(on-change :admin %)}]}

     ;; Phone number
     {:label      (tr :sports-place/phone-number)
      :value      (-> display-data :phone-number)
      :form-field [text-field
                   {:value     (-> edit-data :phone-number)
                    :spec      ::schema/phone-number
                    :on-change #(on-change :phone-number %)}]}

     ;; WWW
     {:label      (tr :sports-place/www)
      :value      (-> display-data :www)
      :form-field [text-field
                   {:value     (-> edit-data :www)
                    :spec      ::schema/www
                    :on-change #(on-change :www %)}]}

     ;; Email
     {:label      (tr :sports-place/email-public)
      :value      (-> display-data :email)
      :form-field [text-field
                   {:value     (-> edit-data :email)
                    :spec      ::schema/email
                    :on-change #(on-change :email %)}]}]))

(defn location-form [{:keys [tr edit-data display-data cities on-change
                                   read-only?]}]
  (let [locale (tr)]
    [form
     {:read-only? read-only?}

     ;; Address
     {:label      (tr :location/address)
      :value      (-> display-data :address)
      :form-field [text-field
                   { :value    (-> edit-data :address)
                    :spec      ::schema/address
                    :on-change #(on-change :address %)}]}

     ;; Postal code
     { :label     (tr :location/postal-code)
      :value      (-> display-data :postal-code)
      :form-field [text-field
                   { :value    (-> edit-data :postal-code)
                    :spec      ::schema/postal-code
                    :on-change #(on-change :postal-code %)}]}

     ;; Postal office
     {:label      (tr :location/postal-office)
      :value      (-> display-data :postal-office)
      :form-field [text-field
                   { :value    (-> edit-data :postal-office)
                    :spec      ::schema/postal-office
                    :on-change #(on-change :postal-office %)}]}

     ;; City
     {:label      (tr :location/city)
      :value      (-> display-data :city :name)
      :form-field [select
                   {:value     (-> edit-data :city :city-code)
                    :items     cities
                    :label-fn  (comp locale :name)
                    :value-fn  :city-code
                    :on-change #(on-change :city :city-code %)}]}]))

(defn expansion-panel [{:keys [label]} & children]
  [mui/expansion-panel {:style {:margin-top "1em"}}
   [mui/expansion-panel-summary {:expand-icon (r/as-element
                                               [mui/icon "expand_more"])}
    [mui/typography {:color "primary"
                     :variant "button"}
     label]]
   (into [mui/expansion-panel-details]
         children)])

(defn full-screen-dialog [{:keys [open? title on-close close-label actions]}
                          & contents]
  [mui/dialog {:open                 open?
               :full-screen          true
               :Transition-component (r/reactify-component slide)
               :Transition-props     {:direction "up"}
               :on-close             on-close}

   [mui/dialog-title (or title "")]
   (into [mui/dialog-content {:style {:padding 0}}]
         contents)
   (conj (into [mui/dialog-actions] actions)
         [mui/button {:on-click on-close}
          close-label])])
