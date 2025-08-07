(ns lipas.ui.sports-sites.routes.views
  (:require [lipas.ui.mui :as mui]
            [lipas.ui.components :as lui]
            [lipas.ui.components.forms :refer [->display-tf]]
            [lipas.ui.components.text-fields :as lui-tf]
            [lipas.ui.map.utils :as map-utils]
            [lipas.ui.sports-sites.activities.subs :as activities-subs]
            [lipas.ui.sports-sites.events :as sports-sites-events]
            [lipas.ui.utils :refer [<== ==>] :as utils]
            [lipas.utils :as cljc-utils]
            [reagent.core :as r]))

(defn calculate-total-route-length
  "Calculate total length of all geometry segments"
  [geoms]
  (if (and geoms (seq (:features geoms)))
    (map-utils/calculate-length-km geoms)
    "0.00"))

(defn simple-route-view
  "Simple mode for managing a single route with all segments"
  [{:keys [tr lipas-id type-code edit-data display-data can-edit?]}]
  (let [locale (<== [:lipas.ui.subs/locale])
        geoms (or (-> edit-data :location :geometries)
                  (-> display-data :location :geometries))
        total-length (calculate-total-route-length geoms)
        all-fids (when geoms
                   (set (map :id (:features geoms))))

        ;; Get activity type from subscription
        activity-val (<== [::activities-subs/activity-value-for-type-code type-code])
        activity-k (some-> activity-val keyword)

        ;; Get existing route or create structure for new one
        existing-route (or
                        (-> edit-data :activities activity-k :routes first)
                        (-> display-data :activities activity-k :routes first))

        route-id (or (:id existing-route)
                     (str "route-" (random-uuid)))

        ;; Get current route name from edit-data or display-data
        route-name (or (:route-name existing-route) {})

        editing? (<== [:lipas.ui.sports-sites.subs/editing? lipas-id])

        ;; Helper function to update route directly
        update-route-name (fn [locale-value]
                            (let [updated-route {:id route-id
                                                 :route-name (assoc route-name locale locale-value)
                                                 :fids all-fids
                                                 :route-length-km (js/parseFloat total-length)}]
                            ;; Update immediately in edit-data
                              (==> [:lipas.ui.sports-sites.events/edit-field
                                    lipas-id
                                    [:activities activity-k :routes]
                                    [updated-route]])))]

    [mui/grid {:container true :spacing 2}

     ;; Route name field
     [mui/grid {:item true :xs 12 :md 6}
      (if (or (not editing?) (not can-edit?))
        (->display-tf
         {:label (tr :route/name)
          :value (get route-name locale)
          :mui-props {:fullWidth true}}
         false
         1)
        [lui-tf/text-field
         {:label (tr :route/name)
          :value (get route-name locale "")
          :on-change update-route-name
          :variant "outlined"
          :fullWidth true}])]

     ;; Auto-calculated length display
     [mui/grid {:item true :xs 12 :md 6}
      [mui/box {:style {:padding-top "16px"}}
       [mui/typography {:variant "body1" :color "textSecondary"}
        (tr :route/length-km)]
       [mui/typography {:variant "h6"}
        (str total-length " km")]]]

     ;; Info text
     [mui/grid {:item true :xs 12}
      [mui/typography {:variant "caption" :color "textSecondary"}
       (tr :route/auto-calculated-info)]]]))

(defn routes-tab
  "Main component for the REITIT tab"
  [{:keys [facility tr lipas-id type-code display-data edit-data can-edit?]}]
  (r/with-let [simple-mode? (r/atom true)]
    (let [activity-val (<== [::activities-subs/activity-value-for-type-code type-code])
          activity-k (some-> activity-val keyword)
          existing-routes (or
                           (-> edit-data :activities activity-k :routes)
                           (-> display-data :activities activity-k :routes)
                           [])
          has-multiple-routes? (> (count existing-routes) 1)]

      [mui/box {:style {:padding "1em"}}
       ;; Mode toggle (Simple/Advanced)
       [mui/form-control-label
        {:control (r/as-element
                   [mui/switch
                    {:checked (not @simple-mode?)
                     :disabled has-multiple-routes?
                     :on-change #(reset! simple-mode? (not %2))}])
         :label (tr :route/advanced-mode)
         :label-placement "start"}]

       [mui/typography {:variant "caption" :color "textSecondary"}
        (if @simple-mode?
          (tr :route/simple-mode-help)
          (tr :route/advanced-mode-help))]

       ;; Content based on mode
       [mui/box {:style {:margin-top "2em"}}
        (if @simple-mode?
          ;; Simple mode content
          [simple-route-view
           {:tr tr
            :lipas-id lipas-id
            :type-code type-code
            :display-data display-data
            :edit-data edit-data
            :can-edit? can-edit?}]

          ;; Advanced mode content
          [mui/grid {:container true :spacing 2}
           [mui/grid {:item true :xs 12}
            [mui/typography {:variant "h6"} "Kehittynyt reittien hallinta"]
            [mui/typography {:variant "body2" :color "textSecondary"}
             "Voit luoda useita reittejä ja määrittää niiden osien järjestyksen."]]
           [mui/grid {:item true :xs 12}
            [mui/typography {:variant "body1"}
             "Toiminnallisuus tulossa pian..."]]])]])))