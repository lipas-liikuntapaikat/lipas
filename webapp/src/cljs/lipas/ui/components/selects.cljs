(ns lipas.ui.components.selects
  (:require ["@mui/material/Typography$default" :as Typography]
            [clojure.reader :refer [read-string]]
            [clojure.spec.alpha :as s]
            [clojure.string :as string]
            [lipas.data.cities :as cities]
            [lipas.data.types :as types]
            [lipas.ui.components.autocompletes :as autocompletes]
            [lipas.ui.mui :as mui]
            [lipas.ui.utils :refer [<==] :as utils]
            [lipas.utils :as cutils]
            [reagent.core :as r]))

(def select-style {:min-width "170px"})

(defn error? [spec value required]
  (if (and spec (or value required))
    ((complement s/valid?) spec value)
    false))

(defn select
  [{:keys [label value items on-change value-fn label-fn helper-text
           sort-fn sort-cmp deselect? spec required tooltip fullWidth]
    :or   {value-fn :value
           label-fn :label
           sort-cmp compare
           tooltip  ""
           fullWidth true}
    :as   props}]
  (let [on-change #(on-change (-> %
                                  .-target
                                  .-value
                                  read-string))
        props     (-> props
                      (dissoc :value-fn :label-fn :label :sort-fn :sort-cmp
                              :deselect? :helper-text)
                      ;; Following fixes Chrome scroll issue
                      ;; https://github.com/mui-org/material-ui/pull/12003
                      (assoc :MenuProps
                             {:PaperProps
                              {:style
                               {:transform "translate2(0)"}}})
                      (assoc :value (if value (pr-str value) ""))
                      (assoc :on-change on-change))
        sort-fn   (or sort-fn label-fn)]
    [mui/tooltip {:title tooltip}
     [mui/form-control
      {:required  required
       :fullWidth fullWidth
       :error     (error? spec value required)
       :variant   "standard"}
      (when label [mui/input-label label])
      (into [mui/select props
             (when deselect?
               [mui/menu-item {:key   "deselect"
                               :value nil}
                "-"])]
            (for [i (sort-by sort-fn sort-cmp items)]
              (let [value (value-fn i)
                    label (label-fn i)]
                [mui/menu-item {:key   (pr-str value)
                                :value (pr-str value)}
                 label])))
      (when helper-text [mui/form-helper-text helper-text])]]))

(defn multi-select
  [{:keys [label value items on-change value-fn label-fn sort-fn
           sort-cmp tooltip helper-text fullWidth required spec]
    :or   {value-fn  :value
           label-fn  :label
           sort-cmp  compare
           tooltip   ""
           fullWidth true
           required  false}
    :as   props}]
  (let [sort-fn (or sort-fn label-fn)]
    [mui/tooltip {:title tooltip}
     [mui/form-control {:fullWidth fullWidth
                        :required  required
                        :error     (error? spec value required)
                        :variant   "standard"}
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
          (label-fn i)])]
      (when helper-text [mui/form-helper-text helper-text])]]))

