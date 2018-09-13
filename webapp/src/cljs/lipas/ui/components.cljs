(ns lipas.ui.components
  (:require cljsjs.react-autosuggest
            [clojure.reader :refer [read-string]]
            [clojure.spec.alpha :as s]
            [clojure.string :refer [trim] :as string]
            [goog.object :as gobj]
            [lipas.ui.mui :as mui]
            [lipas.ui.utils :as utils]
            [reagent.core :as r]))

(def autosuggest js/Autosuggest)

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

(defn email-button [{:keys [on-click label] :as props}]
  [mui/button (merge {:color    "secondary"
                      :variant  "contained"
                      :on-click on-click}
                     props)
   [mui/icon {:style {:margin-right "0.25em"}}
    "email"]
   label])

(defn download-button [{:keys [on-click label] :as props}]
  [mui/button (merge {:color    "secondary"
                      :variant  "outlined"
                      :on-click on-click}
                     props)
   label])

(defn login-button [{:keys [on-click label]}]
  [mui/button {:variant  "contained"
               :color    "secondary"
               :on-click on-click}
   [mui/icon {:style {:margin-right "0.25em"}}
    "lock"]
   label])

(defn register-button [{:keys [href on-click label]}]
  [mui/button {:variant  "contained"
               :color    "secondary"
               :href     href
               :on-click on-click}
   [mui/icon {:style {:margin-right "0.25em"}}
    "group_add"]
   label])

(defn edit-button [{:keys [on-click active? tooltip] :as props}]
  (let [btn-props (-> props
                      (dissoc :active?)
                      (merge {:on-click on-click
                              :color    (if active? "secondary" "primary")}))]
    [mui/tooltip {:title     (or tooltip "")
                  :placement "top"}
     [mui/button btn-props
      [mui/icon "edit_icon"]]]))

(defn save-button [{:keys [on-click tooltip disabled disabled-tooltip] :as props}]
  [mui/tooltip {:title     (if disabled disabled-tooltip "")
                :placement "top"}
   [:span
    [mui/button (merge (dissoc props :disabled-tooltip)
                       {:variant  "contained"
                        :disabled disabled
                        :on-click on-click
                        :color    "secondary"})
     tooltip
     [mui/icon {:style {:margin-left "0.25em"}}
      "save_icon"]]]])

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

(defn confirming-delete-button [{:keys [on-delete tooltip confirm-tooltip]}]
  (r/with-let [timeout  10000
               clicked? (r/atom false)
               timeout* (r/atom nil)]
    [:span
     [mui/tooltip {:style     {:color :yellow}
                   :title     (or tooltip "")
                   :placement "top"}
      [mui/icon-button
       {:on-click #(if @clicked?
                     (do
                       (js/clearTimeout @timeout*)
                       (reset! clicked? false)
                       (on-delete %))
                     (do
                       (reset! timeout*
                               (js/setTimeout
                                (fn []
                                  (reset! clicked? false)) timeout))
                       (reset! clicked? true)))}
       (if @clicked?
         [mui/icon "delete_forever"]
         [mui/icon "delete"])]]
     (when @clicked?
       [mui/typography {:style {:display :inline}
                        :color :error}
        confirm-tooltip])]))

;; Returns actually a list of components.
;; TODO think something more intuitive here.
(defn edit-actions-list [{:keys [editing? valid? logged-in?
                                 user-can-publish? on-discard
                                 on-save-draft save-draft-tooltip
                                 discard-tooltip edit-tooltip
                                 publish-tooltip on-edit-start
                                 invalid-message on-edit-end
                                 on-publish]}]
  [(when (and editing? user-can-publish?)
     [save-button
      {:on-click         on-publish
       :disabled         (not valid?)
       :disabled-tooltip invalid-message
       :tooltip          publish-tooltip}])
   (when (and editing? (not user-can-publish?))
     [save-button
      {:on-click         on-save-draft
       :disabled         (not valid?)
       :disabled-tooltip invalid-message
       :tooltip          save-draft-tooltip}])
   (when editing?
     [discard-button
      {:on-click on-discard
       :tooltip  discard-tooltip}])
   (when (and logged-in? (not editing?))
     [edit-button
      {:disabled (and editing? (not valid?))
       :active?  editing?
       :on-click #(if editing?
                    (on-edit-end %)
                    (on-edit-start %))
       :tooltip  edit-tooltip}])])

