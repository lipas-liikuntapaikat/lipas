(ns lipas.ui.front-page.views
  (:require
   [lipas.ui.components :as lui]
   [lipas.ui.mui :as mui]
   [lipas.ui.svg :as svg]
   [lipas.ui.utils :refer [<== ==> navigate!] :as utils]
   [reagent.core :as r]))

(def links
  {:github    "https://github.com/lipas-liikuntapaikat"
   :lipas-api "http://lipas.cc.jyu.fi/api/index.html"
   :geoserver "http://lipas.cc.jyu.fi/geoserver"
   :lipasinfo "https://www.jyu.fi/sport/fi/yhteistyo/lipas-liikuntapaikat.fi"
   :open-data "https://bit.ly/2v6wE9t"
   :youtube   "https://www.youtube.com/channel/UC-QFmRIY1qYPXX79m23JC4g"
   :cc4.0     "https://creativecommons.org/licenses/by/4.0/"})

(def logos
  [{:img "img/partners/okm.png"}
   #_{:img "img/partners/jaakiekkoliitto.svg" :full-height? true}
   {:img "img/partners/kuntaliitto.png"}
   {:img "img/partners/metsahallitus.svg"}
   #_{:img "img/partners/sport_venue.png"}
   {:img "img/partners/suh.png"}
   {:img "img/partners/syke.png" :full-height? true}
   {:img "img/partners/ukty.png"}
   #_{:img "img/partners/vtt.svg"}
   {:img "img/partners/avi.png"}])

(def known-users
  [{:label "OKM" :href "https://minedu.fi/liikunta"}
   {:label "Retkikartta.fi" :href "https://www.retkikartta.fi/"}
   {:label "Paikkatietoikkuna" :href "https://www.paikkatietoikkuna.fi/"}
   {:label "palvelukartta.hel.fi" :href "https://palvelukartta.hel.fi/"}
   {:label "Finterest" :href "https://finterest.fi/"}
   {:label "Puskaparkit" :href "http://bit.do/puskaparkit"}
   {:label "liiteri" :href "https://liiteri.ymparisto.fi/"}
   {:label "TEAviisari" :href "https://teaviisari.fi"}
   {:label "Uimaan.fi" :href "https://uimaan.fi/"}
   {:label "Me-säätiö" :href "http://data.mesaatio.fi/harrastaminen/"}
   {:label "kuopio.fi" :href "https://www.kuopio.fi/liikunta-ja-ulkoilukartat"}
   {:label "Tässä.fi" :href "https://tassa.fi/"}
   {:label "Tulikartta.fi" :href "https://www.tulikartta.fi/"}
   {:label "SportVenue" :href "https://www.sportvenue.fi/"}
   {:label "FCG" :href "http://www.fcg.fi/shvk"}
   {:label "Ulkoliikunta.fi" :href "https://ulkoliikunta.fi/"}
   {:label "Aluehallintovirasto" :href "https://www.avi.fi/web/avi/liikunta"}
   {:label "LIKES" :href "https://www.likes.fi/"}
   {:label "Kuntaliitto" :href "https://www.kuntaliitto.fi/"}
   {:label "EPSHP" :href "https://www.hyvis.fi/web/etela-pohjanmaa/palvelut"}
   {:label "Paikkatietoalusta" :href "http://www.paikkatietoalusta.fi"}
   {:label "Ladulle.fi" :href "https://ladulle.fi/"}
   {:label "Liikkuva Kuopio" :href "https://liikkuvakuopio.fi/"}
   {:label "Karttaselain" :href "https://www.karttaselain.fi/"}
   {:label "Go SportY" :href "https://www.gosporty.fi/"}])

(defn ->logo [{:keys [img full-height?]}]
  [mui/grid {:item true :xs 12 :sm "auto" :md "auto" :lg "auto" :xl "auto"}
   [:img
    {:style
     (merge
      {:margin "1em" :max-width "200px" :max-height "100px"}
      (when full-height? ;; For IE11.
        {:height "100%"}))
     :src img}]])

