(ns lipas.backend.search-index-lifecycle-test
  "Integration tests for the index-lifecycle helpers used when re-indexing
  Elasticsearch. These run against the real (local/CI) ES cluster but need no
  database, so they build a bare client from the test config."
  (:require
    [clojure.test :refer [deftest is testing use-fixtures]]
    [lipas.backend.search :as search]
    [lipas.test-utils :as tu]))

;; Prefix used for the throwaway indices this ns creates. Deliberately unique so
;; the pattern-based sweep can never touch real indices.
(def ^:private prefix "zzsweep-test")
(def ^:private alias-name "zzsweep-current") ; NB: does not match `<prefix>-*`

(def ^:private ^:dynamic *client* nil)

(defn- mk [suffix] (str prefix "-" suffix))

(defn- create! [suffix]
  (search/create-index! *client* (mk suffix) {:mappings {:dynamic false}}))

(defn- cleanup! [client]
  ;; Deleting the indices also removes their aliases, so `alias-name` needs no
  ;; separate teardown.
  (doseq [idx (search/indices-matching client prefix)]
    (try (search/delete-index! client idx) (catch Exception _ nil))))

(defn- with-fresh-es [f]
  (binding [*client* (search/create-cli
                       (let [{:keys [hosts user pass]} (:search tu/config)]
                         {:hosts hosts :user user :password pass}))]
    (cleanup! *client*)          ; guard against leftovers from a crashed run
    (try (f)
         (finally (cleanup! *client*)))))

(use-fixtures :each with-fresh-es)

(deftest indices-matching-test
  (testing "returns #{} when no index matches the prefix"
    (is (= #{} (search/indices-matching *client* "zzsweep-does-not-exist"))))

  (testing "lists every matching index as a string, alias or not"
    (create! "cur")
    (create! "orphan-1")
    (create! "orphan-2")
    (search/swap-alias! *client* {:new-idx (mk "cur") :alias alias-name})
    (is (= #{(mk "cur") (mk "orphan-1") (mk "orphan-2")}
           (search/indices-matching *client* prefix)))))

(deftest delete-stale-indices!-test
  (testing "deletes the previous index AND orphans not on any alias, keeping keep-idx"
    (create! "cur")
    (create! "orphan-1") ; simulates an index stranded by an earlier failed run
    (create! "orphan-2")
    (search/swap-alias! *client* {:new-idx (mk "cur") :alias alias-name})

    (let [{:keys [deleted failed]} (search/delete-stale-indices! *client* prefix (mk "cur"))]
      (is (empty? failed))
      (is (= #{(mk "orphan-1") (mk "orphan-2")} (set deleted))
          "both the previous-run orphans are reclaimed")
      (is (= #{(mk "cur")} (search/indices-matching *client* prefix))
          "only the kept index remains")
      (is (= #{(mk "cur")} (set (map name (search/current-idxs *client* {:alias alias-name}))))
          "the live alias still points at the kept index"))

    (testing "and is idempotent — a second sweep finds nothing to delete"
      (let [{:keys [deleted failed]} (search/delete-stale-indices! *client* prefix (mk "cur"))]
        (is (empty? deleted))
        (is (empty? failed)))))

  (testing "is a no-op (never throws) when nothing matches the prefix"
    (let [res (search/delete-stale-indices! *client* "zzsweep-empty" (mk "cur"))]
      (is (= {:deleted [] :failed []} res)))))
