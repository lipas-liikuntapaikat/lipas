(ns lipas.ui.components.selects
  (:require
   [cljsjs.react-autosuggest]
   [clojure.reader :refer [read-string]]
   [clojure.spec.alpha :as s]
   [clojure.string :as string]
   [lipas.data.cities :as cities]
   [lipas.data.types :as types]
   [lipas.ui.components.autocompletes :as autocompletes]
   [lipas.ui.mui :as mui]
   [lipas.ui.utils :refer [<==] :as utils]
   [lipas.utils :as cutils]))

(def select-style {:min-width "170px"})

(defn error? [spec value required]
  (if (and spec (or value required))
    ((complement s/valid?) spec value)
    false))

(defn select [{:keys [label value items on-change value-fn label-fn
                      sort-fn sort-cmp deselect? spec required]
               :or   {value-fn :value
                      label-fn :label
                      sort-cmp compare}
               :as   props}]
  (let [on-change #(on-change (-> %
                                  .-target
                                  .-value
                                  read-string
                                  (as-> $ (if (and deselect? (= $ value))
                                            nil ; toggle
                                            $))))
        props   (-> props
                    (dissoc :value-fn :label-fn :label :sort-fn :sort-cmp
                            :deselect?)
                    (assoc :error (error? spec value required))
                    ;; Following fixes Chrome scroll issue
                    ;; https://github.com/mui-org/material-ui/pull/12003
                    (assoc :MenuProps
                           {:PaperProps
                            {:style
                             {:transform "translate2(0)"}}})
                    (assoc :value (if value (pr-str value) ""))
                    (assoc :on-change on-change))
        sort-fn (or sort-fn label-fn)]
    [mui/form-control
     (when label [mui/input-label label])
     (into [mui/select props]
           (for [i (sort-by sort-fn sort-cmp items)]
             (let [value (value-fn i)
                   label (label-fn i)]
               [mui/menu-item {:key   (pr-str value)
                               :value (pr-str value)}
                label])))]))

(defn multi-select [{:keys [label value items on-change value-fn
                            label-fn sort-fn sort-cmp]
                     :or   {value-fn :value
                            label-fn :label
                            sort-cmp compare}
                     :as   props}]
  (let [sort-fn (or sort-fn label-fn)]
    [mui/form-control
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
         (label-fn i)])]]))

(defn year-selector [{:keys [label value on-change required years multi?]
                      :as   props}]
  (let [years     (or years (range 1900 (inc (.getFullYear (js/Date.)))))
        component (if multi? multi-select select)]
    [component (merge (dissoc props :multi?)
                      {:label     label
                       :items     (map #(hash-map :label % :value %) years)
                       :on-change on-change
                       :sort-cmp  utils/reverse-cmp
                       :value     value
                       :required  required})]))

(defn number-selector [{:keys [unit] :as props}]
  [select
   (merge
    {:sort-fn   identity
     :sort-cmp  utils/reverse-cmp
     :value-fn  identity
     :label-fn  #(str % unit)}
    props)])

(defn date-picker [{:keys [label value on-change]}]
  [mui/text-field
   {:type "date"
    :label label
    :value (or value "")
    :Input-label-props
    {:shrink true} ; This makes the label show actually
    :on-change #(on-change (-> % .-target .-value))}])

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

(defn region-selector [{:keys [value on-change]}]
  (let [regions   (<== [:lipas.ui.sports-sites.subs/regions])
        tr        (<== [:lipas.ui.subs/translator])
        locale    (tr)
        avis      cities/by-avi-id
        provinces cities/by-province-id]
    ^{:key value}
    [autocompletes/autocomplete
     {:items          regions
      :value          (map (partial str "city-") value)
      :show-all?      true
      :label          (tr :search/search)
      :value-fn       :region-id
      :label-fn       (comp locale :name)
      :label-style-fn (fn [item label]
                        (let [id (get item :region-id "")]
                          (cond
                            (string/includes? id "province-") (strong1 label)
                            (string/includes? id "avi-")      (strong2 label)
                            :else                             label)))
      :on-change      (comp on-change (partial ->city-codes avis provinces))}]))

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

(defn type-category-selector [{:keys [value on-change]}]
  (let [cats         (<== [:lipas.ui.sports-sites.subs/type-categories])
        tr           (<== [:lipas.ui.subs/translator])
        locale       (tr)
        by-main-cats types/by-main-category
        by-sub-cats  types/by-sub-category]

    ^{:key value}
    [autocompletes/autocomplete
     {:items          cats
      :value          (map (partial str "type-") value)
      :show-all?      true
      :label          (tr :search/search)
      :value-fn       :cat-id
      :label-fn       (fn [item] (str (:type-code item) " " (-> item :name locale)))
      :label-style-fn (fn [item label]
                        (let [cat-id (get item :cat-id "")]
                          (cond
                            (string/includes? cat-id "sub-cat")  (strong1 label)
                            (string/includes? cat-id "main-cat") (strong2 label)
                            :else                                label)))
      :sort-fn        (fn [{:keys [type-code]}]
                        (case type-code
                          (1 2) (* 100 type-code)
                          type-code))
      :on-change      (comp on-change (partial ->type-codes by-main-cats by-sub-cats))}]))


(defn type-selector [{:keys [value on-change]}]
  (let [tr     (<== [:lipas.ui.subs/translator])
        locale (tr)
        types  (<== [:lipas.ui.sports-sites.subs/types-list locale])]
    ^{:key value}
    [autocompletes/autocomplete
     {:items     types
      :value     value
      :show-all? true
      :label     (tr :search/search)
      :value-fn  :type-code
      :label-fn  (comp locale :name)
      :on-change on-change}]))

(defn type-selector-single [{:keys [value on-change]}]
  (let [tr     (<== [:lipas.ui.subs/translator])
        locale (tr)
        types  (<== [:lipas.ui.sports-sites.subs/types-list locale])]
    ^{:key value}
    [select
     {:items     types
      :value     value
      :value-fn  :type-code
      :label-fn  (comp locale :name)
      :on-change on-change}]))

(defn city-selector-single [{:keys [value on-change]}]
  (let [tr     (<== [:lipas.ui.subs/translator])
        locale (tr)
        cities (<== [:lipas.ui.sports-sites.subs/cities-list])]
    ^{:key value}
    [select
     {:items     cities
      :value     value
      :style     select-style
      :label     (tr :stats/select-city)
      :value-fn  :city-code
      :label-fn  (comp locale :name)
      :on-change on-change}]))

(defn city-selector [{:keys [value on-change]}]
  (let [tr     (<== [:lipas.ui.subs/translator])
        locale (tr)
        cities (<== [:lipas.ui.sports-sites.subs/cities-list])]
    ^{:key value}
    [autocompletes/autocomplete
     {:items     cities
      :value     value
      :show-all? true
      :label     (tr :search/search)
      :value-fn  :city-code
      :label-fn  (comp locale :name)
      :on-change on-change}]))

(defn surface-material-selector [{:keys [value on-change]}]
  (let [tr     (<== [:lipas.ui.subs/translator])
        locale (tr)
        items  (<== [:lipas.ui.sports-sites.subs/surface-materials])]
    ^{:key value}
    [autocompletes/autocomplete
     {:value     value
      :label     (tr :search/search)
      :show-all? true
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
      :show-all? true
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
      :show-all? true
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
