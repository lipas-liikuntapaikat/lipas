(ns lipas.ui.sports-sites.activities.views
  (:require ["@hello-pangea/dnd" :refer [DragDropContext Draggable Droppable]]
            ["@mui/material/Alert$default" :as Alert]
            ["@mui/material/Breadcrumbs$default" :as Breadcrumbs]
            ["@mui/material/Link$default" :as Link]
            [clojure.pprint :as pprint]
            [clojure.string :as str]
            [lipas.schema.common :as common-schema]
            [lipas.ui.components.autocompletes :as autocompletes]
            [lipas.ui.components.checkboxes :as checkboxes]
            [lipas.ui.components.dialogs :as dialogs]
            [lipas.ui.components.layouts :as layouts]
            [lipas.ui.components.selects :as selects]
            [lipas.ui.components.tables :as tables]
            [lipas.ui.components.buttons :as lui-btn]
            [lipas.ui.components.forms :refer [->display-tf]]
            [lipas.ui.components.text-fields :as lui-tf]
            [lipas.ui.config :as config]
            ["@mui/material/Button$default" :as Button]
            ["@mui/material/Chip$default" :as Chip]
            ["@mui/material/Divider$default" :as Divider]
            ["@mui/material/Fab$default" :as Fab]
            ["@mui/material/FormControl$default" :as FormControl]
            ["@mui/material/FormHelperText$default" :as FormHelperText]
            ["@mui/material/FormLabel$default" :as FormLabel]
            ["@mui/material/GridLegacy$default" :as Grid]
            ["@mui/material/Icon$default" :as Icon]
            ["@mui/material/Paper$default" :as Paper]
            ["@mui/material/Popper$default" :as Popper]
            ["@mui/material/Tab$default" :as Tab]
            ["@mui/material/Tabs$default" :as Tabs]
            ["@mui/material/Tooltip$default" :as Tooltip]
            ["@mui/material/Typography$default" :as Typography]
            [lipas.ui.mui :as mui]
            [lipas.ui.sports-sites.activities.events :as events]
            [lipas.ui.sports-sites.activities.subs :as subs]
            [lipas.ui.sports-sites.subs :as sports-sites-subs]
            [lipas.ui.sports-sites.views :as sports-sites-views]
            [lipas.ui.utils :refer [<== ==>] :as utils]
            [re-frame.core :as rf]
            [reagent.core :as r]))

(declare make-field)

(defn set-field*
  [lipas-id & args]
  (==> [:lipas.ui.sports-sites.events/edit-field lipas-id (butlast args) (last args)]))

(defn nice-form
  [props children]
  [:> Grid {:container true :spacing 4}
   (doall
     (for [[idx child] (map vector (range) children)]
       [mui/grid {:item true :xs 12 :key (str "item-" idx)} child]))])

(defn form-label
  [{:keys [label]}]
  [:> FormLabel {:style {:color "gray"}}
   label])

