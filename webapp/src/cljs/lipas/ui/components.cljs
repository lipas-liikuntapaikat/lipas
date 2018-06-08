(ns lipas.ui.components
  (:require [cljsjs.react-select]
            [clojure.reader :refer [read-string]]
            [clojure.spec.alpha :as s]
            [lipas.schema.core :as schema]
            [clojure.string :refer [trim]]
            [lipas.ui.mui :as mui]
            [lipas.ui.mui-icons :as mui-icons]
            [reagent.core :as r]))

(defn checkbox [{:keys [label value on-change]}]
  [mui/form-control-label
   {:label label
    :control (r/as-element
              [mui/checkbox
               {:value (or (str value) "-")
                :checked value
                :on-change #(on-change %2)}])}]) ; %2 = checked?

(defn form-table [{:keys [headers
                          items
                          add-tooltip
                          edit-tooltip
                          delete-tooltip
                          on-add
                          on-edit
                          on-delete]}]
  [mui/grid {:container true
             :spacing 8
             :justify "flex-end"}
   [mui/grid {:item true :xs 12}
    [:div {:style {:overflow-x "auto"}}
     [mui/table
      [mui/table-head
       [mui/table-row
        (for [[idx [_ header]] (map-indexed vector headers)]
          ^{:key idx} [mui/table-cell header])
        [mui/table-cell ""]]]
      [mui/table-body
       (for [[idx item] (map-indexed vector items)]
         ^{:key idx} [mui/table-row
                      (for [[k _] headers]
                        ^{:key k} [mui/table-cell {:padding "dense"}
                                   (let [v (get item k)]
                                     (cond
                                       (true? v) "✓"
                                       :else v))])
                      [mui/table-cell {:numeric true
                                       :padding "none"}
                       [mui/tooltip {:title (or edit-tooltip "")
                                     :placement "top"}
                        [mui/icon-button {:on-click #(on-edit item)}
                         [mui-icons/edit]]]
                       [mui/tooltip {:title (or delete-tooltip "")
                                     :placement "top"}
                        [mui/icon-button {:on-click #(on-delete item)}
                         [mui-icons/delete]]]]])]]]]
   [mui/grid {:item true
              :xs 2
              :style {:text-align "right"} }
    [mui/tooltip {:title (or add-tooltip "")
                  :placement "left"}
     [mui/button {:style {:margin-top "0.5em"}
                  :on-click on-add
                  :variant "fab"
                  :color "secondary"}
      [mui-icons/add]]]]])

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

(defn form-card [{:keys [title]} content]
  [mui/grid {:item true
             :xs 12
             :md 12}
   [mui/card {:square true
              :style {:height "100%"}}
    [mui/card-header {:title title}]
    [mui/card-content content]]])

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
                                      [mui-icons/done]
                                      [mui-icons/warning])])}]])

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

(defn text-field [{:keys [value type adornment Input-props
                          on-change spec required]
                   :as props} & children]
  (r/with-let [state (r/atom value)]
    (let [props (-> props
                    (assoc :default-value (or value ""))
                    (dissoc :value)
                    (assoc :error (error? spec @state required))
                    (assoc :Input-props (merge Input-props
                                               (when adornment
                                                 (->adornment adornment))))
                    (assoc :on-change (fn [e]
                                        (swap! state
                                               #(->> e
                                                    .-target
                                                    .-value
                                                    (coerce type)))))
                    (assoc :on-blur #(on-change (trim-safe @state))))]
      (into [mui/text-field props] children))))

(defn text-field-controlled [{:keys [value type on-change]
                              :as props} & children]
  (let [props (-> props
                  (assoc :value (or value ""))
                  (assoc :on-change #(->> %
                                          .-target
                                          .-value
                                          (coerce type)
                                          on-change))
                  (assoc :on-blur #(when (string? value)
                                     (on-change (trim value)))))]
    (into [mui/text-field props] children)))

(defn select [{:keys [label value items on-change required]}]
  [text-field-controlled {:select true
                          :required required
                          :label label
                          :value (pr-str value)
                          :on-change #(on-change (read-string %))}
   (for [{:keys [label value]} (sort-by :label items)]
     [mui/menu-item {:key value :value (pr-str value)}
      label])])

(def react-select (r/adapt-react-class js/Select))


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

(defn multi-select [{:keys [label value items on-change]}]
  (r/with-let [state (r/atom (map (partial str-field :value)
                                  (find-by-vals :value items value)))]
    [mui/text-field
     {:label label
      :Input-label-props
      {:shrink true} ; This makes the label show actually
      :Input-props
      {:input-component js/Select
       :input-props
       {:multi true
        :name label
        :placeholder label
        :value @state
        :on-change (fn [v] (swap! state #(identity v)))
        :on-blur #(on-change (extract-values @state))
        :options (map (partial str-field :value) items)}}}]))

(defn year-selector [{:keys [label value on-change required]}]
  (let [years (range 1900 (inc (.getFullYear (js/Date.))))]
    [select {:label label
             :items (map #(hash-map :label % :value %) years)
             :on-change on-change
             :value value
             :required required}]))

(defn ->select-entry [tr prefix enum]
  {:value enum
   :label (tr (keyword prefix enum))})

(defn date-picker [{:keys [label value on-change]}]
  [mui/text-field
   {:type "date"
    :label label
    :value value
    :Input-label-props
    {:shrink true} ; This makes the label show actually
    :on-change #(on-change (-> % .-target .-value))}])

(defn sports-place-form [{:keys [tr data types admins owners on-change]}]
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
     :items     (map #(hash-map :label (-> % :name :fi)
                                :value (-> % :type-code)) types)
     :on-change #(on-change :type :type-code %)}]

   ;; Ice-stadiums get special treatment
   (when (= 2520 (-> data :type :type-code))
     [select
      {:label     (tr :ice/size-category)
       :value     (-> data :type :size-category)
       :items     [{:value :small       :label (tr :ice/small)}
                   {:value :competition :label (tr :ice/competition)}
                   {:value :large       :label (tr :ice/large)}]
       :on-change #(on-change :type :size-category %)}])

   [select
    {:label     (tr :sports-place/owner)
     :value     (-> data :owner)
     :items     (map (partial ->select-entry tr :owner) owners)
     :on-change #(on-change :owner %)}]
   [select
    {:label     (tr :sports-place/admin)
     :value     (-> data :admin)
     :items     (map (partial ->select-entry tr :admin) admins)
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
     :on-change #(on-change :email %)}]])

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
       :items     (map #(hash-map :label (-> % :name locale)
                                  :value (-> % :city-code)) cities)
       :on-change #(on-change :city :city-code %)}]]))
