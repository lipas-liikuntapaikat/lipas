(ns lipas.ui.sports-sites.views
  (:require
   ["mdi-material-ui/Calculator$default" :as Calculator]
   ["recharts/es6/cartesian/Area" :refer [Area]]
   ["recharts/es6/cartesian/XAxis" :refer [XAxis]]
   ["recharts/es6/cartesian/YAxis" :refer [YAxis]]
      ["recharts/es6/chart/AreaChart" :refer [AreaChart]]
   ["recharts/es6/component/Legend" :refer [Legend]]
   ["recharts/es6/component/ResponsiveContainer" :refer [ResponsiveContainer]]
   ["recharts/es6/component/Tooltip" :refer [Tooltip]]
   [goog.object :as gobj]
   [lipas.ui.charts :as charts]
   [lipas.ui.components :as lui]
   [lipas.ui.components.autocompletes :as autocompletes]
   [lipas.ui.energy.views :as energy]
   [lipas.ui.ice-stadiums.rinks :as rinks]
   [lipas.ui.map.utils :as map-utils]
   [lipas.ui.mui :as mui]
   [lipas.ui.sports-sites.events :as events]
   [lipas.ui.sports-sites.floorball.views :as floorball]
   [lipas.ui.sports-sites.football.views :as football]
   [lipas.ui.sports-sites.subs :as subs]
   [lipas.ui.swimming-pools.pools :as pools]
   [lipas.ui.swimming-pools.slides :as slides]
   [lipas.ui.utils :refer [<== ==>] :as utils]
   [reagent.core :as r]))

;; TODO maybe put this into config / app-db instead?
(def extra-locales [:se])

(defn- allow-editing-status?
  "Status field is displayed only if latest saved status is
  'out-of-service-temporarily'. Applies to both display and edit
  views."
  [tr display-data]
  (or
   true
   (= (:status display-data) (tr (keyword :status "out-of-service-temporarily")))))

(defn- show-status? [tr display-data]
  (or
   (= (:status display-data) (tr (keyword :status "incorrect-data")))
   (= (:status display-data) (tr (keyword :status "out-of-service-permanently")))
   (= (:status display-data) (tr (keyword :status "planned")))
   (= (:status display-data) (tr (keyword :status "planning")))))

