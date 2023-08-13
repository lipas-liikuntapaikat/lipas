(ns lipas.ui.sports-sites.activities.views
  (:require
   [clojure.pprint :as pprint]
   [clojure.string :as str]
   [lipas.ui.components :as lui]
   [lipas.ui.config :as config]
   [lipas.ui.mui :as mui]
   [lipas.ui.sports-sites.activities.events :as events]
   [lipas.ui.sports-sites.activities.subs :as subs]
   [lipas.ui.utils :refer [<== ==>] :as utils]
   [reagent.core :as r]))

(defn set-field
  [lipas-id & args]
  (==> [:lipas.ui.sports-sites.events/edit-field lipas-id (butlast args) (last args)]))

(defn nice-form
  [props & children]
  [into [mui/grid {:container true :spacing 2}]
   (for [child children]
     [mui/grid {:item true :xs 12}
      child])])

(defn form-label
  [{:keys [label]}]
  [mui/form-label {:style {:color "gray"}}
   label])

(def sort-order
  (->>
   [:route-name
    :type
    :activities
    :description-short
    :description-long
    :duration
    :travel-direction
    :route-marking
    :habitat
    :character
    :season
    :species
    :waters
    :fish-population
    :fishing-methods
    :images
    :videos
    :arrival
    :accessibility
    :rules
    :parking]
   (reverse)
   (map-indexed (fn [idx k] [k idx]))
   (into {})))

(defn field-sorter
  [[k _]]
  (get sort-order k -1))

