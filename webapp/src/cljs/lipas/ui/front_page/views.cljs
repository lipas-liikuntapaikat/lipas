(ns lipas.ui.front-page.views
  (:require
   [lipas.ui.components :as lui]
   [lipas.ui.mui :as mui]
   [lipas.ui.svg :as svg]
   [lipas.ui.utils :refer [==> navigate!] :as utils]
   [reagent.core :as r]))

(def links
  {:github    "https://github.com/lipas-liikuntapaikat"
   :lipas-api "http://lipas.cc.jyu.fi/api/index.html"
   :geoserver "http://lipas.cc.jyu.fi/geoserver"
   :lipasinfo "https://www.jyu.fi/sport/fi/yhteistyo/lipas-liikuntapaikat.fi"
   :open-data "https://bit.ly/2v6wE9t"
   :youtube   "https://www.youtube.com/channel/UC-QFmRIY1qYPXX79m23JC4g"})

(def logos
  [{:img "img/partners/okm.png"}
   {:img "img/partners/jaakiekkoliitto.svg" :full-height? true}
   {:img "img/partners/kuntaliitto.png"}
   {:img "img/partners/metsahallitus.svg"}
   {:img "img/partners/sport_venue.png"}
   {:img "img/partners/suh.png"}
   {:img "img/partners/syke.svg" :full-height? true}
   {:img "img/partners/ukty.png"}
   {:img "img/partners/vtt.svg"}
   {:img "img/partners/avi.png"}])

(def known-users
  [{:label "Retkikartta.fi" :href "https://www.retkikartta.fi/"}
   {:label "Paikkatietoikkuna" :href "https://www.paikkatietoikkuna.fi/"}
   {:label "palvelukartta.hel.fi" :href "https://palvelukartta.hel.fi/"}
   {:label "Finterest" :href "https://finterest.fi/"}
   {:label "likiliikkuja.fi" :href "https://likiliikkuja.fi/"}
   {:label "liiteri" :href "https://liiteri.ymparisto.fi/"}
   {:label "TEAviisari" :href "https://teaviisari.fi"}
   {:label "Uimaan.fi" :href "https://uimaan.fi/"}
   {:label "Me-säätiö" :href "http://data.mesaatio.fi/harrastaminen/"}
   {:label "kuopio.fi" :href "https://www.kuopio.fi/liikunta-ja-ulkoilukartat"}
   {:label "Tässä.fi" :href "https://tassa.fi/"}
   {:label "OKM" :href "https://minedu.fi/liikuntapaikkarakentaminen"}
   {:label "Tulikartta.fi" :href "https://www.tulikartta.fi/"}
   {:label "SportVenue" :href "https://www.sportvenue.fi/"}
   {:label "FCG" :href "http://www.fcg.fi/shvk"}])

(defn ->logo [{:keys [img full-height?]}]
  [mui/grid {:item true}
   [:img
    {:style
     (merge
      {:margin "1em" :max-width "200px" :max-height "100px"}
      (when full-height? ;; For IE11.
        {:height "100%"}))
     :src img}]])

(defn ->link [{:keys [label href]}]
  [mui/grid {:item true :md 6 :lg 4}
   [mui/link {:href href :variant "h5"}
    label]])

(defn footer [{:keys [title]} & contents]
  (into
   [mui/grid
    {:container true :spacing 8 :align-items "center"
     :style     {:background-color mui/gray1 :padding "2em"}}
    [mui/grid {:item true :xs 12 :style {:margin-bottom "1em"}}
     [mui/hidden {:smUp true}
      [mui/typography {:variant "h4" :style {:opacity 0.7}}
       title]]
     [mui/hidden {:xsDown true}
      [mui/typography {:variant "h3" :style {:opacity 0.7}}
       title]]]]
   contents))

(defn grid-card [{:keys [title style link link-text xs md lg]
                  :or   {xs 12 md 6 lg 4}} & children]
  [mui/grid {:item true :xs xs :md md :lg lg}
   [mui/card
    {:square true
     :raised true
     :style
     (merge
      {:background-color "rgb(250, 250, 250)"
       ;;:background-color "#e9e9e9"
       :font-size        "1.25em"
       :opacity          0.95}
      style)}

    ;; Header
    [mui/card-header
     (merge
      {:title  title
       :action (when link
                 (r/as-element
                  [mui/icon-button
                   {:href  link
                    :color :secondary}
                   [mui/icon "arrow_forward_ios"]]))}
      (when link
        {:titleTypographyProps
         {:component "a"
          :href      link
          :style     {:font-weight     600
                      :text-decoration "none"}}}))]

    ;; Content
    (into [mui/card-content] children)

    ;; Actions
    (when link-text
      [mui/card-actions
       [mui/button {:variant :text :color "secondary" :href link}
        (str "> " link-text)]])]])

