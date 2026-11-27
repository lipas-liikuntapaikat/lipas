(ns lipas.ui.ptv.review-fetch-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [lipas.ui.ptv.review-fetch :as sut]))

(def ^:private lipas-id 600121)

(deftest edit-fetch-test
  (testing "Save waits for an edit's fetch until it lands"
    (let [db (sut/start {} lipas-id :a true)]
      (is (true? (sut/save-waits? db lipas-id)))
      (is (true? (sut/latest? db lipas-id :a)))
      (is (false? (sut/save-waits? (sut/forget db lipas-id) lipas-id)))))

  (testing "a fetch for viewing the site never blocks Save"
    (is (false? (sut/save-waits? (sut/start {} lipas-id :a false) lipas-id))))

  (testing "nothing fetched, nothing to wait for"
    (is (false? (sut/save-waits? {} lipas-id))))

  (testing "an earlier edit's late response doesn't land in the next edit"
    (let [db (-> {}
                 (sut/start lipas-id :first-edit true)
                 ;; cancelled, then edited again
                 (sut/forget lipas-id)
                 (sut/start lipas-id :second-edit true))]
      (is (false? (sut/latest? db lipas-id :first-edit)))
      (is (true? (sut/latest? db lipas-id :second-edit)))))

  (testing "the view's fetch still on its way when the edit starts is stale"
    (let [db (-> {}
                 (sut/start lipas-id :view false)
                 (sut/forget lipas-id)
                 (sut/start lipas-id :edit true))]
      (is (false? (sut/latest? db lipas-id :view)))
      (is (true? (sut/save-waits? db lipas-id)))))

  (testing "after an edit ends, its response lands nowhere"
    (is (false? (-> {}
                    (sut/start lipas-id :a true)
                    (sut/forget lipas-id)
                    (sut/latest? lipas-id :a)))))

  (testing "sites are apart"
    (let [db (sut/start {} lipas-id :a true)]
      (is (false? (sut/save-waits? db 1)))
      (is (true? (sut/save-waits? (sut/forget db 1) lipas-id))))))

;; The events as they use the rules: a fetch starts (during an edit or
;; not), its response arrives in any order, an edit starts or ends (forget).

(def ^:private gen-op
  (gen/one-of [(gen/fmap (fn [edit?] [:start edit?]) gen/boolean)
               (gen/fmap (fn [n] [:respond n]) gen/nat)
               (gen/return [:forget])]))

(defn- run-ops
  "Runs `ops`, checking after each one the rules against a model: the only
   response that lands is the latest start's, once, while no edit has
   started or ended since; Save waits exactly while that start was an
   edit's."
  [ops]
  (loop [db {}
         started []
         outstanding nil ; [id edit?] of the latest start, until it lands
         [op & more] ops]
    (let [ok? (and (= (boolean (second outstanding)) (sut/save-waits? db lipas-id))
                   (every? (fn [[id]] (= (= id (first outstanding))
                                         (sut/latest? db lipas-id id)))
                           started))]
      (cond
        (not ok?) false
        (nil? op) true
        :else
        (let [[kind arg] op]
          (case kind
            :start (let [id (count started)]
                     (recur (sut/start db lipas-id id arg)
                            (conj started [id arg])
                            [id arg]
                            more))
            :respond (if (empty? started)
                       (recur db started outstanding more)
                       (let [[id] (nth started (mod arg (count started)))]
                         (if (sut/latest? db lipas-id id)
                           (recur (sut/forget db lipas-id) started nil more)
                           (recur db started outstanding more))))
            :forget (recur (sut/forget db lipas-id) started nil more)))))))

(defspec only-the-latest-fetch-lands 200
  (prop/for-all [ops (gen/vector gen-op 0 30)]
                (run-ops ops)))
