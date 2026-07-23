(ns lipas.kb-eval
  "REPL-driven retrieval eval for the AI assistant knowledge base.

   Golden questions are phrased the way real users ask (colloquial,
   cross-lingual, misspelled) and each maps to the source-ref(s) that a
   correct retrieval must surface. Run before shipping corpus, mapping
   or embedding-model changes:

     (require '[lipas.kb-eval :as kb-eval])
     (kb-eval/run-eval (user/search))                 ; hybrid
     (kb-eval/run-eval (user/search) :method :bm25)   ; lexical only
     (kb-eval/run-eval (user/search) :method :knn)    ; semantic only
     (kb-eval/compare-methods (user/search))"
  (:require [lipas.backend.kb :as kb]))

(def golden
  "Each entry: :q question, :lang UI language, :expect set of acceptable
   source-refs — hit means any of them appears in top-3."
  [;; — Colloquial type-code lookups (the classic lipasinfo question) —
   {:q "mikä tyyppikoodi padel-kentälle?" :expect #{"type:1390" "type:2295"}}
   {:q "frisbeegolfrata" :expect #{"type:1180"}}
   {:q "mihin tyyppiin skeittiparkki merkitään?" :expect #{"type:1150" "type:2250"}}
   {:q "pulkkamäki" :expect #{"type:1190"}}
   {:q "hiihtolatu" :expect #{"type:4402"}}
   {:q "uimaranta" :expect #{"type:3220" "type:3230"}}
   {:q "kuntosali" :expect #{"type:2120"}}
   {:q "parkour-paikka" :expect #{"type:1140" "type:2380"}}
   {:q "luontopolku" :expect #{"type:4404"}}
   {:q "beach volley kenttä" :expect #{"type:1330"}}
   {:q "golfkenttä" :expect #{"type:1650"}}
   {:q "velodromi" :expect #{"type:1170"}}
   {:q "yleisurheilukenttä" :expect #{"type:1220"}}
   {:q "keilahalli" :expect #{"type:2610"}}
   {:q "biljardisali" :expect #{"type:2620"}}
   {:q "laavu" :expect #{"type:301"}}
   {:q "ratsastuskenttä" :expect #{"type:6110"}}
   {:q "maauimala" :expect #{"type:3210"}}
   {:q "jäähalli" :expect #{"type:2510" "type:2520"}}
   {:q "tenniskenttä ulkona" :expect #{"type:1370"}}

   ;; — Semantic / no lexical overlap with the official name —
   {:q "avantouintipaikka" :expect #{"type:3240"}}
   {:q "boulderointi sisätiloissa" :expect #{"type:2370"}}
   {:q "petankkikenttä sisällä" :expect #{"type:2290"}}
   {:q "koirapuisto agilityyn" :expect #{"type:6210" "type:6220"}}
   {:q "motocross-rata" :expect #{"type:5320" "type:5310"}}
   {:q "pump track pyöräilyyn" :expect #{"type:1160"}}
   {:q "missä tyypissä on kota tai kammi?" :expect #{"type:301"}}
   {:q "paikka missä voi heittää koreja" :expect #{"type:1310"}}

   ;; — Data-model field definitions —
   {:q "mitä koululiikuntapaikka tarkoittaa?" :expect #{"prop:school-use?"}}
   {:q "kentän valaistus tieto" :expect #{"prop:ligthing?" "prop:lighting-info"}}
   {:q "mihin merkitään pintamateriaali?" :expect #{"prop:surface-material"}}
   {:q "katsomon kapasiteetti" :expect #{"prop:stand-capacity-person"}}
   {:q "reitin pituus kilometreinä" :expect #{"prop:route-length-km"}}

   ;; — Help-CMS task pages —
   ;; CMS refs depend on which help content is loaded: dev-seed slugs
   ;; (tallentajille/*) and migrated prod slugs are both listed as
   ;; alternates so the eval works against either corpus.
   {:q "miten lisään reitin?"
    :expect #{"tallentajille/reitin-lisaaminen"
              "liikunta-ja-ulkoilupaikan-tietojen-lisaaminen-ja-muokkaaminen/reittimaisen-liikuntapaikan-lisaaminen-ja-muokkaaminen"
              "lipas-reitin-lisaaminen-2023"
              "XcZrIepjYe0"}}
   {:q "kuinka tuon gpx-tiedoston kartalle?"
    :expect #{"tallentajille/reitin-lisaaminen"
              "DUa_aMbg9k0"}}
   {:q "miten lisään uuden liikuntapaikan?"
    :expect #{"tallentajille/pisteen-lisaaminen"
              "lipas-liikuntapaikan-lisaaminen-ja-muokkaaminen-2023"
              "cDaGW3EOrsI"}}
   {:q "saavutettavuusanalyysin tekeminen"
    :expect #{"tyokalut/saavutettavuustyokalu"
              "lipas-saavutettavuustyokalu-2023"
              "UiKjGeS4Yyg" "XoGxJfolAPY" "FADGfqvjyY4"}}

   ;; — Cross-lingual: question language ≠ document language —
   {:q "var lägger jag till en padelbana?" :lang "se" :expect #{"type:1390" "type:2295"}}
   {:q "how do I add a route?" :lang "en"
    :expect #{"tallentajille/reitin-lisaaminen"
              "liikunta-ja-ulkoilupaikan-tietojen-lisaaminen-ja-muokkaaminen/reittimaisen-liikuntapaikan-lisaaminen-ja-muokkaaminen"
              "lipas-reitin-lisaaminen-2023"}}
   {:q "swimming hall type code" :lang "en" :expect #{"type:3110"}}
   {:q "skidspår" :lang "se" :expect #{"type:4402"}}])

(defn run-eval
  "Hit@3 rate over the golden set. Returns the rate, per-method, plus the
   misses with what was retrieved instead."
  [search & {:keys [method limit] :or {method :hybrid limit 3}}]
  (let [results (mapv (fn [{:keys [q lang expect]}]
                        (let [hits (kb/search-kb search {:query q
                                                         :lang (or lang "fi")
                                                         :limit limit
                                                         :method method})
                              refs (mapv :source-ref hits)]
                          {:q q
                           :hit? (boolean (some expect refs))
                           :got refs
                           :expect expect}))
                      golden)
        hits (count (filter :hit? results))]
    {:method method
     :hit-at-3 (format "%d/%d (%.0f%%)" hits (count results)
                       (* 100.0 (/ hits (count results))))
     :misses (->> results (remove :hit?) (mapv #(dissoc % :hit?)))}))

(defn compare-methods
  [search]
  (into {}
        (for [m [:bm25 :knn :hybrid]]
          [m (:hit-at-3 (run-eval search :method m))])))