(defn duration
  [{:keys [tr locale label set-field value]}]
  [mui/form-control {:focused true}
   [form-label {:label label}]

   [mui/grid {:container true :spacing 2}

    [mui/grid {:item true :xs 3}
     [lui/text-field
      {:type      "number"
       :value     (:min value)
       :label     "Min"
       :on-change #(set-field :min %)}]]

    [mui/grid {:item true :xs 3}
     [lui/text-field
      {:type      "number"
       :value     (:max value)
       :label     "Max"
       :on-change #(set-field :max %)}]]

    [mui/grid {:item true :xs 6}
     [lui/select
      {:items     [{:label "minuuttia" :value "minutes" :sort 1}
                   {:label "tuntia" :value "hours" :sort 2}
                   {:label "päivää" :value "days" :sort 3}]
       :sort-fn   :sort
       :style     {:min-width "170px"}
       :value     (:unit value)
       :label     "Yksikkö"
       :on-change #(set-field :unit %)}]]]])

(defn image-dialog
  [{:keys [tr locale dialog-state on-save on-close]}]
  [lui/dialog
   {:title         "Image"
    :open?         (:open? @dialog-state)
    :on-save       on-save
    :on-close      #(swap! dialog-state assoc :open? false)
    :save-enabled? (and (some-> @dialog-state :data :url str/lower-case (str/starts-with? "http"))
                        (-> @dialog-state :data :description seq))
    :save-label    "Ok"
    :cancel-label  (tr :actions/cancel)}
   [mui/grid {:container true :spacing 2}
    [mui/grid {:item true :xs 12}
     [lui/text-field
      {:value     (-> @dialog-state :data :url)
       :fullWidth true
       :on-change (fn [s] (swap! dialog-state assoc-in [:data :url] s))
       :label     "Url"}]]

    [mui/grid {:item true :xs 12}
     [lui/text-field
      {:fullWidth true
       :value     (-> @dialog-state :data :description locale)
       :on-change #(swap! dialog-state assoc-in [:data :description locale] %)
       :label     (tr :general/description)
       :multiline true
       :variant   "outlined"}]]

    [mui/grid {:item true :xs 12}
     (when-let [url (:url @dialog-state)]
       [:img
        {:style {:max-width "100%"}
         :src   (:url @dialog-state)}])]]])

(defn images
  [{:keys [value on-change locale label tr read-only?]}]
  (r/with-let [state (r/atom (->> value
                                  (map #(assoc % :id (gensym)))
                                  (utils/index-by :id)))
               dialog-init-state {:open? false
                                  :data  nil
                                  :mode  :edit}
               dialog-state (r/atom dialog-init-state)]

    (let [tr (<== [:lipas.ui.subs/translator])]
      [mui/grid {:container true :spacing 2}

       ;; Dialog
       [image-dialog
        {:tr           tr
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
       [mui/grid {:item true :xs 12}
        [form-label {:label label}]]

       ;; Table
       [mui/grid {:item true :xs 12}
        [lui/form-table
         {:headers         [[:url "Linkki"]
                            [:_description "Kuvaus"]]
          :read-only?      false
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
          :add-tooltip     "Lisää"
          :edit-tooltip    (tr :actions/edit)
          :delete-tooltip  (tr :actions/delete)
          :confirm-tooltip (tr :confirm/press-again-to-delete)
          :add-btn-size    "small"
          :key-fn          :url}]]])))

(defn video-dialog
  [{:keys [tr locale dialog-state on-save on-close]}]
  [lui/dialog
   {:title         "Video"
    :open?         (:open? @dialog-state)
    :on-save       on-save
    :on-close      #(swap! dialog-state assoc :open? false)
    :save-enabled? (and (some-> @dialog-state :data :url str/lower-case (str/starts-with? "http"))
                        (-> @dialog-state :data :description seq))
    :save-label    "Ok"
    :cancel-label  (tr :actions/cancel)}
   [mui/grid {:container true :spacing 2}
    [mui/grid {:item true :xs 12}
     [lui/text-field
      {:value     (-> @dialog-state :data :url)
       :fullWidth true
       :on-change (fn [s] (swap! dialog-state assoc-in [:data :url] s))
       :label     "Url"}]]

    [mui/grid {:item true :xs 12}
     [lui/text-field
      {:fullWidth true
       :value     (-> @dialog-state :data :description locale)
       :on-change #(swap! dialog-state assoc-in [:data :description locale] %)
       :label     "Kuvaus"
       :multiline true
       :variant   "outlined"}]]]])

(defn videos
  [{:keys [value on-change locale label tr read-only?]}]
  (r/with-let [state (r/atom (->> value
                                  (map #(assoc % :id (gensym)))
                                  (utils/index-by :id)))
               dialog-init-state {:open? false
                                  :data  nil
                                  :mode  :edit}
               dialog-state (r/atom dialog-init-state)]

    (let [tr (<== [:lipas.ui.subs/translator])]
      [mui/grid {:container true :spacing 2}

       ;; Dialog
       [video-dialog
        {:tr           tr
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
       [mui/grid {:item true :xs 12}
        [form-label {:label label}]]

       ;; Table
       [mui/grid {:item true :xs 12}
        [lui/form-table
         {:headers         [[:url "Linkki"]
                            [:_description "Kuvaus"][]]
          :read-only?      false
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
          :add-tooltip     "Lisää"
          :edit-tooltip    (tr :actions/edit)
          :delete-tooltip  (tr :actions/delete)
          :confirm-tooltip (tr :confirm/press-again-to-delete)
          :add-btn-size    "small"
          :key-fn          :url}]]])))

(declare make-field)

(defn route-form
  [{:keys [locale geom-type lipas-id route-props state]}]
  (into [nice-form {}]
        (for [[prop-k {:keys [field]}] (sort-by field-sorter utils/reverse-cmp route-props)]
          (make-field
           {:field        field
            :prop-k       prop-k
            :edit-data    @state
            :display-data @state
            :locale       locale
            :set-field    (fn [& args]
                            (let [path (butlast args)
                                  v (last args)]
                              (swap! state assoc-in path v)))
            :geom-type    geom-type
            :lipas-id     lipas-id}))))

(defn routes
  [{:keys [read-only? route-props lipas-id _display-data _edit-data
           locale geom-type label description set-field activity-k]
    :as   props}]
  (let [tr     (<== [:lipas.ui.subs/translator])
        mode   (<== [::subs/mode])
        fids   (<== [::subs/selected-features])
        routes (<== [::subs/routes lipas-id activity-k])

        selected-route-id (<== [::subs/selected-route-id])

        editing? (not read-only?)]

    (r/with-let [route-form-state (r/atom {})]
      [:<>

       (when (= :default mode)
         [:<>
          (when (seq routes)
            [mui/grid {:item true :xs 12}
             [form-label {:label label}]
             [lui/table
              {:headers
               [[:_route-name "Nimi"]
                [:route-length "Pituus (km)"]]
               :items          (->> routes
                                    (mapv (fn [m]
                                            (assoc m :_route-name (get-in m [:route-name locale])))))
               :on-mouse-enter (fn [item]
                                 (==> [:lipas.ui.map.events/highlight-features (:fids item)]))
               :on-mouse-leave (fn [_]
                                 (==> [:lipas.ui.map.events/highlight-features #{}]))

               :on-select (fn [route]
                            (==> [::events/select-route lipas-id (dissoc route :_route-name)])
                            (reset! route-form-state (dissoc route :fids)))}]])
          (when-not read-only?
            [mui/grid {:item true :xs 12}
             [mui/button
              {:variant  "contained"
               :color    "secondary"
               :style    {:margin-top "0.5em" :margin-bottom "0.5em"}
               :on-click (fn []
                           (reset! route-form-state {})
                           (==> [::events/add-route lipas-id activity-k]))}
              "Lisää reitti"]])])

       (when (and editing? (= :add-route mode))
         [:<>

          [mui/grid {:item true :xs 12}
           [mui/typography {:variant "body2"} "Valitse reitin osat kartalta"]]

          [mui/grid {:item true :xs 12}
           [mui/button
            {:variant  "contained"
             :color    "secondary"
             :on-click #(==> [::events/finish-route])}
            "OK"]]])

       (when (and editing? (= :route-details mode))
         [:<>
          [mui/paper {:style
                      {:margin-top       "1em"
                       :margin-bottom    "1.5em"
                       :padding          "0.5em"
                       :background-color mui/gray3}}
           [mui/typography {:variant "body2"}
            label]

           [mui/typography {:variant "caption"}
            description]]

          [route-form
           {:locale      locale
            :lipas-id    lipas-id
            :geom-type   geom-type
            :route-props route-props
            :state       route-form-state}]

          ;; Buttons
          [mui/grid {:container true :spacing 1}

           ;; Done
           [mui/grid {:item true}
            [mui/button
             {:variant  "contained"
              :color    "secondary"
              :on-click #(==> [::events/finish-route-details
                               {:fids       fids
                                :activity-k activity-k
                                :id         selected-route-id
                                :route      @route-form-state
                                :lipas-id   lipas-id}])}
             "Reitti valmis"]]

           ;; Delete
           [mui/grid {:item true}
            [mui/button
             {:variant  "contained"
              :on-click #(==> [:lipas.ui.events/confirm
                               "Haluatko varmasti poistaa tämän reitin?"
                               (fn []
                                 (==> [::events/delete-route lipas-id activity-k selected-route-id]))])}
             (tr :actions/delete)]]

           ;; Cancel
           [mui/grid {:item true}
            [mui/button
             {:variant  "contained"
              :on-click #(==> [::events/cancel-route-details])}
             (tr :actions/cancel)]]]])

       (when config/debug?
         [mui/grid {:item true :xs 12}
          [lui/expansion-panel {:label "debug route props"}
           [mui/grid {:item true :xs 12}
            [:pre (with-out-str (pprint/pprint props))]]]])

       ])))

(defn make-field
  [{:keys [field edit-data locale prop-k read-only? lipas-id set-field activity-k]}]
  (condp = (:type field)

    "select" [lui/select
              {:disabled    read-only?
               :items       (:opts field)
               :label       (get-in field [:label locale])
               :helper-text (get-in field [:description locale])
               :label-fn    (comp locale second)
               :value-fn    first
               :on-change   #(set-field prop-k %)
               :value       (get-in edit-data [prop-k])}]

    "multi-select" [lui/autocomplete
                    {:disabled    read-only?
                     :multi?      true
                     :items       (:opts field)
                     :label       (get-in field [:label locale])
                     :helper-text (get-in field [:description locale])
                     :label-fn    (comp locale second)
                     :value-fn    first
                     :on-change   #(set-field prop-k %)
                     :value       (get-in edit-data [prop-k])}]

    "text-field" [lui/text-field
                  {:disabled    read-only?
                   :label       (get-in field [:label locale])
                   :helper-text (get-in field [:description locale])
                   :fullWidth   true
                   :on-change   #(set-field prop-k locale %)
                   :value       (get-in edit-data [prop-k locale])}]

    "textarea" [lui/text-field
                {:disabled        read-only?
                 :variant         "outlined"
                 :label           (get-in field [:label locale])
                 :helper-text     (get-in field [:description locale])
                 :on-change       #(set-field prop-k locale %)
                 :InputLabelProps {:shrink (some? (get-in edit-data [prop-k locale]))}
                 :multiline       true
                 :fullWidth       true
                 :value           (get-in edit-data [prop-k locale])}]

    "videos" [videos
              {:read-only?  read-only?
               :locale      locale
               :label       (get-in field [:label locale])
               :helper-text (get-in field [:description locale])
               :on-change   #(set-field prop-k %)
               :value       (->> (get-in edit-data [prop-k]))}]

    "images" [images
              {:read-only?  read-only?
               :locale      locale
               :label       (get-in field [:label locale])
               :helper-text (get-in field [:description locale])
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
               :value       (get-in edit-data [prop-k])}]

    "duration" [duration
                {:read-only?  read-only?
                 :lipas-id    lipas-id
                 :locale      locale
                 :label       (get-in field [:label locale])
                 :description (get-in field [:description locale])
                 :route-props (:props field)
                 :set-field   (partial set-field prop-k)
                 :value       (get-in edit-data [prop-k])}]

    (println (str "Unknown field type: " (:type field)))))

(defn view
  [{:keys [type-code display-data edit-data geom-type tr read-only?
           lipas-id]}]
  (let [activities (<== [::subs/activities-for-type type-code])
        activity-k (-> activities :value keyword)
        locale     (tr)
        set-field  (partial set-field lipas-id :activities activity-k)
        editing?   (<== [:lipas.ui.sports-sites.subs/editing? lipas-id])
        read-only? (not editing?)]

    (if read-only?
      [mui/typography "Vasta editointinäkymä on olemassa lajeille. Kirjaudu sisään ja siirry kynäsymbolista muokkaustilaan."]

      [:<>

       ;; Header
       [mui/grid {:item true :xs 12}
        [mui/typography {:variant "h6"}
         (get-in activities [:label locale])]]

       ;; Locale selector
       [mui/grid {:item true :xs 12 :style {:padding-top "0.5em" :padding-bottom "0.5em"}}
        #_[lui/select
           {:value     locale
            :items     {:fi "Suomi"
                        :se "Svenska"
                        :en "English"}
            :label     "Valitse kieli"
            :label-fn  second
            :value-fn  first
            :on-change #(==> [:lipas.ui.events/set-translator %])}]

        ;; Language selector
        [mui/tabs
         {:value          (name locale)
          :indicatorColor "primary"
          :on-change      #(==> [:lipas.ui.events/set-translator (keyword %2)])}
         [mui/tab {:value "fi" :label "Suomi"}]
         [mui/tab {:value "se" :label "Svenska"}]
         [mui/tab {:value "en" :label "English"}]]]

       ;; Form
       [mui/grid {:item true :xs 12}
        [into [nice-form {}]
         (for [[prop-k {:keys [field]}] (-> activities :props
                                            (->> (sort-by field-sorter utils/reverse-cmp)))]
           [make-field
            {:field        field
             :prop-k       prop-k
             :edit-data    (get-in edit-data [:activities activity-k])
             :read-only?   read-only?
             :display-data (get-in display-data [:activities activity-k])
             :locale       locale
             :activity-k   activity-k
             :set-field    set-field
             :geom-type    geom-type
             :lipas-id     lipas-id}])]]

       ;; Debug
       (when config/debug?
         [mui/grid {:item true :xs 12}
          [lui/expansion-panel {:label "debug"}
           [:pre (with-out-str (pprint/pprint activities))]]])])))

(comment

  (do
    (==> [:lipas.ui.map.events/set-zoom 14])
    (==> [:lipas.ui.map.events/set-center 6919553.618920735 445619.43358133035]))

  (==> [:lipas.ui.map.events/show-sports-site 607314])

  )