(defn checkbox [{:keys [label value on-change disabled style]}]
  [mui/form-control-label
   {:label   label
    :style   style
    :control (r/as-element
              [mui/checkbox
               {:value     (str (boolean value))
                :checked   (boolean value)
                :disabled  disabled
                :on-change #(on-change %2)}])}]) ; %2 = checked?

(defn link? [x]
  (and (string? x)
       (or
        (string/starts-with? x "http")
        (string/starts-with? x "www"))))

(defn truncate [s]
  (if (> (count s) 30)
    (str (subs s 0 27) "...")
    s))

(defn display-value [v & {:keys [empty links?]
                          :or   {empty  ""
                                 links? true}}]
  (cond
    (link? v) (if links? [:a {:href v} (truncate v)] v)
    (coll? v) (if (empty? v) empty (string/join ", " v))
    (true? v) CHECK_MARK
    (nil? v)  empty
    :else     v))

(defn table [{:keys [headers items on-select key-fn sort-fn sort-asc? sort-cmp]
              :or   {sort-cmp  compare
                     sort-asc? false}}]
  (r/with-let [key-fn*   (or key-fn (constantly nil))
               sort-fn*  (r/atom sort-fn)
               sort-asc? (r/atom sort-asc?)]

    [mui/grid {:container true}
     [mui/grid {:item true :xs 12}
      [:div {:style {:overflow-x "auto"}} ; Fixes overflow outside screen

       [mui/table

        ;; Head
        [mui/table-head
         (into [mui/table-row (when on-select
                                [mui/table-cell ""])]
               (for [[key header] headers]
                 [mui/table-cell {:on-click #(reset! sort-fn* key)}
                  [mui/table-sort-label
                   {:active    (= key @sort-fn*)
                    :direction (if @sort-asc? "asc" "desc")
                    :on-click  #(swap! sort-asc? not)}
                   header]]))]

        ;; Body
        [mui/table-body

         ;; Rows
         (for [item (if @sort-fn*
                      (sort-by @sort-fn* (if @sort-asc?
                                           utils/reverse-cmp
                                           sort-cmp)
                               items)
                      items)
               :let [id (or (key-fn* item) (:id item) (:lipas-id item) (gensym))]]
           [mui/table-row {:key      id
                           :on-click (when on-select #(on-select item))
                           :hover    true}
            (when on-select
              [mui/table-cell {:padding "checkbox"}
               [mui/icon-button {:on-click #(on-select item)}
                [mui/icon {:color "primary"} "more_horiz"]]])

            ;; Cells
            (for [[k _] headers
                  :let  [v (get item k)]]
              [mui/table-cell {:key (str id k)}
               [mui/typography {:no-wrap false}
                (display-value v)]])])]]]]]))

(defn form-table [{:keys [headers items key-fn add-tooltip
                          edit-tooltip delete-tooltip confirm-tooltip
                          read-only? on-add on-edit on-delete]
                   :as props}]
  (if read-only?

    ;; Normal read-only table
    [table props]

    ;; Table with selectable rows and 'edit' 'delete' and 'add'
    ;; actions
    (r/with-let [selected-item (r/atom nil)
                 key-fn (or key-fn (constantly nil))]
      [mui/grid {:container   true
                 :spacing     8
                 :justify     "flex-end"
                 :align-items "center"}

       ;; Table
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
              [mui/table-row {:key   id
                              :hover true}
               [mui/table-cell {:padding "checkbox"}
                [mui/checkbox {:checked   (= item @selected-item)
                               :on-change (fn [_ checked?]
                                            (let [v (when checked? item)]
                                              (reset! selected-item v)))}]]
               (for [[k _] headers
                     :let  [v (get item k)]]
                 [mui/table-cell {:key     (str id k)
                                  :padding "dense"}
                  (display-value v)])]))]]]]

       ;; Editing tools
       [mui/grid {:item true :xs 10}

        ;; Edit button
        (when @selected-item
          [mui/tooltip {:title     (or edit-tooltip "")
                        :placement "top"}
           [mui/icon-button {:on-click #(on-edit @selected-item)}
            [mui/icon "edit"]]])

        ;; Delete button
        (when @selected-item
          [confirming-delete-button
           {:tooltip         delete-tooltip
            :confirm-tooltip confirm-tooltip
            :on-delete       #(do
                                (on-delete @selected-item)
                                (reset! selected-item nil))}])]

       ;; Add button
       [mui/grid {:item  true :xs 2
                  :style {:text-align "right"}}
        [mui/tooltip {:title     (or add-tooltip "")
                      :placement "left"}
         [mui/button {:style    {:margin-top "1em"}
                      :on-click on-add
                      :variant  "fab"
                      :color    "secondary"}
          [mui/icon "add"]]]]])))

(defn dialog [{:keys [title
                      on-save
                      on-close
                      save-label
                      save-enabled?
                      cancel-label]} content]
  [mui/dialog {:open true
               :full-width true
               :on-close on-close}
   [mui/dialog-title title]
   [mui/dialog-content content]
   [mui/dialog-actions
    [mui/button {:on-click on-close}
     cancel-label]
    [mui/button {:on-click on-save
                 :disabled (not save-enabled?)}
     save-label]]])

(defn form-card [{:keys [title xs md lg]
                  :or   {xs 12 md 6}} & content]
  [mui/grid {:item true
             :xs   xs
             :md   md
             :lg   lg}
   [mui/card {:square true
              :style  {:height "100%"}}
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
    (-> s
        (string/replace "," ".")
        (string/replace #"[^\d.]" "")
        (as-> $ (if (or (string/ends-with? $ ".")
                        (string/ends-with? $ ".0"))
                  $
                  (read-string $))))
    (not-empty s)))

(defn patched-input [props]
  [:input (dissoc props :inputRef)])

(defn text-field-controlled [{:keys [value type on-change spec required
                                     Input-props adornment]
                              :as   props} & children]
  (let [props (-> props
                  (as-> $ (if (= "number" type) (dissoc $ :type) $))
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

(defn select [{:keys [label value items on-change value-fn label-fn
                      sort-fn sort-cmp deselect?]
               :or   {value-fn :value
                      label-fn :label
                      sort-cmp compare}
               :as   props}]
  (let [on-change #(on-change (-> %
                                  .-target
                                  .-value
                                  read-string
                                  (as-> $ (if (and deselect? (= $ value))
                                            nil ; toggle
                                            $))))
        props   (-> props
                    (dissoc :value-fn :label-fn :label :sort-fn :sort-cmp
                            :deselect?)
                    ;; Following fixes Chrome scroll issue
                    ;; https://github.com/mui-org/material-ui/pull/12003
                    (assoc :MenuProps
                           {:PaperProps
                            {:style
                             {:transform "translate2(0)"}}})
                    (assoc :value (if value (pr-str value) ""))
                    (assoc :on-change on-change))
        sort-fn (or sort-fn label-fn)]
    [mui/form-control
     (when label [mui/input-label label])
     (into [mui/select props]
           (for [i (sort-by sort-fn sort-cmp items)]
             (let [value (value-fn i)
                   label (label-fn i)]
               [mui/menu-item {:key   (pr-str value)
                               :value (pr-str value)}
                label])))]))

(defn multi-select [{:keys [label value items on-change value-fn
                            label-fn sort-fn sort-cmp]
                     :or   {value-fn :value
                            label-fn :label
                            sort-cmp compare}
                     :as   props}]
  (let [sort-fn (or sort-fn label-fn)]
    [mui/form-control
     (when label [mui/input-label label])
     [mui/select
      (merge (dissoc props :label :value-fn :label-fn :sort-fn :sort-cmp)
             {:multiple  true
              :value     (map pr-str value)
              :on-change #(on-change (->> %
                                          .-target
                                          .-value
                                          (map read-string)
                                          not-empty))})
      (for [i (sort-by sort-fn sort-cmp items)]
        [mui/menu-item
         {:key   (pr-str (value-fn i))
          :value (pr-str (value-fn i))}
         (label-fn i)])]]))

(defn year-selector [{:keys [label value on-change required years multi?]
                      :as   props}]
  (let [years     (or years (range 1900 (inc (.getFullYear (js/Date.)))))
        component (if multi? multi-select select)]
    [component (merge (dissoc props :multi?)
                      {:label     label
                       :items     (map #(hash-map :label % :value %) years)
                       :on-change on-change
                       :sort-cmp  utils/reverse-cmp
                       :value     value
                       :required  required})]))

(defn number-selector [{:keys [value on-change items unit label deselect?]}]
  [select
   {:label     label
    :items     items
    :sort-fn   identity
    :sort-cmp  utils/reverse-cmp
    :value-fn  identity
    :label-fn  #(str % unit)
    :value     value
    :deselect? deselect?
    :on-change on-change}])

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
            [mui/table-cell
             [mui/typography {:variant "caption"}
              (first row)]]
            [mui/table-cell (-> row second display-value)]]))])

(defn table-form [{:keys [read-only?]} & fields]
  [:div {:style {:overflow-x "auto"
                 :max-width  "600px"}}
   [mui/table
    (into [mui/table-body]
          (for [row  (remove nil? fields)
                :let [{:keys [label value form-field]} row]]
            [mui/table-row
             [mui/table-cell
              [mui/typography {:variant "caption"}
               label]]
             [mui/table-cell {:numeric true
                              :style   {:text-overflow :ellipsis}}
              (if read-only?
                (display-value value)
                [mui/form-group
                 form-field])]]))]])

(defn ->display-tf [{:keys [label value]}]
  (let [value (display-value value :empty "-" :links? false)]
    [text-field {:label     label
                 :value     value
                 :disabled  true}]))

(defn form-trad [{:keys [read-only?]} & data]
  (into [mui/form-group]
        (for [d     data
              :when (some? d)
              :let  [field (-> d :form-field)
                     props (-> field second)]]
          (if read-only?
            (->display-tf d)
            (assoc field 1 (assoc props :label (:label d)))))))

;; (def form table-form)
(def form form-trad)

(defn sports-site-form [{:keys [tr display-data edit-data types size-categories
                                admins owners on-change read-only?]}]
  (let [locale (tr)]
    [form {:read-only? read-only?}

     ;; Name
     {:label      (tr :lipas.sports-site/name)
      :value      (-> display-data :name)
      :form-field [text-field
                   {:spec      :lipas.sports-site/name
                    :required  true
                    :value     (-> edit-data :name)
                    :on-change #(on-change :name %)}]}

     ;; Marketing name
     {:label      (tr :lipas.sports-site/marketing-name)
      :value      (-> display-data :marketing-name)
      :form-field [text-field
                   {:spec      :lipas.sports-site/marketing-name
                    :value     (-> edit-data :marketing-name)
                    :on-change #(on-change :marketing-name %)}]}

     ;; Type
     {:label      (tr :type/name)
      :value      (-> display-data :type :name)
      :form-field [select
                   {:value     (-> edit-data :type :type-code)
                    :required  true
                    :items     types
                    :label-fn  (comp locale :name)
                    :value-fn  :type-code
                    :on-change #(on-change :type :type-code %)}]}

     ;; Ice-stadiums get special treatment
     (when (or (= 2520 (-> edit-data :type :type-code))
               (and read-only?
                    (= 2520 (-> display-data :type :type-code))))
       {:label      (tr :ice/size-category)
        :value      (-> display-data :type :size-category)
        :form-field [select
                     {:value     (-> edit-data :type :size-category)
                      :items     size-categories
                      :value-fn  first
                      :label-fn  (comp locale second)
                      :on-change #(on-change :type :size-category %)}]})

     ;; Owner
     {:label      (tr :lipas.sports-site/owner)
      :value      (-> display-data :owner)
      :form-field [select
                   {:value     (-> edit-data :owner)
                    :required  true
                    :items     owners
                    :value-fn  first
                    :label-fn  (comp locale second)
                    :on-change #(on-change :owner %)}]}

     ;; Admin
     {:label      (tr :lipas.sports-site/admin)
      :value      (-> display-data :admin)
      :form-field [select
                   {:value     (-> edit-data :admin)
                    :required  true
                    :items     admins
                    :value-fn  first
                    :label-fn  (comp locale second)
                    :on-change #(on-change :admin %)}]}

     ;; Construction year
     {:label (tr :lipas.sports-site/construction-year)
      :value (-> display-data :construction-year)
      :form-field [year-selector
                   {:value     (-> edit-data :construction-year)
                    :on-change #(on-change :construction-year %)}]}

     ;; Renovation years
     {:label (tr :lipas.sports-site/renovation-years)
      :value (-> display-data :renovation-years)
      :form-field [year-selector
                   {:multi?    true
                    :value     (-> edit-data :renovation-years)
                    :on-change #(on-change :renovation-years %)}]}

     ;; Phone number
     {:label      (tr :lipas.sports-site/phone-number)
      :value      (-> display-data :phone-number)
      :form-field [text-field
                   {:value     (-> edit-data :phone-number)
                    :spec      :lipas.sports-site/phone-number
                    :on-change #(on-change :phone-number %)}]}

     ;; WWW
     {:label      (tr :lipas.sports-site/www)
      :value      (-> display-data :www)
      :form-field [text-field
                   {:value     (-> edit-data :www)
                    :spec      :lipas.sports-site/www
                    :on-change #(on-change :www %)}]}

     ;; Email
     {:label      (tr :lipas.sports-site/email-public)
      :value      (-> display-data :email)
      :form-field [text-field
                   {:value     (-> edit-data :email)
                    :spec      :lipas.sports-site/email
                    :on-change #(on-change :email %)}]}]))

(defn location-form [{:keys [tr edit-data display-data cities on-change
                             read-only?]}]
  (let [locale (tr)]
    [form
     {:read-only? read-only?}

     ;; Address
     {:label      (tr :lipas.location/address)
      :value      (-> display-data :address)
      :form-field [text-field
                   {:value     (-> edit-data :address)
                    :spec      :lipas.location/address
                    :required  true
                    :on-change #(on-change :address %)}]}

     ;; Postal code
     { :label     (tr :lipas.location/postal-code)
      :value      (-> display-data :postal-code)
      :form-field [text-field
                   {:value     (-> edit-data :postal-code)
                    :required  true
                    :spec      :lipas.location/postal-code
                    :on-change #(on-change :postal-code %)}]}

     ;; Postal office
     {:label      (tr :lipas.location/postal-office)
      :value      (-> display-data :postal-office)
      :form-field [text-field
                   { :value    (-> edit-data :postal-office)
                    :spec      :lipas.location/postal-office
                    :on-change #(on-change :postal-office %)}]}

     ;; City
     {:label      (tr :lipas.location/city)
      :value      (-> display-data :city :name)
      :form-field [select
                   {:value     (-> edit-data :city :city-code)
                    :required  true
                    :items     cities
                    :label-fn  (comp locale :name)
                    :value-fn  :city-code
                    :on-change #(on-change :city :city-code %)}]}

     ;; Neighborhood
     {:label      (tr :lipas.location/neighborhood)
      :value      (-> display-data :city :neighborhood)
      :form-field [text-field
                   {:value    (-> edit-data :city :neighborhood)
                    :spec      :lipas.location.city/neighborhood
                    :on-change #(on-change :city :neighborhood %)}]}]))

(defn expansion-panel [{:keys [label]} & children]
  [mui/expansion-panel {:style {:margin-top "1em"}}
   [mui/expansion-panel-summary {:expand-icon (r/as-element
                                               [mui/icon "expand_more"])}
    [mui/typography {:color "primary"
                     :variant "button"}
     label]]
   (into [mui/expansion-panel-details]
         children)])

(defn full-screen-dialog [{:keys [open? title on-close close-label top-actions
                                  bottom-actions]}
                          & contents]
  [mui/dialog {:open                 open?
               :full-screen          true
               :Transition-component (r/reactify-component slide)
               :Transition-props     {:direction "up"}
               :on-close             on-close
               :Paper-Props          {:style {:background-color mui/gray1}}}

   ;; Top bar
   [mui/mui-theme-provider {:theme mui/jyu-theme-dark}
    (into
     [mui/dialog-actions {:style
                          {:margin           0
                           :padding-right    "0.5em"
                           :background-color mui/primary}}
      [mui/dialog-title {:style {:flex-grow 1}}
       (or title "")]]
     top-actions)]

   ;; Content
   (into [mui/dialog-content {:style {:padding 8}}]
         contents)

   ;; Bottom bar
   [mui/mui-theme-provider {:theme mui/jyu-theme-dark}
    (conj
     (into
      [mui/dialog-actions
       {:style {:margin           0
                :background-color mui/secondary2}}]
      bottom-actions)
     [mui/button {:on-click on-close}
      close-label])]])

(defn confirmation-dialog [{:keys [title message on-cancel on-decline
                                   decline-label cancel-label
                                   on-confirm confirm-label]}]
  [mui/dialog {:open                    true
               :disable-backdrop-click  true
               :disable-escape-key-down true}
   [mui/dialog-title title]
   [mui/dialog-content
    [mui/typography message]]
   [mui/dialog-actions
    [mui/button {:on-click on-cancel} cancel-label]
    (when on-decline
      [mui/button {:on-click on-decline} decline-label])
    [mui/button {:on-click on-confirm} confirm-label]]])

(defn li [text & children]
  (into
   [:li [mui/typography {:variant :body2
                         :color   :default}
         text]]
   children))


(defn simple-matches [items label-fn s]
  (->> items
       (filter #(string/includes?
                 (string/lower-case (label-fn %))
                 (string/lower-case s)))))

(defn js->clj* [x]
  (js->clj x :keywordize-keys true))

(defn ac-hack-input [props]
  (let [props (js->clj* props)
        ref   (:ref props)
        label (:label props)]
    (r/as-element
     [mui/text-field
      {:label      label
       :inputRef   ref
       :InputProps (dissoc props :ref :label)}])))

(defn ac-hack-container [opts]
  (r/as-element
   [mui/paper (merge (js->clj* (gobj/get opts "containerProps")))
    (gobj/get opts "children")]))

(defn ac-hack-item [label-fn item]
  (r/as-element
   [mui/menu-item {:component "div"}
    (-> item
        (js->clj*)
        label-fn)]))

(defn autocomplete [{:keys [label items value value-fn label-fn
                            suggestion-fn on-change]
                     :or   {suggestion-fn (partial simple-matches items label-fn)
                            label-fn      :label
                            value-fn      :value}}]

  (r/with-let [items-m     (utils/index-by value-fn items)
               id          (r/atom (gensym))
               value       (r/atom (or value []))
               input-value (r/atom "")
               suggs       (r/atom items)]

    [mui/grid {:container true}

     ;; Input field
     [mui/grid {:item true}
      [:> autosuggest
       {:id                          @id
        :suggestions                 @suggs
        :getSuggestionValue          #(label-fn (js->clj* %1))

        :onSuggestionsFetchRequested #(reset! suggs (suggestion-fn
                                                     (gobj/get % "value")))

        :onSuggestionsClearRequested #(reset! suggs [])
        :renderSuggestion            (partial ac-hack-item label-fn)
        :renderSuggestionsContainer  ac-hack-container

        :onSuggestionSelected        #(let [v (-> %2
                                                  (gobj/get "suggestion")
                                                  js->clj*)]
                                        (swap! value conj (value-fn v))
                                        (on-change @value)
                                        (reset! input-value ""))

        :renderInputComponent        ac-hack-input
        :inputProps                  {:label    (or label "")
                                      :value    (or @input-value "")

                                      :onChange #(reset! input-value
                                                         (gobj/get %2 "newValue"))}

        :theme                       {:suggestionsList
                                      {:list-style-type "none"
                                       :padding         0
                                       :margin          0}}}]]

     ;; Selected values chips
     (into
      [mui/grid {:item  true
                 :style {:margin-top :auto}}]
      (for [v @value]
        [mui/chip
         {:label     (label-fn (get items-m v))
          :on-delete #(do (swap! value (fn [old-value]
                                         (into (empty old-value)
                                               (remove #{v} old-value))))
                          (on-change @value))}]))]))

(defn icon-text [{:keys [icon text icon-color]}]
  [mui/grid {:container true :align-items :center
             :style     {:padding "0.5em"}}
   [mui/grid {:item true}
    [mui/icon {:color (or icon-color "inherit")}
     icon]]
   [mui/grid {:item true}
    [mui/typography {:variant :body2
                     :style   {:margin-left "0.5em"
                               :display     :inline}}
     text]]])
