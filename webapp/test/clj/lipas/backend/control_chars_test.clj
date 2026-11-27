(ns lipas.backend.control-chars-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [lipas.backend.control-chars :as control-chars]
            [lipas.backend.jwt :as jwt]
            [lipas.test-utils :refer [->json ->transit <-json] :as tu]
            [ring.mock.request :as mock]))

(defonce test-system (atom nil))

(let [{:keys [once each]} (tu/full-system-fixture test-system)]
  (use-fixtures :once once)
  (use-fixtures :each each))

(defn test-db [] (:lipas/db @test-system))
(defn test-app [req] ((:lipas/app @test-system) req))

;;; Walker ;;;

(def forbidden-chars [\u0000 \u001b])

(def gen-clean-string
  "Any ASCII but NUL and ESC, other control characters included, and some
  non-ASCII letters."
  (gen/fmap #(apply str %)
            (gen/vector (gen/elements (concat (map char (remove #{0x00 0x1B} (range 0x80)))
                                              [\ä \ö \å \€])))))

(def gen-clean-data
  "JSON-ish nested data with only clean strings in it."
  (gen/recursive-gen (fn [inner]
                       (gen/one-of [(gen/vector inner 0 4)
                                    (gen/map gen/keyword inner {:max-elements 4})]))
                     (gen/one-of [gen-clean-string gen/small-integer gen/boolean
                                  (gen/return nil)])))

(defn- leaf-paths [x path]
  (cond
    (map? x) (mapcat (fn [[k v]] (leaf-paths v (conj path k))) x)
    (vector? x) (mapcat #(leaf-paths %2 (conj path %1)) (range) x)
    :else [path]))

(defspec clean-data-has-no-offending-paths 200
  (prop/for-all [x gen-clean-data]
                (empty? (control-chars/offending-paths x))))

(defspec a-control-char-anywhere-is-found-at-its-path 200
  (prop/for-all [[x path s] (gen/let [x (gen/such-that #(seq (leaf-paths % [])) gen-clean-data)
                                      path (gen/elements (leaf-paths x []))
                                      prefix gen-clean-string
                                      c (gen/elements forbidden-chars)
                                      suffix gen-clean-string]
                              [x path (str prefix c suffix)])]
                (= [path] (control-chars/offending-paths (if (seq path) (assoc-in x path s) s)))))

(deftest edn-and-transit-types-are-checked
  (testing "keywords, symbols and characters as values"
    (is (= [[:a]] (control-chars/offending-paths {:a (keyword "x\u0000y")})))
    (is (= [[:a]] (control-chars/offending-paths {:a (symbol "x\u0000y")})))
    (is (= [[:a 1]] (control-chars/offending-paths {:a [\b \u0000]}))))
  (testing "map keys, reported at the path of their entry"
    (is (= [[:a "k\u0000"]] (control-chars/offending-paths {:a {"k\u0000" 1}})))
    (is (= [[(keyword "k\u0000")]] (control-chars/offending-paths {(keyword "k\u0000") "ok"}))))
  (testing "set members, reported at the path of the set"
    (is (= [[:tags]] (control-chars/offending-paths {:tags #{"ok" "b\u001bd"}}))))
  (testing "reports at most 10 paths"
    (is (= 10 (count (control-chars/offending-paths (vec (repeat 50 "\u0000"))))))))

;;; Through the app ;;;

(defn- draft-site
  "A site any logged-in user may save as a draft. :images dropped so the save
  doesn't depend on the images privilege."
  [name]
  (-> (tu/gen-sports-site)
      (assoc :status "active" :name name)
      (dissoc :lipas-id :images)))

(defn- post-draft [content-type body]
  (let [token (jwt/create-token (tu/gen-regular-user :db-component (test-db)))]
    (test-app (-> (mock/request :post "/api/sports-sites?draft=true")
                  (mock/content-type content-type)
                  (mock/body body)
                  (tu/token-header token)))))

(deftest nul-in-a-sports-site-is-a-400-not-a-500
  (testing "JSON"
    (let [resp (post-draft "application/json" (->json (draft-site "Latu\u0000")))]
      (is (= 400 (:status resp)))
      (is (= {:type "control-characters" :paths [["body" "name"]]}
             (select-keys (<-json (:body resp)) [:type :paths])))))

  (testing "ESC is rejected too"
    (let [resp (post-draft "application/json" (->json (draft-site "Latu\u001b[31m")))]
      (is (= 400 (:status resp)))
      (is (= [["body" "name"]] (:paths (<-json (:body resp)))))))

  (testing "Transit, with the NUL in a map key of an open map"
    (let [site (assoc-in (draft-site "Latu") [:properties (keyword "x\u0000")] 1)
          resp (post-draft "application/transit+json" (->transit site))]
      (is (= 400 (:status resp)))))

  (testing "EDN, with the NUL as a character value"
    (let [site (assoc (draft-site "Latu") :comment-char \u0000)
          resp (post-draft "application/edn" (pr-str site))]
      (is (= 400 (:status resp)))))

  (testing "other control characters are allowed, like the U+0002 that text
            pasted from PDFs carries at hyphenation points"
    (let [resp (post-draft "application/json" (->json (draft-site "Latu\tA\nB\rLen\u0002topallo\u007f")))]
      (is (= 201 (:status resp))))))

(deftest query-and-path-params-are-checked
  (testing "query param"
    (let [resp (test-app (mock/request :get "/api/lois/category/outdoor-recreation-facilities?x=a%00b"))]
      (is (= 400 (:status resp)))
      (is (= [["query" "x"]] (:paths (<-json (:body resp)))))))
  (testing "path param"
    (let [resp (test-app (mock/request :get "/api/lois/type/fishing%00pier"))]
      (is (= 400 (:status resp)))
      (is (= "control-characters" (:type (<-json (:body resp))))))))
