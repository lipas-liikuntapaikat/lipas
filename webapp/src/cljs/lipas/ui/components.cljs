(ns lipas.ui.components
  (:require [clojure.reader :refer [read-string]]
            [clojure.spec.alpha :as s]
            [clojure.string :refer [trim]]
            [lipas.schema.core :as schema]
            [lipas.ui.mui :as mui]
            [lipas.ui.utils :refer [resolve-year this-year index-by ->timestamp]]
            [reagent.core :as r]))

(def CHECK_MARK "✓")

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
     [mui/input-label label]
     (into [mui/select props]
           (for [i items]
             (let [value (value-fn i)
                   label (label-fn i)]
               [mui/menu-item {:key (pr-str value)
                               :value (pr-str value)}
                label])))]))

(defn extract-values [multi-select-state]
  (as-> multi-select-state $
    (js->clj $)
    (mapv #(read-string (get % "value")) $)))

(defn str-field [field m]
  (update m field pr-str))

(comment (def items [{:label "kissa", :value "kiskis"}
                     {:label "koira", :value "koirkoir"}]))
(comment (find-by-vals items ["kiskis"]))
(defn find-by-vals [field items vals]
  (let [lookup (reduce into {} (map #(hash-map (field %) %) items))]
    (map #(lookup %) vals)))

(defn multi-select [{:keys [label value items on-change] :as props}]
  [mui/form-control
   [mui/input-label label]
   [mui/select
    (merge (dissoc props :label)
           {:multiple true
            :value value
            :on-change #(on-change (-> % .-target .-value))})
    (for [i items]
      [mui/menu-item
       {:key (:value i)
        :value (:value i)}
       (:label i)])]])

(defn year-selector [{:keys [label value on-change required years] :as props}]
  (let [years (or years
                  (range 1900 (inc (.getFullYear (js/Date.)))))]
    [select (merge props {:label label
                          :items (map #(hash-map :label % :value %) years)
                          :on-change on-change
                          :value value
                          :required required})]))

(defn ->select-entry [tr prefix enum]
  {:value enum
   :label (tr (keyword prefix enum))})

(defn date-picker [{:keys [label value on-change]}]
  [mui/text-field
   {:type "date"
    :label label
    :value (or value "")
    :Input-label-props
    {:shrink true} ; This makes the label show actually
    :on-change #(on-change (-> % .-target .-value))}])


(defn site-selector [{:keys [locale label value on-change items required]}]
  [select {:label     label
           :required  required
           :value     (:lipas-id value)
           :items     (map #(hash-map :label (-> % :name locale)
                                      :value (-> % :lipas-id))
                           (map :latest (vals items)))
           :on-change #(when-let [site (get items %)]
                         (on-change site))}])

(defn rev-selector [{:keys [label value on-change items required template-fn]}]
  (let [revs-by-year (index-by (comp resolve-year :timestamp) items)
        years        (range 2000 (inc this-year))
        items        (for [y    years
                           :let [data-exists? (some #{y} (keys revs-by-year))]]
                       {:label (if data-exists?
                                 (str y " " CHECK_MARK)
                                 (str y))
                        :value y
                        :sort  (- y)})]
    [select {:label     label
             :items     (sort-by :sort items)
             :on-change #(when (not-empty (str %))
                           (on-change (or
                                       (get revs-by-year %)
                                       (template-fn (->timestamp %)))))
             :value     (-> value :timestamp resolve-year)
             :required  required}]))

(defn sports-place-form [{:keys [tr data types size-categories
                                 admins owners on-change]}]
  (let [locale (tr)]
    [mui/form-group
     [text-field
      {:label     (tr :sports-place/name-fi)
       :value     (-> data :name :fi)
       :spec      ::schema/name
       :required  true
       :on-change #(on-change :name :fi %)}]
     [text-field
      {:label     (tr :sports-place/name-se)
       :spec      ::schema/name
       :value     (-> data :name :se)
       :on-change #(on-change :name :se %)}]
     [text-field
      {:label     (tr :sports-place/name-en)
       :spec      ::schema/name
       :value     (-> data :name :en)
       :on-change #(on-change :name :en %)}]
     [select
      {:label     (tr :type/type-code)
       :value     (-> data :type :type-code)
       :items     types
       :label-fn  (comp locale :name)
       :value-fn  :type-code
       :on-change #(on-change :type :type-code %)}]

     ;; Ice-stadiums get special treatment
     (when (= 2520 (-> data :type :type-code))
       [select
        {:label     (tr :ice/size-category)
         :value     (-> data :type :size-category)
         :items     size-categories
         :value-fn  first
         :label-fn  (comp locale second)
         :on-change #(on-change :type :size-category %)}])

     [select
      {:label     (tr :sports-place/owner)
       :value     (-> data :owner)
       :items     owners
       :value-fn  first
       :label-fn  (comp locale second)
       :on-change #(on-change :owner %)}]
     [select
      {:label     (tr :sports-place/admin)
       :value     (-> data :admin)
       :items     admins
       :value-fn  first
       :label-fn  (comp locale second)
       :on-change #(on-change :admin %)}]
     [text-field
      {:label     (tr :sports-place/phone-number)
       :value     (-> data :phone-number)
       :spec      ::schema/phone-number
       :on-change #(on-change :phone-number %)}]
     [text-field
      {:label     (tr :sports-place/www)
       :value     (-> data :www)
       :spec      ::schema/www
       :on-change #(on-change :www %)}]
     [text-field
      {:label     (tr :sports-place/email-public)
       :value     (-> data :email)
       :spec      ::schema/email
       :on-change #(on-change :email %)}]]))

(defn location-form [{:keys [tr data cities on-change]}]
  (let [locale (tr)]
    [mui/form-group
     [text-field
      {:label     (tr :location/address)
       :value     (-> data :address)
       :spec      ::schema/address
       :on-change #(on-change :address %)}]
     [text-field
      {:label     (tr :location/postal-code)
       :value     (-> data :postal-code)
       :spec      ::schema/postal-code
       :on-change #(on-change :postal-code %)}]
     [text-field
      {:label     (tr :location/postal-office)
       :value     (-> data :postal-office)
       :spec      ::schema/postal-office
       :on-change #(on-change :postal-office %)}]
     [select
      {:label     (tr :location/city)
       :value     (-> data :city :city-code)
       :items     cities
       :label-fn  (comp locale :name)
       :value-fn  :city-code
       :on-change #(on-change :city :city-code %)}]]))

(defn expansion-panel [{:keys [label]} & children]
  [mui/expansion-panel {:style {:margin-top "1em"}}
   [mui/expansion-panel-summary {:expand-icon (r/as-element
                                               [mui/icon "expand_more"])}
    [mui/typography {:color "primary"
                     :variant "button"}
     label]]
   (into [mui/expansion-panel-details]
         children)])