(defn ->link [{:keys [label href color] :or {color "primary"}}]
  [mui/grid {:item true :xs 12 :md 6 :lg 3 :style {:text-align "center"}}
   [mui/link {:href href :variant "h6" :color color}
    label]])

(defn footer
  [{:keys [title bg-color title-style]
    :or   {bg-color mui/gray1 title-style {:opacity 0.7}}} & contents]

  [mui/grid
   {:container true
    :style
    {:background-color bg-color
     :padding          "1em 2em 1em 2em"}}

   ;; Title
   [mui/grid {:item true :xs 12 :style {:margin-bottom "1em"}}
    [mui/hidden {:smUp true}
     [mui/typography {:variant "h5" :style title-style}
      title]]
    [mui/hidden {:xsDown true}
     [mui/typography {:variant "h4" :style title-style}
      title]]]

   ;; Content
   [mui/grid {:item true :xs 12}
    (into
     [mui/grid
      {:container true}]
     contents)]])

(defn grid-card
  [{:keys [title style link link-text xs md lg xl]
    :or   {xs 12 md 6 lg 6 xl 6}} & children]
  [mui/grid {:item true :xs xs :md md :lg lg :xl xl}
   [mui/card
    {:square true
     :style
     (merge
      {:background-color "rgb(250, 250, 250)"
       :font-size        "1.25em"
       :opacity          0.95
       :margin           "8px"}
      style)}

    ;; Header
    [mui/card-header
     (merge
      {:title  title
       :action (when link
                 (r/as-element
                  [mui/icon-button
                   {:href  link
                    :color "secondary"}
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

(defn fb-plugin []
  (r/create-class
   {:component-did-mount
    (fn []
      (js/FB.XFBML.parse))
    :reagent-render
    (fn []
      [mui/grid {:container true :justify "center"}
       [mui/grid {:item true}
        [:div
         {:class                      "fb-page"
          :data-href                  "https://www.facebook.com/LIPASLiikuntapaikat"
          :data-tabs                  "timeline"
          :data-height                "596"
          :data-small-header          "false"
          :data-adapt-container-width "true"
          :data-hide-cover            "true"
          :data-show-facepile         "true"}]]])}))

(defn create-panel [tr]
  (r/with-let [snack-open? (r/atom true)]
    [mui/grid {:container true}

     ;; Ephmeral snackbar
     (when (utils/ie?)
       [mui/snackbar
        {:open         @snack-open?
         :ContentProps
         {:style
          {:background-color mui/primary
           :outline          (str "1px solid " mui/gray3)}}
         :anchorOrigin {:horizontal "right" :vertical "bottom"}
         :message
         (r/as-element
          [mui/typography {:style {:color "white"}}
           (tr :notifications/ie)])
         :action
         (r/as-element
          [mui/icon-button {:on-click #(reset! snack-open? false)}
           [mui/icon {:color "secondary"} "close"]])}])

     ;; Main section with background image
     [mui/grid
      {:container true
       :justify   "flex-start"
       ;;:align-items ""
       :style
       {:padding             "8px"
        :background-position "right center"
        :background-color    mui/gray3
        :background-image    "url('/img/background_full.png')"
        :background-size     "contain"
        :background-repeat   "no-repeat"}}

      ;; Sports sites
      [mui/grid {:item true :xs 12 :md 12 :lg 8}
       [mui/grid {:container true}
        [grid-card
         {:title     (tr :sport/headline)
          :link      "/liikuntapaikat"
          :link-text (tr :actions/browse-to-map)}
         [mui/typography {:variant "body1" :style {:height "4em"}}
          (tr :sport/description)]
         [:ul
          [lui/li (tr :sport/up-to-date-information)]
          [lui/li (tr :sport/updating-tools)]
          [lui/li (tr :sport/open-interfaces)]]]

        ;; Ice stadiums portal
        ;; [grid-card
        ;;  {:title     (tr :ice/headline)
        ;;   :link      "/jaahalliportaali"
        ;;   :link-text (tr :actions/browse-to-portal)}
        ;;  [mui/typography {:variant "body1"}
        ;;   (tr :ice/description)]
        ;;  [:ul
        ;;   [lui/li (tr :ice/basic-data-of-halls)]
        ;;   [lui/li (tr :ice/entering-energy-data)]
        ;;   [lui/li (tr :ice/updating-basic-data)]]]

        ;; Swimming pools portal
        ;; [grid-card
        ;;  {:title     (tr :swim/headline)
        ;;   :link      "/uimahalliportaali"
        ;;   :link-text (tr :actions/browse-to-portal)}
        ;;  [mui/typography {:variant "body1"}
        ;;   (tr :swim/description)]
        ;;  [:ul
        ;;   [lui/li (tr :swim/basic-data-of-halls)]
        ;;   [lui/li (tr :swim/entering-energy-data)]
        ;;   [lui/li (tr :swim/updating-basic-data)]]]

        ;; Reports
        [grid-card
         {:title     (tr :stats/headline)
          :link      "/tilastot"
          :link-text (tr :stats/browse-to)}
         [mui/typography {:variant "body1" :style {:height "4em"}}
          (tr :stats/description)]
         [:ul
          [lui/li (tr :stats/bullet1)]
          [lui/li (tr :stats/bullet2)]
          [lui/li (tr :stats/bullet3)]]]

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
            [:img
             {:style {:height "24px" :width "24px"}
              :src   "/img/swagger_logo.svg"}]]
           [mui/list-item-text {:primary "Lipas API"}]]

          ;; Geoserver
          [mui/list-item {:button true :component "a" :href (:geoserver links)}
           [mui/list-item-icon
            [:img
             {:style {:height "24px" :width "24px"}
              :src   "/img/geoserver_logo.svg"}]]
           [mui/list-item-text "Geoserver"]]

          ;; Github
          [mui/list-item {:button true :component "a" :href (:github links)}
           [mui/list-item-icon
            [mui/svg-icon
             [svg/github-icon]]]
           [mui/list-item-text {:primary "GitHub"}]]

          ;; Creative commons
          [mui/list-item {:button true :component "a" :href (:cc4.0 links)}
           [mui/list-item-icon
            [mui/icon "copyright"]]
           [mui/list-item-text {:primary "CC 4.0"}]]]]

        ;; Help
        [grid-card { :title (tr :help/headline)}

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
           [mui/list-item-text "0400 247 980"]]

          ;; Register
          [mui/list-item {:button true :component "a" :href "/rekisteroidy"}
           [mui/list-item-icon
            [mui/icon "group_add"]]
           [mui/list-item-text (tr :register/link)]]]]]]

      ;; [grid-card {:md 6 :lg 4}
      ;;  [fb-plugin]]

      ;; Known LIPAS users
      [grid-card {:xs 12 :md 12 :lg 12 :xl 6 :title (tr :data-users/headline)}
       (into
        [mui/grid {:container true :spacing 8}]
        (map ->link known-users))
       [mui/grid {:container true :spacing 16 :style {:margin-top "1em"}}
        [mui/grid {:item true}
         [mui/typography {:variant "h6" :color "primary"}
          (tr :data-users/data-user?)]]
        [mui/grid {:item true}
         [mui/link
          {:underline "always"
           :variant   "h6"
           :color     "secondary"
           :href
           (utils/->mailto
            {:email   "lipasinfo@jyu.fi"
             :subject (tr :data-users/email-subject)
             :body    (tr :data-users/email-body)})}
          (tr :data-users/tell-us)]]]]]

     ;;Partner logos
     [footer {:title (tr :partners/headline) :bg-color mui/gray2}
      (into
       [mui/grid {:container true :align-items "center"}]
       (map ->logo logos))]]))

(defn main []
  (let [tr (<== [:lipas.ui.subs/translator])]
    [create-panel tr]))
