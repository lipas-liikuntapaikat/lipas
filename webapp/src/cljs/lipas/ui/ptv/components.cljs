(ns lipas.ui.ptv.components
  "Shared PTV UI components to avoid circular dependencies"
  (:require ["@mui/material/Collapse$default" :as Collapse]
            ["@mui/material/IconButton$default" :as IconButton]
            ["@mui/icons-material/ExpandMore$default" :as ExpandMoreIcon]
            ["@mui/material/Paper$default" :as Paper]
            ["@mui/material/Stack$default" :as Stack]
            ["@mui/material/Table$default" :as Table]
            ["@mui/material/TableCell$default" :as TableCell]
            ["@mui/material/TableBody$default" :as TableBody]
            ["@mui/material/TableHead$default" :as TableHead]
            ["@mui/material/TableRow$default" :as TableRow]
            ["@mui/material/Tooltip$default" :as Tooltip]
            ["@mui/material/Typography$default" :as Typography]
            [clojure.string :as str]
            [lipas.ui.mui :as mui]
            [lipas.ui.ptv.subs :as subs]
            [lipas.ui.uix.hooks :refer [use-subscribe]]
            [uix.core :as uix :refer [$ defui]]))

(defui service-location-preview
  "Preview component showing how a sports site will appear in PTV as a service location"
  [{:keys [org-id lipas-id]}]
  (let [preview (use-subscribe [::subs/service-location-preview org-id lipas-id])
        [expanded? set-expanded] (uix/use-state false)

        row (uix/fn [{:keys [label value tooltip]}]
              ($ Tooltip {:title (or tooltip "Selite puuttuu")}
                 ($ TableRow
                    ($ TableCell ($ Typography {:variant "caption"} label))
                    ($ TableCell ($ Typography value)))))
        join (fn [coll] (when (seq coll) (str/join ", " coll)))
        get-desc (fn [type lang] (or (->> preview
                                          :serviceChannelDescriptions
                                          (filter (fn [m] (and (= (:type m) type)
                                                               (= (:language m) lang))))
                                          (map :value)
                                          join)
                                     "-"))
        get-name (fn [type lang] (or (->> preview :serviceChannelNames
                                          (filter (fn [m] (and
                                                           (= (:language m) lang)
                                                           (= (:type m) type))))
                                          (map :value)
                                          join)
                                     "-"))

        tt-summary "Tiivistelmä on integraation käyttäjän syöttämä tieto. Mahdollisesti tekoälyn avulla tuotettu."
        tt-description "Palvelupaikan kuvaus on integraation käyttäjän syöttämä tieto. Mahdollisesti tekoälyn avulla tuotettu."

        lang-disclaimer "Tieto täytetään vain mikäli integraation käyttöönoton yhteydessä on ilmoitettu että palvelupaikat halutaan kuvata tällä kielellä. Ota yhteyttä lipasinfo@jyu.fi mikäli haluat muuttaa kielivalintoja."]

    ($ Stack {:spacing 2}
       ;; Collapsible header with expand/collapse button
       ($ Paper
          {:sx #js{:p 2 :bgcolor mui/gray3 :cursor "pointer"}
           :onClick #(set-expanded (not expanded?))}
          ($ Stack
             {:direction "row"
              :alignItems "center"
              :justifyContent "space-between"}
             ($ Typography
                {:variant "subtitle2" :fontWeight "medium"}
                "PTV-integraation sisältö")
             ($ IconButton
                {:size "small"
                 :sx #js{:transform (if expanded? "rotate(180deg)" "rotate(0deg)")
                         :transition "transform 0.2s"}}
                ($ ExpandMoreIcon))))

       ;; Collapsible content
       ($ Collapse {:in expanded? :timeout "auto" :unmountOnExit true}
          ($ Paper {:sx #js{:p 2 :bgcolor mui/gray3}}
             ($ Typography
                {:variant "body2" :mb 2}
                "Näyttää kuinka liikuntapaikka on integroitu PTV-järjestelmään. Vie hiiren osoitin rivin päälle nähdäksesi tiedon alkuperän."))

          ($ Table {:variant "dense"}
             ($ TableHead
                ($ TableRow
                   ($ TableCell "PTV-tietue")
                   ($ TableCell "Arvo")))

             ($ TableBody
                ($ row {:label "Tila"
                        :value (:publishingStatus preview)
                        :tooltip "Integraation PTV:hen viemät kohteet julkaistaan automaattisesti. Vedokseksi vieminen ei ole tuettu."})

                ($ row {:label "Kielet"
                        :value (join (:languages preview))
                        :tooltip "Kielet, joilla kunta on ilmoittanut haluavansa kuvata palvelut ja palvelupaikat kun integraatio on otettu käyttöön ensimmäisen kerran. Ota yhteyttä lipasinfo@jyu.fi mikäli haluat muuttaa kielivalintoja."})

                ($ row {:label "Nimi suomeksi"
                        :value (get-name "Name" "fi")
                        :tooltip "Liikuntapaikan nimi Lipaksessa."})

                ($ row {:label "Nimi ruotsiksi"
                        :value (get-name "Name" "sv")
                        :tooltip (str "Liikuntapaikan nimi ruotsiksi Lipaksessa."
                                      " "
                                      lang-disclaimer)})

                ($ row {:label "Nimi englanniksi"
                        :value (get-name "Name" "en")
                        :tooltip (str "Liikuntapaikan nimi englanniksi Lipaksessa."
                                      " "
                                      lang-disclaimer)})

                ($ row {:label "Vaihtoehtoinen nimi"
                        :value (get-name "AlternativeName" "fi")
                        :tooltip "Liikuntapaikan markkinointinimi Lipaksessa."})

                ($ row {:label "Ensisijainen nimitieto"
                        :value (->> preview :displayNameType first :type)
                        :tooltip "Tämä arvo on aina \"Name\"."})

                ($ row {:label "Maa"
                        :value (->> preview :addresses first :country)
                        :tooltip "Tämä arvo on aina \"FI\""})

                ($ row {:label "Katuosoite"
                        :value (-> preview :addresses first :streetAddress :street first :value)
                        :tooltip "Liikuntapaikan katuosoite Lipaksessa."})

                ($ row {:label "Postinumero"
                        :value (-> preview :addresses first :streetAddress :postalCode)
                        :tooltip "Liikuntapaikan postinumero Lipaksessa."})

                ($ row {:label "Koordinaatit"
                        :value (str "(E) "
                                    (-> preview :addresses first :streetAddress :longitude)
                                    " (N) "
                                    (-> preview :addresses first :streetAddress :latitude))
                        :tooltip "Liikuntapaikan geometrian koordinaatit Lipaksessa muutettuna PTV:n käyttämään ETRS-TM35FIN koordinaatistoon. Aluemaisissa liikuntapaikoissa valitaan keskipiste, reiteissä aloituspiste."})

                ($ row {:label "Sähköpostiosoitteet"
                        :value (->> preview :emails (map :value) join)
                        :tooltip "Liikuntapaikan sähköpostiosoite Lipaksessa."})

                ($ row {:label "Web-sivut"
                        :value (->> preview :webPages (map :url) join)
                        :tooltip "Liikuntapaikan www-osoite Lipaksessa."})

                ($ row {:label "Puhelinnumerot"
                        :value (->> preview
                                    :phoneNumbers
                                    (map (fn [{:keys [number prefixNumber isFinnishServiceNumber]}]
                                           (str prefixNumber " " number (when isFinnishServiceNumber " (suomalainen palvelunumero)"))))
                                    join)
                        :tooltip "Liikuntapaikan puhelinnumero Lipaksessa."})

                ($ row {:label "Tiivistelmä suomeksi"
                        :value (get-desc "Summary" "fi")
                        :tooltip tt-summary})

                ($ row {:label "Tiivistelmä ruotsiksi"
                        :value (get-desc "Summary" "sv")
                        :tooltip (str tt-summary " " lang-disclaimer)})

                ($ row {:label "Tiivistelmä englanniksi"
                        :value (get-desc "Summary" "en")
                        :tooltip (str tt-summary " " lang-disclaimer)})

                ($ row {:label "Kuvaus suomeksi"
                        :value (get-desc "Description" "fi")
                        :tooltip tt-description})

                ($ row {:label "Kuvaus ruotsiksi"
                        :value (get-desc "Description" "sv")
                        :tooltip (str tt-description " " lang-disclaimer)})

                ($ row {:label "Kuvaus englanniksi"
                        :value (get-desc "Description" "en")
                        :tooltip (str tt-description " " lang-disclaimer)})

                ($ row {:label "Organisaation ID"
                        :value (:organizationId preview)
                        :tooltip "Organisaatio (kunta) joka käyttää integraatiota."})

                ($ row {:label "Palveluiden ID:t"
                        :value (join (:services preview))
                        :tooltip "Palvelut joihin palvelupaikka liitetään PTV:ssä."})))))))
