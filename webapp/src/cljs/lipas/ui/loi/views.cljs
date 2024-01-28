(ns lipas.ui.loi.views
  (:require
   [clojure.string :as str]
   [lipas.ui.components :as lui]
   [lipas.ui.components.buttons :as buttons]
   [lipas.ui.loi.events :as events]
   [lipas.ui.loi.subs :as subs]
   [lipas.ui.map.events :as map-events]
   [lipas.ui.map.import :as import]
   [lipas.ui.mui :as mui]
   [lipas.ui.utils :refer [<== ==>] :as utils]
   [reagent.core :as r]))

(defn form-label
  [{:keys [label]}]
  [mui/form-label {:style {:color "gray"}}
   label])

(defn image-dialog
  [{:keys [tr locale dialog-state on-save on-close lipas-id]}]
  [lui/dialog
   {:title         (if (-> @dialog-state :data :url)
                     "Valokuva"
                     "Lisää valokuva")
    :open?         (:open? @dialog-state)
    :on-save       on-save
    :on-close      #(swap! dialog-state assoc :open? false)
    :save-enabled? (and (some-> @dialog-state :data :url str/lower-case (str/starts-with? "http"))
                        (-> @dialog-state :data :description seq))
    :save-label    "Ok"
    :cancel-label  (tr :actions/cancel)}
   [mui/grid {:container true :spacing 2}
    [mui/grid {:item true :xs 12}

     [:input
      {:type      "file"
       :accept    (str/join "," ["image/png" "image/jpeg" "image/jpg" "image/webp"])
       :on-change #(==> [:lipas.ui.sports-sites.activities.events/upload-image
                         (-> % .-target .-files)
                         lipas-id
                         (fn [url]
                           (swap! dialog-state assoc-in [:data :url] url))])}]

     [mui/grid {:item true :xs 12}
      (when-let [url (-> @dialog-state :data :url)]
        [:img
         {:style {:max-width "100%"}
          :src   url}])]

     ;; For debug
     #_[lui/text-field
        {:value     (-> @dialog-state :data :url)
         :fullWidth true
         :on-change (fn [s] (swap! dialog-state assoc-in [:data :url] s))
         :label     "Url"}]]

    [mui/grid {:item true :xs 12}
     [lui/text-field
      {:fullWidth true
       :required  true
       :value     (-> @dialog-state :data :description locale)
       :on-change #(swap! dialog-state assoc-in [:data :description locale] %)
       :label     (tr :general/description)
       :multiline true
       :rows      5
       :variant   "outlined"}]]]])

(defn images
  [{:keys [value on-change locale label tr read-only? lipas-id]}]
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
         :lipas-id     lipas-id
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
          :add-tooltip     "Lisää"
          :edit-tooltip    (tr :actions/edit)
          :delete-tooltip  (tr :actions/delete)
          :confirm-tooltip (tr :confirm/press-again-to-delete)
          :add-btn-size    "small"
          :key-fn          :url}]]])))

