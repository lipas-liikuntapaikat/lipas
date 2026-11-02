(ns lipas.backend.ptv.test-env-reset-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [lipas.backend.core :as core]
            [lipas.backend.db.ptv-service :as ptv-service-db]
            [lipas.backend.db.ptv-service-audit :as ptv-service-audit-db]
            [lipas.backend.db.ptv-site-audit :as ptv-site-audit-db]
            [lipas.backend.org :as backend-org]
            [lipas.backend.ptv.integration :as ptvi]
            [lipas.backend.ptv.test-env-reset :as reset]
            [lipas.backend.search :as search]
            [lipas.test-utils :as tu]
            [lipas.utils :as utils]))

(defonce test-system (atom nil))

(let [{:keys [once each]} (tu/full-system-fixture test-system)]
  (use-fixtures :once once)
  (use-fixtures :each each))

(defn- test-db [] (:lipas/db @test-system))
(defn- test-search [] (:lipas/search @test-system))

(def ^:private test-env-ptv {:api-url "https://api.palvelutietovaranto.trn.suomi.fi/api"})
(def ^:private ptv-org "8f3c1d2e-0000-4000-8000-000000000001")
(def ^:private other-ptv-org "8f3c1d2e-0000-4000-8000-000000000002")

;;; In-memory PTV ;;;

(defn- channel [id source-id & {:keys [type draft?] :or {type "ServiceLocation"}}]
  {:id id
   :sourceId source-id
   :serviceChannelType type
   :organizationId ptv-org
   :publishingStatus "Published"
   :draft? draft?
   :serviceChannelNames [{:language "fi" :value id}]})

(defn- service [id source-id & {:keys [responsible] :or {responsible ptv-org}}]
  {:id id
   :sourceId source-id
   :publishingStatus "Published"
   :organizations [{:roleType "Responsible" :organization {:id responsible}}]
   :ontologyTerms [{:uri "onto"}]
   :serviceClasses [{:uri "class"}]
   :targetGroups [{:uri "target"}]
   :serviceNames [{:language "fi" :value id}]})

