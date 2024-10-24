(ns lipas.ai.core
  (:require [cheshire.core :as json]
            [clj-http.client :as client]
            [clojure.walk :as walk]
            [lipas.backend.config :as config]
            [taoensso.timbre :as log]))

(def openai-config
  (get-in config/default-config [:open-ai]))

(def default-headers
  {:Authorization (str "Bearer " (:api-key openai-config))
   :Content-Type  "application/json"})

(def ptv-system-instruction
  "Olet avustaja, joka auttaa käyttäjiä tuottamaan sisältöä
  Palvelutietovarantoon. Tuotat JSON-muotoista sisältöä. Sinulle
  esitetään kysymyksiä, ja käytät ensisijaisesti lähdeaineistoa ja
  toissijaisesti omaa tietoasi antaaksesi vastauksia. Noudata
  vastuksissa seuraavia tyyliohjeita:

  - Vastauksissa tulee käyttää neutraalia asiatyyliä
  - Tekstit eivät saa olla mainosmaisia
  - Tekstit eivät ole markkinointiviestintää
  - Puhuttele ja kohdista tekstin sisältö sen käyttäjälle sinä-muodossa.
  - Kuvaa asiakkaalle tarjottavaa palvelua, älä palvelua järjestävää organisaatiota tai sen tehtäviä.
  - Vältä mainosmaisia ilmauksia. Keskity kuvaamaan palvelua ja sen käyttöä.
  - Käytä yksinkertaisia ilmaisuja ja tuttuja sanoja.
  - Selitä lyhenteet ja vaikeat termit, jos niitä on pakko käyttää.
  - Vältä ympäripyöreyttä ja epäinformatiivisia lauseita.
  - Muodosta täydellisiä lauseita ja käytä sekä pää- että sivulauseita. Vältä kiemuraisia lauseenvastikkeita ja korvaa ne sivulauseilla.
  - Esitä tärkein asia tekstin alussa ensimmäisessä kappaleessa. Käy suoraan asiaan, taustoita lopussa.
  - Mieti, mitä tietoja lukija tarvitsee saadakseen kokonaiskuvan palvelusta ja tarjolla olevista asiointikanavista sekä päästäkseen käyttämään palvelua.
  - Esitä yhdessä kappaleessa vain yksi asia.
  - Tee tekstiin kappalejakoja.
  - Kappaleessa on korkeintaan neljä virkettä.

Annat vastaukset englanniksi, suomeksi ja ruotsiksi. Eri kieliversiot
  voivat poiketa kieliasultaan toisistaan. Tärkeää on, että kieliasu
  on luettavaa ja selkeää.

Vastauksesi sisältö on koodattu
  JSON-objekteiksi. JSON-sisällön tarkka muoto voidaan antaa
  kehotteessa. Anna käännetyt versiot kaikista pyydetyistä tiedoista
  avaimilla \"fi\" suomeksi, \"se\" ruotsiksi ja \"en\"
  englanniksi. Jos teksti sisältää lainauksia, käytä pakomerkkiä \\
  ennen lainausmerkkiä, jotta JSON-rakenne pysyy eheänä.")

(def ptv-system-instruction-v2
  "You are an assistant who helps users produce content for the Service Information Repository (Palvelutietovaranto). You generate content in JSON format. You will be asked questions and should primarily use source material and secondarily your own knowledge to provide answers. Follow these style guidelines in your responses:
        •	Use a neutral tone in your responses.
        •	Avoid promotional language.
        •	The texts are not marketing communications.
        •	Address and target the content to the user in the \"you\" form.
        •	Describe the service offered to the customer, not the organizing entity or its tasks.
        •	Avoid promotional expressions. Focus on describing the service and its usage.
        •	Use simple expressions and familiar words.
        •	Explain abbreviations and difficult terms if they must be used.
        •	Avoid vagueness and uninformative sentences.
        •	Form complete sentences and use both main and subordinate clauses. Avoid convoluted participial constructions and replace them with subordinate clauses.
        •	Present the most important information at the beginning of the text in the first paragraph. Get straight to the point and provide background information at the end.
        •	Consider what information the reader needs to get a comprehensive view of the service and available service channels and to start using the service.
        •	Present only one topic per paragraph.
        •	Divide the text into paragraphs.
        •	A paragraph should contain a maximum of four sentences.
Provide answers in English, Finnish, and Swedish. Different language versions can differ in their phrasing. It is important that the language is readable and clear.
  Your responses are encoded as JSON objects. The exact format of the JSON content can be specified in the prompt. Provide translated versions of all requested information with the keys \"fi\" for Finnish, \"se\" for Swedish, and \"en\" for English. If the text contains quotes, use an escape character \\ before the quotation mark to maintain the integrity of the JSON structure.")

(comment
  (println ptv-system-instruction-v2))

(defn complete
  [{:keys [completions-url model n #_temperature top-p presence-penalty message-format max-tokens]
    :or   {message-format   {:type "json_object"}
           n                1
           #_#_temperature  0
           top-p            0.5
           presence-penalty -2
           max-tokens       4096}}
   system-instruction
   prompt]
  (let [body   {:model            model
                :n                n
                :max_tokens       max-tokens
                #_#_:temperature  temperature
                :top_p            top-p
                :presence_penalty presence-penalty
                :response_format  message-format
                :messages         [{:role "system" :content system-instruction}
                                   {:role "user" :content prompt}]}
        params {:headers default-headers
                :body    (json/encode body)}]

    (log/info prompt)

    (-> (client/post completions-url params)
        :body
        (json/decode keyword)
        :choices
        first
        (update-in [:message :content] #(json/decode % keyword)))))

(def generate-utp-descriptions-prompt
  "Laadi tämän viestin lopussa olevan JSON-rakenteen kuvaamasta
   liikuntapaikasta tiivistelmä (max 150 merkkiä) ja tekstikuvaus, jotka sopivat
   Palvelutietovarannossa palvelupaikan kuvaukseen. Yksityiskohtaiset
   rakennustekniset tiedot ja olosuhdetiedot jätetään kuvauksista
   pois. Haluan vastauksen muodossa {\"description\":
   {...käännökset...}, \"summary\" {...käännökset...}}. %s")

(defn ->prompt-doc
  [sports-site]
  (walk/postwalk #(if (map? %)
                    (dissoc % :geoms :geometries :simple-geoms :images :videos :id :fids :event-date)
                    %)
                 sports-site))

(defn generate-ptv-descriptions
  [sports-site]
  (let [prompt-doc (->prompt-doc sports-site)]
    (complete openai-config
              ptv-system-instruction-v2
              (format generate-utp-descriptions-prompt (json/encode prompt-doc)))))

(def generate-utp-service-descriptions-prompt
  "Laadi tämän viestin lopussa olevan JSON-rakenteen kuvaamasta
   liikuntapaikasta tiivistelmä (max 150 merkkiä) ja pidempi
   tekstikuvaus, jotka sopivat Palvelutietovarannossa palvelun
   kuvaukseen. Haluan vastauksen muodossa {\"description\":
   {...käännökset...}, \"summary\" {...käännökset...}}. %s")

(defn generate-ptv-service-descriptions
  [doc]
  (let [prompt-doc doc]
    (complete openai-config
              ptv-system-instruction-v2
              (format generate-utp-service-descriptions-prompt (json/encode prompt-doc)))))

(defn get-models
  [{:keys [_api-key models-url]}]
  (let [params {:headers default-headers}]
    (-> (client/get models-url params)
        :body
        (json/decode keyword))))

(comment
  (get-models openai-config)
  (complete openai-config ptv-system-instruction "Why volcanoes erupt?")
  )
