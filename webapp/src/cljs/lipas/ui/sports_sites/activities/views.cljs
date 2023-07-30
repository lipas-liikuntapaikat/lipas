(ns lipas.ui.sports-sites.activities.views
  (:require
   [clojure.pprint :as pprint]
   [lipas.ui.components :as lui]
   [lipas.ui.mui :as mui]
   [lipas.ui.sports-sites.activities.events :as events]
   [lipas.ui.sports-sites.activities.subs :as subs]
   [lipas.ui.utils :refer [<== ==>] :as utils]))

(defn media
  [{:keys [value on-change label]}]
  [mui/grid {:container true}

   ;; Label
   [mui/grid {:item true :xs 12}
    [mui/typography {:variant "caption"}
     label]]

   ;; Table
   [mui/grid {:item true :xs 12}
    [lui/table-v2
     {:headers        [[:url {:label "Linkki"}]
                       [:description {:label "Kuvaus"}][]]
      :items          value
      :allow-editing? (constantly true)
      :on-item-save   (fn [x] (js/console.log "lol"))
      :edit-label     "Muokkaa"
      :save-label     "Tallenna"
      :discard-label  "Kumoa"}]]])

(defn view
  [{:keys [type-code display-data edit-data geom-type tr]}]
  (let [activities (<== [::subs/activities-for-type type-code])
        locale     (tr)]
    [mui/grid {:container true}

     ;; Header
     [mui/grid {:item true :xs 12}
      [mui/typography {:variant "h6"}
       (get-in activities [:label locale])]]

     ;; Form
     [mui/grid {:item true :xs 12}
      [into [mui/form-group]
       (for [[prop-k {:keys [field]}] (:props activities)]
         (condp = (:type field)

           "multi-select" [lui/multi-select
                           {:items       (:opts field)
                            :label       (get-in field [:label locale])
                            :helper-text (get-in field [:description locale])
                            :label-fn    (comp locale second)
                            :value-fn    first
                            :on-change   #(js/alert "lol")
                            :value       (-> field :opts keys first)}]

           "test-field" [lui/text-field
                         {:label       (get-in field [:label locale])
                          :helper-text (get-in field [:description locale])
                          :on-change   #(js/alert "lol")
                          :value       "katti"}]

           "textarea" [lui/text-field
                       {:label       (get-in field [:label locale])
                        :helper-text (get-in field [:description locale])
                        :on-change   #(js/alert "lol")
                        :multiline   true
                        :value       "katti"}]

           "videos" [media
                     {:label       (get-in field [:label locale])
                      :helper-text (get-in field [:description locale])
                      :on-change   #(js/alert "videos")
                      :value       [{:url         "https://kissa.fi/video.mp4"
                                     :description {:fi "Ksisavideo"}}
                                    {:url         "https://koira.fi/video.mp4"
                                     :description {:fi "Koiravideo"}}]}]

           "images" [media
                     {:label       (get-in field [:label locale])
                      :helper-text (get-in field [:description locale])
                      :on-change   #(js/alert "videos")
                      :value       [{:url         "https://kissa.fi/kuva.png"
                                     :description {:fi "Kissakuva"}}
                                    {:url         "https://koira.fi/kuva.png"
                                     :description {:fi "Koirakuva"}}]}]

           nil))]]
     [mui/grid {:item true :xs 12}
      [lui/expansion-panel {:label "debug"}
       [:pre (with-out-str (pprint/pprint activities))]]]]))
