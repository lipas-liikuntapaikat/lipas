(ns lipas.ui.sports-sites.views
  (:require [goog.object :as gobj]
            [lipas.ui.charts :as charts]
            [lipas.ui.components :as lui]
            [lipas.ui.energy.views :as energy]
            [lipas.ui.mui :as mui]
            [lipas.ui.sports-sites.events :as events]
            [lipas.ui.sports-sites.subs :as subs]
            [lipas.ui.utils :refer [<== ==>] :as utils]
            [reagent.core :as r]))

(defn form [{:keys [tr display-data edit-data types size-categories
                    admins owners on-change read-only? sub-headings?]}]
  (let [locale (tr)]
    [lui/form {:read-only? read-only?}

     (when sub-headings?
       [lui/sub-heading {:label "Liikuntapaikka"}])

     ;; Name
     {:label      (tr :lipas.sports-site/name)
      :value      (-> display-data :name)
      :form-field [lui/text-field
                   {:spec      :lipas.sports-site/name
                    :required  true
                    :value     (-> edit-data :name)
                    :on-change #(on-change :name %)}]}

     ;; Marketing name
     {:label      (tr :lipas.sports-site/marketing-name)
      :value      (-> display-data :marketing-name)
      :form-field [lui/text-field
                   {:spec      :lipas.sports-site/marketing-name
                    :value     (-> edit-data :marketing-name)
                    :on-change #(on-change :marketing-name %)}]}

     ;; Type
     {:label      (tr :type/name)
      :value      (-> display-data :type :name)
      :form-field [lui/select
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
        :form-field [lui/select
                     {:value     (-> edit-data :type :size-category)
                      :items     size-categories
                      :value-fn  first
                      :label-fn  (comp locale second)
                      :on-change #(on-change :type :size-category %)}]})

     ;; Construction year
     {:label      (tr :lipas.sports-site/construction-year)
      :value      (-> display-data :construction-year)
      :form-field [lui/year-selector
                   {:value     (-> edit-data :construction-year)
                    :on-change #(on-change :construction-year %)}]}

     ;; Renovation years
     {:label      (tr :lipas.sports-site/renovation-years)
      :value      (-> display-data :renovation-years)
      :form-field [lui/year-selector
                   {:multi?    true
                    :value     (-> edit-data :renovation-years)
                    :on-change #(on-change :renovation-years %)}]}

     ;; Comment
     {:label (tr :lipas.sports-site/comment)
      :value (-> display-data :comment)
      :form-field
      [lui/text-field
       {:spec      :lipas.sports-site/comment
        :value     (-> edit-data :comment)
        :multiline true
        :on-change #(on-change :comment %)}]}

     (when sub-headings?
       [lui/sub-heading {:label "Yhteystiedot"}])

     ;; Email
     {:label      (tr :lipas.sports-site/email-public)
      :value      (-> display-data :email)
      :form-field [lui/text-field
                   {:value     (-> edit-data :email)
                    :spec      :lipas.sports-site/email
                    :on-change #(on-change :email %)}]}

     ;; Phone number
     {:label      (tr :lipas.sports-site/phone-number)
      :value      (-> display-data :phone-number)
      :form-field [lui/text-field
                   {:value     (-> edit-data :phone-number)
                    :spec      :lipas.sports-site/phone-number
                    :on-change #(on-change :phone-number %)}]}

     ;; WWW
     {:label      (tr :lipas.sports-site/www)
      :value      (-> display-data :www)
      :form-field [lui/text-field
                   {:value     (-> edit-data :www)
                    :spec      :lipas.sports-site/www
                    :on-change #(on-change :www %)}]}

     (when sub-headings?
       [lui/sub-heading {:label "Omistus"}])

     ;; Owner
     {:label      (tr :lipas.sports-site/owner)
      :value      (-> display-data :owner)
      :form-field [lui/select
                   {:value     (-> edit-data :owner)
                    :required  true
                    :items     owners
                    :value-fn  first
                    :label-fn  (comp locale second)
                    :on-change #(on-change :owner %)}]}

     ;; Admin
     {:label      (tr :lipas.sports-site/admin)
      :value      (-> display-data :admin)
      :form-field [lui/select
                   {:value     (-> edit-data :admin)
                    :required  true
                    :items     admins
                    :value-fn  first
                    :label-fn  (comp locale second)
                    :on-change #(on-change :admin %)}]}]))

(defn location-form [{:keys [tr edit-data display-data cities on-change
                             read-only? sub-headings?]}]
  (let [locale (tr)]
    [lui/form
     {:read-only? read-only?}

     (when sub-headings?
       [lui/sub-heading {:label "Osoite"}])

     ;; Address
     {:label      (tr :lipas.location/address)
      :value      (-> display-data :address)
      :form-field [lui/text-field
                   {:value     (-> edit-data :address)
                    :spec      :lipas.location/address
                    :required  true
                    :on-change #(on-change :address %)}]}

     ;; Postal code
     { :label     (tr :lipas.location/postal-code)
      :value      (-> display-data :postal-code)
      :form-field [lui/text-field
                   {:value     (-> edit-data :postal-code)
                    :required  true
                    :spec      :lipas.location/postal-code
                    :on-change #(on-change :postal-code %)}]}

     ;; Postal office
     {:label      (tr :lipas.location/postal-office)
      :value      (-> display-data :postal-office)
      :form-field [lui/text-field
                   { :value    (-> edit-data :postal-office)
                    :spec      :lipas.location/postal-office
                    :on-change #(on-change :postal-office %)}]}

     ;; City
     {:label      (tr :lipas.location/city)
      :value      (-> display-data :city :name)
      :form-field [lui/select
                   {:value     (-> edit-data :city :city-code)
                    :required  true
                    :items     cities
                    :label-fn  (comp locale :name)
                    :value-fn  :city-code
                    :on-change #(on-change :city :city-code %)}]}

     ;; Neighborhood
     {:label      (tr :lipas.location/neighborhood)
      :value      (-> display-data :city :neighborhood)
      :form-field [lui/text-field
                   {:value    (-> edit-data :city :neighborhood)
                    :spec      :lipas.location.city/neighborhood
                    :on-change #(on-change :city :neighborhood %)}]}]))

(defn report-readings-button [{:keys [tr lipas-id close]}]
  [mui/button
   {:style    {:margin-top "1em"}
    :on-click #(==> [:lipas.ui.events/confirm
                     (tr :confirm/save-basic-data?)
                     (fn []
                       (==> [::events/save-edits lipas-id])
                       (close)
                       (==> [:lipas.ui.events/report-energy-consumption lipas-id]))
                     (fn []
                       (==> [::events/discard-edits lipas-id])
                       (close)
                       (==> [:lipas.ui.events/report-energy-consumption lipas-id]))])}
   [mui/icon "add"]
   (tr :lipas.user/report-energy-and-visitors)])

(defn energy-consumption-view [{:keys [tr display-data lipas-id
                                       editing? close cold? user-can-publish?]
                                :or   {cold? false}}]
  (r/with-let [selected-year (r/atom {})
               selected-tab  (r/atom 0)]

    ;; Chart/Table tabs
    (if (empty? (:energy-consumption display-data))
      [mui/typography (tr :lipas.energy-consumption/not-reported)]
      [:div
       [mui/tabs {:value     @selected-tab
                  :on-change #(reset! selected-tab %2)}
        [mui/tab {:icon (r/as-element [mui/icon "bar_chart"])}]
        [mui/tab {:icon (r/as-element [mui/icon "table_chart"])}]]

       (case @selected-tab

         ;; Chart tab
         0 [:div {:style {:margin-top "2em"}}
            [charts/yearly-chart
             {:data     (-> display-data :energy-consumption)
              :labels   (merge
                         {:electricity-mwh (tr :lipas.energy-stats/electricity-mwh)
                          :heat-mwh        (tr :lipas.energy-stats/heat-mwh)
                          :cold-mwh        (tr :lipas.energy-stats/cold-mwh)
                          :water-m3        (tr :lipas.energy-stats/water-m3)}
                         (utils/year-labels-map 2000 utils/this-year))
              :on-click (fn [^js e]
                          (let [year (gobj/get e "activeLabel")]
                            (reset! selected-year {lipas-id year})))}]]

         ;; Table tab
         1 [energy/table
            {:read-only? true
             :cold?      cold?
             :tr         tr
             :on-select  #(reset! selected-year {lipas-id (:year %)})
             :items      (-> display-data :energy-consumption)}])

       ;; Monthly chart
       (when-let [year (get @selected-year lipas-id)]
         [energy/monthly-chart
          {:lipas-id lipas-id
           :year     year
           :tr       tr}])

       ;; Report readings button
       (when (and editing? user-can-publish?)
         [report-readings-button
          {:tr       tr
           :lipas-id lipas-id
           :close    close}])])))

(defn- make-headers [tr spectators?]
  (remove nil?
          [[:year (tr :time/year)]
           [:total-count (tr :lipas.visitors/total-count)]
           (when spectators?
             [:spectators-count (tr :lipas.visitors/spectators-count)])]))

(defn visitors-view [{:keys [tr display-data lipas-id editing? close
                             spectators? user-can-publish?]
                      :or   [spectators? false]}]
  (r/with-let [selected-year (r/atom {})
               selected-tab  (r/atom 0)]

    ;; Chart/Table tabs
    (if (empty? (:visitors-history display-data))
      [mui/typography (tr :lipas.visitors/not-reported)]

      [:div
       [mui/tabs {:value     @selected-tab
                  :on-change #(reset! selected-tab %2)}
        [mui/tab {:icon (r/as-element [mui/icon "bar_chart"])}]
        [mui/tab {:icon (r/as-element [mui/icon "table_chart"])}]]

       (case @selected-tab

         ;; Chart tab
         0 [:div {:style {:margin-top "2em"}}
            [charts/yearly-chart
             {:data     (-> display-data :visitors-history)
              :labels   (merge
                         {:total-count      (tr :lipas.visitors/total-count)
                          :spectators-count (tr :lipas.visitors/spectators-count)}
                         (utils/year-labels-map 2000 utils/this-year))
              :on-click (fn [^js e]
                          (let [year (gobj/get e "activeLabel")]
                            (reset! selected-year {lipas-id year})))}]]

         ;; Table tab
         1 [lui/table
            {:headers          (make-headers tr spectators?)
             :items            (-> display-data :visitors-history)
             :on-select        #(reset! selected-year {lipas-id (:year %)})
             :key-fn           :year
             :sort-fn          :year
             :sort-asc?        true
             :hide-action-btn? true
             :read-only?       true}])

       ;; Monthly chart
       (when-let [year (get @selected-year lipas-id)]
         [energy/monthly-visitors-chart
          {:lipas-id lipas-id
           :year     year
           :tr       tr}])

       ;; Report readings button
       (when (and editing? user-can-publish?)
         [report-readings-button
          {:tr       tr
           :lipas-id lipas-id
           :close    close}])])))

(defn contacts-report [{:keys [tr types]}]
  (let [locale  (tr)
        sites   (<== [::subs/sites-list locale types])
        headers [[:name (tr :lipas.sports-site/name)]
                 [:city (tr :lipas.location/city)]
                 [:type (tr :lipas.sports-site/type)]
                 [:address (tr :lipas.location/address)]
                 [:postal-code (tr :lipas.location/postal-code)]
                 [:postal-office (tr :lipas.location/postal-office)]
                 [:email (tr :lipas.sports-site/email-public)]
                 [:phone-number (tr :lipas.sports-site/phone-number)]
                 [:www (tr :lipas.sports-site/www)]]]
    [mui/grid {:container true}
     [mui/grid {:item true :xs 12}
      [mui/paper
       [mui/typography {:color   :secondary
                        :style   {:padding "1em"}
                        :variant :headline}
        (tr :reports/contacts)]
       [lui/download-button
        {:style    {:margin-left "1.5em"}
         :on-click #(==> [::events/download-contacts-report sites headers])
         :label    (tr :actions/download)}]
       [mui/grid {:item true}
        [lui/table
         {:headers headers
          :sort-fn :city
          :items   sites}]]]]]))

(defn main [tr]
  [mui/typography "Nothing here"])