(defn form
  [{:keys [tr locale read-only? view-mode]}]
  (let [loi-cats     (<== [::subs/loi-categories])
        zoomed?      (<== [:lipas.ui.map.subs/zoomed-for-drawing?])
        geom-type    (<== [::subs/geom-type])
        geoms        (<== [::subs/geoms])
        statuses     (<== [::subs/statuses])
        display-data (<== [::subs/selected-loi])
        editing?     (not read-only?)
        edit-data    (<== [::subs/editing-loi])
        loi-cat      (:loi-category edit-data)
        loi-type     (:loi-type edit-data)
        loi-props    (<== [::subs/props loi-cat loi-type])
        form-data    (if read-only? display-data edit-data)]

    [mui/grid {:container true :spacing 2 :style {:padding "1em"}}

     ;; Import
     [import/import-geoms-view
      {:geom-type geom-type
       :on-import (fn []
                    (condp = view-mode
                      :editing (==> [::map-events/import-selected-geoms])
                      :adding  (==> [::map-events/import-selected-geoms-to-new])))
       :show-replace? (= :editing view-mode)}]

     ;; Header
     [mui/grid {:item true :xs 12}
      [mui/grid
       {:container   true
        :style       {:flex-wrap "nowrap"}
        :align-items :center}

       ;; Headline
       [mui/grid {:item true :style {:margin-top "0.5em" :flex-grow 1}}
        [mui/typography {:variant "h6"}
         (condp = view-mode
           :editing "Muokkaa kohdetta"
           :adding  "Lisää muu kohde"
           (get-in form-data [:name locale] "Ei nimeä"))]]

       ;; Close button
       [mui/grid {:item true}
        (when (not editing?)
          [mui/icon-button
           {:style    {:margin-left "-0.25em"}
            :on-click #(==> [:lipas.ui.map.events/unselected])}
           [mui/icon "close"]])]]]

     ;; Status
     [mui/grid {:item true :xs 12}
      [lui/select
       {:items     statuses
        :disabled  read-only?
        :label     "Status"
        :on-change #(==> [::events/edit-loi-field :status %])
        :value-fn  first
        :label-fn  (comp locale second)
        :value     (:status form-data)}]]

     ;; Loi category
     [mui/grid {:item true :xs 12}
      [lui/select
       {:items     loi-cats
        :disabled  read-only?
        :label     "Kategoria"
        :value-fn  first
        :label-fn  (comp locale :label second)
        :on-change #(==> [::events/edit-loi-field :loi-category %])
        :value     loi-cat}]]

     ;; Loi type
     [mui/grid {:item true :xs 12}
      [lui/autocomplete
       {:items     (vals (get-in loi-cats [loi-cat :types]))
        :disabled  read-only?
        :label     "Kohteen tyyppi"
        :value-fn  :value
        :label-fn  (comp locale :label)
        :on-change #(==> [::events/edit-loi-field :loi-type %])
        :value     loi-type}]]

     ;; Zoom helper text
     (when (and editing? (not zoomed?))
       [mui/grid {:item true :xs 12}
        [mui/typography {:variant "body2" :color :error}
         (tr :map/zoom-closer)]])

     ;; Loi type & category helper text
     (when (and editing? (or (not loi-cat) (not loi-type)))
       [mui/grid {:item true :xs 12}
        [mui/typography {:variant "body2" :color :error}
         "Kategoria ja tyyppi tulee olla valittuna ennen kartalle lisäämistä"]])

     ;; Add to map button
     (when (and editing? (not geoms))
       (let [disabled? (or (not loi-cat) (not loi-type) (not zoomed?))]
         [mui/grid {:item true}
          [mui/button
           {:disabled disabled?
            :color    "secondary"
            :variant  "contained"
            :on-click #(==> [:lipas.ui.map.events/start-adding-geom geom-type])}
           [mui/icon "add_location"]
           (tr :map/add-to-map)]]))

     (when (and editing? (not geoms) (#{"Polygon"} geom-type))
       [mui/grid {:item true}
        [mui/button
         {:color    "secondary"
          :variant  "contained"
          :on-click #(==> [::map-events/toggle-import-dialog])}
         (tr :map.import/tooltip)]])

     ;; Props
     (when loi-type
       (into [:<>]
             (for [[k {:keys [field] :as v}] loi-props]
               (let [field-type (-> v :field :type)]
                 [mui/grid {:item true :xs 12}
                  (condp = field-type
                    "checkbox" [lui/checkbox
                                {:disabled  read-only?
                                 :value     (get form-data k)
                                 :label     (get-in v [:field :label locale])
                                 :on-change #(==> [::events/edit-loi-field k %])}]

                    "textarea" [lui/text-field
                                {:fullWidth true
                                 :multiline true
                                 :disabled  read-only?
                                 :rows      5
                                 :value     (get-in form-data [k locale])
                                 :on-change #(==> [::events/edit-loi-field k locale %])
                                 :label     (get-in v [:field :label locale])}]

                    "images" [images
                              {:read-only?  read-only?
                               :lipas-id    0 ;; TODO think
                               :locale      locale
                               :label       (get-in field [:label locale])
                               :helper-text (get-in field [:description locale])
                               :on-change   #(==> [::events/edit-loi-field k %])
                               :value       (->> (get-in form-data [k]))}]

                    "select" [lui/select
                              {:disabled    read-only?
                               :items       (:opts field)
                               :label       (get-in field [:label locale])
                               :helper-text (get-in field [:description locale])
                               :label-fn    (comp locale second)
                               :value-fn    first
                               :on-change   #(==> [::events/edit-loi-field k %])
                               :value       (get-in form-data [k])}]

                    ;; Fallback
                    [lui/text-field
                     {:fullWidth true
                      :disabled  read-only?
                      :value     (get-in form-data [k locale])
                      :on-change #(==> [::events/edit-loi-field k locale %])
                      :label     (get-in v [:field :label locale])}])]))))

     ;; Landing bay for floating controls
     [mui/grid {:item true :xs 12 :style {:height "4em"}}]]))

(defn delete-dialog
  [{:keys [tr loi on-close]}]
  (r/with-let [year   (r/atom utils/this-year)
               status (r/atom "out-of-service-permanently")]
    (let [locale   (tr)
          data     loi
          statuses (<== [::subs/delete-statuses])]

      [lui/dialog
       {:title         (tr :lipas.sports-site/delete (get-in data [:name locale]))
        :cancel-label  (tr :actions/cancel)
        :on-close      on-close
        :save-enabled? (some? status)
        :on-save       (fn []
                         (==> [::events/delete data @status @year])
                         (on-close))
        :save-label    (tr :actions/delete)}

       [mui/grid {:container true :spacing 2}
        [mui/grid {:item true :xs 12}
         [lui/select
          {:label     (tr :lipas.sports-site/delete-reason)
           :required  true
           :value     @status
           :items     statuses
           :on-change #(reset! status %)
           :value-fn  first
           :label-fn  (comp locale second)}]]

        (when (= "out-of-service-permanently" @status)
          [mui/grid {:item true :xs 12}
           [lui/year-selector
            {:label     (tr :time/year)
             :value     @year
             :on-change #(reset! year %)}]])]])))

(defn view
  []
  (r/with-let [delete-dialog-open? (r/atom false)]
    (let [logged-in?   (<== [:lipas.ui.user.subs/logged-in?])
          tr           (<== [:lipas.ui.subs/translator])
          locale       (tr)
          view-mode    (<== [::subs/view-mode])
          edits-valid? (<== [::subs/edits-valid?])
          edit-data    (<== [::subs/editing-loi])
          geoms        (<== [::subs/geoms])]
      [:<>

       [mui/tabs
        {:style          {:margin-top "0.5em"}
         :value          (name locale)
         :indicatorColor "primary"
         :on-change      #(==> [:lipas.ui.events/set-translator (keyword %2)])}
        [mui/tab {:value "fi" :label "Suomi"}]
        [mui/tab {:value "se" :label "Svenska"}]
        [mui/tab {:value "en" :label "English"}]]

       [form
        {:tr         tr
         :locale     locale
         :view-mode  view-mode
         :read-only? (= :display view-mode)}]

       (when (and @delete-dialog-open? logged-in? (#{:display} view-mode))
         [delete-dialog
          {:tr       tr
           :loi      edit-data
           :on-close #(reset! delete-dialog-open? false)}])

       [lui/floating-container
        {:bottom 0 :background-color "transparent"}
        [mui/grid {:container true :spacing 1 :align-items "center" :style {:padding "0.5em"}}

         (when (and logged-in? (= :display view-mode))
           [mui/grid {:item true}
            [buttons/edit-button
             {:color    "secondary"
              :disabled false
              :active?  (= :editing view-mode)
              :on-click #(==> [::events/start-editing])
              :tooltip  (tr :actions/edit)}]])

         (when (and  logged-in? (#{:editing :adding} view-mode))
           [mui/grid {:item true}
            [buttons/save-button
             {:color    "secondary"
              :disabled (not edits-valid?)
              :on-click #(==> [::events/save edit-data geoms])
              :tooltip  (tr :actions/save)}]])

         (when (and logged-in? (#{:editing :adding} view-mode))
           [mui/grid {:item true}
            [buttons/discard-button
             {:color    "default"
              :disabled false
              :on-click #(==> [::events/discard-edits])
              :tooltip  (tr :actions/discard)}]])

         (when (and logged-in? (#{:display} view-mode))
           [mui/grid {:item true}
            [buttons/delete-button
             {:color    "default"
              :disabled false
              :on-click #(reset! delete-dialog-open? true)
              :tooltip  (tr :actions/delete)}]])]]])))
