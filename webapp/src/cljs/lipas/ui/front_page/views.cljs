(ns lipas.ui.front-page.views
  (:require [clojure.string :as str]
            [lipas.schema.users :as users-schema]
            [lipas.ui.components.misc :as misc]
            [lipas.ui.components.text-fields :as text-fields]
            [malli.core :as m]
            [lipas.ui.front-page.events :as events]
            [lipas.ui.front-page.subs :as subs]
            ["@mui/material/Button$default" :as Button]
            ["@mui/material/Card$default" :as Card]
            ["@mui/material/CardActions$default" :as CardActions]
            ["@mui/material/CardContent$default" :as CardContent]
            ["@mui/material/CardHeader$default" :as CardHeader]
            ["@mui/material/CircularProgress$default" :as CircularProgress]
            ["@mui/material/Dialog$default" :as Dialog]
            ["@mui/material/DialogActions$default" :as DialogActions]
            ["@mui/material/DialogContent$default" :as DialogContent]
            ["@mui/material/DialogTitle$default" :as DialogTitle]
            ["@mui/material/GridLegacy$default" :as Grid]
            ["@mui/material/Icon$default" :as Icon]
            ["@mui/material/IconButton$default" :as IconButton]
            ["@mui/material/Link$default" :as Link]
            ["@mui/material/List$default" :as List]
            ["@mui/material/ListItemButton$default" :as ListItemButton]
            ["@mui/material/ListItemIcon$default" :as ListItemIcon]
            ["@mui/material/ListItemText$default" :as ListItemText]
            ["@mui/material/Paper$default" :as Paper]
            ["@mui/material/Snackbar$default" :as Snackbar]
            ["@mui/material/SvgIcon$default" :as SvgIcon]
            ["@mui/material/Typography$default" :as Typography]
            [lipas.ui.mui :as mui]
            [lipas.ui.svg :as svg]
            [lipas.ui.utils :refer [<== ==>] :as utils]
            [reagent.core :as r]))

(def links
  {:github "https://github.com/lipas-liikuntapaikat"
   :lipas-api "https://api.lipas.fi"
   :geoserver "http://lipas.cc.jyu.fi/geoserver"
   :lipasinfo "https://www.jyu.fi/sport/fi/yhteistyo/lipas-liikuntapaikat.fi"
   :open-data "https://www.jyu.fi/fi/avoimet-rajapinnat-ja-ladattavat-lipas-aineistot"
   :youtube "https://www.youtube.com/channel/UC-QFmRIY1qYPXX79m23JC4g"
   :cc4.0 "https://creativecommons.org/licenses/by/4.0/"})

(def logos
  [{:img "img/partners/okm.png"}
   {:img "img/partners/kuntaliitto.png"}
   {:img "img/partners/metsahallitus.png"}
   {:img "img/partners/suh.png"}
   {:img "img/partners/syke.png" :full-height? true}
   {:img "img/partners/ukty.png"}
   {:img "img/partners/avi.png"}
   {:img "img/partners/helsinki.png"}])

