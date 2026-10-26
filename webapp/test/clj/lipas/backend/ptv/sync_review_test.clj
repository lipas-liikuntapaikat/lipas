(ns lipas.backend.ptv.sync-review-test
  "The PTV sync review's backend: finding what a site's last sync sent (the
   base the review tells PTV's changes from Lipas's by), and who may fetch it.
   The site form's review is open to whoever may edit the site, because
   saving a site with PTV sync on syncs it over PTV."
  (:require [clojure.set :as set]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [lipas.backend.core :as core]
            [lipas.backend.jwt :as jwt]
            [lipas.backend.org :as backend-org]
            [lipas.backend.ptv.core :as ptv-core]
            [lipas.backend.ptv.integration :as ptv-integration]
            [lipas.test-utils :as tu]
            [lipas.utils :as utils]
            [ring.mock.request :as mock]))

(defonce test-system (atom nil))

(let [{:keys [once each]} (tu/full-system-fixture test-system)]
  (use-fixtures :once once)
  (use-fixtures :each each))

(defn- test-db [] (:lipas/db @test-system))
(defn- test-app [req] ((:lipas/app @test-system) req))

(def ^:private own-city 91)
(def ^:private other-city 179)
(def ^:private ptv-org-id "3d1759a2-e47a-4947-9a31-cab1c1e2512b")
(def ^:private channel-id "a1b2c3d4-0001-4abc-9def-000000000001")

(defn- seed-org! []
  (backend-org/create-org
    (test-db)
    {:id (random-uuid)
     :name (str "PTV Review Org " (rand-int 1000000))
     :data {}
     :ptv-data {:org-id ptv-org-id
                :city-codes [own-city]
                :owners ["city"]
                :supported-languages ["fi"]}}))

(defn- site
  [lipas-id event-date summary last-sync]
  {:lipas-id lipas-id
   :status "active"
   :event-date event-date
   :name "Oulunkylän tekojää"
   :owner "city"
   :admin "city-sports"
   :type {:type-code 2510}
   :location {:city {:city-code own-city}
              :address "Käärmetie 8"
              :postal-code "00640"
              :postal-office "Helsinki"
              :geometries {:type "FeatureCollection"
                           :features [{:type "Feature"
                                       :geometry {:type "Point"
                                                  :coordinates [24.9612 60.2289]}}]}}
   :ptv (cond-> {:org-id ptv-org-id
                 ;; what a sync stores: the location's sourceId and the type
                 ;; the services were bound for
                 :source-id "lipas-source"
                 :previous-type-code 2510
                 :sync-enabled true
                 :summary {:fi summary}
                 :description {:fi "Pidempi kuvaus"}
                 :service-ids ["svc-a"]
                 :service-channel-ids [channel-id]}
          last-sync (assoc :last-sync last-sync))})

(defn- save! [user site]
  (core/upsert-sports-site!* (test-db) user site))