(defn year-selector [{:keys [label value on-change required years multi?]
                      :as   props}]
  (let [years     (or years (range 1900 (inc (.getFullYear (js/Date.)))))
        component (if multi? multi-select select)]
    [component (merge (dissoc props :multi? :tr)
                      {:label     label
                       :items     (map #(hash-map :label % :value %) years)
                       :on-change on-change
                       :sort-cmp  utils/reverse-cmp
                       :value     value
                       :required  required})]))

(defn years-selector
  [{:keys [tr value on-change style years] :as props
    :or   {years (range 2000 utils/this-year)}}]
  [year-selector
   (merge
     (dissoc props :tr)
     {:label        (tr :stats/select-years)
      :multi?       true
      :render-value (fn [vs]
                      (let [vs (sort vs)]
                        (condp = (count vs)
                          0 "-"
                          1 (first vs)
                          2 (str (first vs) ", " (second vs))
                          (str (first vs) " ... " (last vs)))))
      :value        value
      :style        style
      :years        years
      :on-change    on-change})])

(defn number-selector [{:keys [unit] :as props}]
  [select
   (merge
     {:sort-fn   identity
      :sort-cmp  utils/reverse-cmp
      :value-fn  identity
      :label-fn  #(str % unit)}
     props)])

(defn date-picker
  [{:keys [value on-change type required]
    :or   {type "date"}
    :as   props}]
  [mui/text-field
   (merge
     props
     {:type      type
      :value     (or value "")
      :variant   "standard"
      :Input-label-props
      {:error  (and required (empty? value))
       :shrink true} ; This makes the label show actually
      :on-change (fn [evt] (on-change (-> evt .-target .-value)))})])

(defn- id-parser [prefix]
  (comp
    (filter #(string/starts-with? % prefix))
    (map #(string/replace % prefix ""))
    (map cutils/->int)))

(def parse-avis (id-parser "avi-"))
(def parse-provinces (id-parser "province-"))
(def parse-cities (id-parser "city-"))

(defn ->city-codes [cities-by-avis cities-by-province region-ids]
  (let [avi-ids      (into [] parse-avis region-ids)
        province-ids (into [] parse-provinces region-ids)
        city-codes   (into [] parse-cities region-ids)]
    (into [] (comp cat (remove nil?))
          [(->> avi-ids
                (select-keys cities-by-avis)
                (mapcat second)
                (map :city-code))
           (->> province-ids
                (select-keys cities-by-province)
                (mapcat second)
                (map :city-code))
           city-codes])))

(defn strong1 [text] [:strong text])
(defn strong2 [text] [:strong {:style {:text-transform "uppercase"}} text])

(defn region-selector [{:keys [value on-change regions]}]
  (let [regions      (or regions
                         (<== [:lipas.ui.sports-sites.subs/regions]))
        regions-by-v (utils/index-by :region-id regions)
        tr           (<== [:lipas.ui.subs/translator])
        locale       (tr)
        avis         cities/by-avi-id
        provinces    cities/by-province-id]
    ^{:key value}
    [autocompletes/autocomplete
     {:items            regions
      :value            (map (partial str "city-") value)
      :label            (tr :search/search)
      :multi?           true
      :value-fn         :region-id
      :label-fn         (comp locale :name)
      :render-option-fn (fn [props option _]
                          (let [v (-> option read-string regions-by-v :name locale)]
                            (r/as-element
                              [:r> Typography
                               props
                               (cond
                                 (string/includes? option "province-") (strong1 v)
                                 (string/includes? option "avi-")      (strong2 v)
                                 :else                                 v)])))
      :on-change        (comp on-change (partial ->city-codes avis provinces))}]))

(def parse-main-cats (id-parser "main-cat-"))
(def parse-sub-cats (id-parser "sub-cat-"))
(def parse-types (id-parser "type-"))

(defn ->type-codes [types-by-main-cats types-by-sub-cats cat-ids]
  (let [main-cats  (into [] parse-main-cats cat-ids)
        sub-cats   (into [] parse-sub-cats cat-ids)
        type-codes (into [] parse-types cat-ids)]
    (into [] (comp cat (remove nil?))
          [(->> main-cats
                (select-keys types-by-main-cats)
                (mapcat second)
                (map :type-code))
           (->> sub-cats
                (select-keys types-by-sub-cats)
                (mapcat second)
                (map :type-code))
           type-codes])))

(defn type-category-selector [{:keys [value on-change label]}]
  (let [cats         (<== [:lipas.ui.sports-sites.subs/type-categories])
        tr           (<== [:lipas.ui.subs/translator])
        cats-by-v    (utils/index-by :cat-id cats)
        locale       (tr)
        by-main-cats types/by-main-category
        by-sub-cats  types/by-sub-category]

    ^{:key value}
    [autocompletes/autocomplete
     {:items            cats
      :value            (map (partial str "type-") value)
      :label            (or label (tr :search/search))
      :multi?           true
      :value-fn         :cat-id
      :key-fn           :cat-id
      :label-fn         (fn [item] (str (:type-code item) " " (-> item :name locale)))
      :render-option-fn (fn [props option _]
                          ;; NOTE: The read-string here is a bad design,
                          ;; currently the value is coerced from Clj to EDN string, which is
                          ;; unnecessary.
                          (let [c (-> option read-string cats-by-v)
                                v (str (:type-code c) " " (-> c :name locale))]
                            (r/as-element
                              [:r> Typography
                               props
                               (cond
                                 (string/includes? option "sub-cat")  (strong1 v)
                                 (string/includes? option "main-cat") (strong2 v)
                                 :else                                v)])))
      :sort-fn          (fn [{:keys [type-code cat-id]}]
                          (case type-code
                            (1 2) (* 100 type-code)
                            ;; Special case workaround to sort 7000 codes
                            ;; main -> sub -> type
                            7000 (case cat-id
                                   "main-cat-7000" 7000
                                   "sub-cat-7000" 7001
                                   7002)
                            type-code))
      :on-change        (comp on-change (partial ->type-codes by-main-cats by-sub-cats))}]))

(defn type-selector [{:keys [value on-change]}]
  (let [tr     (<== [:lipas.ui.subs/translator])
        locale (tr)
        types  (<== [:lipas.ui.sports-sites.subs/types-list locale])]
    ^{:key value}
    [autocompletes/autocomplete
     {:items     types
      :value     value
      :multi?    true
      :label     (tr :search/search)
      :value-fn  :type-code
      :label-fn  (comp locale :name)
      :on-change on-change}]))

(defn type-selector-single [{:keys [value on-change types]}]
  (let [tr     (<== [:lipas.ui.subs/translator])
        locale (tr)
        types  (or types
                   (<== [:lipas.ui.sports-sites.subs/types-by-type-code]))]
    ^{:key value}
    [select
     {:items     types
      :value     value
      :value-fn  first
      :label-fn  (comp locale :name second)
      :on-change on-change}]))

(defn city-selector-single [{:keys [value on-change cities]}]
  (let [tr     (<== [:lipas.ui.subs/translator])
        locale (tr)
        cities (or cities
                   (<== [:lipas.ui.sports-sites.subs/cities-by-city-code]))]
    ^{:key value}
    [autocompletes/autocomplete
     {:items     cities
      :value     value
      :style     select-style
      :label     (tr :stats/select-city)
      :value-fn  first
      :label-fn  (comp locale :name second)
      :on-change on-change}]))

(defn surface-material-selector [{:keys [value on-change]}]
  (let [tr     (<== [:lipas.ui.subs/translator])
        locale (tr)
        items  (<== [:lipas.ui.sports-sites.subs/surface-materials])]
    ^{:key value}
    [autocompletes/autocomplete
     {:value     value
      :label     (tr :search/search)
      :multi?    true
      :items     items
      :label-fn  (comp locale second)
      :value-fn  first
      :on-change on-change}]))

(defn admin-selector [{:keys [value on-change]}]
  (let [tr     (<== [:lipas.ui.subs/translator])
        locale (tr)
        items  (<== [:lipas.ui.sports-sites.subs/admins])]
    ^{:key value}
    [autocompletes/autocomplete
     {:style     {:min-width "150px"}
      :value     value
      :deselect? true
      :multi?    true
      :label     (tr :search/search)
      :items     items
      :label-fn  (comp locale second)
      :value-fn  first
      :on-change on-change}]))

(defn admin-selector-single [{:keys [value on-change]}]
  (let [tr     (<== [:lipas.ui.subs/translator])
        locale (tr)
        items  (<== [:lipas.ui.sports-sites.subs/admins])]
    ^{:key value}
    [select
     {:value     value
      :items     items
      :label-fn  (comp locale second)
      :value-fn  first
      :on-change on-change}]))

(defn owner-selector [{:keys [value on-change]}]
  (let [tr     (<== [:lipas.ui.subs/translator])
        locale (tr)
        items  (<== [:lipas.ui.sports-sites.subs/owners])]
    ^{:key value}
    [autocompletes/autocomplete
     {:style     {:min-width "150px"}
      :value     value
      :multi?    true
      :deselect? true
      :label     (tr :search/search)
      :items     items
      :label-fn  (comp locale second)
      :value-fn  first
      :on-change on-change}]))

(defn owner-selector-single [{:keys [value on-change]}]
  (let [tr     (<== [:lipas.ui.subs/translator])
        locale (tr)
        items  (<== [:lipas.ui.sports-sites.subs/owners])]
    ^{:key value}
    [select
     {:value     value
      :items     items
      :label-fn  (comp locale second)
      :value-fn  first
      :on-change on-change}]))

(defn search-results-column-selector [{:keys [value on-change]}]
  (let [tr    (<== [:lipas.ui.subs/translator])
        items (<== [:lipas.ui.search.subs/results-table-columns])]
    [multi-select
     {:value        value
      :items        items
      :style        {:min-width "170px"}
      :label-fn     (comp :label second)
      :value-fn     first
      :label        (tr :actions/select-columns)
      :render-value (fn [v] (tr :actions/select-hint))
      :on-change    on-change}]))

(defn status-selector [{:keys [value on-change]}]
  (let [tr     (<== [:lipas.ui.subs/translator])
        locale (<== [:lipas.ui.subs/locale])
        items  (<== [:lipas.ui.search.subs/statuses])]
    ^{:key value}
    [autocompletes/autocomplete
     {:value        value
      :items        items
      :multi?       true
      :style        {:min-width "170px"}
      :label-fn     (comp locale second)
      :value-fn     first
      :label        (tr :actions/select-statuses)
      :render-value (fn [v] (tr :actions/select-hint))
      :on-change    on-change}]))

(defn status-selector-single [{:keys [value on-change read-only?]}]
  (let [tr     (<== [:lipas.ui.subs/translator])
        locale (<== [:lipas.ui.subs/locale])
        items  (<== [:lipas.ui.sports-sites.subs/resurrect-statuses])]
    [select
     {:value        value
      :items        items
      :style        {:min-width "170px"}
      :label-fn     (comp locale second)
      :value-fn     first
      :label        (tr :actions/select-statuses)
      :on-change    on-change
      :disabled     read-only?}]))
