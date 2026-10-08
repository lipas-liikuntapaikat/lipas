(ns lipas.search-query-test
  "The map search's query builder must only reference fields the
   sports-site mapping has — ES answers a query on an unmapped path with
   zero hits instead of an error. Covers every filter, every sortable
   results-table column, every locale and both search modes."
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [lipas.backend.search :as search]
            [lipas.data.prop-types :as prop-types]
            [lipas.search-query :as search-query]
            [lipas.test-utils :refer [->json] :as tu]
            [ring.mock.request :as mock]))

(defonce test-system (atom nil))

(let [{:keys [once each]} (tu/full-system-fixture test-system)]
  (use-fixtures :once once)
  (use-fixtures :each each))

(defn test-app [req] ((:lipas/app @test-system) req))

(def sortable-columns
  "Column keys of the results table (lipas.ui.search.subs/results-table-headers),
   each of which the user can sort by."
  [:score :name :name-localized.se :name-localized.en :marketing-name
   :type.name :type.main-category :type.sub-category :owner.name :admin.name
   :construction-year :renovation-years :location.city.name :location.address
   :location.postal-code :location.postal-office :www :email :phone-number
   :event-date :lipas-id])

(def all-filters
  "Every filter the map search supports, all at once."
  {:statuses #{"active" "out-of-service-temporarily" "planned"}
   :type-codes #{1340 2230}
   :city-codes #{91 837}
   :bounding-box? true
   :construction-year-min 1990
   :construction-year-max 2020
   :admins #{"city-sports"}
   :owners #{"city"}
   :edit-permission? true
   :properties-filters (into {}
                             (map (fn [[k m]] [k (tu/sample-prop-filter m)]))
                             prop-types/active)})

(def editor
  "A user whose roles all go through the edit-permission query wrapper (an
   admin skips it)."
  {:permissions {:roles [{:role :city-manager :city-code [91]}
                         {:role :type-manager :type-code [1340]}
                         {:role :site-manager :lipas-id [1]}
                         {:role :org-editor :org-id ["00000000-0000-0000-0000-000000000001"]}]}})

(defn- search-bodies
  "Request bodies for every combination of sort column, locale, search mode
   (decay? = map search, false = area analysis), result shape and zoom."
  []
  (for [sort-fn sortable-columns
        locale [:fi :se :en]
        decay? [true false]
        terse? [true false]
        zoom [5 14]] ; both sides of full-geoms-threshold
    {:case [sort-fn locale (if decay? :search :analysis) (if terse? :terse :full) zoom]
     :body (search-query/->es-search-body
             {:filters all-filters
              :string "jalkapallo-kenttä"
              :center {:lon 25.0 :lat 60.2}
              :distance 1000
              :sort {:sort-fn sort-fn :asc? true}
              :decay? decay?
              :locale locale
              :pagination {:page 0 :page-size 25}
              :zoom zoom
              :bbox {:top-left [24.0 61.0] :bottom-right [26.0 60.0]}
              :geom {:type "Point" :coordinates [25.0 60.2]}}
             editor
             terse?)}))

(deftest search-queries-reference-mapped-fields-test
  (let [mapped (tu/mapped-fields (:sports-site search/mappings))]
    (doseq [{:keys [case body]} (search-bodies)]
      (testing (pr-str case)
        (is (empty? (tu/unmapped-fields mapped body)))))))

(deftest search-queries-run-against-elasticsearch-test
  ;; Unmapped fields aside, ES also rejects some drift outright (e.g. a sort
  ;; on a text field) — run every body through the real endpoint, which
  ;; also applies lipas.backend.search-guard.
  (doseq [{:keys [case body]} (search-bodies)]
    (testing (pr-str case)
      (let [resp (test-app (-> (mock/request :post "/api/actions/search")
                               (mock/content-type "application/json")
                               (mock/body (->json body))))]
        (is (= 200 (:status resp)))))))