(defn lang-selector
  [{:keys [locale]}]
  [mui/select
   {:value     (name locale)
    :size      "small"
    :variant   "outlined"
    :on-change #(==> [:lipas.ui.events/set-translator (keyword (.. % -target -value))])}
   [mui/menu-item {:value "fi"} "Suomi"]
   [mui/menu-item {:value "se"} "Svenska"]
   [mui/menu-item {:value "en"} "English"]])

(defn checkbox
  [{:keys [read-only? label helper-text on-change value
           component]
    :or   {component checkboxes/checkbox}}]
  [:> Grid {:container true :spacing 2}

   ;; Label
   [:> Grid {:item true :xs 12}
    [form-label {:label label}]]

   ;; Helper text
   [:> Grid {:item true :xs 12 :style {:margin-top "-0.5em"}}
    [:> FormHelperText helper-text]]

   ;; Chekbox
   [:> Grid {:item true :xs 12}
    [component
     {:label     label
      :value     value
      :disabled  read-only?
      :on-change on-change}]]])

(defn checkboxes
  [{:keys [read-only? items label helper-text label-fn value-fn
           on-change value sort-fn caption-fn component]
    :or   {value-fn  identity
           component checkboxes/switch}}]
  (let [vs (set value)]
    [:> Grid {:container true :spacing 2}

     ;; Label
     (when label
       [:> Grid {:item true :xs 12}
        [form-label {:label label}]])

     ;; Helper text
     [:> Grid {:item true :xs 12 :style {:margin-top "-0.5em"}}
      [:> FormHelperText helper-text]]

     ;; Chekboxes
     [:<>
      (for [item (if sort-fn (sort-by sort-fn items) items)]
        (let [[k _] item]
          ^{:key k}
          [:<>
           [:> Grid {:item true :xs 12 :key k}
            [component
             {:label     (label-fn item)
              :value     (contains? vs k)
              :disabled  read-only?
              :on-change (fn [_]
                           ;; Convert set to vec because schema expects sequential
                           (if (contains? vs k)
                             (on-change (vec (disj vs k)))
                             (on-change (vec (conj vs k)))))}]]
           (when-let [caption (not-empty (and caption-fn (caption-fn item)))]
             [:> Grid {:item true :xs 12 :style {:margin-top   "-1.5em"
                                                  :padding-left "2.8em"}}
              [:> Typography {:variant "caption"} caption]])]))]]))

(defn contact-dialog
  [{:keys [tr locale description dialog-state on-save on-close contact-props]}]
  (let [field-sorter (<== [::subs/field-sorter :default])]
    [dialogs/dialog
     {:title         (tr :utp/add-contact)
      :open?         (:open? @dialog-state)
      :on-save       on-save
      :on-close      #(swap! dialog-state assoc :open? false)
      :save-enabled? true
      :save-label    "Ok"
      :cancel-label  (tr :actions/cancel)}

     [:> Grid {:container true :spacing 2}
      [:> Grid {:item true :xs 12}
       [lang-selector {:locale locale}]]
      (doall
        (for [[prop-k {:keys [field]}] (sort-by field-sorter utils/reverse-cmp contact-props)]
          [mui/grid
           {:key prop-k
            :item true
            :xs 12}
           [make-field
            {:field        field
             :prop-k       prop-k
             :edit-data    (:data @dialog-state)
             :display-data (:data @dialog-state)
             :locale       locale
             :set-field    (fn [& args]
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
                                  :data  nil
                                  :mode  :edit}
               dialog-state (r/atom dialog-init-state)]
    (let [tr (<== [:lipas.ui.subs/translator])]

      ;; Dialog
      [:> Grid {:container true :spacing 2}
       [contact-dialog
        {:tr            tr
         :locale        locale
         :description   description
         :dialog-state  dialog-state
         :contact-props contact-props
         :on-save       (fn []
                          (let [data (:data @dialog-state)]
                            (swap! state assoc (:id data) data))
                          (set-field (->> @state
                                          vals
                                          (keep #(dissoc % :id))
                                          vec))
                          (reset! dialog-state dialog-init-state))}]

       ;; Label
       [:> Grid {:item true :xs 12}
        [form-label {:label label}]]

       ;; Description
       [:> Grid {:item true :xs 12}
        [:> Typography {:variant "caption"} description]]

       ;; Table
       [:> Grid {:item true :xs 12}
        [tables/form-table
         {:key              (str (count (vals @state)))
          :headers          [[:_organization (get-in contact-props [:organization :field :label locale])]
                             [:_role (get-in contact-props [:role :field :label locale])]]
          :hide-header-row? false
          :read-only?       read-only?
          :items            (->> @state
                                 vals
                                 (map #(assoc % :_organization (get-in % [:organization locale])))
                                 (map #(assoc % :_role (->> %
                                                            :role
                                                            (map
                                                              (fn [role]
                                                                (get-in contact-props [:role :field :opts role locale])))
                                                            (str/join ", ")))))
          :on-add           (fn []
                              (reset! dialog-state {:open? true
                                                    :mode  :add
                                                    :data  {:id (gensym)}}))
          :on-edit          (fn [m]
                              (reset! dialog-state {:open? true
                                                    :mode  :edit
                                                    :data  (get @state (:id m))}))
          :on-delete        (fn [m]
                              (swap! state dissoc (:id m))
                              (set-field (->> @state
                                              vals
                                              (mapv #(dissoc % :id)))))
          :add-tooltip      (tr :actions/add)
          :edit-tooltip     (tr :actions/edit)
          :delete-tooltip   (tr :actions/delete)
          :confirm-tooltip  (tr :confirm/delete-confirm)
          :add-btn-size     "small"}]]

       ;; Debug
       (when config/debug?
         [:> Grid {:item true :xs 12}
          [layouts/expansion-panel {:label "debug"}
           [:pre (with-out-str (pprint/pprint contact-props))]]])])))

(defn accessibility
  [{:keys [read-only? lipas-id locale label description set-field
           value accessibility-props]}]

  [:> Grid {:container true}

   ;; Label
   [:> Grid {:item true :xs 12}
    [form-label {:label label}]]

   ;; Description
   [:> Grid {:item true :xs 12}
    [:> Typography {:variant "caption"} description]]

   ;; Expansion panels for each accessibility category
   (for [[prop-k {:keys [field]}] accessibility-props]
     [:> Grid
      {:key prop-k
       :item true :xs 12}
      [layouts/expansion-panel
       {:label            (get-in field [:label locale])
        :default-expanded false}
       (if read-only?
         [->display-tf
          {:label     (get-in field [:description locale])
           :mui-props {:fullWidth true}
           :value     (get-in value [prop-k locale])}
          true
          5]
         [lui-tf/text-field
          {:label     (get-in field [:description locale])
           :multiline true
           :rows      5
           :fullWidth true
           :variant   "outlined"
           :on-change #(set-field prop-k locale %)
           :value     (get-in value [prop-k locale])}])]])])

(defn duration
  [{:keys [read-only? locale label description set-field value]}]
  (let [tr (<== [:lipas.ui.subs/translator])]
    [:> FormControl {:focused true}
     [form-label {:label label}]

     [:> Grid {:container true :spacing 2}

      [:> Grid {:item true :xs 12}
       [:> Typography {:variant "caption"} description]]

      [:> Grid {:item true :xs 3}
       [lui-tf/text-field
        {:type      "number"
         :value     (:min value)
         :label     "Min"
         :on-change #(set-field :min %)
         :disabled read-only?}]]

      [:> Grid {:item true :xs 3}
       [lui-tf/text-field
        {:type      "number"
         :value     (:max value)
         :label     "Max"
         :on-change #(set-field :max %)
         :disabled read-only?}]]

      [:> Grid {:item true :xs 6}
       [selects/select
        {:disabled read-only?
         :items     [{:label {:fi "minuuttia" :se "minuter" :en "minutes"}
                      :value "minutes"
                      :sort  1}
                     {:label {:fi "tuntia" :se "timmar" :en "hours"}
                      :value "hours"
                      :sort  2}
                     {:label {:fi "päivää" :se "dagar" :en "days"}
                      :value "days"
                      :sort  3}]
         :sort-fn   :sort
         :label-fn  (comp locale :label)
         :style     {:min-width "170px"}
         :value     (:unit value)
         :label     (tr :utp/unit)
         :on-change #(set-field :unit %)}]]]]))

(defn textlist-dialog
  [{:keys [tr locale dialog-state on-save on-close lipas-id label description]}]
  [dialogs/dialog
   {:title         (tr :utp/add-highlight)
    :open?         (:open? @dialog-state)
    :on-save       on-save
    :on-close      #(swap! dialog-state assoc :open? false)
    :save-enabled? true
    :save-label    "OK"
    :cancel-label  (tr :actions/cancel)}

   [:> Grid {:container true :spacing 2}
    [:> Grid {:item true :xs 12}
     [lang-selector {:locale locale}]]

    #_[:> Grid {:item true :xs 12}
       [:> Paper {:style {:padding "0.5em" :background-color mui/gray3}}
        [:> Typography description]]]

    [:> Grid {:item true :xs 12}
     [:> Grid {:item true :xs 12}
      ;; FIXME: MUI-v5 input height or paddings are wrong
      [lui-tf/text-field
       {:fullWidth   true
        :required    true
        :helper-text description
        :value       (-> @dialog-state :data locale)
        :on-change   #(swap! dialog-state assoc-in [:data locale] %)
        :label       (tr :utp/highlight)
        :variant     "outlined"}]]]]])

(defn textlist
  [{:keys [read-only? locale label description set-field value]}]
  (r/with-let [state (r/atom (->> value
                                  (map #(assoc % :id (gensym)))
                                  (utils/index-by :id)))
               dialog-init-state {:open? false
                                  :data  nil
                                  :mode  :edit}
               dialog-state (r/atom dialog-init-state)]
    (let [tr (<== [:lipas.ui.subs/translator])]

      ;; Dialog
      [:> Grid {:container true :spacing 2}
       [textlist-dialog
        {:tr           tr
         :locale       locale
         :label        label
         :description  description
         :dialog-state dialog-state
         :on-save      (fn []
                         (let [data (:data @dialog-state)]
                           (swap! state assoc (:id data) data))
                         (set-field (->> @state
                                         vals
                                         (mapv #(dissoc % :id))))
                         (reset! dialog-state dialog-init-state))}]

       ;; Label
       [:> Grid {:item true :xs 12}
        [form-label {:label label}]]

       ;; Description
       [:> Grid {:item true :xs 12}
        [:> Typography {:variant "caption"}] description]

       ;; Table
       [:> Grid {:item true :xs 12}
        [tables/form-table
         {:key              @state
          :headers          [[locale label]]
          :hide-header-row? true
          :read-only?       read-only?
          :items            (->> @state vals)
          :on-dnd-end       (fn [items]
                              (reset! state
                                      (->> items
                                           (map #(assoc % :id (gensym)))
                                           (utils/index-by :id)))
                              (set-field (->> @state
                                              vals
                                              (mapv #(dissoc % :id)))))
          :on-add           (fn []
                              (reset! dialog-state {:open? true
                                                    :mode  :add
                                                    :data  {:id (gensym)}}))
          :on-edit          (fn [m]
                              (reset! dialog-state {:open? true
                                                    :mode  :edit
                                                    :data  (get @state (:id m))}))
          :on-delete        (fn [m]
                              (swap! state dissoc (:id m))
                              (set-field (->> @state
                                              vals
                                              (mapv #(dissoc % :id)))))
          :on-user-sort     (fn [items]
                              (set-field (mapv #(dissoc % :id) items)))
          :add-tooltip      (tr :actions/add)
          :edit-tooltip     (tr :actions/edit)
          :delete-tooltip   (tr :actions/delete)
          :confirm-tooltip  (tr :confirm/delete-confirm)
          :add-btn-size     "small"}]]])))

(defn rules
  [{:keys [read-only? locale label description set-field value common-rules]}]
  (r/with-let [state (r/atom {:common-rules    (:common-rules value)
                              :custom-rules-vs (->> value :custom-rules (map :value))
                              :custom-rules    (or (when-let [coll (:custom-rules value)]
                                                     (utils/index-by :value coll))
                                                   {})})
               dialog-init-state {:open? false
                                  :data  nil
                                  :mode  :edit}
               dialog-state (r/atom dialog-init-state)]
    (let [tr (<== [:lipas.ui.subs/translator])]

      ;; Dialog
      [:> Grid {:container true :spacing 2}
       [dialogs/dialog
        {:title   label
         :open?   (:open? @dialog-state)
         :on-save (fn []
                    (let [data (:data @dialog-state)
                          vs   (map :value (vals data))]

                      ;; Update local state
                      (swap! state (fn [curr]
                                     (-> curr
                                         (assoc :custom-rules data)
                                         (assoc :custom-rules-vs vs))))

                      ;; Update app-db state
                      (set-field :custom-rules (vals data))

                      ;; Close the dialog
                      (swap! dialog-state assoc :open? false)))

         :on-close      #(swap! dialog-state assoc :open? false)
         :save-enabled? true
         :save-label    "OK"
         :cancel-label  (tr :actions/cancel)}

        [:> Grid {:container true :spacing 2}

         ;; Lang selector
         [:> Grid {:item true :xs 12}
          [lang-selector {:locale locale}]]

         ;; Halper text
         #_[:> Grid {:item true :xs 12}
            [:> Paper {:style {:padding "0.5em" :background-color mui/gray3}}
             [:> Typography description]]]

         (doall
           (for [[idx k] (map-indexed vector (keys (:data @dialog-state)))]
             ^{:key k}
             [:<>
              [mui/grid {:item true :xs 12}
               [mui/typography (str (tr :utp/custom-rule) " " (inc idx))]]

             ;; Label
              [mui/grid {:item true :xs 12}
               [lui/text-field
                {:fullWidth       true
                 :required        true
                 #_#_:helper-text description
                 :value           (-> @dialog-state :data (get k) :label locale)
                 :on-change       #(swap! dialog-state assoc-in [:data k :label locale] %)
                 :label           (tr :general/headline)
                 :variant         "outlined"}]]

             ;; Description
              [mui/grid {:item true :xs 12}
               [lui/text-field
                {:fullWidth       true
                 :required        true
                 #_#_:helper-text description
                 :value           (-> @dialog-state :data (get k) :description locale)
                 :on-change       #(swap! dialog-state assoc-in [:data k :description locale] %)
                 :label           (tr :general/description)
                 :variant         "outlined"}]]

             ;; Delete btn
              [mui/grid {:item true :style {:text-align "right"}}
               [lui-btn/confirming-delete-button
                {:tooltip         (tr :actions/delete)
                 :confirm-tooltip (tr :confirm/delete-confirm)
                 :on-delete       (fn [] (swap! dialog-state update :data dissoc k))}]]

              [mui/grid {:item true :xs 12}
               [mui/divider]]]))

         ;; Add / edit btn
         [:> Grid {:item true :xs 12}
          [:> Grid {:container true :justify-content "flex-end" :align-items "center"}
           [:> Grid {:item true}
            [:> Tooltip {:title (tr :actions/add) :placement "right"}
             [:> Fab
              {:on-click (fn []
                           (let [id (str (random-uuid))]
                             (swap! dialog-state assoc-in [:data id] {:value id})))
               :size     "small"
               :color    "secondary"}
              [:> Icon "add"]]]]]]]]

       ;; ;; Label
       ;; [:> Grid {:item true :xs 12}
       ;;  [form-label {:label label}]]

       ;; Common rules checkboxes
       [:> Grid {:item true :xs 12}
        [checkboxes
         {:read-only?  read-only?
          :label       label
          :value       (:common-rules @state)
          :helper-text description
          :sort-fn     (comp :fi :label second)
          :items       common-rules
          :label-fn    (comp locale :label second)
          :caption-fn  (comp locale :description second)
          :value-fn    (comp :value second)
          :on-change   (fn [vs]
                         (swap! state assoc :common-rules vs)
                         (set-field :common-rules vs))}]]

       [:> Grid {:item true :xs 12}
        [:> Typography {:variant "subtitle2"} (tr :utp/custom-rules)]
        #_[:> Divider
           [:> Chip {:size "small" :label "Omat säännöt"}]]]

       ;; Custom rules
       [:> Grid {:item true :xs 12}
        [checkboxes
         {:read-only?      read-only?
          :label           nil
          :value           (:custom-rules-vs @state)
          #_#_:helper-text description
          :sort-fn         (comp locale :label second)
          :items           (:custom-rules @state)
          :label-fn        (comp locale :label second)
          :caption-fn      (comp locale :description second)
          :value-fn        (comp :value second)
          :on-change       (fn [vs]
                             (swap! state assoc :custom-rules-vs vs)
                             (set-field :custom-rules (-> @state
                                                          :custom-rules
                                                          (select-keys vs)
                                                          vals)))}]]
       ;; Add / modify custom rules btn
       (when-not read-only?
         [:> Grid
          {:item       true
           :xs         12
           :style      {:text-align "right"}
           :class-name :no-print}
          [:> Tooltip {:title     (if (seq (:custom-rules @state))
                                     (tr :actions/edit)
                                     (tr :actions/add))
                        :placement "left"}
           [:> Fab
            {:style    {:margin-top "1em"}
             :on-click (fn []
                         (reset! dialog-state {:open? true
                                               :mode  :add
                                               :data  (:custom-rules @state)}))
             :size     "small"
             :color    "secondary"}
            [:> Icon (if (seq (:custom-rules @state))
                        "edit"
                        "add")]]]])])))

(defn image-dialog
  [{:keys [tr locale helper-text dialog-state on-save on-close lipas-id image-props]}]
  (let [description-length-error (> (-> @dialog-state :data :description (get locale) count) 255)]
    [dialogs/dialog
     {:title         (if (-> @dialog-state :data :url)
                       (tr :utp/photo)
                       (tr :utp/add-photo))
      :open?         (:open? @dialog-state)
      :on-save       on-save
      :on-close      #(swap! dialog-state assoc :open? false)
      :save-enabled? (and (some-> @dialog-state :data :url str/lower-case (str/starts-with? "http"))
                          (-> @dialog-state :data :description seq)
                          (not description-length-error))
      :save-label    "Ok"
      :cancel-label  (tr :actions/cancel)}
     [:> Grid {:container true :spacing 2}

      [:> Grid {:item true :xs 12}
       [lang-selector {:locale locale}]]

      ;; Description
      [:> Grid {:item true :xs 12}
       [:> Typography {:variant "caption"} helper-text]]

      [:> Grid {:item true :xs 12}

       [:input
        {:type      "file"
         :accept    (str/join "," ["image/png" "image/jpeg" "image/jpg" "image/webp"])
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
       #_[lui-tf/text-field
          {:value     (-> @dialog-state :data :url)
           :fullWidth true
           :on-change (fn [s] (swap! dialog-state assoc-in [:data :url] s))
           :label     "Url"}]]

      [:> Grid {:item true :xs 12}
       (when-let [url (-> @dialog-state :data :url)]
         [:img
          {:style {:max-width "100%"}
           :src   url}])]

      ;; Description
      [:> Grid {:item true :xs 12}
       [lui-tf/text-field
        {:fullWidth   true
         :required    true
         :value       (-> @dialog-state :data :description locale)
         :on-change   #(swap! dialog-state assoc-in [:data :description locale] %)
         :label       (get-in image-props [:description :field :label locale])
         :helper-text (get-in image-props [:description :field :description locale])
         :multiline   true
         :rows        5
         :variant     "outlined"
         :error       (boolean description-length-error)}]]

      ;; Alt-text
      [:> Grid {:item true :xs 12}
       [lui-tf/text-field
        {:fullWidth   true
         :required    true
         :value       (-> @dialog-state :data :alt-text locale)
         :on-change   #(swap! dialog-state assoc-in [:data :alt-text locale] %)
         :label       (get-in image-props [:alt-text :field :label locale])
         :helper-text (get-in image-props [:alt-text :field :description locale])
         :multiline   true
         :rows        5
         :variant     "outlined"}]]

      ;; Copyright
      [:> Grid {:item true :xs 12}
       [lui-tf/text-field
        {:fullWidth   true
         :required    true
         :value       (-> @dialog-state :data :copyright locale)
         :on-change   #(swap! dialog-state assoc-in [:data :copyright locale] %)
         :label       (get-in image-props [:copyright :field :label locale])
         :helper-text (get-in image-props [:copyright :field :description locale])
         :multiline   true
         :rows        5
         :variant     "outlined"}]]]]))

(defn images
  [{:keys [value on-change locale label helper-text tr read-only? lipas-id image-props]}]
  (r/with-let [state (r/atom (->> value
                                  (map #(assoc % :id (gensym)))
                                  (utils/index-by :id)))
               dialog-init-state {:open? false
                                  :data  nil
                                  :mode  :edit}
               dialog-state (r/atom dialog-init-state)
               popper-state (r/atom {:open? false})]

    (let [tr (<== [:lipas.ui.subs/translator])]
      [:> Grid {:container true :spacing 2}

       ;; Dialog
       [image-dialog
        {:tr           tr
         :lipas-id     lipas-id
         :helper-text  helper-text
         :locale       locale
         :image-props  image-props
         :dialog-state dialog-state
         :on-save      (fn []
                         (let [data (:data @dialog-state)]
                           (swap! state assoc (:id data) data))
                         (on-change (->> @state
                                         vals
                                         (mapv #(dissoc % :id))))
                         (reset! dialog-state dialog-init-state))}]

       ;; Image Preview Popper
       [:> Popper
        {:open           (:open? @popper-state)
         :placement      "right"
         :anchor-el      (:anchor-el @popper-state)
         :disabblePortal false
         :modifiers      [{:name "offset"
                           :options {:offset [0 20]}}]}
        [:img
         {:style {:max-width "400px"}
          :src   (:url @popper-state)}]]

       ;; Label
       [:> Grid {:item true :xs 12}
        [form-label {:label label}]]

       ;; Description
       [:> Grid {:item true :xs 12}
        [:> Typography {:variant "caption"} helper-text]]

       ;; Table
       [:> Grid {:item true :xs 12}
        [tables/form-table
         {:key (str (count (vals @state)))
          :headers    [[:_filename (tr :general/name)]
                       [:_description (tr :general/description)]]
          :read-only? read-only?
          :items      (->> @state
                           vals
                           (map #(assoc % :_description (get-in % [:description locale])))
                           (map #(assoc % :_filename (get-in % [:cms :filename]))))
          :on-add     (fn []
                        (reset! dialog-state {:open? true
                                              :mode  :add
                                              :data  {:id (gensym)}}))
          :on-edit    (fn [m]
                        (reset! dialog-state {:open? true
                                              :mode  :edit
                                              :data  (get @state (:id m))}))
          :on-delete  (fn [m]
                        (swap! state dissoc (:id m))
                        (on-change (->> @state
                                        vals
                                        (mapv #(dissoc % :id)))))

          :on-custom-hover-in  (fn [evt item]
                                 (let [img-url (get-in item [:cms :public-urls :medium]
                                                       (:url item))]
                                   (reset! popper-state {:open?     true
                                                         :anchor-el (.-currentTarget evt)
                                                         :url       img-url})))
          :on-custom-hover-out (fn [_evt _item]
                                 (swap! popper-state assoc :open? false))

          :on-user-sort (fn [items]
                          (on-change (->> items
                                          (mapv #(dissoc % :id)))))
          :add-tooltip         (tr :actions/add)
          :edit-tooltip        (tr :actions/edit)
          :delete-tooltip      (tr :actions/delete)
          :confirm-tooltip     (tr :confirm/delete-confirm)
          :add-btn-size        "small"
          #_#_:key-fn              :url}]]])))

(defn video-dialog
  [{:keys [tr label helper-text locale dialog-state on-save on-close]}]
  [dialogs/dialog
   {:title         (if (-> @dialog-state :data :url)
                     (tr :utp/video)
                     (tr :utp/add-video))
    :open?         (:open? @dialog-state)
    :on-save       on-save
    :on-close      #(swap! dialog-state assoc :open? false)
    :save-enabled? (and (some-> @dialog-state :data :url str/lower-case (str/starts-with? "http"))
                        (-> @dialog-state :data :description seq))
    :save-label    "Ok"
    :cancel-label  (tr :actions/cancel)}
   [:> Grid {:container true :spacing 2}

    [:> Grid {:item true :xs 12}
     [lang-selector {:locale locale}]]

    [:> Grid {:item true :xs 12}
     [:> Typography {:variant "caption"} helper-text]]

    [:> Grid {:item true :xs 12}
     [lui-tf/text-field
      {:value     (-> @dialog-state :data :url)
       :fullWidth true
       :on-change (fn [s] (swap! dialog-state assoc-in [:data :url] s))
       :label     "Url"}]]

    [:> Grid {:item true :xs 12}
     [lui-tf/text-field
      {:fullWidth true
       :value     (-> @dialog-state :data :description locale)
       :on-change #(swap! dialog-state assoc-in [:data :description locale] %)
       :label     (tr :general/description)
       :multiline true
       :rows      5
       :variant   "outlined"}]]]])

(defn videos
  [{:keys [value on-change locale label helper-text tr read-only?]}]
  (r/with-let [state (r/atom (->> value
                                  (map #(assoc % :id (gensym)))
                                  (utils/index-by :id)))
               dialog-init-state {:open? false
                                  :data  nil
                                  :mode  :edit}
               dialog-state (r/atom dialog-init-state)]

    (let [tr (<== [:lipas.ui.subs/translator])]
      [:> Grid {:container true :spacing 2}

       ;; Dialog
       [video-dialog
        {:tr           tr
         :label        label
         :helper-text  helper-text
         :locale       locale
         :dialog-state dialog-state
         :on-save      (fn []
                         (let [data (:data @dialog-state)]
                           (swap! state assoc (:id data) data))
                         (on-change (->> @state
                                         vals
                                         (mapv #(dissoc % :id))))
                         (reset! dialog-state dialog-init-state))}]

       ;; Label
       [:> Grid {:item true :xs 12}
        [form-label {:label label}]]

       ;; Description
       [:> Grid {:item true :xs 12}
        [:> Typography {:variant "caption"} helper-text]]

       ;; Table
       [:> Grid {:item true :xs 12}
        [tables/form-table
         {:key             (str (count (vals @state)))
          :headers         [[:url (tr :utp/link)]
                            [:_description (tr :general/description)] []]
          :read-only?      read-only?
          :items           (->> @state
                                vals
                                (map #(assoc % :_description (get-in % [:description locale]))))
          :on-add          (fn []
                             (reset! dialog-state {:open? true
                                                   :mode  :add
                                                   :data  {:id (gensym)}}))
          :on-edit         (fn [m]
                             (reset! dialog-state {:open? true
                                                   :mode  :edit
                                                   :data  (get @state (:id m))}))
          :on-delete       (fn [m]
                             (swap! state dissoc (:id m))
                             (on-change (->> @state
                                             vals
                                             (mapv #(dissoc % :id)))))

          :on-user-sort (fn [items]
                          (on-change (->> items
                                          (mapv #(dissoc % :id)))))

          :add-tooltip     (tr :actions/add)
          :edit-tooltip    (tr :actions/edit)
          :delete-tooltip  (tr :actions/delete)
          :confirm-tooltip (tr :confirm/delete-confirm)
          :add-btn-size    "small"
          :key-fn          :url}]]])))

(def independent-entity-ks
  #{:arrival :rules :rules-structured :permits-rules-guidelines :highlights})

(defn route-form
  [{:keys [locale lipas-id type-code route-props state read-only? field-sorter]}]
  [nice-form {:read-only? read-only?}
   (doall
     (for [[prop-k {:keys [field show]}] (sort-by field-sorter utils/reverse-cmp route-props)
           :when (or (nil? show)
                     (show {:type-code type-code}))]
       (when-not (and
                   (contains? route-props :independent-entity)
                   (not (:independent-entity @state))
                   (contains? independent-entity-ks prop-k))
         [make-field
          {:read-only?   read-only?
           :key          prop-k
           :field        field
           :prop-k       prop-k
           :edit-data    @state
           :display-data @state
           :locale       locale
           :set-field    (fn [& args]
                           (let [path (butlast args)
                                 v (last args)]
                             (swap! state assoc-in path v)))
           :lipas-id     lipas-id}])))])

(defn- segment-row
  [{:keys [lipas-id activity-k route-id idx segment detail n read-only? move! provided]}]
  (let [tr  (<== [:lipas.ui.subs/translator])
        fid (:fid segment)
        drag-props (when provided
                     (merge (js->clj (.-draggableProps provided))
                            (js->clj (.-dragHandleProps provided))))]
    [mui/stack
     (merge
       {:direction       "row"
        :align-items     "center"
        :spacing         0.5
        :ref             (when provided (.-innerRef provided))
        :on-mouse-enter  #(==> [::events/highlight-segment lipas-id activity-k route-id idx])
        :on-mouse-leave  #(==> [::events/highlight-segment lipas-id activity-k route-id nil])
        :sx              (merge {:padding       "4px 8px"
                                 :border-bottom "1px solid #eee"
                                 :cursor        "default"
                                 "&:hover"      {:background-color "#f5f5f5"}}
                                (when (and detail (not (:connected-to-next? detail)))
                                  {:border-bottom "2px solid #f0ad4e"}))}
       drag-props)

     ;; Drag handle
     (when-not read-only?
       [mui/icon {:style {:font-size "20px" :color "#999" :cursor "grab"}}
        "drag_indicator"])

     ;; Move up / move down
     (when-not read-only?
       [mui/stack {:direction "column" :style {:margin-right "4px"}}
        [mui/icon-button
         {:size     "small"
          :disabled (zero? idx)
          :on-click #(move! idx (dec idx))}
         [mui/icon {:style {:font-size "18px"}} "arrow_upward"]]
        [mui/icon-button
         {:size     "small"
          :disabled (= idx (dec n))
          :on-click #(move! idx (inc idx))}
         [mui/icon {:style {:font-size "18px"}} "arrow_downward"]]])

     ;; Segment label with number, compass direction, and length
     [mui/typography
      {:variant "body2"
       :style   {:flex 1}}
      (str (tr :utp/segment) " " (inc idx)
           (when-let [dir (:compass-direction detail)]
             (str ": " dir))
           (when-let [len (:length-km detail)]
             (str " (" len " km)")))]

     ;; Connectivity indicator
     (when (and detail (not= idx (dec n)))
       (if (and (not (:connected-to-next? detail))
                (:fix-suggestion detail)
                (not read-only?))
         ;; Gap with fix suggestion
         [mui/tooltip {:title (tr :utp/fix-gap)}
          [mui/icon-button
           {:size     "small"
            :color    "warning"
            :on-click #(let [{:keys [target-idx]} (:fix-suggestion detail)]
                         (==> [::events/toggle-segment-direction
                               lipas-id activity-k route-id target-idx]))}
           [mui/icon {:style {:font-size "18px"}} "auto_fix_high"]]]
         ;; Normal connectivity indicator
         [mui/tooltip
          {:title (if (:connected-to-next? detail) "Connected" "Gap")}
          [mui/icon
           {:style {:font-size "12px"
                    :color     (if (:connected-to-next? detail) "#5cb85c" "#f0ad4e")}}
           "circle"]]))

     ;; Reverse button
     (when-not read-only?
       [mui/tooltip {:title (tr :utp/reverse-segment)}
        [mui/icon-button
         {:size     "small"
          :color    (if (:reversed? segment) "primary" "default")
          :on-click #(==> [::events/toggle-segment-direction
                           lipas-id activity-k route-id idx])}
         [mui/icon {:style {:font-size "18px"}} "swap_horiz"]]])

     ;; Duplicate button (out-and-back)
     (when-not read-only?
       [mui/tooltip {:title (tr :utp/duplicate-segment)}
        [mui/icon-button
         {:size     "small"
          :on-click #(==> [::events/duplicate-segment
                           lipas-id activity-k route-id idx])}
         [mui/icon {:style {:font-size "18px"}} "content_copy"]]])

     ;; Remove button
     (when-not read-only?
       [mui/icon-button
        {:size     "small"
         :on-click #(==> [::events/remove-segment
                          lipas-id activity-k route-id idx])}
        [mui/icon {:style {:font-size "18px"}} "close"]])]))

(defn segment-builder
  [{:keys [lipas-id activity-k route-id segments segment-details read-only?]}]
  (let [tr    (<== [:lipas.ui.subs/translator])
        n     (count segments)
        move! (fn [from to]
                (==> [::events/reorder-segments lipas-id activity-k route-id from to]))]
    (when (seq segments)
      [mui/grid {:item true :xs 12 :style {:margin-top "0.5em" :margin-bottom "0.5em"}}

       ;; Header row: title + add button
       [mui/grid {:container true :align-items "center" :justify-content "space-between"
                  :style {:margin-bottom "0.5em"}}
        [mui/grid {:item true}
         [mui/typography {:variant "subtitle2"} (tr :utp/segments)]]
        (when-not read-only?
          [mui/grid {:item true}
           [mui/button
            {:size     "small"
             :on-click #(==> [::events/add-segments-to-route lipas-id activity-k route-id])}
            (tr :utp/add-segments)]])]

       ;; Help text
       [lui/expansion-panel
        {:label            (tr :utp/segment-builder-help-title)
         :default-expanded false
         :style            {:margin-top "0" :margin-bottom "0.5em"}}
        [mui/typography {:variant "body2"}
         (tr :utp/segment-builder-help-text)]]

       [mui/paper {:variant "outlined" :style {:padding "4px"}}
        (if read-only?
          ;; Read-only: no DnD
          (doall
            (for [[idx segment] (map-indexed vector segments)]
              (let [detail (nth segment-details idx nil)]
                ^{:key (str idx "-" (:fid segment))}
                [segment-row
                 {:lipas-id   lipas-id
                  :activity-k activity-k
                  :route-id   route-id
                  :idx        idx
                  :segment    segment
                  :detail     detail
                  :n          n
                  :read-only? true
                  :move!      move!}])))

          ;; Edit mode: with DnD
          [:> DragDropContext
           {:onDragEnd (fn [result]
                         (when-let [dest (.-destination result)]
                           (let [source-idx (.-index (.-source result))
                                 target-idx (.-index dest)]
                             (when (not= source-idx target-idx)
                               (move! source-idx target-idx)))))}
           [:> Droppable {:droppableId "route-segments"}
            (fn [provided]
              (r/as-element
                [:div (merge (js->clj (.-droppableProps provided))
                             {:ref (.-innerRef provided)})
                 (doall
                   (for [[idx segment] (map-indexed vector segments)]
                     (let [detail (nth segment-details idx nil)]
                       ^{:key (str idx "-" (:fid segment))}
                       [:> Draggable {:draggableId (str "seg-" idx) :index idx}
                        (fn [provided]
                          (r/as-element
                            [segment-row
                             {:lipas-id   lipas-id
                              :activity-k activity-k
                              :route-id   route-id
                              :idx        idx
                              :segment    segment
                              :detail     detail
                              :n          n
                              :read-only? false
                              :move!      move!
                              :provided   provided}]))])))
                 (.-placeholder provided)]))]])]])))

(defn single-route
  [{:keys [read-only? route-props lipas-id type-code route activity-k
           locale _label _description _set-field set-field]
    :as   props}]
  ;; Ensure route has an :id - required by schema since commit 63af05df
  (r/with-let [route-form-state (r/atom (if (:id route)
                                          route
                                          (assoc route :id (str (random-uuid)))))
               _ (add-watch route-form-state :lol
                            (fn [_key _atom _old-state new-state]
                              (set-field [new-state])))]

    (let [tr           (<== [:lipas.ui.subs/translator])
          field-sorter (<== [::subs/field-sorter activity-k])]

      [:<>
       [segment-builder
        {:lipas-id        lipas-id
         :activity-k      activity-k
         :route-id        (:id route)
         :segments        (:segments route)
         :segment-details (:segment-details route)
         :read-only?      read-only?}]

       [route-form
        {:locale       locale
         :tr           tr
         :field-sorter field-sorter
         :lipas-id     lipas-id
         :type-code    type-code
         :read-only?   read-only?
         :route-props  route-props
         :state        route-form-state}]])

    (finally
      (remove-watch route-form-state :lol))))

(defn- route-detail-view
  "Route detail view with two tabs: Segments and Details. Initializes form state
   from the selected route subscription data."
  [{:keys [lipas-id activity-k route-id route locale type-code route-props
           field-sorter read-only?]}]
  (r/with-let [route-form-state (r/atom (dissoc route :fids :segment-details
                                                :route-length :elevation-stats))
               detail-tab       (r/atom 0)]
    (let [tr   (<== [:lipas.ui.subs/translator])
          fids (<== [::subs/selected-features])]

      [:<>
       ;; Tabs: Details | Segments
       [mui/tabs {:value     @detail-tab
                  :on-change #(reset! detail-tab %2)
                  :style     {:margin-bottom "0.5em"}}
        [mui/tab {:label (tr :utp/route-details-tab)}]
        [mui/tab {:label (tr :utp/segments)}]]

       ;; Tab content
       (case (int @detail-tab)
         0 [route-form
            {:locale       locale
             :tr           tr
             :field-sorter field-sorter
             :lipas-id     lipas-id
             :type-code    type-code
             :read-only?   read-only?
             :route-props  route-props
             :state        route-form-state}]

         1 [segment-builder
            {:lipas-id        lipas-id
             :activity-k      activity-k
             :route-id        route-id
             :segments        (:segments route)
             :segment-details (:segment-details route)
             :read-only?      false}])

       ;; Buttons — always visible
       [mui/grid {:container true :spacing 1 :style {:margin-top "1em"}}

        ;; Done
        [mui/grid {:item true}
         [mui/button
          {:variant  "contained"
           :color    "secondary"
           :on-click #(==> [::events/finish-route-details
                            {:fids       fids
                             :activity-k activity-k
                             :id         route-id
                             :route      @route-form-state
                             :lipas-id   lipas-id}])}
          (tr :utp/finish-route-details)]]

        ;; Delete
        [mui/grid {:item true}
         [mui/button
          {:variant  "contained"
           :on-click #(==> [:lipas.ui.events/confirm
                            (tr :utp/delete-route-prompt)
                            (fn []
                              (==> [::events/delete-route lipas-id activity-k route-id]))])}
          (tr :actions/delete)]]

        ;; Cancel
        [mui/grid {:item true}
         [mui/button
          {:variant  "contained"
           :on-click #(==> [::events/cancel-route-details])}
          (tr :actions/cancel)]]]])))

(defn multiple-routes
  [{:keys [read-only? route-props lipas-id type-code _display-data _edit-data
           locale label _description _set-field activity-k routes]
    :as   props}]
  (let [tr     (<== [:lipas.ui.subs/translator])
        mode   (<== [::subs/mode])

        selected-route-id (<== [::subs/selected-route-id])

        field-sorter (<== [::subs/field-sorter activity-k])

        editing? (not read-only?)]

    [:<>

     (when-not (#{:route-details :add-route} mode)
       [:<>
        (when (seq routes)
          [mui/grid {:item true :xs 12}
           [form-label {:label label}]
           [lui/table
            {:headers
             [[:_route-name (tr :general/name)]
              [:route-length (tr :utp/length-km)]]
             :items          (->> routes
                                  (mapv (fn [m]
                                          (assoc m :_route-name (get-in m [:route-name locale])))))
             :on-mouse-enter (fn [item]
                               (==> [::events/highlight-route lipas-id activity-k (:id item)]))
             :on-mouse-leave (fn [_]
                               (==> [::events/highlight-route lipas-id activity-k nil]))

             :on-select (fn [route]
                          (==> [::events/select-route lipas-id (dissoc route :_route-name)]))}]])

        (when-not read-only?
          [mui/grid {:item true :xs 12}
           [mui/button
            {:variant  "contained"
             :color    "secondary"
             :style    {:margin-top "0.5em" :margin-bottom "0.5em"}
             :on-click (fn []
                         (==> [::events/add-route lipas-id activity-k]))}
            (tr :utp/add-subroute)]])])

     (when (and editing? (= :add-route mode))
       (let [selected-features (<== [::subs/selected-features])
             n                 (count selected-features)]
         [:<>

          [mui/grid {:item true :xs 12}
           [mui/typography
            {:variant "body2"}
            (tr :utp/select-route-parts-on-map)]
           (when (pos? n)
             [mui/typography
              {:variant "body2" :color "primary"
               :style {:margin-top "0.25em" :font-weight 500}}
              (str n " " (tr :utp/segments-selected))])]

          [mui/grid {:item true :xs 12
                     :style {:display "flex" :gap "0.5em" :margin-top "0.5em"}}
           [mui/button
            {:variant  "contained"
             :color    "secondary"
             :disabled (empty? selected-features)
             :on-click #(==> [::events/finish-route lipas-id activity-k])}
            (tr :utp/add-subroute-ok)]
           [mui/button
            {:variant  "outlined"
             :on-click #(==> [::events/cancel-route-details])}
            (tr :actions/cancel)]]]))

     (when (and editing? (= :route-details mode))
       (let [selected-route (first (filter #(= selected-route-id (:id %)) routes))]
         ;; Key on route-id ensures fresh form state when switching routes
         ^{:key selected-route-id}
         [route-detail-view
          {:lipas-id     lipas-id
           :activity-k   activity-k
           :route-id     selected-route-id
           :route        selected-route
           :locale       locale
           :type-code    type-code
           :route-props  route-props
           :field-sorter field-sorter
           :read-only?   read-only?}]))

     (when config/debug?
       [mui/grid {:item true :xs 12}
        [lui/expansion-panel {:label "debug route props"}
         [mui/grid {:item true :xs 12}
          [:pre (with-out-str (pprint/pprint props))]]]])]))

(defn routes
  [{:keys [read-only? _route-props lipas-id activity-k value
           _locale _label _description _set-field _type-code]
    :as   props}]
  (let [routes (if read-only?
                 value
                 (<== [::subs/routes lipas-id activity-k]))]

    ;; Initialize routes in db if they don't exist yet, and set travel directions
    (when (not read-only?)
      (==> [::events/init-routes lipas-id activity-k]))

    [mui/grid {:container true :spacing 2 :style {:margin-top "1em"}}
     [mui/grid {:item true :xs 12}
      [multiple-routes (assoc props :routes routes)]]]))

(defn lipas-property
  [{:keys [read-only? lipas-id lipas-prop-k label description]}]
  (let [tr        (<== [:lipas.ui.subs/translator])
        geoms     (<== [::subs/geoms read-only?])
        geom-type (<== [::subs/geom-type read-only?])
        value     (<== [::subs/lipas-prop-value lipas-prop-k read-only?])
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
        {:tr          tr
         :prop-k      lipas-prop-k
         :read-only?  read-only?
         :label       label
         :description description
         :value       value
         :set-field   set-field
         :problems?   nil
         :geom-type   geom-type
         :geoms       geoms}])
     (when (and description
                ;; material-field already displays the helper-text, but the read-only field doesn't
                (or read-only?
                    (not (sports-sites-views/material-field? lipas-prop-k))))
       [:> FormHelperText description])]))

(defn make-field
  [{:keys [field edit-data locale prop-k read-only? lipas-id set-field activity-k type-code]}]
  (case (:type field)

    "select" [selects/select
              {:disabled    read-only?
               :deselect?   (if (:default field)
                              false
                              true)
               :items       (:opts field)
               :label       (get-in field [:label locale])
               :helper-text (get-in field [:description locale])
               :label-fn    (comp locale second)
               :value-fn    first
               :on-change   #(set-field prop-k %)
               :value       (or (get-in edit-data [prop-k])
                                (:default field))}]

    "multi-select" [:<>
                    [autocompletes/autocomplete
                     {:disabled        read-only?
                      :multi?          true
                      :items           (:opts field)
                      :label           (get-in field [:label locale])
                      #_#_:helper-text (get-in field [:description locale])
                      :label-fn        (comp locale second)
                      :value-fn        first
                      :on-change       #(set-field prop-k %)
                      :value           (get-in edit-data [prop-k])}]
                    [:> FormHelperText (get-in field [:description locale])]]

    "text-field" [lui-tf/text-field
                  {:disabled    read-only?
                   :label       (get-in field [:label locale])
                   :helper-text (get-in field [:description locale])
                   :fullWidth   true
                   :on-change   #(set-field prop-k locale %)
                   :value       (get-in edit-data [prop-k locale])}]

    "number" [lui-tf/text-field
              {:type        "number"
               :adornment   (:adornment field)
               :disabled    read-only?
               :label       (get-in field [:label locale])
               :helper-text (get-in field [:description locale])
               :fullWidth   true
               :on-change   #(set-field prop-k %)
               :value       (get-in edit-data [prop-k])}]

    "percentage" [lui-tf/text-field
                  (merge
                    {:type        "number"
                     :adornment   "%"
                     :disabled    read-only?
                     :label       (get-in field [:label locale])
                     :helper-text (get-in field [:description locale])
                     :fullWidth   true
                     :spec        common-schema/percentage
                     :on-change   #(set-field prop-k %)
                     :value       (get-in edit-data [prop-k])})]

    ;; FIXME: MUI-v5, outlined input is missing x-padding
    "textarea" [lui-tf/expandable-text-area
                {:disabled        read-only?
                 :variant         "outlined"
                 :label           (get-in field [:label locale])
                 :helper-text     (get-in field [:description locale])
                 :on-change       #(set-field prop-k locale %)
                 :InputLabelProps {:shrink (some? (get-in edit-data [prop-k locale]))}
                 :multiline       true
                 :rows            5
                 :fullWidth       true
                 :value           (get-in edit-data [prop-k locale])}]

    "checkboxes" [checkboxes
                  {:read-only?  read-only?
                   :items       (:opts field)
                   :label       (get-in field [:label locale])
                   :helper-text (get-in field [:description locale])
                   :label-fn    (comp locale second)
                   :value-fn    first
                   :on-change   #(set-field prop-k %)
                   :value       (get-in edit-data [prop-k])}]

    "checkbox" [checkbox
                {:read-only?  read-only?
                 :component   checkboxes/switch
                 :label       (get-in field [:label locale])
                 :helper-text (get-in field [:description locale])
                 :on-change   #(set-field prop-k %)
                 :value       (get-in edit-data [prop-k])}]

    "videos" [videos
              {:read-only?  read-only?
               :locale      locale
               :label       (get-in field [:label locale])
               :helper-text (get-in field [:description locale])
               :on-change   #(set-field prop-k %)
               :value       (->> (get-in edit-data [prop-k]))}]

    "images" [images
              {:read-only?  read-only?
               :lipas-id    lipas-id
               :locale      locale
               :label       (get-in field [:label locale])
               :helper-text (get-in field [:description locale])
               :image-props (:props field)
               :on-change   #(set-field prop-k %)
               :value       (->> (get-in edit-data [prop-k]))}]

    "routes" [routes
              {:read-only?  read-only?
               :lipas-id    lipas-id
               :locale      locale
               :label       (get-in field [:label locale])
               :description (get-in field [:description locale])
               :route-props (:props field)
               :set-field   (partial set-field prop-k)
               :activity-k  activity-k
               :type-code   type-code
               :value       (get-in edit-data [prop-k])}]

    "duration" [duration
                {:read-only?  read-only?
                 :lipas-id    lipas-id
                 :locale      locale
                 :label       (get-in field [:label locale])
                 :description (get-in field [:description locale])
                 :set-field   (partial set-field prop-k)
                 :value       (get-in edit-data [prop-k])}]

    "textlist" [textlist
                {:read-only?  read-only?
                 :lipas-id    lipas-id
                 :locale      locale
                 :label       (get-in field [:label locale])
                 :description (get-in field [:description locale])
                 :set-field   (partial set-field prop-k)
                 :value       (get-in edit-data [prop-k])}]

    "rules" [rules
             {:read-only?   read-only?
              :lipas-id     lipas-id
              :locale       locale
              :common-rules (:opts field)
              :label        (get-in field [:label locale])
              :description  (get-in field [:description locale])
              :set-field    (partial set-field prop-k)
              :value        (get-in edit-data [prop-k])}]

    "contacts" [contacts
                {:read-only?    read-only?
                 :lipas-id      lipas-id
                 :locale        locale
                 :label         (get-in field [:label locale])
                 :description   (get-in field [:description locale])
                 :set-field     (partial set-field prop-k)
                 :contact-props (:props field)
                 :value         (get-in edit-data [prop-k])}]

    "accessibility" [accessibility
                     {:read-only?          read-only?
                      :lipas-id            lipas-id
                      :locale              locale
                      :label               (get-in field [:label locale])
                      :description         (get-in field [:description locale])
                      :set-field           (partial set-field prop-k)
                      :accessibility-props (:props field)
                      :value               (get-in edit-data [prop-k])}]

    "lipas-property" [lipas-property
                      {:read-only?   read-only?
                       :lipas-id     lipas-id
                       :lipas-prop-k (:lipas-property field)
                       :label        (get-in field [:label locale])
                       :description  (get-in field [:description locale])}]

    (println (str "Unknown field type: " (:type field)))))

(defn view
  [{:keys [type-code display-data edit-data tr lipas-id can-edit?]}]
  (let [activity     (<== [::subs/activity-for-type-code type-code])
        activity-k   (-> activity :value keyword)
        field-sorter (<== [::subs/field-sorter activity-k])
        locale       (tr)
        set-field    (partial set-field* lipas-id :activities activity-k)
        editing?     (and can-edit? (<== [:lipas.ui.sports-sites.subs/editing? lipas-id]))
        read-only?   (not editing?)
        props        (or (some-> (get-in activity [:type->props type-code])
                                 (->> (select-keys (:props activity))))
                         (get activity :props))

        edit-data (or edit-data
                      ;; Should match the logic in ::edit-site which
                      ;; chooses which rev to base the edit-data on.
                      @(rf/subscribe [::sports-sites-subs/latest-rev lipas-id]))

        mode              (<== [::subs/mode])
        selected-route-id (<== [::subs/selected-route-id])
        activity-label    (get-in activity [:label locale])
        routes            (when editing?
                            (<== [::subs/routes lipas-id activity-k]))
        selected-route    (when selected-route-id
                            (first (filter #(= selected-route-id (:id %)) routes)))
        route-name        (get-in selected-route [:route-name locale])
        ;; In route-details or add-route mode, only show the routes field
        route-detail?     (and editing? (#{:route-details :add-route} mode))]

    [:<>

     (when (and (<== [:lipas.ui.sports-sites.subs/editing? lipas-id]) (not can-edit?))
       [:> Alert
        {:severity "info"}
        (tr :lipas.sports-site/no-permission-tab)])

     ;; Top bar: breadcrumb + lang selector on same row
     [mui/grid {:item true :xs 12 :style {:padding-top "0.5em" :padding-bottom "0.5em"}}
      [mui/grid {:container true :align-items "center" :justify-content "space-between" :wrap "nowrap"}
       [mui/grid {:item true :style {:display "flex" :align-items "center"}}
        [mui/typography {:color "text.secondary" :style {:margin-right "0.25em"}} "›"]
        [:> Breadcrumbs {:aria-label "breadcrumb"}
         (if route-detail?
           [:> Link {:component "button"
                     :underline "hover"
                     :color     "inherit"
                     :on-click  #(==> [::events/cancel-route-details])}
            activity-label]
           [mui/typography {:color "text.primary"} activity-label])
         (when route-detail?
           (if (= :route-details mode)
             [:> Link {:component "button"
                       :underline "hover"
                       :color     "inherit"
                       :on-click  #(==> [::events/cancel-route-details])}
              (tr :utp/routes-breadcrumb)]
             [mui/typography {:color "text.primary"}
              (tr :utp/routes-breadcrumb)]))
         (when (= :route-details mode)
           [mui/typography {:color "text.primary"}
            (or route-name (tr :utp/segment))])]]
       [mui/grid {:item true}
        [lang-selector {:locale locale}]]]]

     ;; Form — when in route detail mode, only show the routes field
     [mui/grid {:item true :xs 12}
      [nice-form {}
       (for [[prop-k {:keys [field]}] (sort-by field-sorter utils/reverse-cmp props)
             :when (or (not route-detail?) (= "routes" (:type field)))]
         [make-field
          {:key prop-k
           :field        field
           :prop-k       prop-k
           :edit-data    (get-in edit-data [:activities activity-k])
           :read-only?   read-only?
           :display-data (get-in display-data [:activities activity-k])
           :locale       locale
           :activity-k   activity-k
           :type-code    type-code
           :set-field    set-field
           :lipas-id     lipas-id}])]]

     ;; Debug
     (when config/debug?
       [:> Grid {:item true :xs 12}
        [layouts/expansion-panel {:label "debug"}
         [:pre (with-out-str (pprint/pprint activity))]]])]))

(comment

  (do
    (==> [:lipas.ui.map.events/set-zoom 14])
    (==> [:lipas.ui.map.events/set-center 6919553.618920735 445619.43358133035]))

  (==> [:lipas.ui.map.events/show-sports-site 607314]))