(deftest sync-base-is-the-syncs-own-revision-test
  (let [admin (tu/gen-admin-user :db-component (test-db))
        lipas-id (:lipas-id (save! admin (site nil "2026-01-01T00:00:00.000Z" "Ennen" nil)))
        base #(get (ptv-core/get-ptv-sync-bases (test-db) [lipas-id]) lipas-id)]
    (testing "a site never synced has no base"
      (is (nil? (base))))

    ;; A sync writes a revision dated with its own last-sync; later edits
    ;; carry that last-sync forward.
    (save! admin (site lipas-id "2026-02-01T10:00:00.123456Z" "Synkattu" "2026-02-01T10:00:00.123456Z"))
    (save! admin (site lipas-id "2026-03-01T00:00:00.000Z" "Muokattu sen jälkeen" "2026-02-01T10:00:00.123456Z"))

    (testing "the base is what the sync sent, not a later edit"
      (is (= "Synkattu" (get-in (base) [:ptv :summary :fi])))
      (is (= "2026-02-01T10:00:00.123456Z" (get-in (base) [:ptv :last-sync]))))

    (testing "a backdated edit made after the sync doesn't pass for it"
      (save! admin (site lipas-id "2026-01-15T00:00:00.000Z" "Taannehtiva" "2026-02-01T10:00:00.123456Z"))
      (is (= "Synkattu" (get-in (base) [:ptv :summary :fi]))))

    (testing "the next sync replaces the base"
      (save! admin (site lipas-id "2026-04-01T08:00:00.000Z" "Toinen synkka" "2026-04-01T08:00:00.000Z"))
      (is (= "Toinen synkka" (get-in (base) [:ptv :summary :fi]))))

    (testing "only what a sync derives the location from is sent: the geometry by its point"
      (is (= #{} (set/difference (set (keys (base)))
                                 #{:lipas-id :name :name-localized :marketing-name :email
                                   :phone-number :www :reservations-link :location :ptv
                                   :search-meta})))
      (is (= #{} (set/difference (set (keys (:location (base))))
                                 #{:address :postal-code :postal-office :city})))
      (is (= [24.9612 60.2289] (get-in (base) [:search-meta :location :wgs84-point])))
      (is (not (contains? (:ptv (base)) :sync-enabled))))))

;;; HTTP ;;;

(defn- post* [path body token]
  (test-app (cond-> (-> (mock/request :post path)
                        (mock/content-type "application/json")
                        (mock/body (tu/->json body)))
              token (tu/token-header token))))

(defn- user-with-role [role city-code]
  (tu/gen-user {:db? true
                :db-component (test-db)
                :admin? false
                :permissions {:roles [{:role role :city-code [city-code]}]}}))

(def ^:private ptv-calls (atom []))

(defn- with-ptv-stub [f]
  (reset! ptv-calls [])
  (with-redefs [ptv-integration/get-org-service-channel
                (fn [_ptv org-id id]
                  (swap! ptv-calls conj [:channel org-id id])
                  {:id id
                   :sourceId "lipas-source"
                   :serviceChannelNames [{:type "Name" :language "fi" :value "Oulunkylän tekojää"}]
                   :serviceChannelDescriptions []
                   :services [{:service {:id "svc-b" :name "Luistelu"}}]})
                ptv-integration/get-service
                (fn [_ptv _org-id id]
                  (swap! ptv-calls conj [:service id])
                  {:id id :serviceNames [{:type "Name" :language "fi" :value "Jäähallit"}]})
                ptv-integration/get-org-ptv-config-with-fallback
                (fn [_ptv _org-id] {:supported-languages ["fi"]})]
    (f)))

(deftest fetch-sync-bases-test
  (let [_org (seed-org!)
        admin (tu/gen-admin-user :db-component (test-db))
        lipas-id (:lipas-id (save! admin (site nil "2026-02-01T10:00:00.000Z" "Synkattu" "2026-02-01T10:00:00.000Z")))
        body {:lipas-ids [lipas-id]}]
    (testing "a PTV manager gets the bases"
      (let [resp (post* "/api/actions/fetch-ptv-sync-bases" body
                        (jwt/create-token (user-with-role "ptv-manager" own-city)))]
        (is (= 200 (:status resp)))
        (is (= "Synkattu" (get-in (tu/<-json (:body resp)) [(keyword (str lipas-id)) :ptv :summary :fi])))))
    (testing "a user without PTV rights doesn't"
      (is (= 403 (:status (post* "/api/actions/fetch-ptv-sync-bases" body
                                 (jwt/create-token (user-with-role "city-manager" own-city)))))))
    (testing "nor does an anonymous caller"
      (is (= 401 (:status (post* "/api/actions/fetch-ptv-sync-bases" body nil)))))))

(deftest fetch-site-review-test
  (let [_org (seed-org!)
        admin (tu/gen-admin-user :db-component (test-db))
        lipas-id (:lipas-id (save! admin (site nil "2026-02-01T10:00:00.000Z" "Synkattu" "2026-02-01T10:00:00.000Z")))
        body {:lipas-id lipas-id}
        status (fn [user]
                 (:status (post* "/api/actions/fetch-ptv-site-review" body
                                 (some-> user jwt/create-token))))]
    (with-ptv-stub
      (fn []
        (testing "whoever may edit the site gets the review: a save syncs it"
          (let [resp (post* "/api/actions/fetch-ptv-site-review" body
                            (jwt/create-token (user-with-role "city-manager" own-city)))
                review (tu/<-json (:body resp))]
            (is (= 200 (:status resp)))
            (is (= "Synkattu" (get-in review [:base :ptv :summary :fi])))
            (is (= channel-id (get-in review [:channel :id])))
            (is (= [:svc-a] (keys (:services review)))
                "names are read for the services only Lipas connects (JSON keywordises the ids)")
            (is (= ["fi"] (:org-langs review)))))

        (testing "and the municipality's PTV manager"
          (is (= 200 (status (user-with-role "ptv-manager" own-city)))))

        (testing "nobody else"
          (reset! ptv-calls [])
          (is (= 403 (status (user-with-role "city-manager" other-city))))
          (is (= 403 (status (user-with-role "ptv-manager" other-city))))
          (is (= 401 (status nil)))
          (is (empty? @ptv-calls) "no PTV call for a refused request"))))))

(deftest sync-writes-chosen-site-data-test
  (let [_org (seed-org!)
        admin (tu/gen-admin-user :db-component (test-db))
        lipas-id (:lipas-id (save! admin (site nil "2026-02-01T10:00:00.000Z" "Synkattu" "2026-02-01T10:00:00.000Z")))
        sync! (fn [user]
                (with-redefs [ptv-integration/get-org-service-channel
                              (fn [_ _ id] {:id id :sourceId "lipas-source" :services []})
                              ptv-integration/update-service-location
                              (fn [_ id _] {:id id :sourceId "lipas-source" :publishingStatus "Published"})
                              ptv-integration/get-org-ptv-config-with-fallback
                              (fn [_ _] {:supported-languages ["fi"]})]
                  (ptv-core/upsert-ptv-service-location!
                    (test-db) (:lipas/ptv @test-system) (:lipas/search @test-system) user
                    {:lipas-id lipas-id
                     :org-id ptv-org-id
                     :ptv {:org-id ptv-org-id
                           :sync-enabled true
                           :service-channel-ids [channel-id]
                           :service-ids []
                           :summary {:fi "Synkattu"}
                           :description {:fi "Pidempi kuvaus"}}
                     :site-patch {:name "PTV:n nimi"
                                  :marketing-name "Tekojää"
                                  :phone-number "+358 40 1234567"
                                  :email "info@kunta.fi"
                                  :www nil
                                  :location {:address "Käärmetie 10" :postal-code "00650"
                                             :postal-office "Helsinki"}}})))]
    (testing "taking PTV's data into the site needs the right to edit the site"
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"needs the right to edit"
                            (sync! (user-with-role "ptv-manager" own-city))))
      (is (= "Oulunkylän tekojää" (:name (core/get-sports-site (test-db) lipas-id)))))

    (testing "with it, the sync writes the data into the site"
      (sync! admin)
      (let [saved (core/get-sports-site (test-db) lipas-id)]
        (is (= "PTV:n nimi" (:name saved)))
        (is (= "Tekojää" (:marketing-name saved)))
        (is (= "+358 40 1234567" (:phone-number saved)))
        (is (= "info@kunta.fi" (:email saved)))
        (is (= {:address "Käärmetie 10" :postal-code "00650" :postal-office "Helsinki"}
               (select-keys (:location saved) [:address :postal-code :postal-office])))
        (is (= 91 (get-in saved [:location :city :city-code])) "the rest of the location stays")))))

