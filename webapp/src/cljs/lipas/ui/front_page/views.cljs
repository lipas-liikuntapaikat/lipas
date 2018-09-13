(ns lipas.ui.front-page.views
  (:require [lipas.ui.components :as lui]
            [lipas.ui.mui :as mui]
            [lipas.ui.routes :refer [navigate!]]
            [lipas.ui.svg :as svg]
            [lipas.ui.utils :as utils]
            [reagent.core :as r]))

(def links
  {:github    "https://github.com/lipas-liikuntapaikat"
   :lipas-api "http://lipas.cc.jyu.fi/api/index.html"
   :geoserver "http://lipas.cc.jyu.fi/geoserver"
   :lipasinfo "https://www.jyu.fi/sport/fi/yhteistyo/lipas-liikuntapaikat.fi"
   :open-data "https://bit.ly/2v6wE9t"})

(def logos
  [{:img "img/partners/okm.png"}
   {:img "img/partners/jaakiekkoliitto.svg"}
   {:img "img/partners/kuntaliitto.png"}
   {:img "img/partners/metsahallitus.svg"}
   {:img "img/partners/sport_venue.png"}
   {:img "img/partners/suh.png"}
   {:img "img/partners/syke.svg"}
   {:img "img/partners/ukty.png"}
   {:img "img/partners/vtt.svg"}
   {:img "img/partners/avi.png"}])

(defn ->logo [{:keys [img]}]
  [:img {:style {:margin     "1em"
                 :max-width  "200px"
                 :max-height "100px"}
         :src   img}])

(defn footer [tr]
  (into
   [mui/grid {:item  true :xs 12
              :style {:padding "2em"
                      :background-color mui/gray3}}
    [mui/typography {:variant "display2"}
     (tr :partners/headline)]]
   (map ->logo logos)))

(defn grid-card [{:keys [title style link link-text]} & children]
  [mui/grid {:item true :xs 12 :md 12 :lg 12}
   [mui/card {:square true
              :raised true
              :style
              (merge
               {:height           "100%"
                :background-color "rgb(250, 250, 250)"
                ;;:background-color "#e9e9e9"
                :font-size "1.25em"
                :opacity          0.95}
                      style)}
    [mui/card-header {:title  title
                      :action (when link
                                (r/as-element
                                 [mui/icon-button
                                  {:href  link
                                   :color :secondary}
                                  [mui/icon "arrow_forward_ios"]]))}]
    (into [mui/card-content] children)
    [mui/card-actions
     (when link-text
       [mui/button {:variant :text
                    :color   "secondary"
                    :href    link}
        (str "> " link-text)])]]])

(defn create-panel [tr]
  [mui/grid {:container true
             :style
             {:background-position "right top"
              :background-color    "#fafafa"
              :background-image    "url('/img/background_full.png')"
              :background-repeat   :no-repeat}}
   [mui/grid {:item true :xs 12 :md 6 :lg 6}
    [mui/grid {:container true}

     ;; LIPAS
     [mui/grid {:item true :xs 12}
      [:div {:style {:padding "1em"}}
       [mui/grid {:container true
                  :spacing   16}

        [grid-card {:title     (tr :menu/headline)}

         [mui/typography {:variant :body2}
          "LIPAS muodostuu kolmesta palvelusta"]
         ;; [mui/typography (tr :home-page/description)]

         [mui/list {:style {:margin-top "1em"}}

          [mui/list-item {:button   true
                          :on-click #(navigate! "/#/liikuntapaikat")}
           [mui/list-item-icon
            [mui/icon "fitness_center"]]
           [mui/list-item-text {:primary "Liikuntapaikat"}]]

          ;; Ice stadiums portal
          [mui/list-item {:button   true
                          :on-click #(navigate! "/#/jaahalliportaali")}
           [mui/list-item-icon
            [mui/icon "ac_unit"]]
           [mui/list-item-text {:primary "Jäähalliportaali"}]]

          ;; Swimming pools portal
          [mui/list-item {:button   true
                          :on-click #(navigate! "/#/uimahalliportaali")}
           [mui/list-item-icon
            [mui/icon "pool"]]
           [mui/list-item-text {:primary "Uimahalliportaali"}]]]]

        ;; [grid-card {:title     (tr :menu/headline)
        ;;             :link      "http://www.lipas.fi"
        ;;             :link-text "lipas.fi"}
        ;;  [mui/typography (tr :home-page/description)]
        ;;  [:ul
        ;;   [lui/li (tr :sport/up-to-date-information)]
        ;;   [lui/li (tr :sport/updating-tools)]
        ;;   [lui/li (tr :sport/open-interfaces)]]
        ;;  [mui/typography (tr :sport/legacy-disclaimer)]]


        ;; Skating rinks portal
        [grid-card {:title     (tr :ice/headline)
                    :link      "/#/jaahalliportaali"
                    :link-text (tr :actions/browse-to-portal)}
         [mui/typography (tr :ice/description)]
         [:ul
          [lui/li (tr :ice/basic-data-of-halls)]
          [lui/li (tr :ice/entering-energy-data)]
          [lui/li (tr :ice/updating-basic-data)]]]

        ;; Swimming pools portal
        [grid-card {:title     (tr :swim/headline)
                    :link      "/#/uimahalliportaali"
                    :link-text (tr :actions/browse-to-portal)}
         [mui/typography (tr :swim/description)]
         [:ul
          [lui/li (tr :swim/basic-data-of-halls)]
          [lui/li (tr :swim/entering-energy-data)]
          [lui/li (tr :swim/updating-basic-data)]]]

        ;; Open Data
        [grid-card {:title (tr :open-data/headline)}
         [mui/list

          ;; info
          [mui/list-item {:button   true
                          :on-click #(navigate! (:open-data links))}
           [mui/list-item-icon
            [mui/icon "info"]]
           [mui/list-item-text {:primary "Info"}]]

          ;; Lipas-API
          [mui/list-item {:button   true
                          :on-click #(navigate! (:lipas-api links))}
           [mui/list-item-icon
            [:img {:height "24px"
                   :src    "/img/swagger_logo.svg"}]]
           [mui/list-item-text {:primary "Lipas API"}]]

          ;; Geoserver
          [mui/list-item {:button   true
                          :on-click #(navigate! (:geoserver links))}
           [mui/list-item-icon
            [:img {:height "24px"
                   :src    "/img/geoserver_logo.svg"}]]
           [mui/list-item-text "Geoserver"]]

          ;; Github
          [mui/list-item {:button   true
                          :on-click #(navigate! (:github links))}
           [mui/list-item-icon
            [mui/svg-icon
             [svg/github-icon]]]
           [mui/list-item-text {:primary "GitHub"}]]]]

        ;; Help
        [grid-card {:title (tr :help/headline)}

         [mui/list

          ;; Lipasinfo
          [mui/list-item {:button   true
                          :on-click #(navigate! (:lipasinfo links))}
           [mui/list-item-icon
            [mui/icon "library_books"]]
           [mui/list-item-text "lipasinfo.fi"]]

          ;; Email
          [mui/list-item {:button   true
                          :on-click #(navigate! "mailto:lipasinfo@jyu.fi")}
           [mui/list-item-icon
            [mui/icon "email"]]
           [mui/list-item-text "lipasinfo@jyu.fi"]]

          ;; Phone
          [mui/list-item {:button   true
                          :on-click #(navigate! "tel:+358400247980")}
           [mui/list-item-icon
            [mui/icon "phone"]]
           [mui/list-item-text "0400 247 980"]]]]]]]]]
   [footer tr]])

(defn main [tr]
  (create-panel tr))
