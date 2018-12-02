(ns lipas.ui.search.views
  (:require [lipas.ui.components :as lui]
            [lipas.ui.mui :as mui]
            [lipas.ui.search.events :as events]
            [lipas.ui.search.subs :as subs]
            [lipas.ui.utils :refer [<== ==>] :as utils]))

(defn type-selector [{:keys [tr value on-change]}]
  (let [locale (tr)
        types  (<== [:lipas.ui.sports-sites.subs/types-list locale])]
    ^{:key value}
    [lui/autocomplete
     {:items     types
      :value     value
      :label     (tr :search/search)
      :value-fn  :type-code
      :label-fn  (comp locale :name)
      :on-change on-change}]))

(defn city-selector [{:keys [tr value on-change]}]
  (let [locale (tr)
        types  (<== [:lipas.ui.sports-sites.subs/cities-list])]
    ^{:key value}
    [lui/autocomplete
     {:items     types
      :value     value
      :label     (tr :search/search)
      :value-fn  :city-code
      :label-fn  (comp locale :name)
      :on-change on-change}]))

(defn surface-material-selector [{:keys [tr value on-change]}]
  (let [locale (tr)
        items  (<== [:lipas.ui.sports-sites.subs/surface-materials])]
    [lui/autocomplete
     {:value     value
      :label     ""
      :items     items
      :label-fn  (comp locale second)
      :value-fn  first
      :on-change on-change}]))

(defn filters [{:keys [tr]}]
  (let [type-codes        (<== [::subs/types-filter])
        city-codes        (<== [::subs/cities-filter])
        area-min          (<== [::subs/area-min-filter])
        area-max          (<== [::subs/area-max-filter])
        surface-materials (<== [::subs/surface-materials-filter])]
    [mui/grid {:container true
               :spacing   16}

     ;; Types filter
     [mui/typography {:variant :caption}
      (tr :actions/select-types)]
     [mui/grid {:item true :xs 12}
      [type-selector
       {:tr        tr
        :value     type-codes
        :on-change #(==> [::events/set-type-filter %])}]]

     ;; Cities filter
     [mui/typography {:variant :caption :xs 12
                      :style   {:margin-top "1em"}}
      (tr :actions/select-cities)]
     [mui/grid {:item true :xs 12}
      [city-selector
       {:tr        tr
        :value     city-codes
        :on-change #(==> [::events/set-city-filter %])}]]

     ;; Area filters
     [mui/typography {:variant :caption
                      :style   {:margin-top "1em"}}
      (tr :actions/filter-area-m2)]

     [mui/grid {:item true :xs 12}
      [mui/grid {:container true :spacing 16}

       ;; Area min filter
       [mui/grid {:item true :xs 6}
        [lui/text-field {:label     "Min"
                         :defer-ms  500
                         :type      "number"
                         :value     area-min
                         :on-change #(==> [::events/set-area-min-filter %])}]]

       ;; Area max filter
       [mui/grid {:item true :xs 6}
        [lui/text-field {:label     "Max"
                         :defer-ms  500
                         :type      "number"
                         :value     area-max
                         :on-change #(==> [::events/set-area-max-filter %])}]]]]

     ;; Surface materials filter
     [mui/typography {:variant :caption
                      :style   {:margin-top "1em"}}
      (tr :actions/filter-surface-materials)]
     [mui/grid {:item true :xs 12}
      [surface-material-selector
       {:tr        tr
        :value     surface-materials
        :on-change #(==> [::events/set-surface-materials-filter %])}]]]))

(defn search-view [{:keys [tr on-result-click]}]
  (let [search-str (<== [::subs/search-string])
        results    (<== [::subs/search-results-list])
        total      (<== [::subs/search-results-total-count])]
    [mui/grid {:item true :xs 12 :style {:flex 1}}
     [mui/typography {:style   {:margin-bottom "0.5em"}
                      :variant :display1}
      "LIPAS"]
     [mui/grid {:container true}
      [mui/grid {:item true :xs 12}
       [lui/text-field {:value     search-str
                        :on-change #(==> [::events/update-search-string %])}]
       [mui/button {:on-click #(==> [::events/submit-search])}
        [mui/icon "search"]
        "Hae"]]]
     [lui/expansion-panel {:label            "Rajaa hakua"
                           :default-expanded true}
      [filters {:tr tr}]]
     (when-not (empty? results)
       [mui/typography {:variant "body2"
                        :style   {:margin-top  "1em"
                                  :margin-left "1em"}}
        (str total " hakutulosta")])
     [mui/divider]
     [into [mui/list]
      (for [{:keys [type city score name] :as result} results]
        [mui/list-item
         {:button   true
          :on-click #(on-result-click result)}
         [mui/list-item-text {:primary   name
                              :secondary (str type ", " city ", " score)}]])]]))