(defn- stub-ptv
  "PTV for an upsert: the location's GET (before and after the write) is
   `channel`; the write and connection changes are recorded in `calls`."
  [calls channel & {:keys [services]}]
  {#'ptv-integration/get-org-service-channel (fn [_ _ id] (assoc channel :id id))
   #'ptv-integration/update-service-location (fn [_ id data]
                                               (swap! calls conj [:put data])
                                               {:id id :sourceId "lipas-source" :publishingStatus "Published"})
   #'ptv-integration/update-service-connections (fn [_ _ service-id f]
                                                  (swap! calls conj [:connect service-id (f #{})]))
   #'ptv-integration/get-org-services (fn [_ _] {:itemList services})
   #'ptv-integration/get-org-ptv-config-with-fallback (fn [_ _] {:supported-languages ["fi"]})})

(defn- tool-sync!
  [user lipas-id ptv]
  (ptv-core/upsert-ptv-service-location!
    (test-db) (:lipas/ptv @test-system) (:lipas/search @test-system) user
    {:lipas-id lipas-id
     :org-id ptv-org-id
     :ptv (merge {:org-id ptv-org-id
                  :sync-enabled true
                  :service-channel-ids [channel-id]
                  :service-ids ["svc-a"]
                  :summary {:fi "Synkattu"}
                  :description {:fi "Pidempi kuvaus"}}
                 ptv)}))

(def ^:private visiting
  {:type "Location" :subType "Single"
   :streetAddress {:street [{:value "Käärmetie 8" :language "fi"}] :postalCode "00640"
                   :municipality {:code "091"}}})

(deftest sync-keeps-what-lipas-has-no-field-for-test
  (let [_org (seed-org!)
        admin (tu/gen-admin-user :db-component (test-db))
        lipas-id (:lipas-id (save! admin (site nil "2026-02-01T10:00:00.000Z" "Synkattu" "2026-02-01T10:00:00.000Z")))
        calls (atom [])
        po-box {:type "Postal" :subType "PostOfficeBox"
                :postOfficeBoxAddress {:postOfficeBox [{:value "PL 5" :language "fi"}
                                                       {:value "PB 5" :language "sv"}]
                                       :postalCode "00641" :municipality {:code "091"}}}
        unsendable {:type "Postal" :subType "Street"
                    :streetAddress {:street [{:value "Liikekuja 3" :language "fi"}]
                                    :streetNumber "" :postalCode "00640"}}
        channel {:sourceId "lipas-source" :services [{:service {:id "svc-a"}}]
                 :modified "2026-02-03T08:00:00.123456"
                 :addresses [visiting po-box unsendable]}
        {:keys [ptv]} (with-redefs-fn (stub-ptv calls channel)
                        #(tool-sync! admin lipas-id {}))
        sent (some (fn [[k data]] (when (= :put k) data)) @calls)]
    (testing "PTV's postal address goes back, kept to the location's languages"
      (is (= [{:type "Postal" :subType "PostOfficeBox" :country "FI"
               :postOfficeBoxAddress {:postOfficeBox [{:value "PL 5" :language "fi"}]
                                      :postalCode "00641" :municipality "091"}}]
             (filterv #(= "Postal" (:type %)) (:addresses sent)))))
    (testing "one PTV's API would reject isn't sent: it would fail the write"
      (is (not-any? #(= "Liikekuja 3" (-> % :streetAddress :street first :value)) (:addresses sent))))
    (testing "the sync stores PTV's :modified of its write, the evidence for the next review"
      (is (= "2026-02-03T08:00:00.123456" (:ptv-modified ptv)))
      (is (= "2026-02-03T08:00:00.123456"
             (get-in (core/get-sports-site (test-db) lipas-id) [:ptv :ptv-modified]))))
    (testing "a user's save keeps it: it is the sync's own record"
      (let [saved (core/get-sports-site (test-db) lipas-id)]
        (core/save-sports-site! (test-db) (:lipas/search @test-system) (:lipas/ptv @test-system) admin
                                (-> saved
                                    (assoc :event-date (utils/timestamp) :phone-number "040 1")
                                    (assoc-in [:ptv :sync-enabled] false)
                                    (assoc-in [:ptv :ptv-modified] "forged")))
        (is (= "2026-02-03T08:00:00.123456"
               (get-in (core/get-sports-site (test-db) lipas-id) [:ptv :ptv-modified])))))))

(deftest sync-swaps-service-on-type-change-test
  (let [_org (seed-org!)
        admin (tu/gen-admin-user :db-component (test-db))
        ;; synced as an ice stadium (2510, sub-category 2500), connected to the
        ;; org's ice stadium service and to one made in PTV
        lipas-id (:lipas-id (save! admin (-> (site nil "2026-02-01T10:00:00.000Z" "Synkattu" "2026-02-01T10:00:00.000Z")
                                             (assoc-in [:ptv :service-ids] ["svc-ice" "svc-own"]))))
        services [{:id "svc-ice" :sourceId (str "lipas-" ptv-org-id "-2500")}
                  {:id "svc-swim" :sourceId (str "lipas-" ptv-org-id "-3100")}
                  {:id "svc-own"}]
        channel {:sourceId "lipas-source" :services [{:service {:id "svc-ice"}} {:service {:id "svc-own"}}]
                 :addresses [visiting]}]
    ;; an editor turns it into a swimming hall (3110, sub-category 3100)
    (save! admin (-> (core/get-sports-site (test-db) lipas-id)
                     (assoc :event-date "2026-03-01T00:00:00.000Z" :type {:type-code 3110})))
    (testing "a sync from the PTV tool swaps the sub-category's service, the others stay"
      (let [calls (atom [])
            {:keys [ptv]} (with-redefs-fn (stub-ptv calls channel :services services)
                            #(tool-sync! admin lipas-id {:service-ids ["svc-ice" "svc-own"]}))]
        (is (= #{"svc-own" "svc-swim"} (set (:service-ids ptv))))
        (is (= 3110 (:previous-type-code ptv)))
        (is (= #{[:connect "svc-ice" #{}] [:connect "svc-swim" #{channel-id}]}
               (set (filter #(= :connect (first %)) @calls))))))

    (testing "a sub-category without a service in PTV is a blocker, before any write"
      (save! admin (-> (core/get-sports-site (test-db) lipas-id)
                       (assoc :event-date (utils/timestamp) :type {:type-code 1110})
                       (assoc-in [:ptv :service-ids] [])))
      (let [calls (atom [])
            e (try (with-redefs-fn (stub-ptv calls channel :services services)
                     #(tool-sync! admin lipas-id {:service-ids []}))
                   nil
                   (catch clojure.lang.ExceptionInfo e e))]
        (is (= [:ptv/missing-service] (:blockers (ex-data e))))
        (is (empty? (filter #(= :put (first %)) @calls)))))))

(deftest site-patch-schema-test
  (let [_org (seed-org!)
        admin (tu/gen-admin-user :db-component (test-db))
        lipas-id (:lipas-id (save! admin (site nil "2026-02-01T10:00:00.000Z" "Synkattu" "2026-02-01T10:00:00.000Z")))
        body (fn [patch] {:lipas-id lipas-id
                          :org-id ptv-org-id
                          :ptv {:org-id ptv-org-id :sync-enabled true
                                :service-channel-ids [channel-id] :service-ids ["svc-a"]
                                :summary {:fi "Synkattu"} :description {:fi "Pidempi kuvaus"}}
                          :site-patch patch})
        calls (atom [])
        status (fn [patch]
                 (with-redefs-fn (stub-ptv calls {:sourceId "lipas-source" :services [{:service {:id "svc-a"}}]
                                                  :addresses [visiting]})
                   #(:status (post* "/api/actions/save-ptv-service-location" (body patch)
                                    (jwt/create-token admin)))))]
    (testing "only the fields a site editor may take, with valid values"
      (is (= 400 (status {:status "incorrect-data"})))
      (is (= 400 (status {:location {:city {:city-code 5}}})))
      (is (= 400 (status {:location {:postal-code "ei postinumero"}})))
      (is (= 400 (status {:email "ei sähköposti"})))
      (is (empty? @calls) "refused before PTV"))
    (testing "a valid patch is written"
      (is (= 200 (status {:phone-number "+358 40 1234567" :location {:postal-office "Helsinki"}})))
      (is (= "+358 40 1234567" (:phone-number (core/get-sports-site (test-db) lipas-id)))))))

(comment
  (clojure.test/run-tests *ns*))