(def known-users
  [{:label "OKM" :href "https://minedu.fi/liikunta"}
   {:label "Retkikartta.fi" :href "https://www.retkikartta.fi/"}
   {:label "Paikkatietoikkuna" :href "https://www.paikkatietoikkuna.fi/"}
   {:label "palvelukartta.hel.fi" :href "https://palvelukartta.hel.fi/"}
   {:label "Finterest" :href "https://finterest.fi/"}
   {:label "liiteri" :href "https://liiteri.ymparisto.fi/"}
   {:label "TEAviisari" :href "https://teaviisari.fi"}
   {:label "Uimaan.fi" :href "https://uimaan.fi/"}
   {:label "Me-säätiö" :href "https://www.mesaatio.fi/vaikuttavuus/melvio"}
   {:label "kuopio.fi" :href "https://www.kuopio.fi/liikunta-ja-ulkoilukartat"}
   {:label "Tässä.fi" :href "https://tassa.fi/"}
   {:label "Tulikartta.fi" :href "https://www.tulikartta.fi/"}
   {:label "SportVenue" :href "https://www.sportvenue.fi/"}
   {:label "FCG" :href "https://www.fcg.fi/palvelut/johtaminen/tutkimuspalvelut-tiedolla-johtamisen-tuki/sahkoinen-hyvinvointikertomus/"}
   {:label "Ulkoliikunta.fi" :href "https://ulkoliikunta.fi/"}
   {:label "Aluehallintovirasto" :href "https://avi.fi/tietoa-meista/tehtavamme/opetus-ja-kulttuuri/liikuntatoimi"}
   {:label "LIKES" :href "https://www.likes.fi/"}
   {:label "Kuntaliitto" :href "https://www.kuntaliitto.fi/"}
   {:label "EPSHP" :href "https://www.hyvaep.fi/palvelut/"}
   {:label "Ladulle.fi" :href "https://ladulle.fi/"}
   {:label "Liikkuva Kuopio" :href "https://liikkuvakuopio.fi/"}
   {:label "Karttaselain" :href "https://www.karttaselain.fi/"}
   {:label "Go SportY" :href "https://www.gosporty.fi/"}
   {:label "FLUENT" :href "https://www.fluentprogress.fi/fluent-outdoors-latutilanne-ja-liikuntapaikat"}
   {:label "ulkoilutampereenseutu.fi" :href "https://ulkoilutampereenseutu.fi/"}
   {:label "RetkiSuomi" :href "https://retkisuomi.fi/"}
   {:label "Kuntamaisema" :href "https://kuntamaisema.fi/"}
   {:label "Sweco" :href "https://www.sweco.fi/"}
   {:label "huts.fi" :href "https://huts.fi/"}
   {:label "Retkellä.fi" :href "https://retkellä.fi"}
   {:label "Trailmap" :href "https://trailmap.fi"}
   {:label "visithame.fi" :href "https://visithame.fi/"}
   {:label "Virma" :href "https://virma.fi"}
   {:label "Bikeland" :href "https://www.bikeland.fi/"}
   {:label "Luontoon.fi" :href "https://www.luontoon.fi/"}
   {:label "LIVERTTI" :href "https://activehealthylife.webnode.fi/"}
   {:label "Kuutoskaupunkivertailu" :href "https://kuutoskaupunkivertailu.fi/"}
   {:label "Toimio" :href "https://toimio.com/"}
   {:label "Olympiakomitea" :href "https://www.olympiakomitea.fi/"}
   {:label "Sitowise" :href "https://www.sitowise.com/fi/teknologia-ja-design/tuoteratkaisut/routa-infran-kunnossapitojarjestelma"}])

(defn ->logo [{:keys [img full-height?]}]
  [:> Grid {:item true :xs 12 :sm "auto" :md "auto" :lg "auto" :xl "auto"}
   [:img
    {:style
     (merge
       {:margin "1em" :max-width "200px" :max-height "100px"}
       (when full-height? ;; For IE11.
         {:height "100%"}))
     :src img}]])

(defn ->link [{:keys [label href color] :or {color "primary"}}]
  [:> Grid {:item true :xs 12 :md 6 :lg 3 :style {:text-align "center"}}
   [:> Link {:href href :variant "h6" :color color}
    label]])

(defn footer
  [{:keys [title bg-color title-style]
    :or {bg-color mui/gray1
         title-style {:opacity 0.7}}}
   & contents]

  [:> Grid
   {:container true
    :style
    {:background-color bg-color
     :padding "1em 2em 1em 2em"}}

   ;; Title
   [:> Grid {:item true :xs 12 :style {:margin-bottom "1em"}}
    ;; smUp
    [:> Typography
     {:variant "h5" :style title-style
      :sx {:display {:xs "block"
                     :sm "none"}}}
     title]
    ;; xsDown
    [:> Typography
     {:variant "h4" :style title-style
      :sx {:display {:xs "none"
                     :sm "block"}}}
     title]]

   ;; Content
   [:> Grid {:item true :xs 12}
    (into
      [:> Grid
       {:container true}]
      contents)]])