(defn create-panel [tr]
  [mui/grid {:container true}

   [mui/mui-theme-provider {:theme mui/jyu-theme-dark}

    ;; "Jumbotron" header
    [mui/grid {:item true :xs 12}
     [mui/paper {:square true
                 :style  {:background-color mui/secondary
                          ;; :text-align       :center
                          :padding          "1em 2em 1em 2em"}}

      [mui/grid {:container true :align-items :center :spacing 16}

       [mui/grid {:item true :xs 12}
        [mui/typography {:variant "h3" :style {:color "white"}}
         "LIPAS"]]

       [mui/grid {:item true}
        [mui/typography {:variant "h6" :style {:color "white"}}
         (tr :sport/headline)]]

       [mui/grid {:item true}
        [mui/typography {:variant "h6" :style {:color "white"}}
         (tr :ice/headline)]]

       [mui/grid {:item true}
        [mui/typography {:variant "h6" :style {:color "white"}}
         (tr :swim/headline)]]]]]]

   ;; Main section with background image
   [mui/grid {:item true :xs 12}
    [mui/grid
     {:container true
      :spacing   16
      :style
      {:padding             "1em"
       :background-position "right center"
       :background-color    mui/gray3
       :background-image    "url('/img/background_full.png')"
       :background-size     :contain
       :background-repeat   :no-repeat}}

     ;; Sports sites
     [grid-card
      {:title     (tr :sport/headline)
       :link      "/#/liikuntapaikat"
       :link-text (tr :actions/browse-to-map)}
      [mui/typography {:variant "body1"}
       (tr :sport/description)]
      [:ul
       [lui/li (tr :sport/up-to-date-information)]
       [lui/li (tr :sport/updating-tools)]
       [lui/li (tr :sport/open-interfaces)]]]

     ;; Ice stadiums portal
     [grid-card
      {:title     (tr :ice/headline)
       :link      "/#/jaahalliportaali"
       :link-text (tr :actions/browse-to-portal)}
      [mui/typography {:variant "body1"}
       (tr :ice/description)]
      [:ul
       [lui/li (tr :ice/basic-data-of-halls)]
       [lui/li (tr :ice/entering-energy-data)]
       [lui/li (tr :ice/updating-basic-data)]]]

     ;; Swimming pools portal
     [grid-card
      {:title     (tr :swim/headline)
       :link      "/#/uimahalliportaali"
       :link-text (tr :actions/browse-to-portal)}
      [mui/typography {:variant "body1"}
       (tr :swim/description)]
      [:ul
       [lui/li (tr :swim/basic-data-of-halls)]
       [lui/li (tr :swim/entering-energy-data)]
       [lui/li (tr :swim/updating-basic-data)]]]

     ;; Reports
     [grid-card
      {:title     (tr :stats/headline)
       :link      "/#/tilastot"
       :link-text (tr :stats/browse-to)}
      [mui/typography {:variant "body1"}
       (tr :stats/description)]
      [:ul
       [lui/li (tr :stats/bullet1)]
       [lui/li (tr :stats/bullet2)]]]

     ;; Open Data
     [grid-card {:title (tr :open-data/headline)}
      [mui/list

       ;; info
       [mui/list-item {:button true :component "a" :href (:open-data links)}
        [mui/list-item-icon
         [mui/icon "info"]]
        [mui/list-item-text {:primary "Info"}]]

       ;; Lipas-API
       [mui/list-item {:button true :component "a" :href (:lipas-api links)}
        [mui/list-item-icon
         [:img {:height "24px" :src "/img/swagger_logo.svg"}]]
        [mui/list-item-text {:primary "Lipas API"}]]

       ;; Geoserver
       [mui/list-item {:button true :component "a" :href (:geoserver links)}
        [mui/list-item-icon
         [:img {:height "24px" :src "/img/geoserver_logo.svg"}]]
        [mui/list-item-text "Geoserver"]]

       ;; Github
       [mui/list-item {:button true :component "a" :href (:github links)}
        [mui/list-item-icon
         [mui/svg-icon
          [svg/github-icon]]]
        [mui/list-item-text {:primary "GitHub"}]]]]

     ;; Help
     [grid-card {:title (tr :help/headline)}

      [mui/list

       ;; Lipasinfo
       [mui/list-item {:button true :component "a" :href (:lipasinfo links)}
        [mui/list-item-icon
         [mui/icon "library_books"]]
        [mui/list-item-text "lipasinfo.fi"]]

       ;; Youtube
       [mui/list-item {:button true :component "a" :href (:youtube links)}
        [mui/list-item-icon
         [mui/icon "video_library"]]
        [mui/list-item-text "Youtube"]]

       ;; Email
       [mui/list-item {:button true :component "a" :href "mailto:lipasinfo@jyu.fi"}
        [mui/list-item-icon
         [mui/icon "email"]]
        [mui/list-item-text "lipasinfo@jyu.fi"]]

       ;; Phone
       [mui/list-item {:button true :component "a" :href "tel:+358400247980"}
        [mui/list-item-icon
         [mui/icon "phone"]]
        [mui/list-item-text "0400 247 980"]]]]]]

   ;; LIPAS-data users list
   [footer {:title (tr :data-users/headline)}

    ;; List items
    (into [:<>] (map ->link known-users))

    ;; Are you also data user? text
    [mui/grid {:container true :xs 12 :style {:margin-top "2em"}}
     [mui/grid {:item true :xs 12}
      [mui/typography {:color "secondary" :variant "h4"}
       (tr :data-users/data-user?)]]

     ;; Tell us mailto link
     [mui/grid {:item true :xs 12}
      [mui/link
       {:underline "always"
        :variant   "h6"
        :href
        (utils/->mailto
         {:email   "lipasinfo@jyu.fi"
          :subject (tr :data-users/email-subject)
          :body    (tr :data-users/email-body)})}
       (tr :data-users/tell-us)]]]]

   ;; Partner logos
   [footer {:title (tr :partners/headline)}
    (into [:<>] (map ->logo logos))]])

(defn main [tr]
  [create-panel tr])
