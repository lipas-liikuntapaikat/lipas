;; PTV sync review demo scenarios for Utajärvi (local DB <-> PTV test env).
;; (load-file "dev/user/ptv_drift_scenarios.clj")
;; (user.ptv-drift-scenarios/run-all!)   ; (re)creates every scenario
;; (user.ptv-drift-scenarios/run-all! [#'user.ptv-drift-scenarios/s10-phone!])
;; (user.ptv-drift-scenarios/report)     ; each site's review rows
;; Re-runnable: every run first syncs each scenario site to PTV (Lipas is the
;; master), then applies the scenario's PTV-side and Lipas-side changes.
(ns user.ptv-drift-scenarios
  (:require [clojure.string :as str]
            [integrant.repl.state :as state]
            [lipas.backend.core :as core]
            [lipas.backend.db.db :as db]
            [lipas.backend.gis :as gis]
            [lipas.backend.ptv.core :as ptv-core]
            [lipas.backend.ptv.integration :as ptvi]
            [lipas.data.ptv :as ptv-data]
            [lipas.data.ptv-review :as ptv-review]
            [lipas.utils :as utils]))

(def org "3d1759a2-e47a-4947-9a31-cab1c1e2512b")

(def svc
  {:liikuntasalit "5f74a478-e79e-42cf-992b-c99731d93bf3"
   :jaahallit "8ee111d8-a61c-4d05-bc77-a5fe13c38d0a"
   :lahiliikunta "d084ad7e-8700-4343-beea-5b5f788bb808"
   :yleisurheilu "7720c415-3a1d-4617-9134-2e7f60650aaf"
   :pallokentat "0ebaa2ae-5c1a-4a26-901a-d43110458257"
   :reitit "ae8e8177-5cb4-4631-bd5b-a19573e5c260"
   :uimarannat "ea2e945a-21b8-45d3-a8f6-db0758042113"
   ;; made in PTV
   :liikuntaneuvonta "8ef15c31-67a5-47a2-b03d-fa0f07e1700b"
   :soveltava "84a7c9a6-064b-43ba-b135-77e4efce5bb0"
   :puistojumppa "0d7771c4-6090-4755-9448-ea4c4771d503"})

(defn- sys [] state/system)
(defn- dbc [] (:lipas/db (sys)))
(defn- ptv [] (:lipas/ptv (sys)))
(defn- search [] (:lipas/search (sys)))

(defn- admin []
  (db/get-user-by-username (dbc) {:username "admin"}))

(defn- channel-by-lipas-id
  "The Utajärvi service location whose sourceId carries `lipas-id`."
  [lipas-id]
  (->> (:itemList (ptvi/get-org-service-channels (ptv) org))
       (filter #(= "ServiceLocation" (:serviceChannelType %)))
       (filter #(some-> (:sourceId %) (str/includes? (str "-" lipas-id))))
       first))

(defn- sync!
  "Syncs as the PTV tool does after a review that keeps Lipas's version of
   everything (:reviewed): the scenarios reset PTV to Lipas, over whatever
   an earlier run changed there."
  [lipas-id ptv-map]
  (let [ch-id (or (first (:service-channel-ids ptv-map))
                  (-> (db/get-sports-site (dbc) lipas-id) :ptv :service-channel-ids first))
        ch (when ch-id (ptvi/get-org-service-channel (ptv) org ch-id))]
    (ptv-core/upsert-ptv-service-location!
      (dbc) (ptv) (search) (admin)
      (cond-> {:lipas-id lipas-id :org-id org :ptv ptv-map}
        ch (assoc :reviewed {:modified (:modified ch)
                             :service-ids (vec (ptv-data/channel-service-ids ch))})))))

(defn- save-site!
  "A Lipas revision written without a sync (as when an edit's sync failed),
   indexed so the PTV tool sees it."
  [site]
  (let [resp (core/upsert-sports-site!* (dbc) (admin) (assoc site :event-date (utils/timestamp)))]
    (core/index! (search) resp :sync (core/index-context (dbc)))
    resp))

(defn- ptv-edit!
  "What an edit in PTV's own UI does: PUT the location as `f` changes the
   synced site, under the location's own sourceId. `body-f` changes the PUT
   body for what Lipas's payload can't say (a second phone, a postal
   address)."
  ([lipas-id f] (ptv-edit! lipas-id f identity))
  ([lipas-id f body-f]
   (let [site (db/get-sports-site (dbc) lipas-id)
         ch-id (-> site :ptv :service-channel-ids first)
         edited (f site)
         body (ptv-data/->ptv-service-location org gis/wgs84->tm35fin-no-wrap (utils/timestamp)
                                               (core/enrich edited))]
     (ptvi/update-service-location (ptv) ch-id (body-f body)))))

(defn- connect! [lipas-id service-k]
  (let [ch-id (-> (db/get-sports-site (dbc) lipas-id) :ptv :service-channel-ids first)]
    (ptvi/update-service-connections (ptv) org (svc service-k) #(conj % ch-id))))

(defn- disconnect! [lipas-id service-k]
  (let [ch-id (-> (db/get-sports-site (dbc) lipas-id) :ptv :service-channel-ids first)]
    (ptvi/update-service-connections (ptv) org (svc service-k) #(disj % ch-id))))

(defn- set-site! [lipas-id f]
  (save-site! (f (db/get-sports-site (dbc) lipas-id))))

(defn- in-sync!
  "Link `lipas-id` to its PTV location and sync it with these texts and
   services. Twice: the first sync of a location keeps its PTV connections,
   the second makes Lipas's list the master."
  [lipas-id {:keys [name summary description services en site-fields]}]
  (let [ch (channel-by-lipas-id lipas-id)
        _ (assert ch (str "no PTV location for " lipas-id))
        site (db/get-sports-site (dbc) lipas-id)]
    (save-site! (cond-> (-> site
                            (assoc :name (or name (:name site)))
                            (dissoc :marketing-name)
                            (merge site-fields))
                  (:ptv site) (update :ptv dissoc :summary :description)
                  en (assoc-in [:name-localized :en] (:name en))
                  (and (not en) (:name-localized site)) (update :name-localized dissoc :en)))
    (let [ptv-map {:org-id org
                   :sync-enabled true
                   :service-channel-ids [(:id ch)]
                   :service-ids (mapv svc services)
                   :summary (cond-> {:fi summary} en (assoc :en (:summary en)))
                   :description (cond-> {:fi description} en (assoc :en (:description en)))}]
      (sync! lipas-id ptv-map)
      (sync! lipas-id ptv-map))
    (:id ch)))

(defn- fresh!
  "in-sync! for a site that has no PTV location yet: the first run exports
   it as a new one (later runs find it by its sourceId)."
  [lipas-id {:keys [summary description services] :as opts}]
  (when-not (channel-by-lipas-id lipas-id)
    (sync! lipas-id {:org-id org
                     :sync-enabled true
                     :service-ids (mapv svc services)
                     :summary {:fi summary}
                     :description {:fi description}}))
  (in-sync! lipas-id opts))

(defn- add-phone [body phone]
  (let [{:keys [prefix number]} (ptv-data/parse-phone-number phone)]
    (update body :phoneNumbers (fnil conj [])
            {:number number :prefixNumber prefix :isFinnishServiceNumber false :language "fi"})))

;;; Scenarios ;;;

(defn s1-in-sync! []
  ;; Ajan tasalla: nothing differs.
  (in-sync! 615340
            {:name "Utajärven pallokenttä"
             :summary "Utajärven pallokenttä on kunnan ylläpitämä nurmikenttä keskustassa."
             :description "Utajärven pallokenttä sijaitsee keskustassa. Kentällä voi pelata jalkapalloa ja muita pallopelejä kesäkaudella."
             :services [:pallokentat]
             :en {:name "Utajärvi ball field"
                  :summary "The Utajärvi ball field is a municipality-maintained grass field in the town centre."
                  :description "The Utajärvi ball field is in the town centre. Football and other ball games can be played there in summer."}}))

(defn s2-ptv-texts! []
  ;; Muutettu PTV:ssä: summary and description edited in PTV.
  (in-sync! 89913
            {:name "Utajärven jäähalli"
             :summary "Utajärven jäähalli on kunnan sisäjäähalli, jossa voi luistella ja pelata jääkiekkoa talvikaudella."
             :description "Jäähalli sijaitsee urheilukentän vieressä. Hallissa on kaukalo, pukuhuoneet ja katsomo. Yleisövuorot ja vuorojen varaukset löytyvät kunnan verkkosivuilta."
             :services [:jaahallit]})
  (ptv-edit! 89913 #(-> %
                        (assoc-in [:ptv :summary :fi] "Utajärven jäähalli on kunnan sisäjäähalli, jossa voi luistella ja pelata jääkiekkoa. Halli on auki lokakuusta maaliskuuhun.")
                        (assoc-in [:ptv :description :fi] "Jäähalli sijaitsee urheilukentän vieressä. Hallissa on kaukalo, pukuhuoneet ja katsomo. Yleisövuorot ovat lauantaisin klo 12–14. Vuoroja voi varata liikuntatoimistosta."))))

(defn s3-lipas-texts! []
  ;; Muutettu Lipaksessa: description edited in Lipas, not yet synced.
  (in-sync! 506495
            {:name "Utajärven koulun piha-alue"
             :summary "Utajärven koulun piha-alue on avoin lähiliikuntapaikka koulun pihalla."
             :description "Piha-alueella on pelikenttä, kiipeilytelineitä ja koripallokorit. Alue on koulupäivien aikana koulun käytössä."
             :services [:lahiliikunta]})
  (set-site! 506495 #(assoc-in % [:ptv :description :fi] "Piha-alueella on pelikenttä, kiipeilytelineitä ja koripallokorit. Alue on koulupäivien aikana koulun käytössä. Iltaisin ja viikonloppuisin alue on kaikkien käytössä.")))

(defn s4-conflict! []
  ;; Ristiriita: the summary edited both in PTV and in Lipas.
  (in-sync! 509023
            {:name "Nuoriso- ja liikuntatalon kuntosali"
             :summary "Nuoriso- ja liikuntatalon kuntosalilla voi harjoitella omatoimisesti kuntosalikortilla."
             :description "Kuntosali sijaitsee nuoriso- ja liikuntatalon kellarikerroksessa. Salilla on monipuoliset laitteet ja vapaat painot. Kuntosalikortin voi ostaa kunnan asiointipisteestä."
             :services [:liikuntasalit]})
  (ptv-edit! 509023 #(assoc-in % [:ptv :summary :fi] "Nuoriso- ja liikuntatalon kuntosalilla voi harjoitella omatoimisesti kuntosalikortilla joka päivä klo 6–22."))
  (set-site! 509023 #(assoc-in % [:ptv :summary :fi] "Nuoriso- ja liikuntatalon kuntosali on avoinna kuntosalikortilla, ja salilla on myös ohjattuja vuoroja.")))

(defn s5-connections! []
  ;; Liitokset: PTV removed one connection and added another.
  (in-sync! 507128
            {:name "Utajärven urheilukenttä"
             :summary "Utajärven urheilukenttä on yleisurheilukenttä, jossa on juoksuradat ja kenttälajien suorituspaikat."
             :description "Urheilukenttä sijaitsee keskustassa koulukeskuksen vieressä. Kentällä on 400 metrin juoksurata sekä hyppy- ja heittopaikat. Kenttä on vapaasti käytettävissä, kun siellä ei ole järjestettyjä tapahtumia."
             :services [:yleisurheilu :pallokentat]})
  (disconnect! 507128 :pallokentat)
  (connect! 507128 :liikuntaneuvonta))

(defn s6-names! []
  ;; Nimi: PTV renamed the location and gave it a marketing name.
  (in-sync! 523482
            {:name "Utajärven kirkonkylän valaistu latu"
             :summary "Kirkonkylän valaistu latu on noin viiden kilometrin hiihtolatu keskustan tuntumassa."
             :description "Latu kulkee kirkonkylän ympäristössä ja on valaistu klo 6–22. Ladulla voi hiihtää perinteisellä ja vapaalla tyylillä. Latu kunnostetaan lumitilanteen mukaan."
             :services [:reitit]})
  (ptv-edit! 523482 #(assoc % :name "Kirkonkylän valaistu latu" :marketing-name "Utajärven kuntolatu")))

(defn s7-new-language! []
  ;; Uusi kieliversio: English texts written in Lipas after the last sync.
  (in-sync! 600093
            {:name "Kirkonkylän uimaranta"
             :summary "Kirkonkylän uimaranta on hiekkapohjainen uimaranta Utajärven keskustassa."
             :description "Uimarannalla on laituri, pukukopit ja lasten uimapaikka. Rannalla ei ole valvontaa. Uimaveden laatu tarkastetaan kesän aikana."
             :services [:uimarannat]})
  (set-site! 600093 #(-> %
                         (assoc-in [:name-localized :en] "Kirkonkylä beach")
                         (assoc-in [:ptv :summary :en] "Kirkonkylä beach is a sandy beach in the centre of Utajärvi.")
                         (assoc-in [:ptv :description :en] "The beach has a pier, changing cabins and a shallow area for children. The beach is not supervised. The water quality is checked during the summer."))))

(defn s8-first-link! []
  ;; Ensimmäinen linkitys: the site isn't synced; its PTV location has texts
  ;; and a connection of its own. Linked by the user in the UI.
  (let [site (db/get-sports-site (dbc) 506497)
        ch (channel-by-lipas-id 506497)]
    (save-site! (-> site
                    (assoc :name "Hyvinvointitalon lähiliikuntapaikka")
                    (dissoc :ptv)))
    (ptvi/update-service-location
      (ptv) (:id ch)
      (ptv-data/->ptv-service-location
        org gis/wgs84->tm35fin-no-wrap (utils/timestamp)
        (core/enrich (assoc site :ptv {:org-id org
                                       :source-id (:sourceId ch)
                                       :languages ["fi"]
                                       :service-ids [(svc :lahiliikunta)]
                                       :summary {:fi "Hyvinvointitalon lähiliikuntapaikalla on kuntoilulaitteita ja pelialue kaikenikäisille."}
                                       :description {:fi "Lähiliikuntapaikka sijaitsee hyvinvointitalon pihalla. Alueella on ulkokuntoilulaitteita, pieni pelikenttä ja penkkejä. Puistojumppaa järjestetään kesäisin tiistaisin."}}))))
    (ptvi/update-service-connections (ptv) org (svc :puistojumppa) #(conj % (:id ch)))
    (:id ch)))

(defn s9-everything! []
  ;; Kaikki yhdessä: PTV renamed it, changed the description and added a
  ;; connection; Lipas changed the summary and the description.
  (in-sync! 79800
            {:name "Nuoriso- ja liikuntatalon liikuntasali"
             :summary "Nuoriso- ja liikuntatalon liikuntasali on monitoimisali palloiluun ja ryhmäliikuntaan."
             :description "Liikuntasali sijaitsee nuoriso- ja liikuntatalossa. Salissa voi pelata sählyä, lentopalloa ja koripalloa. Vuoroja voi varata liikuntatoimistosta."
             :services [:liikuntasalit]})
  (ptv-edit! 79800 #(-> %
                        (assoc :name "Utajärven liikuntasali")
                        (assoc-in [:ptv :description :fi] "Liikuntasali sijaitsee nuoriso- ja liikuntatalossa. Salissa voi pelata sählyä, lentopalloa ja koripalloa. Vuoroja voi varata sähköisesti kunnan verkkosivuilta.")))
  (connect! 79800 :soveltava)
  (set-site! 79800 #(-> %
                        (assoc-in [:ptv :summary :fi] "Nuoriso- ja liikuntatalon liikuntasali on monitoimisali palloiluun, ryhmäliikuntaan ja tapahtumiin.")
                        (assoc-in [:ptv :description :fi] "Liikuntasali sijaitsee nuoriso- ja liikuntatalossa. Salissa voi pelata sählyä, lentopalloa ja koripalloa. Salin voi vuokrata myös tapahtumiin."))))

(defn s10-phone! []
  ;; Yhteystiedot: the phone number changed in PTV.
  (in-sync! 506493
            {:name "Utajärven frisbeegolfrata"
             :summary "Utajärven frisbeegolfrata on yhdeksänväyläinen rata keskustan tuntumassa."
             :description "Frisbeegolfrata kulkee metsäisessä maastossa urheilukentän takana. Radalla on yhdeksän väylää ja opastaulu. Rata on vapaasti käytettävissä lumettomana aikana."
             :services [:lahiliikunta]
             :site-fields {:phone-number "08 5875 5700"}})
  (ptv-edit! 506493 #(assoc % :phone-number "040 123 4567")))

(defn s11-second-phone-and-page! []
  ;; Yksi paikka Lipaksessa: PTV has a second phone and an extra web page.
  (in-sync! 514575
            {:name "Valkeisjärven uimapaikka"
             :summary "Valkeisjärven uimapaikka on luonnonrannalla sijaitseva uimapaikka Puolangantien varrella."
             :description "Uimapaikalla on laituri ja pukukoppi. Rannalla ei ole valvontaa. Uimapaikalle pääsee autolla Puolangantieltä, ja tien varressa on pieni pysäköintialue."
             :services [:uimarannat]
             :site-fields {:phone-number "08 5875 5700"
                           :www "https://www.utajarvi.fi/uimarannat"}})
  (ptv-edit! 514575 identity
             #(-> %
                  (add-phone "040 765 4321")
                  (update :webPages (fnil conj [])
                          {:url "https://www.utajarvi.fi/liikunta" :value "Liikuntapalvelut"
                           :language "fi" :orderNumber 2}))))

(defn s12-address! []
  ;; Osoite: the visiting address changed in PTV.
  (fresh! 600121
          {:name "Hyvinvointitalon ulkokuntoilupaikka"
           :summary "Hyvinvointitalon ulkokuntoilupaikalla on kuntoilulaitteita kaikenikäisille."
           :description "Ulkokuntoilupaikka sijaitsee hyvinvointitalon pihalla. Laitteilla voi harjoitella lihaskuntoa ja tasapainoa. Paikka on vapaasti käytettävissä ympäri vuoden."
           :services [:lahiliikunta]})
  (ptv-edit! 600121 #(assoc-in % [:location :address] "Kirkkotie 21")))

(defn s13-point! []
  ;; Sijainti: PTV's point is about 200 m north of Lipas's (information only).
  (fresh! 605450
          {:name "Utajärven urheilukentän ulkokuntoilupaikka"
           :summary "Urheilukentän ulkokuntoilupaikalla on kuntoilulaitteita urheilukentän laidalla."
           :description "Ulkokuntoilupaikka sijaitsee urheilukentän laidalla koulukeskuksen vieressä. Laitteilla voi harjoitella lihaskuntoa. Paikka on vapaasti käytettävissä ympäri vuoden."
           :services [:lahiliikunta]})
  (ptv-edit! 605450 #(update-in % [:location :geometries :features 0 :geometry :coordinates 1]
                                + 0.0018)))

(defn s14-sv-added! []
  ;; Kieliversio: PTV added a Swedish version, which Utajärvi doesn't publish.
  (fresh! 606904
          {:name "Utajärven kuntoportaat"
           :summary "Utajärven kuntoportaat ovat uimarannan rinteessä olevat ulkoportaat kuntoiluun."
           :description "Kuntoportaat nousevat uimarannalta harjun päälle. Portaissa on noin 150 askelmaa ja välitasanteita. Portaat ovat valaistut ja käytettävissä ympäri vuoden."
           :services [:lahiliikunta]})
  (ptv-edit! 606904 #(-> %
                         (assoc-in [:name-localized :se] "Utajärvi motionstrappor")
                         (assoc-in [:ptv :languages] ["fi" "se"])
                         (assoc-in [:ptv :summary :se] "Utajärvi motionstrappor är en utomhustrappa för motion i sluttningen vid badstranden.")
                         (assoc-in [:ptv :description :se] "Trappan går från badstranden upp till åsen. Den har cirka 150 trappsteg och vilplan. Trappan är belyst och kan användas året runt."))))

(defn s15-first-link-bilingual! []
  ;; Ensimmäinen linkitys, kaksikielinen: the site isn't synced; its PTV
  ;; location has Finnish and English texts. Linked by the user in the UI.
  (let [site (db/get-sports-site (dbc) 527789)
        ch (channel-by-lipas-id 527789)
        body (ptv-data/->ptv-service-location
               org gis/wgs84->tm35fin-no-wrap (utils/timestamp)
               (core/enrich (-> site
                                (assoc-in [:name-localized :en] "Utajärvi skatepark")
                                (assoc :ptv (cond-> {:org-id org
                                                     :languages ["fi" "en"]
                                                     :service-ids [(svc :lahiliikunta)]
                                                     :summary {:fi "Utajärven skeittipuisto on betonipohjainen skeittipaikka keskustassa."
                                                               :en "The Utajärvi skatepark is a concrete skate park in the town centre."}
                                                     :description {:fi "Skeittipuistossa on ramppeja, reilejä ja boksi. Puisto sopii rullalaudoille, potkulaudoille ja BMX-pyörille. Alue on valaistu."
                                                                   :en "The skatepark has ramps, rails and a box. It suits skateboards, scooters and BMX bikes. The area is lit."}}
                                              ch (assoc :source-id (:sourceId ch)))))))]
    (save-site! (dissoc site :ptv))
    (:id (if ch
           (ptvi/update-service-location (ptv) (:id ch) body)
           (ptvi/create-service-location (ptv) body)))))

(defn s16-postal-address! []
  ;; Postiosoite: PTV has a postal address, which Lipas has no field for; a
  ;; sync must keep it. Lipas changed the summary, so the site needs a sync.
  (fresh! 526137
          {:name "Keisarintien päiväkodin leikkikenttä"
           :summary "Keisarintien päiväkodin leikkikenttä on avoin leikkipaikka päiväkodin pihalla."
           :description "Leikkikentällä on keinuja, liukumäki ja hiekkalaatikko. Päiväkodin aukioloaikoina kenttä on päiväkodin käytössä, muulloin kaikkien käytettävissä."
           :services [:lahiliikunta]})
  (ptv-edit! 526137 identity
             #(update % :addresses conj
                      {:type "Postal" :subType "PostOfficeBox" :country "FI"
                       :postOfficeBoxAddress {:postOfficeBox [{:value "PL 1" :language "fi"}]
                                              :postalCode "91601"
                                              :municipality "889"}}))
  (set-site! 526137 #(assoc-in % [:ptv :summary :fi] "Keisarintien päiväkodin leikkikenttä on avoin leikkipaikka päiväkodin pihalla Utajärven keskustassa.")))

(defn s17-en-added-in-ptv! []
  ;; Kieliversio: PTV added an English version, which Utajärvi publishes
  ;; but the site has no English texts for: "Ota PTV:n tekstit" keeps it.
  (in-sync! 80247
            {:name "Kylätalo Katajiston liikuntasali"
             :summary "Katajiston kylätalon liikuntasali on pieni sali kyläläisten liikuntaan."
             :description "Liikuntasali sijaitsee Katajiston kylätalossa. Salissa voi pelata sulkapalloa ja pöytätennistä sekä harrastaa ryhmäliikuntaa. Vuorot varataan kylätoimikunnalta."
             :services [:liikuntasalit]})
  (ptv-edit! 80247 #(-> %
                        (assoc-in [:name-localized :en] "Katajisto village hall gym")
                        (assoc-in [:ptv :languages] ["fi" "en"])
                        (assoc-in [:ptv :summary :en] "The gym of Katajisto village hall is a small hall for the villagers' exercise.")
                        (assoc-in [:ptv :description :en] "The gym is in Katajisto village hall. You can play badminton and table tennis and take part in group exercise. Book a turn from the village committee."))))

(def all [#'s1-in-sync! #'s2-ptv-texts! #'s3-lipas-texts! #'s4-conflict! #'s5-connections!
          #'s6-names! #'s7-new-language! #'s8-first-link! #'s9-everything!
          #'s10-phone! #'s11-second-phone-and-page! #'s12-address! #'s13-point!
          #'s14-sv-added! #'s15-first-link-bilingual! #'s16-postal-address!
          #'s17-en-added-in-ptv!])

(defn run-all!
  ([] (run-all! all))
  ([scenarios]
   (vec (for [s scenarios]
          (try [(:name (meta s)) :ok (s)]
               (catch Exception e [(:name (meta s)) :failed (ex-message e) (some-> e ex-data :resp :body)]))))))

(defn- channel-texts [ch]
  (let [by-lang (fn [k type-k]
                  (->> (k ch)
                       (filter #(= type-k (:type %)))
                       (map (juxt :language :value))
                       (into (sorted-map))))]
    {:name (by-lang :serviceChannelNames "Name")
     :alternative-name (by-lang :serviceChannelNames "AlternativeName")
     :summary (by-lang :serviceChannelDescriptions "Summary")
     :description (by-lang :serviceChannelDescriptions "Description")
     :services (->> (:services ch)
                    (map #(-> % :service :id))
                    sort vec)
     :languages (:languages ch)
     :phones (mapv (juxt :prefixNumber :number) (:phoneNumbers ch))
     :emails (mapv :value (:emails ch))
     :web-pages (mapv (juxt :language :url) (:webPages ch))
     :addresses (mapv (fn [{:keys [type subType streetAddress postOfficeBoxAddress]}]
                        [type subType
                         (or (some-> streetAddress :street first :value)
                             (some-> postOfficeBoxAddress :postOfficeBox first :value))
                         (or (:postalCode streetAddress) (:postalCode postOfficeBoxAddress))])
                      (:addresses ch))}))

(defn state
  "Where `lipas-id` stands after a sync: the saved site, its PTV location and
   the review rows that remain."
  [lipas-id]
  (let [site (db/get-sports-site (dbc) lipas-id)
        ch-id (-> site :ptv :service-channel-ids first)
        ch (when ch-id (ptvi/get-org-service-channel (ptv) org ch-id))
        base (get (ptv-core/get-ptv-sync-bases (dbc) [lipas-id]) lipas-id)]
    {:lipas {:name (:name site)
             :name-en (-> site :name-localized :en)
             :marketing-name (:marketing-name site)
             :summary (-> site :ptv :summary)
             :description (-> site :ptv :description)
             :services (-> site :ptv :service-ids sort vec)
             :last-sync (-> site :ptv :last-sync)}
     :ptv (channel-texts ch)
     :review (mapv (juxt :key :origin)
                   (:rows (ptv-review/sync-review site base ch {}
                                                  (ptv-data/effective-languages site ["fi" "en"]))))}))

(defn report
  "Each scenario site's review, as the PTV tool computes it."
  []
  (let [ids [615340 89913 506495 509023 507128 523482 600093 506497 79800
             506493 514575 600121 605450 606904 527789 526137 80247]
        bases (ptv-core/get-ptv-sync-bases (dbc) ids)
        langs ["fi" "en"]]
    (vec (for [id ids
               :let [site (db/get-sports-site (dbc) id)
                     ch-id (-> site :ptv :service-channel-ids first)
                     ch (when ch-id (ptvi/get-org-service-channel (ptv) org ch-id))
                     review (ptv-review/sync-review site (get bases id) ch {}
                                                    (ptv-data/effective-languages site langs))]]
           [id (:name site)
            (mapv (fn [r] [(:key r) (:origin r) (when (:language-added? r) :lang-added)])
                  (:rows review))]))))
