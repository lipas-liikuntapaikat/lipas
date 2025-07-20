(ns lipas.data.sports-sites
  (:require [clojure.string :as str]
            [lipas.data.admins :as admins]
            [lipas.data.owners :as owners]
            [lipas.data.status :as status]))

(def document-statuses
  {"draft"
   {:fi "Ehdotus"
    :se nil
    :en "Draft"}
   "published"
   {:fi "Julkaistu"
    :se nil
    :en "Published"}})

(def statuses status/statuses)

(def field-types
  {"floorball-field"
   {:fi "Salibandykenttä"
    :en "Floorball field"
    :se "Innebandyplan"}})

(def common-keys
  {:lipas-id {:required true
              :name {:fi "Lipas ID" :se "Lipas ID" :en "Lipas ID"}
              :description {:fi "Liikuntapaikan yksilöivä tunniste LIPAS-järjestelmässä"
                            :se "Idrottsplatsens unika identifierare i LIPAS-systemet"
                            :en "Unique identifier of the sports facility in the LIPAS system"}
              :data-type "Integer"}
   :status {:required true
            :name {:fi "Liikuntapaikan tila"
                   :se "Idrottsplatsens status"
                   :en "Sports facility status"}
            :description {:fi "Onko liikuntapaikka suunnitteilla, toiminnassa, pois käytössä väliaikaisesti tai pysyvästi."
                          :se "Är idrottsplatsen i planeringsskede, i drift, tillfälligt ur bruk eller permanent stängd."
                          :en "Is the sports facility in planning, operational, temporarily out of use, or permanently closed."}
            :data-type (str/join "|" (keys statuses))}
   :event-date {:name {:fi "Aikaleima"
                       :se "Tidsstämpel"
                       :en "Timestamp"}
                :description {:fi "Aikaleima milloin liikuntapaikan tiedot tulivat voimaan."
                              :se "Tidsstämpel när idrottsplatsens uppgifter trädde i kraft."
                              :en "Timestamp when the sports facility information became effective."}
                :data-type "timestamp"}
   :name {:required true
          :name {:fi "Liikuntapaikan nimi suomeksi"
                 :se "Idrottsplatsens namn på finska"
                 :en "Sports facility name in Finnish"}
          :description {:fi "Liikuntapaikan virallinen nimi suomen kielellä"
                        :se "Idrottsplatsens officiella namn på finska"
                        :en "Official name of the sports facility in Finnish"}
          :data-type "string"}
   :marketing-name {:name {:fi "Liikuntapaikan markkinointinimi"
                           :se "Idrottsplatsens marknadsföringsnamn"
                           :en "Sports facility marketing name"}
                    :description {:fi "Markkinoinnissa käytettävä nimi, joka voi poiketa virallisesta nimestä"
                                  :se "Namnet som används i marknadsföring, som kan skilja sig från det officiella namnet"
                                  :en "Name used in marketing, which may differ from the official name"}
                    :data-type "string"}
   :name-localized.se {:name {:fi "Nimi ruotsiksi"
                              :se "Namn på svenska"
                              :en "Name in Swedish"}
                       :description {:fi "Liikuntapaikan nimi ruotsin kielellä"
                                     :se "Idrottsplatsens namn på svenska"
                                     :en "Sports facility name in Swedish"}
                       :data-type "string"}
   :name-localized.en {:name {:fi "Nimi englanniksi"
                              :se "Namn på engelska"
                              :en "Name in English"}
                       :description {:fi "Liikuntapaikan nimi englannin kielellä"
                                     :se "Idrottsplatsens namn på engelska"
                                     :en "Sports facility name in English"}
                       :data-type "string"}
   :owner {:required true
           :name {:fi "Omistaja"
                  :se "Ägare"
                  :en "Owner"}
           :description {:fi "Kohteessa olevien rakenteiden tai palveluiden omistaja"
                         :se "Ägare av strukturer eller tjänster på platsen"
                         :en "Owner of structures or services at the site"}
           :data-type (str/join "|" (keys owners/all))}
   :admin {:required true
           :name {:fi "Ylläpitäjä"
                  :se "Underhållare"
                  :en "Administrator"}
           :description {:fi "Kohteen ylläpidon toteuttava taho"
                         :se "Den part som ansvarar för underhållet av platsen"
                         :en "The party responsible for site maintenance"}
           :data-type (str/join "|" (keys admins/all))}
   :email {:name {:fi "Sähköposti (julkinen)"
                  :se "E-post (offentlig)"
                  :en "Email (public)"}
           :description {:fi "Julkinen sähköpostiosoite yhteydenottoja varten"
                         :se "Offentlig e-postadress för kontakt"
                         :en "Public email address for contact"}
           :data-type "text"}
   :www {:name {:fi "Web-sivu"
                :se "Webbsida"
                :en "Website"}
         :description {:fi "Liikuntapaikan kotisivujen osoite"
                       :se "Idrottsplatsens hemsideadress"
                       :en "Sports facility website address"}
         :data-type "text"}
   :reservations-link {:name {:fi "Tilavaraukset"
                              :se "Lokalreservationer"
                              :en "Reservations"}
                       :description {:fi "Linkki tilavarausjärjestelmään"
                                     :se "Länk till bokningssystemet"
                                     :en "Link to reservation system"}
                       :data-type "text"}
   :phone-number {:name {:fi "Puhelinnumero"
                         :se "Telefonnummer"
                         :en "Phone number"}
                  :description {:fi "Yhteystietojen puhelinnumero"
                                :se "Telefonnummer för kontakt"
                                :en "Contact phone number"}
                  :data-type "text"}
   :comment {:name {:fi "Lisätieto"
                    :se "Ytterligare information"
                    :en "Additional information"}
             :description {:fi "Vapaamuotoinen lisätieto liikuntapaikasta"
                           :se "Fritext tilläggsinformation om idrottsplatsen"
                           :en "Free-form additional information about the sports facility"}
             :data-type "text"}
   :construction-year {:name {:fi "Rakennusvuosi"
                              :se "Byggår"
                              :en "Construction year"}
                       :description {:fi "Vuosi, jolloin liikuntapaikka rakennettiin"
                                     :se "År då idrottsplatsen byggdes"
                                     :en "Year when the sports facility was built"}
                       :data-type "Integer"}
   :renovation-years {:name {:fi "Peruskorjausvuodet"
                             :se "Renoveringsår"
                             :en "Renovation years"}
                      :description {:fi "Vuodet, jolloin liikuntapaikkaa on peruskorjattu"
                                    :se "År då idrottsplatsen har renoverats"
                                    :en "Years when the sports facility has been renovated"}
                      :data-type "array[Integer]"}
   :type.type-code {:required true
                    :name {:fi "Tyyppi"
                           :se "Typ"
                           :en "Type"}
                    :description {:fi "Liikuntapaikan tyyppikoodi LIPAS-luokituksen mukaan"
                                  :se "Idrottsplatsens typkod enligt LIPAS-klassificeringen"
                                  :en "Sports facility type code according to LIPAS classification"}
                    :data-type "Integer"}
   :location.address {:required true
                      :name {:fi "Katuosoite"
                             :se "Gatuadress"
                             :en "Street address"}
                      :description {:fi "Liikuntapaikan katuosoite"
                                    :se "Idrottsplatsens gatuadress"
                                    :en "Sports facility street address"}
                      :data-type "string"}
   :location.postal-code {:required true
                          :name {:fi "Postinumero"
                                 :se "Postnummer"
                                 :en "Postal code"}
                          :description {:fi "Liikuntapaikan postinumero"
                                        :se "Idrottsplatsens postnummer"
                                        :en "Sports facility postal code"}
                          :data-type "string"}
   :location.postal-office {:name {:fi "Postitoimipaikka"
                                   :se "Postort"
                                   :en "Postal office"}
                            :description {:fi "Postinumeroa vastaava postitoimipaikka"
                                          :se "Postort som motsvarar postnumret"
                                          :en "Postal office corresponding to the postal code"}
                            :data-type "string"}
   :location.neighborhood {:name {:fi "Kuntaosa"
                                  :se "Kommundel"
                                  :en "Neighborhood"}
                           :description {:fi "Kunnan osa-alue, jossa liikuntapaikka sijaitsee"
                                         :se "Kommundel där idrottsplatsen ligger"
                                         :en "Municipal sub-area where the sports facility is located"}
                           :data-type "string"}
   :location.city.city-code {:required true
                             :name {:fi "Kuntanumero"
                                    :se "Kommunnummer"
                                    :en "Municipality code"}
                             :description {:fi "Kunnan virallinen numerokoodi"
                                           :se "Kommunens officiella nummerkod"
                                           :en "Official municipality number code"}
                             :data-type "string"}
   :location.geometries {:required true
                         :name {:fi "Geometriat"
                                :se "Geometrier"
                                :en "Geometries"}
                         :description {:fi "Liikuntapaikan sijainnin geometriat (pisteet, viivat, alueet)"
                                       :se "Idrottsplatsens platsgeometrier (punkter, linjer, områden)"
                                       :en "Sports facility location geometries (points, lines, areas)"}
                         :data-type "GeoJSON"}
   :properties {:name {:fi "Ominaisuudet"
                       :se "Egenskaper"
                       :en "Properties"}
                :description {:fi "Liikuntapaikkatyyppiin liittyvät erityisominaisuudet"
                              :se "Speciella egenskaper relaterade till idrottsplatstypen"
                              :en "Special properties related to the sports facility type"}
                :data-type "complex"}
   :activities {:name {:fi "Toiminnat"
                       :se "Aktiviteter"
                       :en "Activities"}
                :description {:fi "Liikuntapaikalla harrastettavat liikuntalajit ja toiminnat"
                              :se "Idrottsgrenar och aktiviteter som utövas på idrottsplatsen"
                              :en "Sports and activities practiced at the sports facility"}
                :data-type "complex"}
   :circumstances {:name {:fi "Olosuhteet"
                          :se "Förhållanden"
                          :en "Circumstances"}
                   :description {:fi "Liikuntapaikan olosuhdetiedot (mm. valaistusvuorot)"
                                 :se "Idrottsplatsens förhållandeinformation (bl.a. belysningstider)"
                                 :en "Sports facility circumstance information (e.g. lighting hours)"}
                   :data-type "complex"}
   #_#_:fields {:name {:fi "Kentät"
                   :se "Planer"
                   :en "Fields"}
            :description {:fi "Jäähallien, uimahallien yms. sisäiset kentät/radat/altaat"
                          :se "Interna planer/banor/bassänger i ishallar, simhallar etc."
                          :en "Internal fields/tracks/pools in ice halls, swimming halls etc."}
            :data-type "array[field]"}})

(def csv-headers
  ["Tietue"
   "Pakollinen tieto"
   "Tietotyyppi"
   "Nimi fi"
   "Nimi se"
   "Nimi en"
   "Kuvaus fi"
   "Kuvaus se"
   "Kuvaus en"])

(def csv-data
  (into [csv-headers]
        (for [[k {:keys [name description required data-type]}] (sort-by first common-keys)]
          [(clojure.core/name k)
           required
           data-type
           (:fi name)
           (:se name)
           (:en name)
           (:fi description)
           (:se description)
           (:en description)])))

(comment
  (keys common-keys)
  )
