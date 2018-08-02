(ns lipas.ui.front-page.views
  (:require [lipas.ui.mui :as mui]
            [lipas.ui.routes :refer [navigate!]]
            [lipas.ui.svg :as svg]
            [reagent.core :as r]))

(def links
  {:github    "https://github.com/lipas-liikuntapaikat/lipas-api"
   :lipas     "http://lipas.cc.jyu.fi/api/index.html"
   :geoserver "http://lipas.cc.jyu.fi/geoserver"
   :lipasinfo "https://www.jyu.fi/sport/fi/yhteistyo/lipas-liikuntapaikat.fi"
   :open-data "https://bit.ly/2v6wE9t"})

(defn grid-card [{:keys [title style link link-text]} & children]
  [mui/grid {:item true :xs 12 :md 12 :lg 12}
   [mui/card {:square true
              :raised true
              :style  (merge  {:height           "100%"
                               ;;:background-color "#e9e9e9"
                               :opacity          0.95}
                              style)}
    [mui/card-header {:title  title
                      :action (when link
                                (r/as-element
                                 [mui/icon-button
                                  {:on-click #(navigate! link)
                                   :color    "secondary"}
                                  [mui/icon "arrow_forward_ios"]]))}]
    (into [mui/card-content] children)
    [mui/card-actions
     (when link-text
       [mui/button {:color "secondary"
                    :href  link}
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
     [mui/grid {:item true :xs 12}
      [:div {:style {:padding "1em"}}
       [mui/grid {:container true
                  :spacing   16}

        ;; Disclaimer
        [grid-card {:style {:background-color "#f7ed33"}
                    :title (tr :disclaimer/headline)}
         [mui/typography (tr :disclaimer/test-version)]]

        ;; Sports Sites
        [grid-card {:title     (tr :sport/headline)
                    :link      "http://www.lipas.fi"
                    :link-text "lipas.fi"}
         [mui/typography (tr :sport/legacy-disclaimer)]]

        ;; Skating rinks portal
        [grid-card {:title     (tr :ice/headline)
                    :link      "/#/jaahalliportaali"
                    :link-text "Siirry portaaliin"}
         [mui/typography (tr :ice/description)]
         [:ul
          [:li "221 jäähallin perustiedot"]
          [:li "Energiankulutustietojen syöttäminen"]
          [:li "Perustietojen päivitys"]]]

        ;; Swimming pools portal
        [grid-card {:title     (tr :swim/headline)
                    :link      "/#/uimahalliportaali"
                    :link-text "Siirry portaaliin"}
         [mui/typography (tr :swim/description)]
         [:ul
          [:li "211 uimahallin perustiedot"]
          [:li "Energiankulutustietojen syöttäminen"]
          [:li "Perustietojen päivitys"]]]

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
                          :on-click #(navigate! (:lipas links))}
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
             svg/github-icon]]
           [mui/list-item-text {:primary "GitHub"}]]]]

        ;; Help
        [grid-card {:title (tr :help/headline)}

         [mui/list

          ;; Lipasinfo
          [mui/list-item {:button true
                          :on-click #(navigate! (:lipasinfo links))}
           [mui/list-item-icon
            [mui/icon "library_books"]]
           [mui/list-item-text "lipasinfo.fi"]]

          ;; Email
          [mui/list-item {:button true
                          :on-click #(navigate! "mailto:lipasinfo@jyu.fi")}
           [mui/list-item-icon
            [mui/icon "email"]]
           [mui/list-item-text "lipasinfo@jyu.fi"]]

          ;; Phone
          [mui/list-item {:button true
                          :on-click #(navigate! "tel:+358400247980")}
           [mui/list-item-icon
            [mui/icon "phone"]]
           [mui/list-item-text "0400 247 980"]]]]

        ;; ;; Data Users
        ;; [grid-card {:title (tr :data-users/headline)}
        ;;  [mui/typography (tr :data-users/description)]]

        ;; ;; Partners
        ;; [grid-card {:title (tr :partners/headline)}
        ;;  [mui/typography (tr :partners/description)]]

        ;; ;; Team
        ;; [grid-card {:title (tr :team/headline)}
        ;;  [mui/typography (tr :team/description)]]
        ]]]]]])

(defn main [tr]
  (create-panel tr))