(defn grid-card
  [{:keys [title style link link-text xs md lg xl]
    :or {xs 12 md 6 lg 6 xl 6}} & children]
  [:> Grid {:item true :xs xs :md md :lg lg :xl xl}
   [:> Card
    {:square true
     :style
     (merge
       {:background-color "rgb(250, 250, 250)"
        :font-size "1.25em"
        :opacity 0.95
        :margin "8px"}
       style)}

    ;; Header
    [:> CardHeader
     (merge
       {:title title
        :action (when link
                  (r/as-element
                    [:> IconButton
                     {:href link
                      :color "secondary"}
                     [:> Icon "arrow_forward_ios"]]))}
       (when link
         {:titleTypographyProps
          {:component "a"
           :href link
           :style {:font-weight 600
                   :text-decoration "none"}}}))]

    ;; Content
    (into [:> CardContent] children)

    ;; Actions
    (when link-text
      [:> CardActions
       [:> Button {:variant :text :color "secondary" :href link}
        (str "> " link-text)]])]])

(defn grid-card-2
  [{:keys [title style link link-text xs md lg xl]
    :or {xs 12 md 6 lg 6 xl 6}} & children]
  [:> Grid {:item true :xs xs :md md :lg lg :xl xl}
   [:> Paper {:square true
              :style
              (merge
                {:background-color "rgb(250, 250, 250)"
                 :font-size "1.25em"
                 :height "360px"
                 :opacity 0.95
                 :margin "8px"
                 :padding "16px 10px 0 16px"}
                style)}

    [:> Grid
     {:container true
      :spacing 2
      :justify-content "space-between"
      :style {:height "100%"}}

     ;; Header
     [:> Grid {:item true :xs 12}
      [:> Grid {:container true :justify-content "space-between"}
       [:> Grid {:item true :xs 11}
        [:> Typography
         (merge {:variant "h4"
                 :color "secondary"
                 :style {:font-weight 600
                         :font-size "2rem"
                         :text-decoration "none"}}
                (when link
                  {:component "a"
                   :href link}))
         title]]
       [:> Grid {:item true :xs 1}
        (when link
          [:> IconButton {:href link :color "secondary"}
           [:> Icon "arrow_forward_ios"]])]]]

     ;; Content
     (into [:> Grid {:item true :xs 12}] children)

     ;; Actions
     (when link-text
       [:> Grid {:item true :xs 12}
        [:> Grid
         {:container true
          :direction "row"
          :style {:height "100%"}
          :align-content "flex-end"}
         [:> Grid {:item true :xs 12}
          [:> Button
           {:variant "text"
            :style {:margin-bottom "-8px"}
            :color "secondary"
            :href link}
           (str "> " link-text)]]]])]]])

(defn fb-plugin []
  (r/create-class
    {:component-did-mount
     (fn []
       (js/FB.XFBML.parse))
     :reagent-render
     (fn []
       [:> Grid {:container true :justify-content "center"}
        [:> Grid {:item true}
         [:div
          {:class "fb-page"
           :data-href "https://www.facebook.com/LIPASLiikuntapaikat"
           :data-tabs "timeline"
           :data-height "596"
           :data-small-header "false"
           :data-adapt-container-width "true"
           :data-hide-cover "true"
           :data-show-facepile "true"}]]])}))

