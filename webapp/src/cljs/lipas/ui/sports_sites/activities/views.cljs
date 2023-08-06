(ns lipas.ui.sports-sites.activities.views
  (:require
   [clojure.pprint :as pprint]
   [lipas.ui.components :as lui]
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
  [into [mui/grid {:container true :spacing 2 :style {:padding-top "1em" :padding-bottom "1em"}}]
   (for [child children]
     [mui/grid {:item true :xs 12}
      child])])

(defn media
  [{:keys [value on-change label tr read-only?]}]
  (let [tr (<== [:lipas.ui.subs/translator])]
    [mui/grid {:container true}

     ;; Label
     [mui/grid {:item true :xs 12}
      [mui/typography {:variant "caption"}
       label]]

     ;; Table
     [mui/grid {:item true :xs 12}
      [lui/form-table
       {:headers         [[:url "Linkki"]
                          [:description "Kuvaus"][]]
        :read-only?      false
        :items           value
        :on-add          #(js/alert "TODO")
        :on-edit         #(js/alert "TODO")
        :on-delete       #(js/alert "TODO")
        :add-tooltip     "Lisää"
        :edit-tooltip    (tr :actions/edit)
        :delete-tooltip  (tr :actions/delete)
        :confirm-tooltip (tr :confirm/press-again-to-delete)
        :add-btn-size    "small"
        :key-fn          :url
        }]]]))

(declare make-field)

(defn route-form
  [{:keys [locale on-change geom-type lipas-id route-props state]}]
  (into [nice-form {}]
        (for [[prop-k {:keys [field]}] route-props]
          (make-field
           {:field        field
            :prop-k       prop-k
            :edit-data    @state
            :display-data @state
            :locale       locale
            :set-field    #(swap! state assoc prop-k %2)
            :geom-type    geom-type
            :lipas-id     lipas-id}))))

(defn routes
  [{:keys [read-only? route-props lipas-id _display-data _edit-data
           locale geom-type label description set-field]
    :as props}]
  (let [mode     (<== [::subs/mode])
        fids     (<== [::subs/selected-features])
        routes   (<== [::subs/routes lipas-id])
        editing? (not read-only?)]
    (println "Routes")
    (println routes)
    (r/with-let [route-form-state (r/atom {})]
      [mui/grid {:container true :spacing 2}

       (when (= :default mode)
         [:<>
          (when (seq routes)
            [mui/grid {:item true :xs 12}
             [lui/table
              {:headers
               [[:route-name "Nimi"]
                [:route-length "Pituus (km)"]]
               :items     routes
               :on-select (fn [route]
                            (==> [::events/select-route route])
                            (reset! route-form-state (dissoc route :fids)))}]])
          (when-not read-only?
            [mui/grid {:item true :xs 12}
             [mui/button
              {:variant  "contained"
               :color    "secondary"
               :on-click #(==> [::events/add-route lipas-id])}
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
         (let [set-field (partial set-field :route1)]
           [:<>
            [mui/typography {:variant "h6"}
             label]

            [mui/typography {:variant "body2"}
             description]

            [route-form
             {:locale      locale
              :lipas-id    lipas-id
              :geom-type   geom-type
              :route-props route-props
              :on-change   set-field
              :state       route-form-state}]

            [mui/button
             {:variant  "contained"
              :color    "secondary"
              :on-click #(==> [::events/finish-route-details
                               {:fids     fids
                                :route    @route-form-state
                                :lipas-id lipas-id}])}
             "Reitti valmis"]]))

       [mui/grid {:item true :xs 12}
        [lui/expansion-panel {:label "debug route props"}
         [mui/grid {:item true :xs 12}
          [:pre (with-out-str (pprint/pprint props))]]]]

       ])))

(defn make-field
  [{:keys [field edit-data locale prop-k read-only? lipas-id set-field]}]
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

    "multi-select" [lui/multi-select
                    {:disabled    read-only?
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
                   :on-change   #(set-field prop-k %)
                   :value       (get-in edit-data [prop-k])}]

    "textarea" [lui/text-field
                {:disabled    read-only?
                 :variant     "outlined"
                 :label       (get-in field [:label locale])
                 :helper-text (get-in field [:description locale])
                 :on-change   #(set-field prop-k %)
                 :multiline   true
                 :fullWidth   true
                 :value       (get-in edit-data [prop-k])}]

    "videos" [media
              {:read-only?  read-only?
               :label       (get-in field [:label locale])
               :helper-text (get-in field [:description locale])
               :on-change   #(set-field prop-k %)
               :value       (->>
                             [{:url         "https://kissa.fi/video.mp4"
                               :description {:fi "Ksisavideo"}}
                              {:url         "https://koira.fi/video.mp4"
                               :description {:fi "Koiravideo"}}]
                             (map #(update % :description locale)))}]

    "images" [media
              {:read-only?  read-only?
               :label       (get-in field [:label locale])
               :helper-text (get-in field [:description locale])
               :on-change   #(set-field prop-k %)
               :value       (->> [{:url         "https://kissa.fi/kuva.png"
                                   :description {:fi "Kissakuva"}}
                                  {:url         "https://koira.fi/kuva.png"
                                   :description {:fi "Koirakuva"}}]
                                 (map #(update % :description locale)))}]

    "linestring-feature-collection" [routes
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
        locale     (tr)
        set-field  (partial set-field :activities)
        editing?   (<== [:lipas.ui.sports-sites.subs/editing? lipas-id])
        read-only? (not editing?)]
    [mui/grid {:container true}

     ;; Header
     [mui/grid {:item true :xs 12}
      [mui/typography {:variant "h6"}
       (get-in activities [:label locale])]]

     ;; Form
     [mui/grid {:item true :xs 12}
      [into [nice-form {}]
       (for [[prop-k {:keys [field]}] (:props activities)]
         (make-field
          {:field        field
           :prop-k       prop-k
           :edit-data    edit-data
           :read-only?   read-only?
           :display-data display-data
           :locale       locale
           :set-field    set-field
           :geom-type    geom-type
           :lipas-id     lipas-id}))]]

     ;; Debug
     [mui/grid {:item true :xs 12}
      [lui/expansion-panel {:label "debug"}
       [:pre (with-out-str (pprint/pprint activities))]]]]))


(comment

  (do
    (==> [:lipas.ui.map.events/set-zoom 14])
    (==> [:lipas.ui.map.events/set-center 6919553.618920735 445619.43358133035]))

  (==> [:lipas.ui.map.events/show-sports-site 607314])

  )
