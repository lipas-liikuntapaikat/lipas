(ns lipas.dev
  "Entrypoint for `lein ring` dev server."
  (:require
   [lipas.backend.system :as backend]
   dev
   [lipas.backend.config :as config]
   [ring.middleware.reload :refer [wrap-reload]]
   [lipas.backend.core :as core]
   [lipas.search-indexer :as si]
   [lipas.data.types :as types]))

(def system (backend/start-system! (dissoc config/default-config :server :nrepl)))
(def app (:app system))
(def dev-handler (-> #'app wrap-reload))

(comment

  ;; See `lipas.repl` for more automated solution

  (require '[lipas.backend.config :as config])
  (require '[lipas.backend.system :as system])
  (require '[lipas.backend.core :as core])

  (def dev-config (dissoc config/system-config :lipas/nrepl))
  (def s0 (system/start-system!))

  (def current-system (atom nil))

  (do
    (when @current-system
      (system/stop-system! @current-system))
    (reset! current-system (system/start-system! dev-config)))


  (require '[lipas.backend.jwt :as jwt])
  (def admin (core/get-user (:db @current-system) "admin@lipas.fi"))

  (jwt/create-token admin)
   "eyJhbGciOiJIUzUxMiJ9.eyJpZCI6IjQ3ZmQ0MTI2LWQ5MjMtNDdmYi1hZmUzLWNlOWU3YzI1N2QxZiIsImVtYWlsIjoiYWRtaW5AbGlwYXMuZmkiLCJ1c2VybmFtZSI6ImFkbWluIiwicGVybWlzc2lvbnMiOnsiYWRtaW4_Ijp0cnVlLCJkcmFmdD8iOnRydWV9LCJleHAiOjE3MjEzOTA5NTh9.2A-i6EzYZpqDILHS41_HTTGCdoiR_IfvGVuC5AZreAJNavgRc07u9o_Gx3_TI8qCHRj4Bid46QJSOvevtK-cxQ"



  (require '[migratus.core :as migratus])

  (def migratus-config
    {:store         :database
     :migration-dir "migrations/"
     :db            {:dbtype   "postgresql"
                     :dbname   (get (System/getenv) "DB_NAME")
                     :host     (get (System/getenv) "DB_HOST")
                     :user     (get (System/getenv) "DB_USER")
                     :port     (get (System/getenv) "DB_PORT")
                     :password (get (System/getenv) "DB_PASSWORD")}})

  (migratus/create migratus-config "organization")
  (migratus/migrate migratus-config)

  (require '[lipas.data.types :as types])

  (require '[lipas.search-indexer :as si])
  (si/main
   nil
   (:lipas/db @current-system)
   (:lipas/search @current-system)
   "search")


  )


(comment
  (require '[lipas.integration.ptv.core :as ptv])
  (require '[lipas.data.ptv :as ptv-data])
  (require '[lipas.ai.core :as ai])
  (ptv/get-eligible-sites (:search @current-system) {:city-codes [889] :owners ["city" "city-main-owner"]})

  (ptv/->ptv-service
   {:sub-category "Liikunta- ja ulkoilureitit",
    :sub-category-id 4400,
    :org-id "7b83257d-06ad-4e3b-985d-16a5c9d3fced"
    :city-codes [889]
    :source-id "lipas-7b83257d-06ad-4e3b-985d-16a5c9d3fced-4400",
    :description
    {:fi
     "Utajärven liikunta- ja ulkoilureitit tarjoavat monipuolisia mahdollisuuksia ulkoiluun ja liikuntaan. Reitit sisältävät useita latuja, kuntoratoja, kävely- ja ulkoilureittejä sekä moottorikelkkareittejä. Reitit on suunniteltu tukemaan sekä kuntoliikuntaa että rentouttavaa ulkoilua kauniissa luonnonympäristössä. Reitit ovat käytettävissä ympäri vuoden ja ne tarjoavat sekä talvi- että kesäaktiviteetteja.",
     :se
     "Utajärvi erbjuder ett brett utbud av idrotts- och friluftsleder för utomhusaktiviteter. Lederna inkluderar flera skidspår, motionsspår, promenad- och friluftsleder samt skoterleder. Lederna är utformade för att stödja både konditionsträning och avkopplande friluftsliv i en vacker naturlig miljö. Lederna är tillgängliga året runt och erbjuder aktiviteter för både vinter och sommar.",
     :en
     "Utajärvi's sports and outdoor recreation routes offer a variety of opportunities for outdoor activities and exercise. The routes include several ski tracks, fitness tracks, walking and outdoor recreation routes, and snowmobile routes. The routes are designed to support both fitness activities and relaxing outdoor recreation in a beautiful natural environment. The routes are available year-round and offer both winter and summer activities."},
    :summary
    {:fi
     "Utajärven liikunta- ja ulkoilureitit sisältävät latuja, kuntoratoja, kävely- ja ulkoilureittejä sekä moottorikelkkareittejä.",
     :se
     "Utajärvi erbjuder skidspår, motionsspår, promenad- och friluftsleder samt skoterleder.",
     :en
     "Utajärvi's routes include ski tracks, fitness tracks, walking and outdoor recreation routes, and snowmobile routes."}})

  (def r *1)
  (->> r (map :owner))

  (core/make-overview r)

  (require '[lipas.backend.core :as core])


  (def lol1
    (->> r
         (map (fn [m] {:name          (-> m :search-meta :name)
                       :category      (-> m :search-meta :type :name :fi)
                       :sub-category  (-> m :search-meta :type :sub-category :name :fi)
                       :main-category (-> m :search-meta :type :main-category :name :fi)
                       :city          (-> m :search-meta :location :city :name :fi)
                       :email         (:email m)
                       :www           (:www m)
                       :phone-number  (:phone-number m)}))))

  (->> lol1
       (map #(select-keys % [:category :main-category :sub-category]))
       frequencies
       (map (fn [[k v]] (assoc k :count v)))
       (group-by :sub-category))

  (->> lol1 (group-by (comp )))


  (def token-resp
    (ptv/authenticate {:token-url (get ptv/test-config :token-url)
                       :username  (get-in ptv/test-config [:creds :api :username])
                       :password  (get-in ptv/test-config [:creds :api :password])
                       :org-id    (get-in ptv/test-config [:creds :org-id])}))


  (def org {:id                  (get-in ptv/test-config [:creds :org-id])
            :city-codes          [889]
            :supported-languages ["fi" "en"]})

  (ptv/create-service {:service-url (get ptv/test-config :service-url)
                       :token       (:ptvToken token-resp)}
                      (ptv-data/->ptv-service org (:type-code (:type (first r)))))

  (core/generate-ptv-service-descriptions
   (:search @current-system)
   {:sub-category-id 4400 :city-codes [889]})

  (def ptv-create-resp *1)
  (:id ptv-create-resp)

  ;; Round-trip does not work
  (ptv/update-service {:service-url (get ptv/test-config :service-url)
                       :token       (:ptvToken token-resp)}
                      (:id ptv-create-resp)
                      ptv-create-resp)

  (def luistelukentta (->> r
                           (filter #(= "Luistelukenttä" (-> % :search-meta :type :name :fi)))
                           first))

  (require '[lipas.backend.gis :as gis])
  (gis/wgs84->tm35fin-no-wrap [26.7203204266102 64.8603485630088])

  (ptv/create-service-location
   {:service-location-url (get ptv/test-config :service-location-url)
    :token                (:ptvToken token-resp)}
   (ptv-data/->ptv-service-location org gis/wgs84->tm35fin-no-wrap luistelukentta))

  (def ptv-create2-resp *1)

  luistelukentta
  (:type (first r))
  ;; => {:type-code 2150}

  (println (json/encode (ptv-data/->ptv-service org (:type-code (:type (first r))))))

  (core/get-sports-site (:db @current-system) 89913)

  (def uh
    {:properties        {:area-m2 1539, :surface-material []},
     :email             "palaute@utajarvi.fi",
     :envelope
     {:insulated-ceiling?      true,
      :insulated-exterior?     false,
      :low-emissivity-coating? false},
     :phone-number      "+358858755700",
     :building
     {:total-volume-m3           17700,
      :seating-capacity          250,
      :total-ice-area-m2         1539,
      :total-surface-area-m2     2457,
      :total-ice-surface-area-m2 1539},
     :ventilation
     {:dryer-type               "munters",
      :heat-pump-type           "none",
      :dryer-duty-type          "automatic",
      :heat-recovery-type       "thermal-wheel",
      :heat-recovery-efficiency 75},
     :admin             "city-technical-services",
     :www               "https://www.utajarvi.fi",
     :name              "Utajärven jäähalli",
     :construction-year 1997,
     :type              {:type-code 2520, :size-category "small"},
     :lipas-id          89913,
     :renovation-years  [2014],
     :conditions
     {:open-months                     6,
      :stand-temperature-c             7,
      :ice-average-thickness-mm        40,
      :air-humidity-min                60,
      :air-humidity-max                90,
      :maintenance-water-temperature-c 45,
      :ice-surface-temperature-c       -4,
      :weekly-maintenances             12,
      :skating-area-temperature-c      7,
      :daily-open-hours                11,
      :average-water-consumption-l     700},
     :status            "active",
     :event-date        "2019-04-05T13:54:19.910Z",
     :refrigeration
     {:original?            true,
      :refrigerant          "R404A",
      :refrigerant-solution "freezium"},
     :location
     {:city          {:city-code 889},
      :address       "Laitilantie 5",
      :geometries
      {:type "FeatureCollection",
       :features
       [{:type "Feature",
         :geometry
         {:type        "Point",
          :coordinates [26.4131256689191 64.7631112249574]}}]},
      :postal-code   "91600",
      :postal-office "Utajärvi"},
     :owner             "city",
     :hall-id           "91600UT1"})

  (def uh-json (-> uh core/enrich (json/encode)))

  (ai/complete
   ai/openai-config
   ai/ptv-system-instruction
   (str "Laadi tämän viestin lopussa olevan JSON-rakenteen kuvaamasta
   liikuntapaikasta tiiviestelmän ja tekstikuvauksen, jotka sopivat
   Palvelutietovarannossa palvelupaikan kuvaukseen. Kuvaus ja
   tiivistelmä on tarkoitettu palvelun käyttäjille, jotka ovat
   tavallisia ihmisiä, jotka haluavat tietoa palvelusta liikunnan
   näkökulmasta. Yksityiskohtaiset rakennustekniset tiedot ja
   olosuhdetiedot jätetään kuvauksista pois. Haluan vastauksen
   muodossa {\"description\": {...käännökset...}, \"summary\"
   {...käännökset...}}." uh-json))

  *1

  )

;; Ontologiapaska

(comment
  (require '[clj-http.client :as http])
  (require '[cheshire.core :as json])
  (require '[lipas.data.types :as types])

  (def finto-uri "https://api.finto.fi/rest/v1/search")

  (def entries (->> types/all vals (map (juxt :type-code (comp :fi :name) (comp :fi :tags)))))
  (defn q [s]
    (->
     (http/get finto-uri {:query-params {:query s :lang "fi" :maxhits 5 :vocab "koko"}})
     :body
     (json/decode keyword)
     :results
     (->> (map (juxt :prefLabel :uri)))))

  (q "")
  ;; => ()
  ;; => (["ammunta (toiminta)" "http://www.yso.fi/onto/koko/p25336"])
  ;; => (["ammunta (toiminta)" "http://www.yso.fi/onto/koko/p25336"])
  ;; => (["suunnistus" "http://www.yso.fi/onto/koko/p5355"]
  ;;     ["suunnistuskartat" "http://www.yso.fi/onto/koko/p87997"]
  ;;     ["suunnistuskartat" "http://www.yso.fi/onto/koko/p87997"])
  ;; => ()
  ;; => ()
  ;; => (["hyppyrimäet" "http://www.yso.fi/onto/koko/p72457"])
  ;; => (["sisäliikuntapaikat" "http://www.yso.fi/onto/koko/p66287"]
  ;;     ["sisäliikunta" "http://www.yso.fi/onto/koko/p69660"])
  ;; => (["keilahallit" "http://www.yso.fi/onto/koko/p9812"])
  ;; => (["laskettelu" "http://www.yso.fi/onto/koko/p10549"]
  ;;     ["laskettelukengät" "http://www.yso.fi/onto/koko/p41350"]
  ;;     ["laskettelukeskukset" "http://www.yso.fi/onto/koko/p84378"]
  ;;     ["hiihtokeskukset" "http://www.yso.fi/onto/koko/p4432"]
  ;;     ["hiihtokeskukset" "http://www.yso.fi/onto/koko/p4432"])
  ;; => (["laskettelu" "http://www.yso.fi/onto/koko/p10549"])
  ;; => (["laskettelu" "http://www.yso.fi/onto/koko/p10549"])
  ;; => ()
  ;; => (["lähiliikenne" "http://www.yso.fi/onto/koko/p89823"]
  ;;     ["paikallisliikenne" "http://www.yso.fi/onto/koko/p11760"]
  ;;     ["lähiliikennejunat" "http://www.yso.fi/onto/koko/p91819"]
  ;;     ["lähiliikennevaunut" "http://www.yso.fi/onto/koko/p91797"]
  ;;     ["lähiliikenteen rautatieasemat"
  ;;      "http://www.yso.fi/onto/koko/p67093"])
  ;; => (["liikuntapuistot (liikunta-alueet, puistot)"
  ;;      "http://www.yso.fi/onto/koko/p84486"]
  ;;     ["liikuntapuistot (ulkoliikuntapaikat)"
  ;;      "http://www.yso.fi/onto/koko/p69291"])
  ;; => ()

  ;; => (["retkeily" "http://www.yso.fi/onto/koko/p36881"]
  ;;     ["retkeilyalueet" "http://www.yso.fi/onto/koko/p33303"]
  ;;     ["retkeilyalueet" "http://www.yso.fi/onto/koko/p33303"]
  ;;     ["hostellit" "http://www.yso.fi/onto/koko/p31112"]
  ;;     ["retkeilymajat (rakennukset)" "http://www.yso.fi/onto/koko/p85802"])
  ;; => ()
  ;; => (["retkeily" "http://www.yso.fi/onto/koko/p36881"])
  ;; => (["maauimalat" "http://www.yso.fi/onto/koko/p76123"]
  ;;     ["uimalat" "http://www.yso.fi/onto/koko/p13459"])
  ;; => (["uimarannat" "http://www.yso.fi/onto/koko/p32279"])
  ;; => (["koiraurheilu" "http://www.yso.fi/onto/koko/p72589"])
  ;; => (["jääurheilualueet" "http://www.yso.fi/onto/koko/p75572"])
  ;; => (["jää" "http://www.yso.fi/onto/koko/p31914"]
  ;;     ["jäädykkeet" "http://www.yso.fi/onto/koko/p6277"]
  ;;     ["jäädykkeet" "http://www.yso.fi/onto/koko/p6277"]
  ;;     ["jäädytys" "http://www.yso.fi/onto/koko/p73668"]
  ;;     ["jäädytyshoito" "http://www.yso.fi/onto/koko/p89073"])

  ;; => (["golfkentät" "http://www.yso.fi/onto/koko/p32648"])
  ;; => (["moottoriurheilu" "http://www.yso.fi/onto/koko/p31773"])
  ;; => ()
  ;; => (["yleisurheilukentät" "http://www.yso.fi/onto/koko/p85607"])
  ;; => (["jäähallit" "http://www.yso.fi/onto/koko/p11376"])
  ;; => (["kylpylät" "http://www.yso.fi/onto/koko/p35112"])
  ;; => (["uimahallit" "http://www.yso.fi/onto/koko/p11070"])
  ;; => (["uima-altaat" "http://www.yso.fi/onto/koko/p1459"])
  ;; => (["kiipeily" "http://www.yso.fi/onto/koko/p4261"])
  ;; => ()
  ;; => ()
  ;; => (["hevosurheilu" "http://www.yso.fi/onto/koko/p35523"])
  ;; => (["pallokentät" "http://www.yso.fi/onto/koko/p75504"])
  ;; => (["ulkoilureitit" "http://www.yso.fi/onto/koko/p32315"])
  ;; => ()
  ;; => (["retkeilyalueet" "http://www.yso.fi/onto/koko/p33303"])
  ;; => (["virkistysalueet" "http://www.yso.fi/onto/koko/p37350"]
  ;;     ["puistot" "http://www.yso.fi/onto/koko/p34333"]
  ;;     ["viheralueet" "http://www.yso.fi/onto/koko/p33392"])
  ;; => (["urheiluhallit" "http://www.yso.fi/onto/koko/p33522"])
  ;; => (["talviurheilu" "http://www.yso.fi/onto/koko/p8460"])
  ;; => ()
  ;; => (["urheiluilmailu" "http://www.yso.fi/onto/koko/p18298"])
  ;; => (["liikuntasalit" "http://www.yso.fi/onto/koko/p85878"]
  ;;     ["liikuntatilat" "http://www.yso.fi/onto/koko/p30560"])
  ;; => ()
  ;; => ()
  ;; => ()
  ;; => (["eläinurheilu" "http://www.yso.fi/onto/koko/p8973"])
  ;; => (["moottoriurheilu" "http://www.yso.fi/onto/koko/p31773"])
  ;; => (["veneurheilu" "http://www.yso.fi/onto/koko/p75772"])
  ;; => ()
  ;; => (["urheiluilmailu" "http://www.yso.fi/onto/koko/p18298"])
  ;; => (["ilmailu" "http://www.yso.fi/onto/koko/p36083"])
  ;; => (["veneily" "http://www.yso.fi/onto/koko/p34055"])
  ;; => (["luontoliikunta" "http://www.yso.fi/onto/koko/p12424"])
  ;; => (["liikuntapaikat" "http://www.yso.fi/onto/koko/p10416"])
  ;; => (["vesiliikunta" "http://www.yso.fi/onto/koko/p18621"])
  ;; => (["sisäliikunta" "http://www.yso.fi/onto/koko/p69660"])
  ;; => (["kissa" "http://www.yso.fi/onto/koko/p37252"])


  ;; => (["Urheilukalastus. Urheilumetsästys. Ammunta"
  ;;      "http://udcdata.info/067250"]
  ;;     ["ammunta" "http://www.yso.fi/onto/yso/p15302"]
  ;;     ["ammunta (urheilu)" "http://www.yso.fi/onto/yso/p15302"]
  ;;     ["ammunta" "http://www.yso.fi/onto/yso/p15302"]
  ;;     ["ammunta (urheilu)" "http://www.yso.fi/onto/yso/p15302"])
  ;; => (["maastohiihtokeskukset" "http://www.yso.fi/onto/jupo/p3019"]
  ;;     ["maastohiihtokeskukset" "http://www.yso.fi/onto/koko/p75831"])
  ;; => (["Suunnistus" "http://cv.iptc.org/newscodes/subjectcode/15044000"]
  ;;     ["Retkeily. Kävely. Vuorikiipeily. Suunnistus. Leirintä"
  ;;      "http://udcdata.info/067078"]
  ;;     ["Suunnistus" "http://urn.fi/URN:NBN:fi:au:ykl:79.42"]
  ;;     ["suunnistus" "http://www.yso.fi/onto/yso/p1120"]
  ;;     ["suunnistus" "http://www.yso.fi/onto/yso/p1120"])
  ;; => ()
  ;; => ()
  ;; => (["hyppyrimäet" "http://urn.fi/URN:NBN:fi:au:kaunokki:48402"]
  ;;     ["hyppyrimäet" "http://www.yso.fi/onto/yso/p23387"]
  ;;     ["hyppyrimäet" "http://www.yso.fi/onto/yso/p23387"]
  ;;     ["hyppyrimäet" "http://www.yso.fi/onto/yso/p23387"]
  ;;     ["hyppyrimäet" "http://www.yso.fi/onto/yso/p23387"])
  ;; => (["sisäliikunta" "http://www.yso.fi/onto/yso/p26620"]
  ;;     ["sisäliikunta" "http://www.yso.fi/onto/yso/p26620"]
  ;;     ["sisäliikunta" "http://www.yso.fi/onto/jupo/p248"]
  ;;     ["sisäliikunta" "http://www.yso.fi/onto/yso/p26620"]
  ;;     ["sisäliikunta" "http://www.yso.fi/onto/yso/p26620"])
  ;; => (["suorituspaikat" "http://www.yso.fi/onto/yso/Y346328"])
  ;; => (["keilahallit" "http://www.yso.fi/onto/yso/p398"]
  ;;     ["keilahallit" "http://www.yso.fi/onto/yso/p398"]
  ;;     ["keilahallit" "http://www.yso.fi/onto/yso/p398"]
  ;;     ["keilahallit" "http://www.yso.fi/onto/yso/p398"]
  ;;     ["keilahallit" "http://www.yso.fi/onto/yso/p398"])
  ;; => (["veneurheilu" "http://www.yso.fi/onto/jupo/p2938"]
  ;;     ["veneurheilu" "http://www.yso.fi/onto/koko/p75772"])
  ;; => ()
  ;; => (["laskettelu" "http://www.yso.fi/onto/yso/p11029"]
  ;;     ["laskettelu" "http://www.yso.fi/onto/yso/p11029"]
  ;;     ["laskettelu" "http://www.yso.fi/onto/yso/p11029"]
  ;;     ["laskettelu" "http://www.yso.fi/onto/yso/p11029"]
  ;;     ["laskettelu" "http://www.yso.fi/onto/yso/p11029"])
  ;; => (["hiihtokeskukset" "http://urn.fi/URN:NBN:fi:au:kaunokki:50194"]
  ;;     ["hiihtokeskukset" "http://urn.fi/URN:NBN:fi:au:lapponica:L294"]
  ;;     ["hiihtokeskukset" "http://www.yso.fi/onto/yso/p17756"]
  ;;     ["hiihtokeskukset" "http://www.yso.fi/onto/yso/p17756"]
  ;;     ["hiihtokeskukset" "http://www.yso.fi/onto/yso/p17756"])
  ;; => ()
  ;; => (["laskettelurinteet" "http://www.yso.fi/onto/gtk/GTK2015ID4629"]
  ;;     ["laskettelurinteet" "http://www.yso.fi/onto/jupo/p2439"]
  ;;     ["laskettelurinteet" "http://www.yso.fi/onto/mao/p9071"])
  ;; => (["liikuntapuistot" "http://www.yso.fi/onto/jupo/p2312"]
  ;;     ["liikuntapuistot" "http://www.yso.fi/onto/mao/p9919"])
  ;; => (["lähiliikunta" "http://www.yso.fi/onto/keko/p144"]
  ;;     ["lähiliikunta" "http://www.yso.fi/onto/yso/Y365988"])


  entries

  (def lol (atom [["tyyppi" "tyyppi_nimi" "lipas_hakusana" "ontologiasana" "uri"]]))

  (doseq [[type-code name-fi tags] entries
          :let                     [tags (into [name-fi] tags)]
          tag                      tags
          :let                     [res (q tag)]
          [label uri]              res]
    (swap! lol conj [type-code name-fi tag label uri]))

  @lol

  (require '[clojure.string :as str])

  (doseq [line @lol]
    (->> line
         (map #(format "\"%s\"" %))
         (str/join ",")
         println))

  (require '[lipas.backend.search :as search])


  (def search (get-in @current-system [:search ]))
  (def cli (get-in @current-system [:search :client]))
  (def idx (get-in @current-system [:search :indices :sports-site :search]))
  (def doc
    (:_source (:body (search/fetch-document cli idx 607333))))

  (ai/->prompt-doc doc)

  (require '[lipas.backend.core :as core])
  (core/generate-ptv-descriptions search {:lipas-id 607333})
  *1


  (def sss
    (->>
     ({:sub-category "Yleisurheilukentät ja -paikat",
       :sub-category-id 1200,
       :source-id "lipas-92374b0f-7d3c-4017-858e-666ee3ca2761-1200",
       :description
       {:fi
        "Utajärven yleisurheilukentät ja -paikat tarjoavat monipuoliset puitteet yleisurheiluharrastukselle. Palvelu sisältää yleisurheilun harjoitusalueet, jotka sopivat niin amatööreille kuin kokeneemmillekin urheilijoille. Kentät ovat avoinna yleisölle ja niitä voi käyttää monenlaisiin yleisurheilulajeihin.",
        :se
        "Utajärvi friidrottsplatser erbjuder mångsidiga faciliteter för friidrottsintresserade. Tjänsten inkluderar friidrottsövningsområden som lämpar sig för både amatörer och mer erfarna idrottare. Platserna är öppna för allmänheten och kan användas för olika friidrottsgrenar.",
        :en
        "Utajärvi's athletics fields and venues offer versatile facilities for athletics enthusiasts. The service includes athletics training areas suitable for both amateurs and more experienced athletes. The fields are open to the public and can be used for various athletics disciplines."},
       :summary
       {:fi
        "Utajärven yleisurheilukentät tarjoavat monipuoliset puitteet urheiluun.",
        :se "Utajärvi friidrottsplatser erbjuder mångsidiga faciliteter.",
        :en "Utajärvi's athletics fields offer versatile facilities."},
       :id "e3fbd01b-9d50-45d6-90e7-8727823397be"}
      {:sub-category "Retkeilyn palvelut",
       :sub-category-id 2,
       :source-id "lipas-92374b0f-7d3c-4017-858e-666ee3ca2761-2",
       :summary {:fi "aefaefaeefa"},
       :description {:fi "aefaefaeefa"},
       :id "92869a75-c343-4303-ae8a-56cc44a42572"}
      {:sub-category "Liikunta- ja ulkoilureitit",
       :sub-category-id 4400,
       :source-id "lipas-92374b0f-7d3c-4017-858e-666ee3ca2761-4400",
       :summary {:fi "aefaefaeefa"},
       :description {:fi "aefaefaeefa"},
       :id "5ac3a07c-d12c-4faa-a5b5-5a4974d91492"}
      {:sub-category "Lähiliikunta ja liikuntapuistot",
       :sub-category-id 1100,
       :source-id "lipas-92374b0f-7d3c-4017-858e-666ee3ca2761-1100",
       :summary {:fi "aefaefaeefa"},
       :description {:fi "aefaefaeefa"},
       :id "3762f489-fb69-462a-8199-098f777098cc"}
      {:sub-category "Maauimalat ja uimarannat",
       :sub-category-id 3200,
       :source-id "lipas-92374b0f-7d3c-4017-858e-666ee3ca2761-3200",
       :summary {:fi "aefaefaeefa"},
       :description {:fi "aefaefaeefa"},
       :id "69e61977-c2b0-4c6f-b7ef-c2d1693c5898"}
      {:sub-category "Kuntoilukeskukset ja liikuntasalit",
       :sub-category-id 2100,
       :source-id "lipas-92374b0f-7d3c-4017-858e-666ee3ca2761-2100",
       :summary {:fi "aefaefaeefa"},
       :description {:fi "aefaefaeefa"},
       :id "b4b0da58-0f07-4a60-a8ee-c87d8c1327f5"}
      {:sub-category "Jäähallit",
       :sub-category-id 2500,
       :source-id "lipas-92374b0f-7d3c-4017-858e-666ee3ca2761-2500",
       :summary {:fi "aefaefaeefa"},
       :description {:fi "aefaefaeefa"},
       :id "a7430df4-7c78-4a94-8e4a-6ad008aacbff"}
      {:sub-category "Pallokentät",
       :sub-category-id 1300,
       :source-id "lipas-92374b0f-7d3c-4017-858e-666ee3ca2761-1300",
       :summary {:fi "aefaefaeefa"},
       :description {:fi "aefaefaeefa"},
       :id "33cab6fc-9410-4860-be45-6655202beb5a"}
      {:sub-category "Jääurheilualueet ja luonnonjäät",
       :sub-category-id 1500,
       :source-id "lipas-92374b0f-7d3c-4017-858e-666ee3ca2761-1500",
       :summary {:fi "aefaefaeefa"},
       :description {:fi "aefaefaeefa"},
       :id "931b5abb-3a81-4279-8437-77e81238a3da"})
     (map (fn [m] (-> m
                      (assoc :summary {:fi "trolololo" :se "trolololo" :en "trolololo"})
                      (assoc :description {:fi "trolololo" :se "trolololo" :en "trolololo"})
                      (update :source-id str "-lol-2")
                      (merge {:org-id "92374b0f-7d3c-4017-858e-666ee3ca2761",
                              :city-codes [889],
                              :owners ["city" "city-main-owner"]}))))))

  (doseq [m sss]
    (core/upsert-ptv-service! m))

  (def resp *1)

  )

;; Token eyJhbGciOiJIUzUxMiJ9.eyJpZCI6IjQ3ZmQ0MTI2LWQ5MjMtNDdmYi1hZmUzLWNlOWU3YzI1N2QxZiIsImVtYWlsIjoiYWRtaW5AbGlwYXMuZmkiLCJ1c2VybmFtZSI6ImFkbWluIiwicGVybWlzc2lvbnMiOnsiYWRtaW4_Ijp0cnVlLCJkcmFmdD8iOnRydWV9LCJleHAiOjE3MjA3OTI5OTJ9.6d4ArmLA9XUzO8tQB85tDhW-kYwpoH0whvrceylS0mzXGNDLzYF424yrO7kuiGbrYxEWcwI-XrgItG4nfGv0vQ


(comment
  (require '[lipas.maintenance :as maint])

  (def robot (core/get-user (:db @current-system) "robot@lipas.fi"))
  (maint/merge-types (:db @current-system) (:search @current-system) robot 104 103)
  (maint/merge-types (:db @current-system) (:search @current-system) robot 102 101)
  (maint/merge-types (:db @current-system) (:search @current-system) robot 108 106)


  (require '[lipas.backend.gis :as gis])

  (def test-point2
    {:type "FeatureCollection",
     :features
     [{:type "Feature",
       :geometry
       {:type        "Point",
        :coordinates [19.720539797408946,
                      65.62057217751676]}}]})

  (require '[cheshire.core :as json])

  (println (json/encode (maint/point->area test-point2)))

  ;; 1620 golfkenttä (piste) -> 1650 golfkenttä (alue)
  (maint/duplicate-point->area-draft (:db @current-system) (:search @current-system) robot 1620 1650)

  )