(defn form
  [{:keys [tr display-data edit-data types size-categories admins
           owners on-change read-only? sub-headings?  lipas-id]}]

  (let [locale         (tr)
        name-conflict? (<== [::subs/sports-site-name-conflict?])]

    [lui/form {:read-only? read-only?}

     (when (show-status? tr display-data)
       [mui/typography {:variant "h6" :color "error"}
        (:status display-data)])

     (when sub-headings?
       [lui/sub-heading {:label (tr :lipas.sports-site/headline)}])

     ;; Last modified
     {:label      (tr :general/last-modified)
      :value      (-> display-data :event-date)
      :form-field [lui/text-field
                   {:value     (-> display-data :event-date)
                    :on-change #()
                    :disabled  true}]}

     ;; Status
     (when (allow-editing-status? tr display-data)
       {:label      (tr :lipas.sports-site/status)
        :value      (-> display-data :status)
        :form-field [lui/status-selector-single
                     {:required  true
                      :value     (-> edit-data :status)
                      :on-change #(on-change :status %)}]})

     ;; Type
     {:label      (tr :type/name)
      :value      (-> display-data :type :name)
      :form-field [autocompletes/autocomplete
                   {:value     (-> edit-data :type :type-code)
                    :required  true
                    :multi?    false
                    :items     types
                    :label-fn  (comp locale :name)
                    :value-fn  :type-code
                    :on-change #(on-change :type :type-code %)}]}

     ;; Disabled 2022-02-06
     ;;
     ;; Ice-stadiums get special treatment
     ;; (when (or (= 2520 (-> edit-data :type :type-code))
     ;;           (and read-only?
     ;;                (= 2520 (-> display-data :type :type-code))))
     ;;   {:label      (tr :ice/size-category)
     ;;    :value      (-> display-data :type :size-category)
     ;;    :form-field [lui/select
     ;;                 {:value     (-> edit-data :type :size-category)
     ;;                  :items     size-categories
     ;;                  :value-fn  first
     ;;                  :label-fn  (comp locale second)
     ;;                  :on-change #(on-change :type :size-category %)}]})

     ;; Name
     {:label      (tr :lipas.sports-site/name)
      :value      (-> display-data :name)
      :form-field [lui/text-field
                   {:spec        :lipas.sports-site/name
                    :required    true
                    :value       (-> edit-data :name)
                    :on-change   #(on-change :name %)
                    :adornment   (when name-conflict?
                                   (r/as-element [mui/icon {:color "secondary"} "warning"]))
                    :helper-text (when name-conflict?
                                   "Nimi on jo käytössä toisella
                                   liikuntapaikalla. Keksi
                                   yksilöivämpi nimi.") ; TODO translations
                    :on-blur     #(==> [::events/check-sports-site-name lipas-id %])}]}

     ;; Localized name(s)
     (into
      [:<>]
      (for [l extra-locales]
        {:label      (tr (keyword
                          "lipas.sports-site"
                          (str "name-localized-" (name l))))
         :value      (-> display-data :name-localized l)
         :form-field [lui/text-field
                      {:spec      :lipas.sports-site/name
                       :value     (-> edit-data :name-localized l)
                       :on-change #(on-change :name-localized l %)}]}))

     ;; Marketing name
     {:label      (tr :lipas.sports-site/marketing-name)
      :value      (-> display-data :marketing-name)
      :form-field [lui/text-field
                   {:spec      :lipas.sports-site/marketing-name
                    :value     (-> edit-data :marketing-name)
                    :on-change #(on-change :marketing-name %)}]}

     ;; Construction year
     {:label      (tr :lipas.sports-site/construction-year)
      :value      (-> display-data :construction-year)
      :form-field [lui/year-selector2
                   {:value     (-> edit-data :construction-year)
                    :on-change #(on-change :construction-year %)
                    :deselect? true}]}

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
        :min-rows  5
        :value     (-> edit-data :comment)
        :multiline true
        :on-change #(on-change :comment %)}]}

     (when sub-headings?
       [lui/sub-heading {:label (tr :lipas.sports-site/contact)}])

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
      :type       :link
      :form-field [lui/text-field
                   {:value     (-> edit-data :www)
                    :spec      :lipas.sports-site/www
                    :on-change #(on-change :www %)}]}

     ;; Reservations-link
     {:label      (tr :lipas.sports-site/reservations-link)
      :value      (-> display-data :reservations-link)
      :type       :link
      :form-field [lui/text-field
                   {:value     (-> edit-data :reservations-link)
                    :spec      :lipas.sports-site/reservations-link
                    :on-change #(on-change :reservations-link %)}]}

     (when sub-headings?
       [lui/sub-heading {:label (tr :lipas.sports-site/ownership)}])

     ;; Owner
     {:label      (tr :lipas.sports-site/owner)
      :value      (-> display-data :owner)
      :form-field [lui/select
                   {:value     (-> edit-data :owner)
                    :required  true
                    :spec      :lipas.sports-site/owner
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
                    :spec      :lipas.sports-site/admin
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
       [lui/sub-heading {:label (tr :lipas.sports-site/address)}])

     ;; Address
     {:label      (tr :lipas.location/address)
      :value      (-> display-data :address)
      :form-field [lui/text-field
                   {:value     (-> edit-data :address)
                    :spec      :lipas.location/address
                    :required  true
                    :on-change #(on-change :address %)}]}

     ;; Postal code
     {:label      (tr :lipas.location/postal-code)
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
                   {:value     (-> edit-data :postal-office)
                    :spec      :lipas.location/postal-office
                    :on-change #(on-change :postal-office %)}]}

     ;; City
     {:label      (tr :lipas.location/city)
      :value      (-> display-data :city :name)
      :form-field [autocompletes/autocomplete
                   {:value     (-> edit-data :city :city-code)
                    :required  true
                    :spec      :lipas.location.city/city-code
                    :items     cities
                    :label-fn  (comp locale :name)
                    :value-fn  :city-code
                    :on-change #(on-change :city :city-code %)}]}

     ;; Neighborhood
     {:label      (tr :lipas.location/neighborhood)
      :value      (-> display-data :city :neighborhood)
      :form-field [lui/text-field
                   {:value     (-> edit-data :city :neighborhood)
                    :spec      :lipas.location.city/neighborhood
                    :on-change #(on-change :city :neighborhood %)}]}]))

(defn surface-material-selector
  [{:keys [tr value on-change label multi? spec tooltip disabled]}]
  (let [locale (tr)
        items  (<== [::subs/surface-materials])]
    [lui/autocomplete
     {:value       value
      :multi?      multi?
      :disabled    disabled
      :helper-text tooltip
      :label       label
      :spec        spec
      :items       items
      :label-fn    (comp locale second)
      :value-fn    first
      :on-change   on-change}]))

(defn material-field? [k]
  (contains? #{:surface-material
               :training-spot-surface-material
               :running-track-surface-material} k))

(defn retkikartta? [k]
  (= k :may-be-shown-in-excursion-map-fi?))

(defn harrastuspassi? [k]
  (= k :may-be-shown-in-harrastuspassi-fi?))

(defn retkikartta-field
  [{:keys [tr on-change problems?] :as props}]
  (let [message    (tr :retkikartta/disclaimer)
        on-change* (fn [v]
                     (if (true? v)
                       (==> [:lipas.ui.events/confirm message (partial on-change v)])
                       (on-change v)))]
    [:<>
     [lui/checkbox (assoc props :on-change on-change*)]
     (when problems?
       [mui/typography {:color "error"}
        (tr :map/retkikartta-problems-warning)])]))

(defn harrastuspassi-field
  [{:keys [tr on-change] :as props}]
  (let [message    (tr :harrastuspassi/disclaimer)
        on-change* (fn [v]
                     (if (true? v)
                       (==> [:lipas.ui.events/confirm message (partial on-change v)])
                       (on-change v)))]
    [:<>
     [lui/checkbox (assoc props :on-change on-change*)]]))

(defn route-length-km-field
  [{:keys [tr geoms on-change] :as props}]
  [mui/grid {:container true :wrap "nowrap"}
   [mui/grid {:item true :style {:flex-grow 1}}
    [mui/form-group
     [lui/text-field (dissoc props :geoms)]]]
   [mui/grid {:item true}
    [mui/tooltip {:title (tr :map/calculate-route-length)}
     [mui/icon-button
      {:on-click #(-> geoms map-utils/calculate-length on-change)}
      [:> Calculator]]]]])

(defn show-calc? [k geom-type]
  (and (= :route-length-km k) (#{"LineString"} geom-type)))

(defn special-case? [type-code]
  ;; Uimahalli / jäähalli
  (#{3110 3130 2510 2520} type-code))

(defn pools-field
  [{:keys [tr read-only? width] :as props}]
  (let [dialogs   (<== [:lipas.ui.swimming-pools.subs/dialogs])
        add-data  (<== [:lipas.ui.sports-sites.subs/new-site-data])
        data      (if add-data
                    {:edit-data add-data}
                    (<== [:lipas.ui.map.subs/selected-sports-site]))
        max-width (<== [:lipas.ui.map.subs/drawer-width width])
        lipas-id  (-> data :edit-data :lipas-id)]
    [:<>
     (when (-> dialogs :pool :open?)
       [pools/dialog {:tr tr :lipas-id lipas-id}])

     (if read-only?
       [:<>
        [pools/read-only-table
         {:tr    tr
          :items (-> data :display-data :pools)}]
        [:span {:style {:margin-top "1em"}}]]
       [pools/table
        {:tr           tr
         :add-btn-size "small"
         :items        (-> data :edit-data :pools)
         :max-width    max-width
         :lipas-id     (-> data :edit-data :lipas-id)}])]))

(defn slides-field
  [{:keys [tr read-only? width] :as props}]
  (let [dialogs   (<== [:lipas.ui.swimming-pools.subs/dialogs])
        add-data  (<== [:lipas.ui.sports-sites.subs/new-site-data])
        data      (if add-data
                    {:edit-data add-data}
                    (<== [:lipas.ui.map.subs/selected-sports-site]))
        max-width (<== [:lipas.ui.map.subs/drawer-width width])
        lipas-id  (-> data :edit-data :lipas-id)]
    [:<>
     (when (-> dialogs :slide :open?)
       [slides/dialog {:tr tr :lipas-id lipas-id}])

     (if read-only?
       [:<>
        [slides/read-only-table
         {:tr    tr
          :items (-> data :display-data :slides)}]
        [:span {:style {:margin-top "1em"}}]]
       [slides/table
        {:tr           tr
         :add-btn-size "small"
         :items        (-> data :edit-data :slides)
         :max-width    max-width
         :lipas-id     (-> data :edit-data :lipas-id)}])]))

(defn rinks-field
  [{:keys [tr read-only? width] :as props}]
  (let [dialogs   (<== [:lipas.ui.ice-stadiums.subs/dialogs])
        add-data  (<== [:lipas.ui.sports-sites.subs/new-site-data])
        data      (if add-data
                    {:edit-data add-data}
                    (<== [:lipas.ui.map.subs/selected-sports-site]))
        max-width (<== [:lipas.ui.map.subs/drawer-width width])
        lipas-id  (-> data :edit-data :lipas-id)]
    [:<>
     (when (-> dialogs :rink :open?)
       [rinks/dialog {:tr tr :lipas-id lipas-id}])

     (if read-only?
       [:<>
        [rinks/read-only-table
         {:tr    tr
          :items (-> data :display-data :rinks)}]
        [:span {:style {:margin-top "1em"}}]]
       [rinks/table
        {:tr           tr
         :add-btn-size "small"
         :max-width    max-width
         :items        (-> data :edit-data :rinks)
         :lipas-id     (-> data :edit-data :lipas-id)}])]))

(defn properties-form
  [{:keys [tr edit-data display-data type-code on-change read-only?
           key geoms geom-type problems? width]}]
  (let [locale      (tr)
        types-props (<== [::subs/types-props type-code])
        types-props (if false #_(special-case? type-code)
                        (select-keys types-props [:may-be-shown-in-harrastuspassi-fi?])
                        types-props)]
    (into
     [lui/form
      {:key        key
       :read-only? read-only?}

      ;; Swimming halls
      (when (#{3110 3130} type-code)
        [:<>

         ;; Pools
         [mui/typography {:variant "body2"}
          (tr :lipas.swimming-pool.pools/headline)]
         [pools-field
          {:tr         tr
           :width      width
           :read-only? read-only?}]

         ;; Slides
         [mui/typography {:variant "body2"}
          (tr :lipas.swimming-pool.slides/headline)]
         [slides-field
          {:tr         tr
           :width      width
           :read-only? read-only?}]])

      ;; Ice stadiums
      #_(when (#{2510 2520} type-code)
        [:<>

         ;; Rinks
         [mui/typography {:variant "body2"}
          (tr :lipas.ice-stadium.rinks/headline)]
         [rinks-field
          {:tr         tr
           :width      width
           :read-only? read-only?}]])]

     (sort-by
      (juxt :disabled? (comp - :priority) #(or (:sort %) (:label %)))

      (into
       (for [[k v] types-props
             :let  [label     (-> types-props k :name locale)
                    data-type (:data-type v)
                    tooltip   (if (:derived? v)
                                "Lasketaan automaattisesti olosuhdetiedoista"
                                (-> v :description locale))
                    spec      (keyword :lipas.sports-site.properties k)
                    value     (-> edit-data k)
                    on-change #(on-change k %)
                    disabled?  (:derived? v)]]
         {:label     label
          :value     (-> display-data k)
          :disabled? disabled?
          :priority  (:priority v)
          :form-field
          (cond
            (material-field? k) [surface-material-selector
                                 {:tr        tr
                                  :multi?    (= :surface-material k)
                                  :disabled  disabled?
                                  :tooltip   tooltip
                                  :spec      spec
                                  :label     label
                                  :value     value
                                  :on-change on-change}]
            (retkikartta? k)    [retkikartta-field
                                 {:tr        tr
                                  :value     value
                                  :on-change on-change
                                  :tooltip   tooltip
                                  :problems? problems?}]
            (harrastuspassi? k) [harrastuspassi-field
                                 {:tr        tr
                                  :value     value
                                  :on-change on-change
                                  :tooltip   tooltip}]

            (show-calc? k geom-type) [route-length-km-field
                                      {:tr        tr
                                       :value     value
                                       :type      "number"
                                       :spec      spec
                                       :label     label
                                       :tooltip   tooltip
                                       :geoms     geoms
                                       :on-change on-change}]
            (= "boolean" data-type)  [lui/checkbox
                                      {:value     value
                                       :tooltip   tooltip
                                       :disabled  disabled?
                                       :on-change on-change}]
            :else                    [lui/text-field
                                      {:value     value
                                       :disabled  disabled?
                                       :tooltip   tooltip
                                       :spec      spec
                                       :type      (when (#{"numeric" "integer"} data-type)
                                                    "number")
                                       :on-change on-change}])})

       (concat
        ;; Ice stadium special props
        (when (#{2510 2520} type-code)
          (let [data         (<== [:lipas.ui.map.subs/selected-sports-site])
                lipas-id     (-> data :display-data :lipas-id)
                on-change    (fn [n k v] (if lipas-id
                                           ;; Existing site
                                           (==> [::events/edit-field lipas-id [:rinks n k] v])
                                           (==> [::events/edit-new-site-field [:rinks n k] v])))
                edit-data    (or (-> data :edit-data)
                                 (<== [:lipas.ui.sports-sites.subs/new-site-data]))
                display-data (-> data :display-data)]


            [;; Rink 1 width
             {:label    (tr :lipas.ice-stadium.rinks/rink1-width)
              :sort     "1A"
              :priority 1
              :value    (get-in display-data [:rinks 0 :width-m])
              :form-field
              [lui/text-field
               {:adornment "m"
                :type      "number"
                :value     (get-in edit-data [:rinks 0 :width-m])
                :spec      :lipas.ice-stadium.rink/width-m
                :on-change #(on-change 0 :width-m %)}]}

             ;; Rink 1 length
             {:label    (tr :lipas.ice-stadium.rinks/rink1-length)
              :sort     "1B"
              :priority 1
              :value    (get-in display-data [:rinks 0 :length-m])
              :form-field
              [lui/text-field
               {:adornment "m"
                :type      "number"
                :value     (get-in edit-data [:rinks 0 :length-m])
                :spec      :lipas.ice-stadium.rink/length-m
                :on-change #(on-change 0 :length-m %)}]}

             ;; Rink 1 area m2
             {:label    (tr :lipas.ice-stadium.rinks/rink1-area-m2)
              :sort     "1C"
              :priority 1
              :value    (get-in display-data [:rinks 0 :area-m2])
              :form-field
              [lui/text-field
               {:adornment "m"
                :type      "number"
                :value     (get-in edit-data [:rinks 0 :area-m2])
                :spec      :lipas.ice-stadium.rink/area-m2
                :on-change #(on-change 0 :area-m2 %)}]}

             ;; Rink 2 width
             {:label    (tr :lipas.ice-stadium.rinks/rink2-width)
              :sort     "2A"
              :priority 1
              :value    (get-in display-data [:rinks 1 :width-m])
              :form-field
              [lui/text-field
               {:adornment "m"
                :type      "number"
                :value     (get-in edit-data [:rinks 1 :width-m])
                :spec      :lipas.ice-stadium.rink/width-m
                :on-change #(on-change 1 :width-m %)}]}

             ;; Rink 2 length
             {:label    (tr :lipas.ice-stadium.rinks/rink2-length)
              :sort     "2B"
              :priority 1
              :value    (get-in display-data [:rinks 1 :length-m])
              :form-field
              [lui/text-field
               {:adornment "m"
                :type      "number"
                :value     (get-in edit-data [:rinks 1 :length-m])
                :spec      :lipas.ice-stadium.rink/length-m
                :on-change #(on-change 1 :length-m %)}]}

             ;; Rink 2 area m2
             {:label    (tr :lipas.ice-stadium.rinks/rink2-area-m2)
              :sort     "2C"
              :priority 1
              :value    (get-in display-data [:rinks 1 :area-m2])
              :form-field
              [lui/text-field
               {:adornment "m"
                :type      "number"
                :value     (get-in edit-data [:rinks 1 :area-m2])
                :spec      :lipas.ice-stadium.rink/area-m2
                :on-change #(on-change 1 :area-m2 %)}]}

             ;; Rink 3 width
             {:label    (tr :lipas.ice-stadium.rinks/rink3-width)
              :sort     "3A"
              :priority 1
              :value    (get-in display-data [:rinks 2 :width-m])
              :form-field
              [lui/text-field
               {:adornment "m"
                :type      "number"
                :value     (get-in edit-data [:rinks 2 :width-m])
                :spec      :lipas.ice-stadium.rink/width-m
                :on-change #(on-change 2 :width-m %)}]}


             ;; Rink 3 length
             {:label    (tr :lipas.ice-stadium.rinks/rink3-length)
              :sort     "3B"
              :priority 1
              :value    (get-in display-data [:rinks 2 :length-m])
              :form-field
              [lui/text-field
               {:adornment "m"
                :type      "number"
                :value     (get-in edit-data [:rinks 2 :length-m])
                :spec      :lipas.ice-stadium.rink/length-m
                :on-change #(on-change 2 :length-m %)}]}

             ;; Rink 3 area m2
             {:label    (tr :lipas.ice-stadium.rinks/rink3-area-m2)
              :sort     "3C"
              :priority 1
              :value    (get-in display-data [:rinks 2 :area-m2])
              :form-field
              [lui/text-field
               {:adornment "m"
                :type      "number"
                :value     (get-in edit-data [:rinks 2 :area-m2])
                :spec      :lipas.ice-stadium.rink/area-m2
                :on-change #(on-change 2 :area-m2 %)}]}


             ]))

        ;; Swimming pool special props
        (when (#{3110 3130} type-code)
          ;; Platforms
          (let [data         (<== [:lipas.ui.map.subs/selected-sports-site])
                lipas-id     (-> data :display-data :lipas-id)
                on-change    (fn [k v] (==> [::events/edit-field lipas-id [:facilities k] v]))
                edit-data    (-> data :edit-data :facilities)
                display-data (-> data :display-data :facilities)]
            [;; Platforms 1m count
             {:label (tr :lipas.swimming-pool.facilities/platforms-1m-count)
              :sort  "1"
              :value (-> display-data :platforms-1m-count)
              :form-field
              [lui/text-field
               {:adornment (tr :units/pcs)
                :type      "number"
                :value     (-> edit-data :platforms-1m-count)
                :spec      :lipas.swimming-pool.facilities/platforms-1m-count
                :on-change #(on-change :platforms-1m-count %)}]}
             ;; Platforms 3m count
             {:label (tr :lipas.swimming-pool.facilities/platforms-3m-count)
              :sort  "2"
              :value (-> display-data :platforms-3m-count)
              :form-field
              [lui/text-field
               {:adornment (tr :units/pcs)
                :type      "number"
                :value     (-> edit-data :platforms-3m-count)
                :spec      :lipas.swimming-pool.facilities/platforms-3m-count
                :on-change #(on-change :platforms-3m-count %)}]}

             ;; Platforms 5m count
             {:label (tr :lipas.swimming-pool.facilities/platforms-5m-count)
              :value (-> display-data :platforms-5m-count)
              :sort  "3"
              :form-field
              [lui/text-field
               {:adornment (tr :units/pcs)
                :type      "number"
                :value     (-> edit-data :platforms-5m-count)
                :spec      :lipas.swimming-pool.facilities/platforms-5m-count
                :on-change #(on-change :platforms-5m-count %)}]}

             ;; Platforms 7.5m count
             {:label (tr :lipas.swimming-pool.facilities/platforms-7.5m-count)
              :value (-> display-data :platforms-7.5m-count)
              :sort  "4"
              :form-field
              [lui/text-field
               {:adornment (tr :units/pcs)
                :type      "number"
                :value     (-> edit-data :platforms-7.5m-count)
                :spec      :lipas.swimming-pool.facilities/platforms-7.5m-count
                :on-change #(on-change :platforms-7.5m-count %)}]}

             ;; Platforms 10m count
             {:label (tr :lipas.swimming-pool.facilities/platforms-10m-count)
              :value (-> display-data :platforms-10m-count)
              :sort  "5"
              :form-field
              [lui/text-field
               {:adornment (tr :units/pcs)
                :type      "number"
                :value     (-> edit-data :platforms-10m-count)
                :spec      :lipas.swimming-pool.facilities/platforms-10m-count
                :on-change #(on-change :platforms-10m-count %)}]}]))))))))

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
       ;; (when (and editing? user-can-publish?)
       ;;   [report-readings-button
       ;;    {:tr       tr
       ;;     :lipas-id lipas-id
       ;;     :close    close}])
       ])))

(defn- make-headers [tr spectators?]
  (remove nil?
          [[:year (tr :time/year)]
           [:total-count (tr :lipas.visitors/total-count)]
           (when spectators?
             [:spectators-count (tr :lipas.visitors/spectators-count)])]))

(defn visitors-view
  [{:keys [tr display-data lipas-id editing? close spectators?
           user-can-publish?]
    :or   {spectators? false}}]
  (r/with-let [selected-year (r/atom {})
               selected-tab  (r/atom 0)]

    ;; Chart/Table tabs
    (if (empty? (:visitors-history display-data))
      [mui/typography (tr :lipas.visitors/not-reported)]

      [:div
       [mui/tabs {:value @selected-tab :on-change #(reset! selected-tab %2)}
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
       ;; (when (and editing? user-can-publish?)
       ;;   [report-readings-button
       ;;    {:tr       tr
       ;;     :lipas-id lipas-id
       ;;     :close    close}])
       ])))

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
       [mui/typography {:color "secondary" :style {:padding "1em"} :variant "h5"}
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

(defn delete-dialog [{:keys [tr lipas-id on-close]}]
  (let [locale       (tr)
        data         (<== [::subs/latest-rev lipas-id])
        statuses     (<== [::subs/delete-statuses])
        status       (<== [::subs/selected-delete-status])
        year         (<== [::subs/selected-delete-year])
        can-publish? (<== [:lipas.ui.user.subs/permission-to-publish? lipas-id])
        draft?       (not can-publish?)]

    [lui/dialog
     {:title         (tr :lipas.sports-site/delete (:name data))
      :cancel-label  (tr :actions/cancel)
      :on-close      on-close
      :save-enabled? (some? status)
      :on-save       (fn []
                       (==> [::events/delete data status year draft?])
                       (on-close))
      :save-label    (tr :actions/delete)}

     [mui/form-group
      [lui/select
       {:label     (tr :lipas.sports-site/delete-reason)
        :required  true
        :value     status
        :items     statuses
        :on-change #(==> [::events/select-delete-status %])
        :value-fn  first
        :label-fn  (comp locale second)}]

      (when (= "out-of-service-permanently" status)
        [lui/year-selector
         {:label     (tr :time/year)
          :value     year
          :on-change #(==> [::events/select-delete-year %])}])]]))

(defn site-view [{:keys [title on-close close-label bottom-actions lipas-id]}
                 & contents]
  (let [tr                  (<== [:lipas.ui.subs/translator])
        delete-dialog-open? (<== [::subs/delete-dialog-open?])]

    [mui/grid {:container true :style {:background-color mui/gray1}}
     [mui/grid {:item true :xs 12 :style {:padding "8px 8px 0px 8px"}}

      (when delete-dialog-open?
        [delete-dialog
         {:tr       tr
          :lipas-id lipas-id
          :on-close #(==> [::events/toggle-delete-dialog])}])

      [mui/paper {:style {:background-color "#fff"}}

       ;; Site name
       [mui/tool-bar {:disable-gutters true}
        [mui/tooltip {:title (or close-label "")}
         [mui/icon-button
          {:on-click on-close :style {:margin-left "0.5em" :margin-right "0.4em"}}

          ;; "back to listing" button
          [mui/icon {:color :primary}
           "arrow_back_ios"]]]
        [mui/typography {:style {:color mui/primary} :variant "h4"}
         title]]]]

     ;; Contents
     (into
      [mui/grid {:item true :xs 12 :style {:padding 8}}]
      contents)

     ;; Floating actions
     [lui/floating-container {:right 24 :bottom 16 :background-color "transparent"}
      (into
       [mui/grid
        {:container true :align-items "center" :spacing 1}]
       (for [c     bottom-actions
             :when (some? c)]
         [mui/grid {:item true}
          c]))]

     ;; Small footer on top of which floating container may scroll
     [mui/grid
      {:item true :xs 12 :style {:height "5em" :background-color mui/gray1}}]]))

(defn elevation-profile
  [{:keys [lipas-id]}]
  (r/with-let [selected-tab (r/atom 0)]
    (let [elevation (<== [::subs/elevation lipas-id])
          labels    {}]
      [:<>
       [:> ResponsiveContainer {:width "100%" :height 300}
        [:> AreaChart
         {:data   (nth elevation @selected-tab)
          :layout "horizontal" :on-click #(js/alert "lol")}
         [:> Legend {:content (fn [^js props] (charts/legend labels props))}]
         [:> Tooltip {:content (fn [^js props] (charts/subsidies-tooltip labels props))}]
         [:> XAxis {:dataKey "distance-m" :tick true}]
         [:> YAxis
          {:tick    charts/font-styles
           :dataKey :elevation-m
           :domain  #js["dataMin" "dataMax"]}]
         [:> Area
          {:dataKey :elevation-m
           :fill    (:energy-mwh charts/colors)
           :stroke  (:electricity-mwh charts/colors)}]]]
       (into [mui/tabs {:value @selected-tab :on-change #(reset! selected-tab %2)}]
             (for [segment (range (count elevation))]
               [mui/tab {:value segment :label (str "Seg-" segment)}]))])))

(defn main []
  [mui/typography "Nothing here"])
