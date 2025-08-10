(ns lipas.ui.sports-sites.activities.views
  (:require ["@mui/material/Alert$default" :as Alert]
            [clojure.pprint :as pprint]
            [clojure.string :as str]
            [lipas.ui.components :as lui]
            [lipas.ui.components.buttons :as lui-btn]
            [lipas.ui.components.forms :refer [->display-tf]]
            [lipas.ui.components.text-fields :as lui-tf]
            [lipas.ui.config :as config]
            [lipas.ui.mui :as mui]
            [lipas.ui.sports-sites.activities.events :as events]
            [lipas.ui.sports-sites.activities.subs :as subs]
            [lipas.ui.sports-sites.subs :as sports-sites-subs]
            [lipas.ui.sports-sites.views :as sports-sites-views]
            [lipas.ui.utils :refer [<== ==>] :as utils]
            [re-frame.core :as rf]
            [reagent.core :as r]
            [uix.core :as uix :refer [$]]))

(declare make-field)

(defn set-field*
  [lipas-id & args]
  (==> [:lipas.ui.sports-sites.events/edit-field lipas-id (butlast args) (last args)]))

(defn nice-form
  [props children]
  [mui/grid {:container true :spacing 2 :sx #js{:pt 2}}
   (doall
    (for [[idx child] (map vector (range) children)]
      [mui/grid {:item true :xs 12 :key (str "item-" idx)} child]))])

(defn form-label
  [{:keys [label]}]
  [mui/form-label {:style {:color "gray"}}
   label])

(defn lang-selector
  [{:keys [locale]}]
  [mui/tabs
   {:value (name locale)
    :indicator-color "primary"
    :text-color "inherit"
    :on-change #(==> [:lipas.ui.events/set-translator (keyword %2)])}
   [mui/tab {:value "fi" :label "Suomi"}]
   [mui/tab {:value "se" :label "Svenska"}]
   [mui/tab {:value "en" :label "English"}]])

(defn checkbox
  [{:keys [read-only? label helper-text on-change value
           component]
    :or {component lui/checkbox}}]
  [mui/grid {:container true :spacing 2}

   ;; Label
   [mui/grid {:item true :xs 12}
    [form-label {:label label}]]

   ;; Helper text
   [mui/grid {:item true :xs 12 :style {:margin-top "-0.5em"}}
    [mui/form-helper-text helper-text]]

   ;; Chekbox
   [mui/grid {:item true :xs 12}
    [component
     {:label label
      :value value
      :disabled read-only?
      :on-change on-change}]]])

(defn checkboxes
  [{:keys [read-only? items label helper-text label-fn value-fn
           on-change value sort-fn caption-fn component]
    :or {value-fn identity
         component lui/switch}}]
  (let [vs (set value)]
    [mui/grid {:container true :spacing 2}

     ;; Label
     (when label
       [mui/grid {:item true :xs 12}
        [form-label {:label label}]])

     ;; Helper text
     [mui/grid {:item true :xs 12 :style {:margin-top "-0.5em"}}
      [mui/form-helper-text helper-text]]

     ;; Chekboxes
     [:<>
      (for [item (if sort-fn (sort-by sort-fn items) items)]
        (let [[k _] item]
          ^{:key k}
          [:<>
           [mui/grid {:item true :xs 12 :key k}
            [component
             {:label (label-fn item)
              :value (contains? vs k)
              :disabled read-only?
              :on-change (fn [_]
                           (if (contains? vs k)
                             (on-change (disj vs k))
                             (on-change (conj vs k))))}]]
           (when-let [caption (not-empty (and caption-fn (caption-fn item)))]
             [mui/grid {:item true :xs 12 :style {:margin-top "-1.5em"
                                                  :padding-left "2.8em"}}
              [mui/typography {:variant "caption"} caption]])]))]]))

(defn contact-dialog
  [{:keys [tr locale description dialog-state on-save on-close contact-props]}]
  (let [field-sorter (<== [::subs/field-sorter :default])]
    [lui/dialog
     {:title (tr :utp/add-contact)
      :open? (:open? @dialog-state)
      :on-save on-save
      :on-close #(swap! dialog-state assoc :open? false)
      :save-enabled? true
      :save-label "Ok"
      :cancel-label (tr :actions/cancel)}

     [mui/grid {:container true :spacing 2}
      [mui/grid {:item true :xs 12}
       [lang-selector {:locale locale}]]
      (doall
       (for [[prop-k {:keys [field]}] (sort-by field-sorter utils/reverse-cmp contact-props)]
         [mui/grid
          {:key prop-k
           :item true
           :xs 12}
          [make-field
           {:field field
            :prop-k prop-k
            :edit-data (:data @dialog-state)
            :display-data (:data @dialog-state)
            :locale locale
            :set-field (fn [& args]
                         (let [path (into [:data] (butlast args))
                               v (last args)]
                           (swap! dialog-state assoc-in path v)))}]]))]]))

(defn contacts
  [{:keys [read-only? lipas-id locale label description set-field
           value contact-props]}]
  (r/with-let [state (r/atom (->> value
                                  (map #(assoc % :id (gensym)))
                                  (utils/index-by :id)))
               dialog-init-state {:open? false
                                  :data nil
                                  :mode :edit}
               dialog-state (r/atom dialog-init-state)]
    (let [tr (<== [:lipas.ui.subs/translator])]

      ;; Dialog
      [mui/grid {:container true :spacing 2}
       [contact-dialog
        {:tr tr
         :locale locale
         :description description
         :dialog-state dialog-state
         :contact-props contact-props
         :on-save (fn []
                    (let [data (:data @dialog-state)]
                      (swap! state assoc (:id data) data))
                    (set-field (->> @state
                                    vals
                                    (mapv #(dissoc % :id))))
                    (reset! dialog-state dialog-init-state))}]

       ;; Label
       [mui/grid {:item true :xs 12}
        [form-label {:label label}]]

       ;; Description
       [mui/grid {:item true :xs 12}
        [mui/typography {:variant "caption"} description]]

       ;; Table
       [mui/grid {:item true :xs 12}
        [lui/form-table
         {:key (str (count (vals @state)))
          :headers [[:_organization (get-in contact-props [:organization :field :label locale])]
                    [:_role (get-in contact-props [:role :field :label locale])]]
          :hide-header-row? false
          :read-only? read-only?
          :items (->> @state
                      vals
                      (map #(assoc % :_organization (get-in % [:organization locale])))
                      (map #(assoc % :_role (->> %
                                                 :role
                                                 (map
                                                  (fn [role]
                                                    (get-in contact-props [:role :field :opts role locale])))
                                                 (str/join ", ")))))
          :on-add (fn []
                    (reset! dialog-state {:open? true
                                          :mode :add
                                          :data {:id (gensym)}}))
          :on-edit (fn [m]
                     (reset! dialog-state {:open? true
                                           :mode :edit
                                           :data (get @state (:id m))}))
          :on-delete (fn [m]
                       (swap! state dissoc (:id m))
                       (set-field (->> @state
                                       vals
                                       (mapv #(dissoc % :id)))))
          :add-tooltip (tr :actions/add)
          :edit-tooltip (tr :actions/edit)
          :delete-tooltip (tr :actions/delete)
          :confirm-tooltip (tr :confirm/delete-confirm)
          :add-btn-size "small"}]]

       ;; Debug
       (when config/debug?
         [mui/grid {:item true :xs 12}
          [lui/expansion-panel {:label "debug"}
           [:pre (with-out-str (pprint/pprint contact-props))]]])])))

(defn accessibility
  [{:keys [read-only? lipas-id locale label description set-field
           value accessibility-props]}]

  [mui/grid {:container true}

   ;; Label
   [mui/grid {:item true :xs 12}
    [form-label {:label label}]]

   ;; Description
   [mui/grid {:item true :xs 12}
    [mui/typography {:variant "caption"} description]]

   ;; Expansion panels for each accessibility category
   (for [[prop-k {:keys [field]}] accessibility-props]
     [mui/grid
      {:key prop-k
       :item true :xs 12}
      [lui/expansion-panel
       {:label (get-in field [:label locale])
        :default-expanded false}
       (if read-only?
         [->display-tf
          {:label (get-in field [:description locale])
           :mui-props {:fullWidth true}
           :value (get-in value [prop-k locale])}
          true
          5]
         [lui/text-field
          {:label (get-in field [:description locale])
           :multiline true
           :rows 5
           :fullWidth true
           :variant "outlined"
           :on-change #(set-field prop-k locale %)
           :value (get-in value [prop-k locale])}])]])])

(defn duration
  [{:keys [read-only? locale label description set-field value]}]
  (let [tr (<== [:lipas.ui.subs/translator])]
    [mui/form-control {:focused true}
     [form-label {:label label}]

     [mui/grid {:container true :spacing 2}

      [mui/grid {:item true :xs 12}
       [mui/typography {:variant "caption"} description]]

      [mui/grid {:item true :xs 3}
       [lui/text-field
        {:type "number"
         :value (:min value)
         :label "Min"
         :on-change #(set-field :min %)
         :disabled read-only?}]]

      [mui/grid {:item true :xs 3}
       [lui/text-field
        {:type "number"
         :value (:max value)
         :label "Max"
         :on-change #(set-field :max %)
         :disabled read-only?}]]

      [mui/grid {:item true :xs 6}
       [lui/select
        {:disabled read-only?
         :items [{:label {:fi "minuuttia" :se "minuter" :en "minutes"}
                  :value "minutes"
                  :sort 1}
                 {:label {:fi "tuntia" :se "timmar" :en "hours"}
                  :value "hours"
                  :sort 2}
                 {:label {:fi "päivää" :se "dagar" :en "days"}
                  :value "days"
                  :sort 3}]
         :sort-fn :sort
         :label-fn (comp locale :label)
         :style {:min-width "170px"}
         :value (:unit value)
         :label (tr :utp/unit)
         :on-change #(set-field :unit %)}]]]]))

(defn textlist-dialog
  [{:keys [tr locale dialog-state on-save on-close lipas-id label description]}]
  [lui/dialog
   {:title (tr :utp/add-highlight)
    :open? (:open? @dialog-state)
    :on-save on-save
    :on-close #(swap! dialog-state assoc :open? false)
    :save-enabled? true
    :save-label "OK"
    :cancel-label (tr :actions/cancel)}

   [mui/grid {:container true :spacing 2}
    [mui/grid {:item true :xs 12}
     [lang-selector {:locale locale}]]

    #_[mui/grid {:item true :xs 12}
       [mui/paper {:style {:padding "0.5em" :background-color mui/gray3}}
        [mui/typography description]]]

    [mui/grid {:item true :xs 12}
     [mui/grid {:item true :xs 12}
      ;; FIXME: MUI-v5 input height or paddings are wrong
      [lui/text-field
       {:fullWidth true
        :required true
        :helper-text description
        :value (-> @dialog-state :data locale)
        :on-change #(swap! dialog-state assoc-in [:data locale] %)
        :label (tr :utp/highlight)
        :variant "outlined"}]]]]])

(defn textlist
  [{:keys [read-only? locale label description set-field value]}]
  (r/with-let [state (r/atom (->> value
                                  (map #(assoc % :id (gensym)))
                                  (utils/index-by :id)))
               dialog-init-state {:open? false
                                  :data nil
                                  :mode :edit}
               dialog-state (r/atom dialog-init-state)]
    (let [tr (<== [:lipas.ui.subs/translator])]

      ;; Dialog
      [mui/grid {:container true :spacing 2}
       [textlist-dialog
        {:tr tr
         :locale locale
         :label label
         :description description
         :dialog-state dialog-state
         :on-save (fn []
                    (let [data (:data @dialog-state)]
                      (swap! state assoc (:id data) data))
                    (set-field (->> @state
                                    vals
                                    (mapv #(dissoc % :id))))
                    (reset! dialog-state dialog-init-state))}]

       ;; Label
       [mui/grid {:item true :xs 12}
        [form-label {:label label}]]

       ;; Description
       [mui/grid {:item true :xs 12}
        [mui/typography {:variant "caption"}] description]

       ;; Table
       [mui/grid {:item true :xs 12}
        [lui/form-table
         {:key @state
          :headers [[locale label]]
          :hide-header-row? true
          :read-only? read-only?
          :items (->> @state vals)
          :on-dnd-end (fn [items]
                        (reset! state
                                (->> items
                                     (map #(assoc % :id (gensym)))
                                     (utils/index-by :id)))
                        (set-field (->> @state
                                        vals
                                        (mapv #(dissoc % :id)))))
          :on-add (fn []
                    (reset! dialog-state {:open? true
                                          :mode :add
                                          :data {:id (gensym)}}))
          :on-edit (fn [m]
                     (reset! dialog-state {:open? true
                                           :mode :edit
                                           :data (get @state (:id m))}))
          :on-delete (fn [m]
                       (swap! state dissoc (:id m))
                       (set-field (->> @state
                                       vals
                                       (mapv #(dissoc % :id)))))
          :on-user-sort (fn [items]
                          (set-field (mapv #(dissoc % :id) items)))
          :add-tooltip (tr :actions/add)
          :edit-tooltip (tr :actions/edit)
          :delete-tooltip (tr :actions/delete)
          :confirm-tooltip (tr :confirm/delete-confirm)
          :add-btn-size "small"}]]])))

(defn rules
  [{:keys [read-only? locale label description set-field value common-rules]}]
  (r/with-let [state (r/atom {:common-rules (:common-rules value)
                              :custom-rules-vs (->> value :custom-rules (map :value))
                              :custom-rules (or (when-let [coll (:custom-rules value)]
                                                  (utils/index-by :value coll))
                                                {})})
               dialog-init-state {:open? false
                                  :data nil
                                  :mode :edit}
               dialog-state (r/atom dialog-init-state)]
    (let [tr (<== [:lipas.ui.subs/translator])]

      ;; Dialog
      [mui/grid {:container true :spacing 2}
       [lui/dialog
        {:title label
         :open? (:open? @dialog-state)
         :on-save (fn []
                    (let [data (:data @dialog-state)
                          vs (map :value (vals data))]

                      ;; Update local state
                      (swap! state (fn [curr]
                                     (-> curr
                                         (assoc :custom-rules data)
                                         (assoc :custom-rules-vs vs))))

                      ;; Update app-db state
                      (set-field :custom-rules (vals data))

                      ;; Close the dialog
                      (swap! dialog-state assoc :open? false)))

         :on-close #(swap! dialog-state assoc :open? false)
         :save-enabled? true
         :save-label "OK"
         :cancel-label (tr :actions/cancel)}

        [mui/grid {:container true :spacing 2}

         ;; Lang selector
         [mui/grid {:item true :xs 12}
          [lang-selector {:locale locale}]]

         ;; Halper text
         [mui/grid {:item true :xs 12}
          [mui/paper {:style {:padding "0.5em" :background-color mui/gray3}}
           [mui/typography description]]]

         (doall
          (for [[idx k] (map-indexed vector (keys (:data @dialog-state)))]
            ^{:key k}
            [:<>
             [mui/grid {:item true :xs 12}
              [mui/typography (str (tr :utp/custom-rule) " " (inc idx))]]

             ;; Label
             [mui/grid {:item true :xs 12}
              [lui/text-field
               {:fullWidth true
                :required true
                #_#_:helper-text description
                :value (-> @dialog-state :data (get k) :label locale)
                :on-change #(swap! dialog-state assoc-in [:data k :label locale] %)
                :label (tr :general/headline)
                :variant "outlined"}]]

             ;; Description
             [mui/grid {:item true :xs 12}
              [lui/text-field
               {:fullWidth true
                :required true
                #_#_:helper-text description
                :value (-> @dialog-state :data (get k) :description locale)
                :on-change #(swap! dialog-state assoc-in [:data k :description locale] %)
                :label (tr :general/description)
                :variant "outlined"}]]

             ;; Delete btn
             [mui/grid {:item true :style {:text-align "right"}}
              [lui-btn/confirming-delete-button
               {:tooltip (tr :actions/delete)
                :confirm-tooltip (tr :confirm/delete-confirm)
                :on-delete (fn [] (swap! dialog-state update :data dissoc k))}]]

             [mui/grid {:item true :xs 12}
              [mui/divider]]]))

         ;; Add / edit btn
         [mui/grid {:item true :xs 12}
          [mui/grid {:container true :justify-content "flex-end" :align-items "center"}
           [mui/grid {:item true}
            [mui/tooltip {:title (tr :actions/add) :placement "right"}
             [mui/fab
              {:on-click (fn []
                           (let [id (str (random-uuid))]
                             (swap! dialog-state assoc-in [:data id] {:value id})))
               :size "small"
               :color "secondary"}
              [mui/icon "add"]]]]]]]]

       ;; ;; Label
       ;; [mui/grid {:item true :xs 12}
       ;;  [form-label {:label label}]]

       ;; Common rules checkboxes
       [mui/grid {:item true :xs 12}
        [checkboxes
         {:read-only? read-only?
          :label label
          :value (:common-rules @state)
          :helper-text description
          :sort-fn (comp :fi :label second)
          :items common-rules
          :label-fn (comp locale :label second)
          :caption-fn (comp locale :description second)
          :value-fn (comp :value second)
          :on-change (fn [vs]
                       (swap! state assoc :common-rules vs)
                       (set-field :common-rules vs))}]]

       [mui/grid {:item true :xs 12}
        [mui/typography {:variant "subtitle2"} (tr :utp/custom-rules)]
        #_[mui/divider
           [mui/chip {:size "small" :label "Omat säännöt"}]]]

       ;; Custom rules
       [mui/grid {:item true :xs 12}
        [checkboxes
         {:read-only? read-only?
          :label nil
          :value (:custom-rules-vs @state)
          #_#_:helper-text description
          :sort-fn (comp locale :label second)
          :items (:custom-rules @state)
          :label-fn (comp locale :label second)
          :caption-fn (comp locale :description second)
          :value-fn (comp :value second)
          :on-change (fn [vs]
                       (swap! state assoc :custom-rules-vs vs)
                       (set-field :custom-rules (-> @state
                                                    :custom-rules
                                                    (select-keys vs)
                                                    vals)))}]]
       ;; Add / modify custom rules btn
       (when-not read-only?
         [mui/grid
          {:item true
           :xs 12
           :style {:text-align "right"}
           :class-name :no-print}
          [mui/tooltip {:title (if (seq (:custom-rules @state))
                                 (tr :actions/edit)
                                 (tr :actions/add))
                        :placement "left"}
           [mui/fab
            {:style {:margin-top "1em"}
             :on-click (fn []
                         (reset! dialog-state {:open? true
                                               :mode :add
                                               :data (:custom-rules @state)}))
             :size "small"
             :color "secondary"}
            [mui/icon (if (seq (:custom-rules @state))
                        "edit"
                        "add")]]]])])))

(defn image-dialog
  [{:keys [tr locale helper-text dialog-state on-save on-close lipas-id image-props]}]
  (let [description-length-error (> (-> @dialog-state :data :description (get locale) count) 255)]
    [lui/dialog
     {:title (if (-> @dialog-state :data :url)
               (tr :utp/photo)
               (tr :utp/add-photo))
      :open? (:open? @dialog-state)
      :on-save on-save
      :on-close #(swap! dialog-state assoc :open? false)
      :save-enabled? (and (some-> @dialog-state :data :url str/lower-case (str/starts-with? "http"))
                          (-> @dialog-state :data :description seq)
                          (not description-length-error))
      :save-label "Ok"
      :cancel-label (tr :actions/cancel)}
     [mui/grid {:container true :spacing 2}

      [mui/grid {:item true :xs 12}
       [lang-selector {:locale locale}]]

      ;; Description
      [mui/grid {:item true :xs 12}
       [mui/typography {:variant "caption"} helper-text]]

      [mui/grid {:item true :xs 12}

       [:input
        {:type "file"
         :accept (str/join "," ["image/png" "image/jpeg" "image/jpg" "image/webp"])
         :on-change #(==> [::events/upload-utp-image
                           (-> % .-target .-files)
                           lipas-id
                           (fn [{:keys [public-urls] :as cms-meta}]
                             (let [url (:original public-urls)]
                               (swap! dialog-state (fn [state]
                                                     (-> state
                                                         (assoc-in [:data :url] url)
                                                         (assoc-in [:data :cms] cms-meta))))))])}]

       ;; For debug
       #_[lui/text-field
          {:value (-> @dialog-state :data :url)
           :fullWidth true
           :on-change (fn [s] (swap! dialog-state assoc-in [:data :url] s))
           :label "Url"}]]

      [mui/grid {:item true :xs 12}
       (when-let [url (-> @dialog-state :data :url)]
         [:img
          {:style {:max-width "100%"}
           :src url}])]

      ;; Description
      [mui/grid {:item true :xs 12}
       [lui/text-field
        {:fullWidth true
         :required true
         :value (-> @dialog-state :data :description locale)
         :on-change #(swap! dialog-state assoc-in [:data :description locale] %)
         :label (get-in image-props [:description :field :label locale])
         :helper-text (get-in image-props [:description :field :description locale])
         :multiline true
         :rows 5
         :variant "outlined"
         :error (boolean description-length-error)}]]

      ;; Alt-text
      [mui/grid {:item true :xs 12}
       [lui/text-field
        {:fullWidth true
         :required true
         :value (-> @dialog-state :data :alt-text locale)
         :on-change #(swap! dialog-state assoc-in [:data :alt-text locale] %)
         :label (get-in image-props [:alt-text :field :label locale])
         :helper-text (get-in image-props [:alt-text :field :description locale])
         :multiline true
         :rows 5
         :variant "outlined"}]]

      ;; Copyright
      [mui/grid {:item true :xs 12}
       [lui/text-field
        {:fullWidth true
         :required true
         :value (-> @dialog-state :data :copyright locale)
         :on-change #(swap! dialog-state assoc-in [:data :copyright locale] %)
         :label (get-in image-props [:copyright :field :label locale])
         :helper-text (get-in image-props [:copyright :field :description locale])
         :multiline true
         :rows 5
         :variant "outlined"}]]]]))

(defn images
  [{:keys [value on-change locale label helper-text tr read-only? lipas-id image-props]}]
  (r/with-let [state (r/atom (->> value
                                  (map #(assoc % :id (gensym)))
                                  (utils/index-by :id)))
               dialog-init-state {:open? false
                                  :data nil
                                  :mode :edit}
               dialog-state (r/atom dialog-init-state)
               popper-state (r/atom {:open? false})]

    (let [tr (<== [:lipas.ui.subs/translator])]
      [mui/grid {:container true :spacing 2}

       ;; Dialog
       [image-dialog
        {:tr tr
         :lipas-id lipas-id
         :helper-text helper-text
         :locale locale
         :image-props image-props
         :dialog-state dialog-state
         :on-save (fn []
                    (let [data (:data @dialog-state)]
                      (swap! state assoc (:id data) data))
                    (on-change (->> @state
                                    vals
                                    (mapv #(dissoc % :id))))
                    (reset! dialog-state dialog-init-state))}]

       ;; Image Preview Popper
       [mui/popper
        {:open (:open? @popper-state)
         :placement "right"
         :anchor-el (:anchor-el @popper-state)
         :disabblePortal false
         :modifiers [{:name "offset"
                      :options {:offset [0 20]}}]}
        [:img
         {:style {:max-width "400px"}
          :src (:url @popper-state)}]]

       ;; Label
       [mui/grid {:item true :xs 12}
        [form-label {:label label}]]

       ;; Description
       [mui/grid {:item true :xs 12}
        [mui/typography {:variant "caption"} helper-text]]

       ;; Table
       [mui/grid {:item true :xs 12}
        [lui/form-table
         {:key (str (count (vals @state)))
          :headers [[:_filename (tr :general/name)]
                    [:_description (tr :general/description)]]
          :read-only? read-only?
          :items (->> @state
                      vals
                      (map #(assoc % :_description (get-in % [:description locale])))
                      (map #(assoc % :_filename (get-in % [:cms :filename]))))
          :on-add (fn []
                    (reset! dialog-state {:open? true
                                          :mode :add
                                          :data {:id (gensym)}}))
          :on-edit (fn [m]
                     (reset! dialog-state {:open? true
                                           :mode :edit
                                           :data (get @state (:id m))}))
          :on-delete (fn [m]
                       (swap! state dissoc (:id m))
                       (on-change (->> @state
                                       vals
                                       (mapv #(dissoc % :id)))))

          :on-custom-hover-in (fn [evt item]
                                (let [img-url (get-in item [:cms :public-urls :medium]
                                                      (:url item))]
                                  (reset! popper-state {:open? true
                                                        :anchor-el (.-currentTarget evt)
                                                        :url img-url})))
          :on-custom-hover-out (fn [_evt _item]
                                 (swap! popper-state assoc :open? false))

          :on-user-sort (fn [items]
                          (on-change (->> items
                                          (mapv #(dissoc % :id)))))
          :add-tooltip (tr :actions/add)
          :edit-tooltip (tr :actions/edit)
          :delete-tooltip (tr :actions/delete)
          :confirm-tooltip (tr :confirm/delete-confirm)
          :add-btn-size "small"
          #_#_:key-fn :url}]]])))

(defn video-dialog
  [{:keys [tr label helper-text locale dialog-state on-save on-close]}]
  [lui/dialog
   {:title (if (-> @dialog-state :data :url)
             (tr :utp/video)
             (tr :utp/add-video))
    :open? (:open? @dialog-state)
    :on-save on-save
    :on-close #(swap! dialog-state assoc :open? false)
    :save-enabled? (and (some-> @dialog-state :data :url str/lower-case (str/starts-with? "http"))
                        (-> @dialog-state :data :description seq))
    :save-label "Ok"
    :cancel-label (tr :actions/cancel)}
   [mui/grid {:container true :spacing 2}

    [mui/grid {:item true :xs 12}
     [lang-selector {:locale locale}]]

    [mui/grid {:item true :xs 12}
     [mui/typography {:variant "caption"} helper-text]]

    [mui/grid {:item true :xs 12}
     [lui/text-field
      {:value (-> @dialog-state :data :url)
       :fullWidth true
       :on-change (fn [s] (swap! dialog-state assoc-in [:data :url] s))
       :label "Url"}]]

    [mui/grid {:item true :xs 12}
     [lui/text-field
      {:fullWidth true
       :value (-> @dialog-state :data :description locale)
       :on-change #(swap! dialog-state assoc-in [:data :description locale] %)
       :label (tr :general/description)
       :multiline true
       :rows 5
       :variant "outlined"}]]]])

(defn videos
  [{:keys [value on-change locale label helper-text tr read-only?]}]
  (r/with-let [state (r/atom (->> value
                                  (map #(assoc % :id (gensym)))
                                  (utils/index-by :id)))
               dialog-init-state {:open? false
                                  :data nil
                                  :mode :edit}
               dialog-state (r/atom dialog-init-state)]

    (let [tr (<== [:lipas.ui.subs/translator])]
      [mui/grid {:container true :spacing 2}

       ;; Dialog
       [video-dialog
        {:tr tr
         :label label
         :helper-text helper-text
         :locale locale
         :dialog-state dialog-state
         :on-save (fn []
                    (let [data (:data @dialog-state)]
                      (swap! state assoc (:id data) data))
                    (on-change (->> @state
                                    vals
                                    (mapv #(dissoc % :id))))
                    (reset! dialog-state dialog-init-state))}]

       ;; Label
       [mui/grid {:item true :xs 12}
        [form-label {:label label}]]

       ;; Description
       [mui/grid {:item true :xs 12}
        [mui/typography {:variant "caption"} helper-text]]

       ;; Table
       [mui/grid {:item true :xs 12}
        [lui/form-table
         {:key (str (count (vals @state)))
          :headers [[:url (tr :utp/link)]
                    [:_description (tr :general/description)] []]
          :read-only? read-only?
          :items (->> @state
                      vals
                      (map #(assoc % :_description (get-in % [:description locale]))))
          :on-add (fn []
                    (reset! dialog-state {:open? true
                                          :mode :add
                                          :data {:id (gensym)}}))
          :on-edit (fn [m]
                     (reset! dialog-state {:open? true
                                           :mode :edit
                                           :data (get @state (:id m))}))
          :on-delete (fn [m]
                       (swap! state dissoc (:id m))
                       (on-change (->> @state
                                       vals
                                       (mapv #(dissoc % :id)))))

          :on-user-sort (fn [items]
                          (on-change (->> items
                                          (mapv #(dissoc % :id)))))

          :add-tooltip (tr :actions/add)
          :edit-tooltip (tr :actions/edit)
          :delete-tooltip (tr :actions/delete)
          :confirm-tooltip (tr :confirm/delete-confirm)
          :add-btn-size "small"
          :key-fn :url}]]])))

(def independent-entity-ks
  #{:arrival :rules :rules-structured :permits-rules-guidelines :highlights})

;; Route attribute components (geometry editing is in routes tab)

(defn route-attributes-form
  "Form for editing route metadata/attributes (not geometry)"
  [{:keys [locale lipas-id type-code route-props state read-only? field-sorter]}]
  [nice-form {:read-only? read-only?}
   (doall
    (for [[prop-k {:keys [field show]}] (sort-by field-sorter utils/reverse-cmp route-props)
          :when (and (or (nil? show)
                         (show {:type-code type-code}))
                     ;; Exclude geometry-related fields - these are in Routes tab
                     (not (#{:fids :segments :ordering-method :geometries} prop-k)))]
      (when-not (and
                 (contains? route-props :independent-entity)
                 (not (:independent-entity @state))
                 (contains? independent-entity-ks prop-k))
        [make-field
         {:read-only? read-only?
          :key prop-k
          :field field
          :prop-k prop-k
          :edit-data @state
          :display-data @state
          :locale locale
          :set-field (fn [& args]
                       (let [path (butlast args)
                             v (last args)]
                         (swap! state assoc-in path v)))
          :lipas-id lipas-id}])))])

(defn route-selector
  "Simple dropdown to select which route to view/edit in Activities tab"
  [{:keys [routes selected-route-id on-select locale tr]}]
  (when (> (count routes) 1)
    [mui/grid {:item true :xs 12}
     [lui/select
      {:label (tr :utp/select-route)
       :items routes
       :value selected-route-id
       :label-fn (fn [route]
                   (let [name (get-in route [:route-name locale])
                         length (:route-length-km route)]
                     (if (and name length)
                       (str name " (" length " km)")
                       (or name
                           (str (tr :utp/route) " " (inc (.indexOf routes route)))))))
       :value-fn :id
       :on-change on-select}]]))

(defn single-route-attributes
  "Simplified single route editor - attributes only, no geometry"
  [{:keys [read-only? route-props lipas-id type-code route activity-k
           locale _label _description _set-field set-field]}]
  (r/with-let [route-form-state (r/atom route)
               ;; Add watcher for auto-save on changes (only in edit mode)
               _ (when-not read-only?
                   (add-watch route-form-state :sync-route
                              (fn [_key _atom old-state new-state]
                                ;; Use the same approach as multiple-route-attributes for consistency
                                ;; This ensures routes remain as a vector, not a plain map
                                (when (and (not= old-state new-state)
                                           (:id new-state))
                                  (==> [::events/save-route-attributes
                                        {:lipas-id lipas-id
                                         :activity-k activity-k
                                         :route-id (:id new-state)
                                         :attributes new-state}])))))]
    (let [tr (<== [:lipas.ui.subs/translator])
          field-sorter (<== [::subs/field-sorter activity-k])
          ;; Get route with calculated length
          route-with-length (<== [::subs/route-with-calculated-length lipas-id activity-k (:id route)])]

      ;; Set persistent highlight for the single route
      (when (and route-with-length (:fids route-with-length) (seq (:fids route-with-length)))
        (==> [:lipas.ui.map.events/highlight-route-features lipas-id (:fids route-with-length)]))

      [:<>
       ;; Show route name and length
       [mui/grid {:item true :xs 12}
        [mui/typography {:variant "h6"}
         (str (get-in route-with-length [:route-name locale] (tr :utp/route))
              (when-let [length (:route-length-km route-with-length)]
                (str " (" length " km)")))]]

       [route-attributes-form
        {:locale locale
         :tr tr
         :field-sorter field-sorter
         :lipas-id lipas-id
         :type-code type-code
         :read-only? read-only?
         :route-props route-props
         :state route-form-state}]

       ;; Link to geometry editing in Routes tab - only shown in edit mode
       (when-not read-only?
         [mui/grid {:item true :xs 12 :style {:margin-top "1em"}}
          [mui/button
           {:variant "contained"
            :color "primary"
            :start-icon (r/as-element [mui/icon "map"])
            :on-click #(==> [:lipas.ui.sports-sites.events/switch-to-routes-tab lipas-id (:id route)])}
           (tr :utp/edit-routes)]])])
    (finally
      (when-not read-only?
        (remove-watch route-form-state :sync-route))
      ;; Clear highlights when component unmounts
      (==> [:lipas.ui.map.events/highlight-route-features lipas-id #{}]))))

(defn multiple-route-attributes
  "Multiple routes editor - attributes only, geometry editing in Routes tab"
  [{:keys [read-only? route-props lipas-id type-code locale label
           activity-k routes]}]
  (r/with-let [selected-route-id (r/atom (or (:id (first routes))
                                             (str (random-uuid))))
               route-form-state (r/atom {})
               ;; Add watcher for auto-save on changes (only in edit mode)
               _ (when-not read-only?
                   (add-watch route-form-state :sync-route
                              (fn [_key _atom old-state new-state]
                                ;; Only save if state actually changed and we have a selected route
                                (when (and (not= old-state new-state)
                                           @selected-route-id
                                           (seq new-state))
                                  (==> [::events/save-route-attributes
                                        {:lipas-id lipas-id
                                         :activity-k activity-k
                                         :route-id @selected-route-id
                                         :attributes new-state}])))))]
    (let [tr (<== [:lipas.ui.subs/translator])
          field-sorter (<== [::subs/field-sorter activity-k])
          ;; Get routes with calculated lengths
          routes-with-lengths (<== [::subs/routes-with-calculated-lengths lipas-id activity-k])
          current-route (first (filter #(= (:id %) @selected-route-id) routes-with-lengths))]

      ;; Update form state when route selection changes
      (reset! route-form-state (dissoc current-route :fids :segments))

      ;; Set persistent highlight for selected route
      (when (and current-route (:fids current-route) (seq (:fids current-route)))
        (==> [:lipas.ui.map.events/highlight-route-features lipas-id (:fids current-route)]))

      [:<>
       ;; Route selector dropdown - visible in both read-only and edit modes
       (when (seq routes-with-lengths)
         [mui/grid {:item true :xs 12}
          [mui/typography {:variant "h6" :style {:margin-bottom "0.5em"}}
           (tr :utp/select-route)]
          [route-selector
           {:routes routes-with-lengths
            :selected-route-id @selected-route-id
            :on-select (fn [route-id]
                         (reset! selected-route-id route-id)
                         (let [route (first (filter #(= (:id %) route-id) routes-with-lengths))]
                           (reset! route-form-state (dissoc route :fids :segments))
                           ;; Update highlight to new route
                           (if (and route (:fids route) (seq (:fids route)))
                             (==> [:lipas.ui.map.events/highlight-route-features lipas-id (:fids route)])
                             (==> [:lipas.ui.map.events/highlight-route-features lipas-id #{}]))))
            :locale locale
            :tr tr}]])

       ;; Route attributes form for selected route
       (when @selected-route-id
         [:<>
          [mui/grid {:item true :xs 12}
           [mui/typography {:variant "h6" :style {:margin-top "1em"}}
            (or (get-in current-route [:route-name locale])
                (tr :utp/new-route))]]

          [route-attributes-form
           {:locale locale
            :tr tr
            :field-sorter field-sorter
            :lipas-id lipas-id
            :type-code type-code
            :read-only? read-only?
            :route-props route-props
            :state route-form-state}]

          ;; Action buttons - only shown in edit mode
          (when-not read-only?
            [mui/grid {:container true :spacing 1 :style {:margin-top "1em"}}

             ;; Edit geometry in Routes tab
             [mui/grid {:item true}
              [mui/button
               {:variant "contained"
                :color "primary"
                :start-icon (r/as-element [mui/icon "map"])
                :on-click #(==> [:lipas.ui.sports-sites.events/switch-to-routes-tab
                                 lipas-id @selected-route-id])}
               (tr :utp/edit-routes)]]

             ;; Delete route
             [mui/grid {:item true}
              [mui/button
               {:variant "outlined"
                :color "error"
                :on-click #(==> [:lipas.ui.events/confirm
                                 (tr :utp/delete-route-prompt)
                                 (fn []
                                   (==> [::events/delete-route lipas-id activity-k
                                         @selected-route-id]))])}
               (tr :utp/delete-route)]]])])])
    (finally
      (when-not read-only?
        (remove-watch route-form-state :sync-route))
      ;; Clear highlights when component unmounts
      (==> [:lipas.ui.map.events/highlight-route-features lipas-id #{}]))))

(defn routes
  "Main routes component for Activities tab - handles attributes only"
  [{:keys [read-only? _route-props lipas-id activity-k value
           _locale _label _description _set-field _type-code]
    :as props}]
  (let [tr (<== [:lipas.ui.subs/translator])
        ;; Always get routes with calculated lengths for display
        routes (<== [::subs/routes-with-calculated-lengths lipas-id activity-k])
        ;; Fall back to value if subscription returns empty (for read-only mode with no edit data)
        routes-to-display (if (seq routes) routes value)
        route-count (count routes-to-display)]

    [mui/grid {:container true :spacing 2}

     ;; Route view based on count
     [mui/grid {:item true :xs 12}
      (cond
        ;; No routes yet - redirect to Routes tab for creation
        (= route-count 0)
        [:<>
         [mui/typography {:variant "body1" :style {:margin-bottom "0.5em"}}
          (tr :utp/no-routes-yet)]
         (when-not read-only?
           [mui/button
            {:variant "contained"
             :color "secondary"
             :start-icon (r/as-element [mui/icon "add_location"])
             :on-click #(==> [:lipas.ui.sports-sites.events/switch-to-routes-tab lipas-id])}
            (tr :utp/add-first-route)])]

        ;; Single route - simple view
        (= route-count 1)
        [single-route-attributes (assoc props :route (first routes-to-display))]

        ;; Multiple routes - list with selector
        :else
        [multiple-route-attributes (assoc props :routes routes-to-display)])]]))

(defn lipas-property
  [{:keys [read-only? lipas-id lipas-prop-k label description]}]
  (let [tr (<== [:lipas.ui.subs/translator])
        geoms (<== [::subs/geoms read-only?])
        geom-type (<== [::subs/geom-type read-only?])
        value (<== [::subs/lipas-prop-value lipas-prop-k read-only?])
        set-field (partial set-field* lipas-id :properties lipas-prop-k)]
    [:<>
     ;; Because the value (from display-data) is completely different type than
     ;; edit-data, we need to display it using different component. Same logic as ->field.
     (if read-only?
       [->display-tf
        {:label label
         :value value
         :mui-props {:fullWidth true}}
        false
        1]
       [sports-sites-views/make-prop-field
        {:tr tr
         :prop-k lipas-prop-k
         :read-only? read-only?
         :label label
         :description description
         :value value
         :set-field set-field
         :problems? nil
         :geom-type geom-type
         :geoms geoms}])
     (when (and description
                ;; material-field already displays the helper-text, but the read-only field doesn't
                (or read-only?
                    (not (sports-sites-views/material-field? lipas-prop-k))))
       [mui/form-helper-text description])]))

(defn make-field
  [{:keys [field edit-data locale prop-k read-only? lipas-id set-field activity-k type-code]}]
  (case (:type field)

    "select" [lui/select
              {:disabled read-only?
               :deselect? (if (:default field)
                            false
                            true)
               :items (:opts field)
               :label (get-in field [:label locale])
               :helper-text (get-in field [:description locale])
               :label-fn (comp locale second)
               :value-fn first
               :on-change #(set-field prop-k %)
               :value (or (get-in edit-data [prop-k])
                          (:default field))}]

    "multi-select" [:<>
                    [lui/autocomplete
                     {:disabled read-only?
                      :multi? true
                      :items (:opts field)
                      :label (get-in field [:label locale])
                      #_#_:helper-text (get-in field [:description locale])
                      :label-fn (comp locale second)
                      :value-fn first
                      :on-change #(set-field prop-k %)
                      :value (get-in edit-data [prop-k])}]
                    [mui/form-helper-text (get-in field [:description locale])]]

    "text-field" [lui/text-field
                  {:disabled read-only?
                   :label (get-in field [:label locale])
                   :helper-text (get-in field [:description locale])
                   :fullWidth true
                   :on-change #(set-field prop-k locale %)
                   :value (get-in edit-data [prop-k locale])}]

    "number" [lui/text-field
              {:type "number"
               :adornment (:adornment field)
               :disabled read-only?
               :label (get-in field [:label locale])
               :helper-text (get-in field [:description locale])
               :fullWidth true
               :on-change #(set-field prop-k %)
               :value (get-in edit-data [prop-k])}]

    "percentage" [lui/text-field
                  (merge
                   {:type "number"
                    :adornment "%"
                    :disabled read-only?
                    :label (get-in field [:label locale])
                    :helper-text (get-in field [:description locale])
                    :fullWidth true
                    :spec [:or
                           [:int {:min 0 :max 100}]
                           [:double {:min 0.0 :max 100.0}]]
                    :on-change #(set-field prop-k %)
                    :value (get-in edit-data [prop-k])})]

    ;; FIXME: MUI-v5, outlined input is missing x-padding
    "textarea" [lui-tf/expandable-text-area
                {:disabled read-only?
                 :variant "outlined"
                 :label (get-in field [:label locale])
                 :helper-text (get-in field [:description locale])
                 :on-change #(set-field prop-k locale %)
                 :InputLabelProps {:shrink (some? (get-in edit-data [prop-k locale]))}
                 :multiline true
                 :rows 5
                 :fullWidth true
                 :value (get-in edit-data [prop-k locale])}]

    "checkboxes" [checkboxes
                  {:read-only? read-only?
                   :items (:opts field)
                   :label (get-in field [:label locale])
                   :helper-text (get-in field [:description locale])
                   :label-fn (comp locale second)
                   :value-fn first
                   :on-change #(set-field prop-k %)
                   :value (get-in edit-data [prop-k])}]

    "checkbox" [checkbox
                {:read-only? read-only?
                 :component lui/switch
                 :label (get-in field [:label locale])
                 :helper-text (get-in field [:description locale])
                 :on-change #(set-field prop-k %)
                 :value (get-in edit-data [prop-k])}]

    "videos" [videos
              {:read-only? read-only?
               :locale locale
               :label (get-in field [:label locale])
               :helper-text (get-in field [:description locale])
               :on-change #(set-field prop-k %)
               :value (->> (get-in edit-data [prop-k]))}]

    "images" [images
              {:read-only? read-only?
               :lipas-id lipas-id
               :locale locale
               :label (get-in field [:label locale])
               :helper-text (get-in field [:description locale])
               :image-props (:props field)
               :on-change #(set-field prop-k %)
               :value (->> (get-in edit-data [prop-k]))}]

    "routes" [routes
              {:read-only? read-only?
               :lipas-id lipas-id
               :locale locale
               :label (get-in field [:label locale])
               :description (get-in field [:description locale])
               :route-props (:props field)
               :set-field (partial set-field prop-k)
               :activity-k activity-k
               :type-code type-code
               :value (get-in edit-data [prop-k])}]

    "duration" [duration
                {:read-only? read-only?
                 :lipas-id lipas-id
                 :locale locale
                 :label (get-in field [:label locale])
                 :description (get-in field [:description locale])
                 :set-field (partial set-field prop-k)
                 :value (get-in edit-data [prop-k])}]

    "textlist" [textlist
                {:read-only? read-only?
                 :lipas-id lipas-id
                 :locale locale
                 :label (get-in field [:label locale])
                 :description (get-in field [:description locale])
                 :set-field (partial set-field prop-k)
                 :value (get-in edit-data [prop-k])}]

    "rules" [rules
             {:read-only? read-only?
              :lipas-id lipas-id
              :locale locale
              :common-rules (:opts field)
              :label (get-in field [:label locale])
              :description (get-in field [:description locale])
              :set-field (partial set-field prop-k)
              :value (get-in edit-data [prop-k])}]

    "contacts" [contacts
                {:read-only? read-only?
                 :lipas-id lipas-id
                 :locale locale
                 :label (get-in field [:label locale])
                 :description (get-in field [:description locale])
                 :set-field (partial set-field prop-k)
                 :contact-props (:props field)
                 :value (get-in edit-data [prop-k])}]

    "accessibility" [accessibility
                     {:read-only? read-only?
                      :lipas-id lipas-id
                      :locale locale
                      :label (get-in field [:label locale])
                      :description (get-in field [:description locale])
                      :set-field (partial set-field prop-k)
                      :accessibility-props (:props field)
                      :value (get-in edit-data [prop-k])}]

    "lipas-property" [lipas-property
                      {:read-only? read-only?
                       :lipas-id lipas-id
                       :lipas-prop-k (:lipas-property field)
                       :label (get-in field [:label locale])
                       :description (get-in field [:description locale])}]

    (println (str "Unknown field type: " (:type field)))))

(defn view
  [{:keys [type-code display-data edit-data tr lipas-id can-edit?]}]
  (let [activity (<== [::subs/activity-for-type-code type-code])
        activity-k (-> activity :value keyword)
        field-sorter (<== [::subs/field-sorter activity-k])
        locale (tr)
        set-field (partial set-field* lipas-id :activities activity-k)
        editing? (and can-edit? (<== [:lipas.ui.sports-sites.subs/editing? lipas-id]))
        read-only? (not editing?)
        props (or (some-> (get-in activity [:type->props type-code])
                          (->> (select-keys (:props activity))))
                  (get activity :props))

        edit-data (or edit-data
                      ;; Should match the logic in ::edit-site which
                      ;; chooses which rev to base the edit-data on.
                      @(rf/subscribe [::sports-sites-subs/latest-rev lipas-id]))

        ;; Check if there's any actual activity data
        activity-data (get-in edit-data [:activities activity-k])
        has-activity-data? (and activity-data
                                (some (fn [[k v]]
                                        ;; Check if there's meaningful data (not just empty/nil values)
                                        (and (not= k :status) ; Don't count status itself
                                             (cond
                                               (map? v) (some seq (vals v))
                                               (sequential? v) (seq v)
                                               :else v)))
                                      activity-data))]

    [:<>

     (when (and (<== [:lipas.ui.sports-sites.subs/editing? lipas-id]) (not can-edit?))
       [:> Alert
        {:severity "info"}
        (tr :lipas.sports-site/no-permission-tab)])

     ;; If no activities are configured for this type and we're in read-only mode, show a message
     (if (and (nil? activity) read-only?)
       [mui/box {:style {:padding "2em" :text-align "center"}}
        [mui/typography {:variant "body1" :color "textSecondary"}
         (tr :route/no-activities-defined "No activities defined for this sports site type")]]

       ;; Otherwise show the normal interface
       [:<>
        ;; Header
        #_[mui/grid {:item true :xs 12}
           [mui/typography {:variant "h6"}
            (get-in activities [:label locale])]]

        ;; Locale selector
        [mui/grid {:item true :xs 12 :style {:padding-top "0.5em" :padding-bottom "0.5em"}}
         [lang-selector {:locale locale}]]

        ;; Form
        [mui/grid {:item true :xs 12}
         [nice-form {}
          (for [[prop-k {:keys [field]}] (sort-by field-sorter utils/reverse-cmp props)
                ;; In read-only mode, skip fields that have no data
                ;; Always skip status field if there's no other activity data
                :when (or (not read-only?)
                          (not (and (= prop-k :status) (not has-activity-data?))))]
            [make-field
             {:key prop-k
              :field field
              :prop-k prop-k
              :edit-data activity-data
              :read-only? read-only?
              :display-data (get-in display-data [:activities activity-k])
              :locale locale
              :activity-k activity-k
              :type-code type-code
              :set-field set-field
              :lipas-id lipas-id}])]]

        ;; Debug
        (when config/debug?
          [mui/grid {:item true :xs 12}
           [lui/expansion-panel {:label "debug"}
            [:pre (with-out-str (pprint/pprint activity))]]])])]))

(comment

  (do
    (==> [:lipas.ui.map.events/set-zoom 14])
    (==> [:lipas.ui.map.events/set-center 6919553.618920735 445619.43358133035]))

  (==> [:lipas.ui.map.events/show-sports-site 607314]))
