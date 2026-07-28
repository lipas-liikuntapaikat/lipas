(ns lipas.backend.help-test
  "Pure-function tests for the help v1→v2 migration transform and the
   KB doc builder that consumes v2 trees."
  (:require [clojure.test :refer [deftest is testing]]
            [lipas.backend.help :as help]
            [lipas.backend.kb :as kb]
            [lipas.schema.help :as help-schema]
            [lipas.utils :as utils]
            [malli.core :as m]))

(def v1-fixture
  "Shape of prod v1 data: shared structure, {:fi :se :en} leaves,
   placeholder junk in untranslated locales, timestamp slugs."
  [{:slug :new-section-1745834399585
    :title {:fi "Työkaluja monialaiseen liikuntasuunnitteluun"
            :se "Ny sektion"
            :en "New Section"}
    :pages [{:slug :new-page-1
             :title {:fi "Pistemäisen liikuntapaikan lisääminen ja muokkaaminen"
                     :se "Ny sida"
                     :en "New Page"}
             :blocks [{:block-id "7e97f983-5da6-410c-a080-8f652a75330b"
                       :type :pdf
                       :url "https://example.com/a.pdf"
                       :title {:fi "Ohje" :se "" :en ""}
                       :caption {:fi "" :se "" :en ""}}
                      {:block-id "67c45c1e-9e68-457d-b9d4-c07c5ba04fb5"
                       :type :video
                       :provider :youtube
                       :video-id "cDaGW3EOrsI"
                       :title {:fi "Video-ohje" :se "" :en ""}}
                      {:block-id "2437b8b8-3379-4710-89c6-fd336f0989b3"
                       :type :text
                       :content {:fi "Sisältöä." :se "" :en ""}}]}
            {:slug :welcome
             :title {:fi "Toinen sivu" :se "Välkommen" :en "Welcome"}
             :blocks [{:block-id "51a966a0-8874-4853-8ef7-644f072e534d"
                       :type :type-code-explorer}]}]}
   {:slug :general
    ;; identical fi titles → slug collision must be resolved
    :title {:fi "Työkaluja monialaiseen liikuntasuunnitteluun" :se "" :en ""}
    :pages []}])

(deftest ->slug-test
  (is (= "pistemaisen-liikuntapaikan-lisaaminen-ja-muokkaaminen"
         (utils/->slug "Pistemäisen liikuntapaikan lisääminen ja muokkaaminen")))
  (is (= "tyokaluja-ylli-hankkeen-tuloksia"
         (utils/->slug "Työkaluja (YLLI-hankkeen tuloksia)")))
  (is (= "" (utils/->slug nil)))
  (is (= "" (utils/->slug "!!!"))))

(deftest v1->v2-tree-test
  (let [tree (help/v1->v2-tree v1-fixture :fi)]
    (testing "validates against the v2 schema"
      (is (m/validate help-schema/LocaleTree tree)))

    (testing "slugs regenerated from fi titles, old slugs kept as aliases"
      (is (= "tyokaluja-monialaiseen-liikuntasuunnitteluun"
             (-> tree first :slug)))
      (is (= ["new-section-1745834399585"] (-> tree first :aliases)))
      (is (= "pistemaisen-liikuntapaikan-lisaaminen-ja-muokkaaminen"
             (-> tree first :pages first :slug))))

    (testing "slug collisions get a numeric suffix"
      (is (= "tyokaluja-monialaiseen-liikuntasuunnitteluun-2"
             (-> tree second :slug))))

    (testing "localized leaves collapse to plain strings"
      (is (= "Ohje" (-> tree first :pages first :blocks first :title)))
      (is (= "Sisältöä." (-> tree first :pages first :blocks (nth 2) :content))))

    (testing "every node gets a stable id"
      (is (every? some? (map :id tree)))
      (is (every? some? (mapcat #(map :id (:pages %)) tree))))

    (testing "se tree drops fi-only text but keeps structure decisions to editors"
      (let [se-tree (help/v1->v2-tree v1-fixture :se)]
        ;; text block with blank se content is dropped; media blocks stay
        (is (= [:pdf :video] (->> se-tree first :pages first :blocks (map :type))))))))

(deftest help-cms->docs-test
  (let [fi-tree (help/v1->v2-tree v1-fixture :fi)
        docs (kb/help-cms->docs {:fi fi-tree :se [] :en []})]
    (testing "one doc per page with real text content, fi only"
      (is (= #{"fi"} (set (map :lang docs))))
      ;; page 2 has only a type-code-explorer block → no body → skipped;
      ;; empty section produces nothing
      (is (= 1 (count docs))))

    (testing "doc carries canonical deep link"
      (let [doc (first docs)]
        (is (= "tyokaluja-monialaiseen-liikuntasuunnitteluun/pistemaisen-liikuntapaikan-lisaaminen-ja-muokkaaminen"
               (:source-ref doc)))
        (is (= (str "?ohje=" (:source-ref doc)) (:deep-link doc)))
        (is (re-find #"Video-ohje" (:body doc)))
        (is (re-find #"PDF: Ohje" (:body doc)))))

    (testing "bare media URLs never leak into languages without titles"
      (is (empty? (kb/help-cms->docs
                    {:fi [] :se (help/v1->v2-tree v1-fixture :se) :en []}))))))
