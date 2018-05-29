(ns lipas.schema.core
  (:require [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]))

(def this-year #?(:cljs (.getFullYear (js/Date.))
                  :clj  (.getYear (java.time.LocalDate/now))))

;; Sports-place

(s/def ::name (s/and string? #(<= 2 (count %))))

(def owners #{:city
              :registered-association
              :company-ltd
              :city-main-owner
              :foundation
              :state
              :other
              :unkonwn})

(s/def ::owner owners)

(def admins #{:city-sports
              :city-education
              :city-technical-services
              :city-other
              :private-association
              :private-company
              :private-foundation
              :state
              :other
              :unknown})

(s/def ::admin admins)

(s/def ::phone-number string?)
(s/def ::www string?)

;; Location

(s/def ::address string?)

(def postal-code-regex #"[0-9]{5}")
(comment (re-matches postal-code-regex "00010"))

(s/def ::postal-code (s/and string? #(re-matches postal-code-regex %)))
(s/def ::postal-office string?)

(def city-codes {"004"
                 "005"
                 "006"
                 "009"
                 "010"
                 "014"
                 "015"
                 "016"
                 "017"
                 "018"
                 "019"
                 "020"
                 "035"
                 "040"
                 "043"
                 "044"
                 "045"
                 "046"
                 "047"
                 "049"
                 "050"
                 "051"
                 "052"
                 "060"
                 "061"
                 "062"
                 "065"
                 "069"
                 "071"
                 "072"
                 "073"
                 "074"
                 "075"
                 "076"
                 "077"
                 "078"
                 "079"
                 "081"
                 "082"
                 "083"
                 "084"
                 "085"
                 "086"
                 "090"
                 "091"
                 "092"
                 "095"
                 "097"
                 "098"
                 "099"
                 "101"
                 "102"
                 "103"
                 "105"
                 "106"
                 "108"
                 "109"
                 "111"
                 "139"
                 "140"
                 "142"
                 "143"
                 "145"
                 "146"
                 "148"
                 "149"
                 "150"
                 "151"
                 "152"
                 "153"
                 "163"
                 "164"
                 "165"
                 "167"
                 "169"
                 "170"
                 "171"
                 "172"
                 "173"
                 "174"
                 "175"
                 "176"
                 "177"
                 "178"
                 "179"
                 "180"
                 "181"
                 "182"
                 "183"
                 "184"
                 "186"
                 "202"
                 "204"
                 "205"
                 "208"
                 "210"
                 "211"
                 "212"
                 "213"
                 "214"
                 "216"
                 "217"
                 "218"
                 "219"
                 "220"
                 "223"
                 "224"
                 "226"
                 "227"
                 "230"
                 "231"
                 "232"
                 "233"
                 "235"
                 "236"
                 "239"
                 "240"
                 "241"
                 "243"
                 "244"
                 "245"
                 "246"
                 "247"
                 "248"
                 "249"
                 "250"
                 "251"
                 "252"
                 "254"
                 "255"
                 "256"
                 "257"
                 "259"
                 "260"
                 "261"
                 "262"
                 "263"
                 "265"
                 "266"
                 "271"
                 "272"
                 "273"
                 "275"
                 "276"
                 "277"
                 "279"
                 "280"
                 "281"
                 "283"
                 "284"
                 "285"
                 "286"
                 "287"
                 "288"
                 "289"
                 "290"
                 "291"
                 "292"
                 "293"
                 "295"
                 "297"
                 "299"
                 "300"
                 "301"
                 "303"
                 "304"
                 "305"
                 "306"
                 "308"
                 "309"
                 "310"
                 "312"
                 "315"
                 "316"
                 "317"
                 "318"
                 "319"
                 "320"
                 "322"
                 "398"
                 "399"
                 "400"
                 "401"
                 "402"
                 "403"
                 "405"
                 "406"
                 "407"
                 "408"
                 "410"
                 "413"
                 "414"
                 "415"
                 "416"
                 "417"
                 "418"
                 "419"
                 "420"
                 "421"
                 "422"
                 "423"
                 "424"
                 "425"
                 "426"
                 "429"
                 "430"
                 "431"
                 "433"
                 "434"
                 "435"
                 "436"
                 "438"
                 "439"
                 "440"
                 "441"
                 "442"
                 "443"
                 "444"
                 "445"
                 "475"
                 "476"
                 "478"
                 "479"
                 "480"
                 "481"
                 "482"
                 "483"
                 "484"
                 "485"
                 "489"
                 "490"
                 "491"
                 "492"
                 "493"
                 "494"
                 "495"
                 "498"
                 "499"
                 "500"
                 "501"
                 "503"
                 "504"
                 "505"
                 "506"
                 "507"
                 "508"
                 "529"
                 "531"
                 "532"
                 "533"
                 "534"
                 "535"
                 "536"
                 "537"
                 "538"
                 "540"
                 "541"
                 "543"
                 "544"
                 "545"
                 "559"
                 "560"
                 "561"
                 "562"
                 "563"
                 "564"
                 "567"
                 "573"
                 "576"
                 "577"
                 "578"
                 "580"
                 "581"
                 "582"
                 "583"
                 "584"
                 "585"
                 "586"
                 "587"
                 "588"
                 "589"
                 "592"
                 "593"
                 "594"
                 "595"
                 "598"
                 "599"
                 "601"
                 "602"
                 "603"
                 "604"
                 "606"
                 "607"
                 "608"
                 "609"
                 "611"
                 "614"
                 "615"
                 "616"
                 "617"
                 "618"
                 "619"
                 "620"
                 "623"
                 "624"
                 "625"
                 "626"
                 "630"
                 "631"
                 "632"
                 "633"
                 "635"
                 "636"
                 "638"
                 "640"
                 "678"
                 "680"
                 "681"
                 "682"
                 "683"
                 "684"
                 "686"
                 "687"
                 "689"
                 "691"
                 "692"
                 "694"
                 "696"
                 "697"
                 "698"
                 "699"
                 "700"
                 "701"
                 "702"
                 "704"
                 "705"
                 "707"
                 "708"
                 "710"
                 "728"
                 "729"
                 "730"
                 "732"
                 "734"
                 "736"
                 "737"
                 "738"
                 "739"
                 "740"
                 "741"
                 "742"
                 "743"
                 "746"
                 "747"
                 "748"
                 "749"
                 "751"
                 "753"
                 "754"
                 "755"
                 "758"
                 "759"
                 "761"
                 "762"
                 "765"
                 "766"
                 "768"
                 "770"
                 "771"
                 "772"
                 "774"
                 "775"
                 "776"
                 "777"
                 "778"
                 "781"
                 "783"
                 "784"
                 "785"
                 "790"
                 "791"
                 "831"
                 "832"
                 "833"
                 "834"
                 "835"
                 "837"
                 "838"
                 "841"
                 "844"
                 "845"
                 "846"
                 "848"
                 "849"
                 "850"
                 "851"
                 "853"
                 "854"
                 "855"
                 "856"
                 "857"
                 "858"
                 "859"
                 "863"
                 "864"
                 "885"
                 "886"
                 "887"
                 "889"
                 "890"
                 "891"
                 "892"
                 "893"
                 "895"
                 "905"
                 "906"
                 "908"
                 "909"
                 "911"
                 "912"
                 "913"
                 "915"
                 "916"
                 "917"
                 "918"
                 "919"
                 "920"
                 "921"
                 "922"
                 "923"
                 "924"
                 "925"
                 "926"
                 "927"
                 "928"
                 "931"
                 "932"
                 "933"
                 "934"
                 "935"
                 "936"
                 "937"
                 "940"
                 "941"
                 "942"
                 "943"
                 "944"
                 "945"
                 "946"
                 "971"
                 "972"
                 "973"
                 "975"
                 "976"
                 "977"
                 "978"
                 "979"
                 "980"
                 "981"
                 "988"
                 "989"
                 "992"})

(s/def ::city-code city-codes)

(def type-codes {"101"
                 "102"
                 "103"
                 "104"
                 "106"
                 "107"
                 "108"
                 "109"
                 "110"
                 "111"
                 "112"
                 "201"
                 "202"
                 "203"
                 "204"
                 "205"
                 "206"
                 "207"
                 "301"
                 "302"
                 "304"
                 "1110"
                 "1120"
                 "1130"
                 "1140"
                 "1150"
                 "1160"
                 "1170"
                 "1180"
                 "1210"
                 "1220"
                 "1310"
                 "1320"
                 "1330"
                 "1340"
                 "1350"
                 "1360"
                 "1370"
                 "1380"
                 "1510"
                 "1520"
                 "1530"
                 "1540"
                 "1550"
                 "1560"
                 "1610"
                 "1620"
                 "1630"
                 "1640"
                 "2110"
                 "2120"
                 "2130"
                 "2140"
                 "2150"
                 "2210"
                 "2220"
                 "2230"
                 "2240"
                 "2250"
                 "2260"
                 "2270"
                 "2280"
                 "2290"
                 "2310"
                 "2320"
                 "2330"
                 "2340"
                 "2350"
                 "2360"
                 "2370"
                 "2380"
                 "2510"
                 "2520"
                 "2530"
                 "2610"
                 "3110"
                 "3120"
                 "3130"
                 "3210"
                 "3220"
                 "3230"
                 "3240"
                 "4110"
                 "4210"
                 "4220"
                 "4230"
                 "4240"
                 "4310"
                 "4320"
                 "4401"
                 "4402"
                 "4403"
                 "4404"
                 "4405"
                 "4411"
                 "4412"
                 "4421"
                 "4422"
                 "4430"
                 "4440"
                 "4451"
                 "4452"
                 "4510"
                 "4520"
                 "4530"
                 "4610"
                 "4620"
                 "4630"
                 "4640"
                 "4710"
                 "4720"
                 "4810"
                 "4820"
                 "4830"
                 "4840"
                 "5110"
                 "5120"
                 "5130"
                 "5140"
                 "5150"
                 "5160"
                 "5210"
                 "5310"
                 "5320"
                 "5330"
                 "5340"
                 "5350"
                 "5360"
                 "5370"
                 "6110"
                 "6120"
                 "6130"
                 "6140"
                 "6210"
                 "6220"
                 "7000"})

(s/def ::sports-place-type type-codes)

(s/def ::type-code (s/and int? pos?))

(s/def ::relevant-year (s/int-in 1800 (inc this-year)))

(defn gen-str [min max]
  (gen/fmap #(apply str %)
            (gen/vector (gen/char-alpha) (+ min (rand-int max)))))

(defn email-gen []
  "Function that returns a Generator for email addresses"
  (gen/fmap
   (fn [[name host tld]]
     (str name "@" host "." tld))
   (gen/tuple
    (gen-str 1 15)
    (gen-str 1 15)
    (gen-str 2 63))))

(def email-regex #"^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,63}$")

(comment (gen/generate (s/gen ::email)))
(comment (gen/generate (gen/vector (gen/char-alpha 10))))
(comment (gen/generate (gen-str 1 5)))
(comment (s/conform ::email-type "kissa@koira.fi"))
(s/def ::email-type (s/and string? #(re-matches email-regex %)))
(s/def ::email (s/with-gen
                 ::email-type
                 email-gen))

;;; User ;;;

(s/def ::firstname (s/and string? #(<= 1 (count %) 128)))
(s/def ::lastname (s/and string? #(<= 1 (count %) 128)))
(s/def ::username (s/and string? #(<= 1 (count %) 128)))
(s/def ::password (s/and string? #(<= 6 (count %) 128)))

(s/def ::user-data (s/keys :req-un [::firstname
                                    ::lastname]))
(s/def ::permissions map?)
(s/def ::user (s/keys :req-un [::email
                               ::username
                               ::password
                               ::user-data]
                      :opt-un [::permissions]))

;;; General ;;;

(comment (s/valid? ::construction-year 2018))
(s/def ::construction-year ::relevant-year)

(comment (s/valid? ::material :concrete))
(comment (s/valid? ::material :kebab))
(s/def ::material #{:concrete :brick :tile :steel :wood :glass})

;;; Building ;;;

(s/def ::main-designers string?)
(s/def ::total-surface-area-m2 (s/int-in 100 (inc 50000)))
(s/def ::total-volume-m3 (s/int-in 100 (inc 200000)))
(s/def ::pool-room-total-area-m2 (s/int-in 100 (inc 10000)))
(s/def ::total-water-area-m2 (s/int-in 100 (inc 10000)))
(s/def ::heat-sections boolean?)
(s/def ::main-construction-materials (s/coll-of ::material))
(s/def ::piled? boolean?)
(s/def ::supporting-structures-description string?)
(s/def ::ceiling-description string?)
(s/def ::staff-count (s/int-in 0 (inc 1000)))
(s/def ::seating-capacity (s/int-in 0 (inc 10000)))
(s/def ::heat-source #{:private-power-station :district-heating})
(s/def ::ventilation-units-count (s/int-in 0 (inc 100)))

(comment (s/valid? ::main-construction-materials [:concrete :brick]))
(comment (s/valid? ::ventilation-units-count 100))

(s/def ::building (s/keys :opt-un [::construction-year
                                   ::main-designers
                                   ::total-surface-area-m2
                                   ::total-volume-m3
                                   ::pool-room-total-area-m2
                                   ::total-water-area-m2
                                   ::heat-sections?
                                   ::main-construction-materials
                                   ::piled?
                                   ::supporting-structures-description
                                   ::ceiling-description
                                   ::staff-count
                                   ::seating-capacity
                                   ::heat-source
                                   ::ventilation-units-count]))

(comment (s/valid? ::building {:construction-year 1995
                               :main-designer "Tipokatti"}))

;;; Renovations ;;;

(s/def ::year ::relevant-year)
(s/def ::comment string?)

(s/def ::renovation (s/keys :req-un [::year]
                            :opt-un [::comment
                                     ::main-designers]))

;;; Water treatment ;;;

(s/def ::ozonation boolean?)
(s/def ::uv-treatment boolean?)
(s/def ::activated-carbon boolean?)

(s/def ::filtering-method #{:pressure-suction      ; Paineimu
                            :pressure-sand         ; Painehiekka
                            :suction-sand          ; Imuhiekka ?
                            :open-sand             ; Avohiekka
                            :other                 ; Muu
                            :multi-layer-filtering ; Monikerrossuodatus
                            :coal                  ; Hiili ?
                            :precipitation         ; Saostus ?
                            :activated-carbon      ; aktiivihiili
                            })

;;; Pools ;;;

(s/def ::pool-type #{:main-pool         ; Pääallas
                     :diving-pool       ; Hyppyallas
                     :multipurpose-pool ; Monitoimiallas
                     :teaching-pool     ; Opetusallas
                     :paddling-pool     ; Kahluuallas
                     :childrens-pool    ; Lastenallas
                     :cold-pool         ; Kylmäallas
                     :whirlpool-bath    ; Poreallas
                     :therapy-pool      ; Terapia-allas
                     })

;;; Ice Rinks ;;;

(s/def ::ice-rink-category #{:training
                             :competition
                             :large})
