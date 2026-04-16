(ns lipas.ui.sports-sites.images.views
  "Site-level image links editor.

  Unlike the UTP/Luontoon images in
  lipas.ui.sports-sites.activities.views, site-level images are URL-only: the
  files live in an external image bank owned by the facility's city (e.g.
  Loimaa). LIPAS stores only the metadata (url/alt-text/copyright/description)
  under CC BY 4.0."
  (:require ["@mui/material/GridLegacy$default" :as Grid]
            ["@mui/material/Popper$default" :as Popper]
            ["@mui/material/Tab$default" :as Tab]
            ["@mui/material/Tabs$default" :as Tabs]
            ["@mui/material/Typography$default" :as Typography]
            [clojure.string :as str]
            [lipas.ui.components.dialogs :as dialogs]
            [lipas.ui.components.tables :as tables]
            [lipas.ui.components.text-fields :as lui-tf]
            [lipas.ui.utils :refer [<== ==>] :as utils]
            [reagent.core :as r]))

(defn- lang-selector
  [{:keys [locale]}]
  [:> Tabs
   {:value (name locale)
    :indicator-color "primary"
    :text-color "inherit"
    :on-change #(==> [:lipas.ui.events/set-translator (keyword %2)])}
   [:> Tab {:value "fi" :label "Suomi"}]
   [:> Tab {:value "se" :label "Svenska"}]
   [:> Tab {:value "en" :label "English"}]])

(defn- valid-url? [s]
  (and (string? s)
       (let [lower (str/lower-case s)]
         (or (str/starts-with? lower "https://")
             (str/starts-with? lower "http://")))))

(defn image-dialog
  [{:keys [tr locale dialog-state on-save]}]
  [dialogs/dialog
   {:title (if (-> @dialog-state :data :url)
             (tr :lipas.sports-site.images/edit-image)
             (tr :lipas.sports-site.images/add-image))
    :open? (:open? @dialog-state)
    :on-save on-save
    :on-close #(swap! dialog-state assoc :open? false)
    :save-enabled? (valid-url? (-> @dialog-state :data :url))
    :save-label "Ok"
    :cancel-label (tr :actions/cancel)}
   [:> Grid {:container true :spacing 2}

    [:> Grid {:item true :xs 12}
     [lang-selector {:locale locale}]]

    [:> Grid {:item true :xs 12}
     [:> Typography {:variant "caption"}
      (tr :lipas.sports-site.images/cc-by-notice)]]

    [:> Grid {:item true :xs 12}
     [lui-tf/text-field
      {:fullWidth true
       :required true
       :value (-> @dialog-state :data :url)
       :on-change #(swap! dialog-state assoc-in [:data :url] %)
       :label (tr :lipas.sports-site.images/url)
       :helper-text (tr :lipas.sports-site.images/url-helper)
       :variant "outlined"
       :error (and (some? (-> @dialog-state :data :url))
                   (not (valid-url? (-> @dialog-state :data :url))))}]]

    (when (valid-url? (-> @dialog-state :data :url))
      [:> Grid {:item true :xs 12}
       [:img {:style {:max-width "100%"}
              :src (-> @dialog-state :data :url)}]])

    [:> Grid {:item true :xs 12}
     [lui-tf/text-field
      {:fullWidth true
       :value (-> @dialog-state :data :alt-text locale)
       :on-change #(swap! dialog-state assoc-in [:data :alt-text locale] %)
       :label (tr :lipas.sports-site.images/alt-text)
       :helper-text (tr :lipas.sports-site.images/alt-text-helper)
       :multiline true
       :rows 3
       :variant "outlined"}]]

    [:> Grid {:item true :xs 12}
     [lui-tf/text-field
      {:fullWidth true
       :value (-> @dialog-state :data :copyright locale)
       :on-change #(swap! dialog-state assoc-in [:data :copyright locale] %)
       :label (tr :lipas.sports-site.images/copyright)
       :helper-text (tr :lipas.sports-site.images/copyright-helper)
       :multiline true
       :rows 3
       :variant "outlined"}]]

    [:> Grid {:item true :xs 12}
     [lui-tf/text-field
      {:fullWidth true
       :value (-> @dialog-state :data :description locale)
       :on-change #(swap! dialog-state assoc-in [:data :description locale] %)
       :label (tr :lipas.sports-site.images/description)
       :helper-text (tr :lipas.sports-site.images/description-helper)
       :multiline true
       :rows 3
       :variant "outlined"}]]]])

(defn images
  "Site-level images editor.

  Props:
    :value        - current :images vector from edit-data
    :on-change    - called with updated vector
    :locale       - active UI locale
    :read-only?   - disables editing, still shows table/preview"
  [{:keys [value on-change locale read-only?]}]
  (r/with-let [state (r/atom (->> value
                                  (map #(assoc % :id (gensym)))
                                  (utils/index-by :id)))
               dialog-init {:open? false :data nil :mode :edit}
               dialog-state (r/atom dialog-init)
               popper-state (r/atom {:open? false})]
    (let [tr (<== [:lipas.ui.subs/translator])
          commit! #(on-change (->> @state vals (mapv (fn [m] (dissoc m :id)))))]
      [:> Grid {:container true :spacing 2}

       [image-dialog
        {:tr tr
         :locale locale
         :dialog-state dialog-state
         :on-save (fn []
                    (let [data (:data @dialog-state)]
                      (swap! state assoc (:id data) data))
                    (commit!)
                    (reset! dialog-state dialog-init))}]

       [:> Popper
        {:open (:open? @popper-state)
         :placement "right"
         :anchor-el (:anchor-el @popper-state)
         :modifiers [{:name "offset" :options {:offset [0 20]}}]}
        [:img {:style {:max-width "400px"}
               :src (:url @popper-state)}]]

       [:> Grid {:item true :xs 12}
        [:> Typography {:variant "caption"}
         (tr :lipas.sports-site.images/cc-by-notice)]]

       [:> Grid {:item true :xs 12}
        [tables/form-table
         {:key (str (count (vals @state)))
          :headers [[:_url (tr :lipas.sports-site.images/url)]
                    [:_description (tr :general/description)]]
          :read-only? read-only?
          :items (->> @state
                      vals
                      (map #(assoc % :_url (:url %)))
                      (map #(assoc % :_description (get-in % [:description locale]))))
          :on-add (fn []
                    (reset! dialog-state {:open? true :mode :add
                                          :data {:id (gensym)}}))
          :on-edit (fn [m]
                     (reset! dialog-state {:open? true :mode :edit
                                           :data (get @state (:id m))}))
          :on-delete (fn [m]
                       (swap! state dissoc (:id m))
                       (commit!))
          :on-custom-hover-in (fn [evt item]
                                (reset! popper-state {:open? true
                                                      :anchor-el (.-currentTarget evt)
                                                      :url (:url item)}))
          :on-custom-hover-out (fn [_ _]
                                 (swap! popper-state assoc :open? false))
          :on-user-sort (fn [items]
                          (reset! state (utils/index-by :id items))
                          (commit!))
          :add-tooltip (tr :actions/add)
          :edit-tooltip (tr :actions/edit)
          :delete-tooltip (tr :actions/delete)
          :confirm-tooltip (tr :confirm/delete-confirm)
          :add-btn-size "small"}]]])))

(defn view
  "Full images tab contents. Expects the site's :images vector and a lipas-id.
   Dispatches edit-field events to persist changes through the standard save
   flow."
  [{:keys [lipas-id edit-data display-data read-only?]}]
  (let [tr (<== [:lipas.ui.subs/translator])
        locale (tr)
        value (if read-only?
                (:images display-data)
                (:images edit-data))]
    [:> Grid {:container true :spacing 2}
     [:> Grid {:item true :xs 12}
      [:> Typography {:variant "h6"}
       (tr :lipas.sports-site.images/headline)]]
     [:> Grid {:item true :xs 12}
      [images
       {:value value
        :locale locale
        :read-only? read-only?
        :on-change (fn [v]
                     (==> [:lipas.ui.sports-sites.events/edit-field
                           lipas-id [:images] v]))}]]]))