(defn newsletter-signup []
  (r/with-let [open? (r/atom false)
               email (r/atom nil)]
    (let [tr (<== [:lipas.ui.subs/translator])
          user (<== [:lipas.ui.user.subs/user-data])
          _ (when user (reset! email (:email user)))]
      [:<>

       ;; Signup modal
       [:> Dialog
        {:open @open?
         :full-width true
         :on-close #(reset! open? false)
         :max-width "sm"}
        [:> DialogTitle
         (tr :newsletter/subscribe)]
        [:> DialogContent

         [:> Grid {:container true :spacing 2}
          ;; Email
          [:> Grid {:item true :xs 12}
           [text-fields/text-field
            {:value @email
             :full-width true
             :label (tr :lipas.user/email)
             :spec users-schema/email-schema
             :on-change #(reset! email %)}]]

          ;; Privacy policy
          [:> Grid {:item true :xs 12}
           [:> Link
            {:color "primary"
             :style {:margin-top "1em"}
             :href "/pdf/tietosuojailmoitus_lipas_uutiskirje.pdf"
             :target "_blank"}
            (tr :help/privacy-policy)]]]]

        [:> DialogActions
         [:> Button {:on-click #(reset! open? false)}
          (tr :actions/cancel)]
         [:> Button
          {:color "secondary"
           :disabled (not (m/validate users-schema/email-schema @email))
           :on-click
           (fn []
             (==> [::events/subscribe-newsletter {:email @email}])
             (reset! open? false))}
          (tr :newsletter/subscribe-short)]]]

       ;; Signup btn
       [:> Button
        {:color "secondary"
         :on-click #(reset! open? true)}
        (tr :newsletter/subscribe)]])))

(defn newsletter []
  (let [tr (<== [:lipas.ui.subs/translator])
        newsletter-data (<== [::subs/newsletter-data])
        newsletter-error (<== [::subs/newsletter-error])
        newsletter-in-progress? (<== [::subs/newsletter-in-progress?])]
    [:> Grid {:container true :spacing 2}
     [:> Grid {:item true :xs 12}
      (when newsletter-in-progress?
        [:> CircularProgress])

      (when (and (not newsletter-in-progress?)
                 (not (seq newsletter-data))
                 newsletter-error)
        [:> Typography "Unable to retrieve newsletter."])

      (when (and (not newsletter-in-progress?) newsletter-data)
        (into
          [:> List]
          (for [m newsletter-data]
            [:> ListItemButton {:component "a" :href (:url m) :target "_blank"}
             [:> ListItemIcon
              [:> Icon "mail_outline"]]
             [:> ListItemText
              {:primary (str (:send-time m) " | " (:title m))
               :secondary (:preview-text m)}]])))]

     [:> Grid {:item true :xs 12}
      [:> Grid {:container true :justify-content "space-between" :align-items "center"}

       [:> Grid {:item true}
        [newsletter-signup]]

       [:> Grid {:item true}
        [:> Link
         {:color "primary"
          :style {:margin-right "1em"}
          :href "/pdf/tietosuojailmoitus_lipas_uutiskirje.pdf"
          :target "_blank"}
         (tr :help/privacy-policy)]]]]]))

(defn- format-number [n]
  (when n
    (->> (str n)
         reverse
         (partition-all 3)
         (map #(apply str (reverse %)))
         reverse
         (str/join "\u00A0"))))

(defn- days-since [date-str]
  (when date-str
    (let [date-part (first (str/split date-str #"T"))
          then (.getTime (js/Date. date-part))
          now (.getTime (js/Date.))
          diff-ms (- now then)]
      (Math/floor (/ diff-ms (* 1000 60 60 24))))))

(defn- stat-item [{:keys [value label]}]
  [:> Grid {:item true :xs 6 :sm 6 :md "auto"
            :style {:text-align "center" :padding "1em 2em"}}
   [:> Typography {:variant "h3"
                   :component "div"
                   :style {:font-weight 700
                           :color mui/secondary
                           :line-height 1.2}}
    value]
   [:> Typography {:variant "body1"
                   :component "div"
                   :style {:opacity 0.85
                           :margin-top "0.25em"
                           :color "white"}}
    label]])

(defn lipas-in-numbers [tr]
  (let [stats-data (<== [::subs/stats-data])
        in-progress? (<== [::subs/stats-in-progress?])]
    (when (or stats-data in-progress?)
      [:> Grid
       {:container true
        :style {:background-color mui/primary
                :color "white"
                :padding "2em 1em"}}

       ;; Heading
       [:> Grid {:item true :xs 12
                 :style {:text-align "center" :margin-bottom "1em"}}
        [:> Typography {:variant "h4"
                        :component "h2"
                        :style {:font-weight 600
                                :color "white"}}
         (tr :lipas-in-numbers/headline)]]

       (if in-progress?
         ;; Loading
         [:> Grid {:item true :xs 12 :style {:text-align "center"}}
          [:> CircularProgress {:style {:color "white"}}]]

         ;; Stats
         (when stats-data
           [:<>
            [:> Grid {:item true :xs 12}
             [:> Grid {:container true
                       :justify-content "center"
                       :align-items "flex-start"}
              [stat-item {:value (format-number (:total-count stats-data))
                          :label (tr :lipas-in-numbers/sports-facilities)}]
              [stat-item {:value (format-number (:city-count stats-data))
                          :label (tr :lipas-in-numbers/municipalities)}]
              [stat-item {:value (format-number (:updated-last-year stats-data))
                          :label (tr :lipas-in-numbers/updated-last-year)}]
              [stat-item {:value (format-number (:route-length-km stats-data))
                          :label (tr :lipas-in-numbers/km-of-routes)}]]]

            ;; Last updated line
            (when-let [d (days-since (:last-updated stats-data))]
              [:> Grid {:item true :xs 12
                        :style {:text-align "center" :margin-top "1em"}}
               [:> Typography {:variant "body2"
                               :style {:opacity 0.7 :color "white"}}
                (str (tr :lipas-in-numbers/last-updated) " "
                     (case d
                       0 (tr :lipas-in-numbers/today)
                       1 (tr :lipas-in-numbers/yesterday)
                       (str/replace (tr :lipas-in-numbers/days-ago) "{1}" (str d))))]])]))])))

(defn create-panel [tr]
  (r/with-let [snack-open? (r/atom true)]
    (let [newsletter-data (<== [::subs/newsletter-data])]
      [:> Grid {:container true}

       ;; Ephmeral snackbar
       (when (utils/ie?)
         [:> Snackbar
          {:open @snack-open?
           :ContentProps
           {:style
            {:background-color mui/primary
             :outline (str "1px solid " mui/gray3)}}
           :anchorOrigin {:horizontal "right" :vertical "bottom"}
           :message
           (r/as-element
             [:> Typography {:style {:color "white"}}
              (tr :notifications/ie)])
           :action
           (r/as-element
             [:> IconButton {:on-click #(reset! snack-open? false)}
              [:> Icon {:color "secondary"} "close"]])}])

       ;; Main section with background image
       [:> Grid
        {:container true
         :justify-content "flex-start"
         ;;:align-items ""
         :style
         {:padding "8px"
          :background-position "right center"
          :background-color mui/gray3
          :background-image "url('/img/background_full.png')"
          :background-size "contain"
          :background-repeat "no-repeat"}}

        ;; Sports sites
        [:> Grid {:item true :xs 12 :md 12 :lg 8}
         [:> Grid {:container true}
          [grid-card-2
           {:title (tr :sport/headline)
            :link "/liikuntapaikat"
            :link-text (tr :actions/browse-to-map)}
           [:> Typography {:variant "body1" :style {:height "1.65em"}}
            (tr :sport/description)]
           [:ul
            [misc/li (tr :sport/up-to-date-information)]
            [misc/li (tr :sport/updating-tools)]
            [misc/li (tr :sport/analysis-tools)]
            [misc/li (tr :sport/open-interfaces)]]]

          ;; Ice stadiums portal
          ;; [grid-card
          ;;  {:title     (tr :ice/headline)
          ;;   :link      "/jaahalliportaali"
          ;;   :link-text (tr :actions/browse-to-portal)}
          ;;  [:> Typography {:variant "body1"}
          ;;   (tr :ice/description)]
          ;;  [:ul
          ;;   [misc/li (tr :ice/basic-data-of-halls)]
          ;;   [misc/li (tr :ice/entering-energy-data)]
          ;;   [misc/li (tr :ice/updating-basic-data)]]]

          ;; Swimming pools portal
          ;; [grid-card
          ;;  {:title     (tr :swim/headline)
          ;;   :link      "/uimahalliportaali"
          ;;   :link-text (tr :actions/browse-to-portal)}
          ;;  [:> Typography {:variant "body1"}
          ;;   (tr :swim/description)]
          ;;  [:ul
          ;;   [misc/li (tr :swim/basic-data-of-halls)]
          ;;   [misc/li (tr :swim/entering-energy-data)]
          ;;   [misc/li (tr :swim/updating-basic-data)]]]

          ;; Reports
          [grid-card-2
           {:title (tr :stats/headline)
            :link "/tilastot"
            :link-text (tr :stats/browse-to)}
           [:> Typography {:variant "body1" :style {:height "4em"}}
            (tr :stats/description)]
           [:ul
            [misc/li (tr :stats/bullet2)]
            [misc/li (tr :stats/bullet4)]
            [misc/li (tr :stats/bullet1)]
            [misc/li (tr :stats/bullet3)]]]

          ;; Open Data
          [grid-card-2 {:title (tr :open-data/headline)}
           [:> List

            ;; info
            [:> ListItemButton {:component "a" :href (:open-data links)}
             [:> ListItemIcon
              [:> Icon "info"]]
             [:> ListItemText {:primary "Info"}]]

            ;; Lipas-API
            [:> ListItemButton {:component "a" :href (:lipas-api links)}
             [:> ListItemIcon
              [:img
               {:style {:height "24px" :width "24px"}
                :src "/img/swagger_logo.svg"}]]
             [:> ListItemText {:primary "Lipas API"}]]

            ;; Geoserver
            [:> ListItemButton {:component "a" :href (:geoserver links)}
             [:> ListItemIcon
              [:img
               {:style {:height "24px" :width "24px"}
                :src "/img/geoserver_logo.svg"}]]
             [:> ListItemText "Geoserver"]]

            ;; Github
            [:> ListItemButton {:component "a" :href (:github links)}
             [:> ListItemIcon
              [:> SvgIcon
               [svg/github-icon]]]
             [:> ListItemText {:primary "GitHub"}]]

            ;; Creative commons
            [:> ListItemButton {:component "a" :href (:cc4.0 links)}
             [:> ListItemIcon
              [:> Icon "copyright"]]
             [:> ListItemText {:primary "CC 4.0"}]]]]

          ;; Help
          [grid-card-2 {:title (tr :help/headline)}

           [:> List

            ;; Lipasinfo
            [:> ListItemButton {:component "a" :href (:lipasinfo links)}
             [:> ListItemIcon
              [:> Icon "library_books"]]
             [:> ListItemText "lipasinfo.fi"]]

            ;; Youtube
            [:> ListItemButton {:component "a" :href (:youtube links)}
             [:> ListItemIcon
              [:> Icon "video_library"]]
             [:> ListItemText "Youtube"]]

            ;; Email
            [:> ListItemButton {:component "a" :href "mailto:lipasinfo@jyu.fi"}
             [:> ListItemIcon
              [:> Icon "email"]]
             [:> ListItemText "lipasinfo@jyu.fi"]]

            ;; Phone
            [:> ListItemButton {:component "a" :href "tel:+358400247980"}
             [:> ListItemIcon
              [:> Icon "phone"]]
             [:> ListItemText "0400 247 980"]]

            ;; Register
            [:> ListItemButton {:component "a" :href "/rekisteroidy"}
             [:> ListItemIcon
              [:> Icon "group_add"]]
             [:> ListItemText (tr :register/link)]]]]]]

        ;; [grid-card {:md 6 :lg 4}
        ;;  [fb-plugin]]

        ;; Newsletter
        (when (seq newsletter-data)
          [grid-card {:xs 12 :md 12 :lg 8 :xl 8 :title "Uutiskirjeet"}
           [newsletter]])

        ;; Known LIPAS users
        [grid-card {:xs 12 :md 12 :lg 12 :xl 8 :title (tr :data-users/headline)}
         (into
           [:> Grid {:container true :spacing 2}]
           (map ->link known-users))
         [:> Grid {:container true :spacing 2 :style {:margin-top "1em"}}
          [:> Grid {:item true}
           [:> Typography {:variant "h6" :color "primary"}
            (tr :data-users/data-user?)]]
          [:> Grid {:item true}
           [:> Link
            {:underline "always"
             :variant "h6"
             :color "secondary"
             :href
             (utils/->mailto
               {:email "lipasinfo@jyu.fi"
                :subject (tr :data-users/email-subject)
                :body (tr :data-users/email-body)})}
            (tr :data-users/tell-us)]]]]]

       ;; LIPAS in numbers
       [lipas-in-numbers tr]

       ;;Partner logos
       [footer {:title (tr :partners/headline)
                :title-style {:color mui/secondary}
                :bg-color mui/gray2}
        (into
          [:> Grid {:container true :align-items "center" :spacing 4}]
          (map ->logo logos))]])))

(defn main []
  (let [tr (<== [:lipas.ui.subs/translator])]
    [create-panel tr]))