(defn- fake-ptv-http
  "Stands in for ptvi/http: serves the org list endpoints from `state` and
  applies ServiceLocation/Service PUTs the way PTV does — a draft blocks the
  write, and a sourceId sent together with the archive is dropped."
  [state]
  (fn [_ptv _auth-org-id {:keys [method url form-params]}]
    (let [path (str/replace url #"^.*/api" "")
          published (fn [k]
                      {:pageCount 1
                       :itemList (filterv #(= "Published" (:publishingStatus %))
                                          (vals (get @state k)))})]
      (case path
        "/v11/ServiceChannel/list/organization" {:body (published :channels)}
        "/v11/Service/list/organization" {:body (published :services)}
        (let [[_ kind id] (re-matches #"/v11/(ServiceChannel/ServiceLocation|Service)/(.+)" path)
              k (if (= "Service" kind) :services :channels)]
          (assert (= :put method) (str "unexpected request " method " " path))
          (swap! state update :puts conj [path form-params])
          (when (get-in @state [k id :draft?])
            (throw (ex-info "HTTP Error: 400 Cannot update entity with status Modified"
                            {:resp {:status 400}})))
          (swap! state update-in [k id]
                 (fn [entity]
                   (if (= "Deleted" (:publishingStatus form-params))
                     (assoc entity :publishingStatus "Deleted")
                     (cond-> entity
                       (:sourceId form-params) (assoc :sourceId (:sourceId form-params))))))
          {:body (get-in @state [k id])})))))

(defn- ptv-state []
  (atom {:puts []
         :channels (utils/index-by :id
                                   [(channel "c-lipas" "lipas-org-100-ts")
                                    (channel "c-draft" "lipas-org-101-ts" :draft? true)
                                    (channel "c-native" nil)
                                    (channel "c-echannel" nil :type "EChannel")])
         :services (utils/index-by :id
                                   [(service "s-lipas" "lipas-org-1100")
                                    (service "s-adopted" "lipas-org-ptv-s-adopted")
                                    (service "s-half-done" "lipas-org-1200-tombstone-1")
                                    (service "s-native" nil)
                                    (service "s-produced" "lipas-other-1300" :responsible other-ptv-org)])}))

;;; LIPAS seed ;;;

(defn- create-org! [ptv-org-id]
  (let [org {:id (java.util.UUID/randomUUID)
             :name (str "Org " ptv-org-id)
             :ptv-data {:org-id ptv-org-id :city-codes [91]}}]
    (backend-org/create-org (test-db) org)
    org))

(defn- seed-site! [user lipas-id city-code ptv]
  (let [site (cond-> (tu/make-point-site lipas-id :city-code city-code)
               ptv (assoc :ptv ptv))
        resp (core/upsert-sports-site!* (test-db) user site)]
    (core/index! (test-search) resp :sync)
    (ptv-site-audit-db/insert-audit! (test-db) {:lipas-id lipas-id
                                                :event-date (utils/timestamp)
                                                :document {:summary {:status "approved"}}})))

(defn- seed-service! [user org-id source-id]
  (let [rev (ptv-service-db/insert-service-rev! (test-db) {:org-id org-id
                                                           :source-id source-id
                                                           :author-id (:id user)
                                                           :event-date (utils/timestamp)
                                                           :document {:source-id source-id}})]
    (ptv-service-audit-db/insert-audit! (test-db) {:org-id org-id
                                                   :source-id source-id
                                                   :service-revision-id (:id rev)
                                                   :event-date (utils/timestamp)
                                                   :document {:summary {:status "approved"}}})))

(defn- indexed-ptv [lipas-id]
  (let [{:keys [client indices]} (test-search)]
    (-> (search/fetch-document client (get-in indices [:sports-site :search]) lipas-id)
        :body :_source :ptv)))

;;; Tests ;;;

(deftest reset-org-test
  (let [user (tu/gen-user {:db? true :db-component (test-db)})
        org (create-org! ptv-org)
        other-org (create-org! other-ptv-org)
        state (ptv-state)
        ctx {:db (test-db) :search (test-search) :ptv test-env-ptv :user user}]
    ;; 900001 is integrated; 900002 belongs to the other org's integration;
    ;; 900003 lost its :ptv earlier but kept its audit; 900004 is elsewhere.
    (seed-site! user 900001 91 {:org-id ptv-org :source-id "lipas-org-100-ts"})
    (seed-site! user 900002 91 {:org-id other-ptv-org :source-id "lipas-other-200-ts"})
    (seed-site! user 900003 91 nil)
    (seed-site! user 900004 92 nil)
    ;; a re-audit: the counts are of rows, not sites
    (ptv-site-audit-db/insert-audit! (test-db) {:lipas-id 900001
                                                :event-date (utils/timestamp)
                                                :document {:summary {:status "changes-requested"}}})
    (seed-service! user (:id org) "lipas-org-1100")
    (seed-service! user (:id org) "lipas-org-ptv-s-adopted")
    (seed-service! user (:id other-org) "lipas-other-1300")

    (with-redefs [ptvi/http (fake-ptv-http state)]
      (testing "the plan covers only what carries the org's LIPAS mark"
        (is (= {:ptv-org-id ptv-org
                :lipas-org (select-keys org [:id :name])
                :ptv {:archive {:service-locations [{:id "c-lipas" :source-id "lipas-org-100-ts" :name "c-lipas"}
                                                    {:id "c-draft" :source-id "lipas-org-101-ts" :name "c-draft"}]
                                :services [{:id "s-lipas" :source-id "lipas-org-1100" :name "s-lipas"}
                                           {:id "s-adopted" :source-id "lipas-org-ptv-s-adopted" :name "s-adopted"}
                                           {:id "s-half-done" :source-id "lipas-org-1200-tombstone-1" :name "s-half-done"}]}
                      :untouched {:channels 2 :services 2}}
                :lipas {:remove-ptv-from-sites [900001]
                        :delete {:ptv-site-audits 3 :ptv-service-audits 2 :ptv-services 2}}}
               (reset/reset-plan ctx ptv-org)))
        (is (empty? (:puts @state)) "planning writes nothing"))

      (let [result (reset/reset-org! ctx ptv-org)]
        (testing "a draft blocks its entity without stopping the rest"
          (is (= {:ptv-org-id ptv-org
                  :ptv {:service-locations {:archived 1
                                            :skipped-modified [{:id "c-draft" :source-id "lipas-org-101-ts" :name "c-draft"}]
                                            :failed []}
                        :services {:archived 3 :skipped-modified [] :failed []}}
                  :lipas {:ptv-site-audits 3 :ptv-service-audits 2 :ptv-services 2 :sites 1}}
                 result)))

        (testing "PTV: LIPAS-marked entities are archived, services under a tombstone sourceId"
          (let [{:keys [channels services]} @state]
            (is (= "Deleted" (get-in channels ["c-lipas" :publishingStatus])))
            (is (= "Published" (get-in channels ["c-draft" :publishingStatus])))
            (doseq [id ["s-lipas" "s-adopted" "s-half-done"]]
              (is (= "Deleted" (get-in services [id :publishingStatus])) id))
            (is (re-matches #"lipas-org-1100-tombstone-\d+" (get-in services ["s-lipas" :sourceId])))
            (is (re-matches #"lipas-org-ptv-s-adopted-tombstone-\d+" (get-in services ["s-adopted" :sourceId])))
            (is (= "lipas-org-1200-tombstone-1" (get-in services ["s-half-done" :sourceId]))
                "an existing tombstone isn't renamed again")
            (is (= #{"Published"}
                   (set (map :publishingStatus [(channels "c-native") (channels "c-echannel")
                                                (services "s-native") (services "s-produced")])))
                "entities without the org's LIPAS mark are left alone")
            (is (= "lipas-other-1300" (get-in services ["s-produced" :sourceId])))))

        (testing "LIPAS: the org's sites lose :ptv in the DB and the index, and its rows are gone"
          (is (nil? (:ptv (core/get-sports-site (test-db) 900001))))
          (is (nil? (indexed-ptv 900001)))
          (is (= other-ptv-org (get-in (core/get-sports-site (test-db) 900002) [:ptv :org-id])))
          (is (= other-ptv-org (:org-id (indexed-ptv 900002))))
          (is (= #{900002 900004}
                 (set (keys (ptv-site-audit-db/get-current-by-lipas-ids (test-db) [900001 900002 900003 900004])))))
          (is (empty? (ptv-service-db/get-current-by-org (test-db) (:id org))))
          (is (empty? (ptv-service-audit-db/get-current-by-org (test-db) (:id org))))
          (is (= ["lipas-other-1300"] (map :source-id (ptv-service-db/get-current-by-org (test-db) (:id other-org)))))
          (is (= 1 (count (ptv-service-audit-db/get-current-by-org (test-db) (:id other-org)))))))

      (testing "a rerun after the draft is resolved finishes the job and touches nothing else"
        (swap! state update-in [:channels "c-draft"] dissoc :draft?)
        (is (= {:ptv-org-id ptv-org
                :ptv {:service-locations {:archived 1 :skipped-modified [] :failed []}
                      :services {:archived 0 :skipped-modified [] :failed []}}
                :lipas {:ptv-site-audits 0 :ptv-service-audits 0 :ptv-services 0 :sites 0}}
               (reset/reset-org! ctx ptv-org)))
        (is (= "Deleted" (get-in @state [:channels "c-draft" :publishingStatus])))))))

(deftest refuses-production-ptv-test
  (let [user (tu/gen-user {:db? true :db-component (test-db)})
        _ (create-org! ptv-org)
        state (ptv-state)
        ctx {:db (test-db) :search (test-search) :user user
             :ptv {:api-url "https://api.palvelutietovaranto.suomi.fi/api"}}]
    (with-redefs [ptvi/http (fn [& _] (swap! state update :puts conj :called) {:body {}})]
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"not the test environment"
                            (reset/reset-plan ctx ptv-org)))
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"not the test environment"
                            (reset/reset-org! ctx ptv-org)))
      (is (empty? (:puts @state)) "PTV is never called"))))
