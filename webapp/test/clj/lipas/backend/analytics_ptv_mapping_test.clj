(ns lipas.backend.analytics-ptv-mapping-test
  "The analytics index maps the scalar :ptv fields so the PTV adoption
  dashboard can query revision history directly. Bulk indexing doesn't fail on
  per-document mapping errors, so a :ptv value the mapping rejects would
  silently drop that revision from the index. These tests index generated
  sites with generated :ptv metadata through the real analytics enrichment
  into a throwaway index with the real analytics mapping."
  (:require
    [clojure.test :refer [deftest is testing use-fixtures]]
    [lipas.backend.search :as search]
    [lipas.schema.sports-sites.ptv :as ptv-schema]
    [lipas.search-indexer :as indexer]
    [lipas.test-utils :as tu]
    [malli.generator :as mg]))

(def ^:private idx "zzanalytics-ptv-test")

(def ^:private ^:dynamic *client* nil)

(defn- with-fresh-index [f]
  (binding [*client* (search/create-cli
                       (let [{:keys [hosts user pass]} (:search tu/config)]
                         {:hosts hosts :user user :password pass}))]
    (try (search/delete-index! *client* idx) (catch Exception _ nil))
    (search/create-index! *client* idx (:analytics search/mappings))
    (try (f)
         (finally (try (search/delete-index! *client* idx) (catch Exception _ nil))))))

(use-fixtures :each with-fresh-index)

(defn- gen-ptv
  "Schema-generated :ptv plus the keys the sync writes outside ptv-meta,
  shaped like prod data (nanosecond last-sync, nullable status/source-id)."
  []
  (merge (mg/generate ptv-schema/ptv-meta)
         {:last-sync (rand-nth ["2026-10-05T12:35:18.754301703Z" "2025-11-03T08:00:00Z"])
          :publishing-status (rand-nth ["Published" "Deleted" nil])
          :source-id (rand-nth ["lipas-org-1-2026-10-05T12-27-48.988948683Z" nil])
          :previous-type-code (rand-nth [2210 nil])}))

(defn- revision-row [site]
  {:id (str (random-uuid))
   :document site
   :author_id nil
   :status "published"
   :created_at (:event-date site)})

(defn- index-revisions! [sites]
  (let [docs (keep #(indexer/enrich-for-analytics {} (revision-row %)) sites)]
    (doseq [batch (partition-all 100 docs)]
      (search/bulk-index-sync! *client* (search/->bulk idx :id batch)))
    (count docs)))

(defn- query [body]
  (:body (search/search *client* idx body)))

(defn- hit-count [q]
  (-> (query {:size 0 :track_total_hits true :query q}) :hits :total :value))

(deftest ptv-revisions-index-without-loss-test
  (let [sites (repeatedly 30 #(assoc (tu/gen-sports-site) :ptv (gen-ptv)))
        plain (repeatedly 5 #(dissoc (tu/gen-sports-site) :ptv))
        n (index-revisions! (concat sites plain))]

    (testing "every generated revision is indexed — none rejected by the mapping"
      (is (= n (hit-count {:match_all {}}))))

    (testing "PTV revisions are found by the indexed org-id"
      (is (= (count sites) (hit-count {:exists {:field :ptv.org-id}}))))

    (testing "scalar PTV fields are aggregatable"
      (is (= (count sites)
             (->> (query {:size 0 :aggs {:s {:terms {:field :ptv.sync-enabled}}}})
                  :aggregations :s :buckets (map :doc_count) (reduce +))))
      (is (= "2026-10-05T12:35:18.754Z"
             (-> (query {:size 0 :aggs {:m {:max {:field :ptv.last-sync}}}})
                 :aggregations :m :value_as_string))
          "nanosecond timestamps parse (truncated to millis)"))))

(deftest adoption-chart-query-test
  ;; The Kibana Vega charts "PTV adoption per month by municipality" and
  ;; "PTV integration — accumulated total" run this aggregation verbatim:
  ;; first :ptv revision per site.
  (let [site (dissoc (tu/gen-sports-site) :ptv)
        rev (fn [date ptv?] (cond-> (assoc site :event-date date) ptv? (assoc :ptv (gen-ptv))))]
    (index-revisions! [(rev "2025-01-10T10:00:00.000Z" false)
                       (rev "2025-03-15T10:00:00.000Z" true)
                       (rev "2025-06-01T10:00:00.000Z" true)])
    (let [buckets (-> (query {:size 0
                              :query {:exists {:field :ptv.org-id}}
                              :aggs {:sites {:terms {:field :lipas-id :size 20000}
                                             :aggs {:first {:min {:field :event-date}}
                                                    :city {:terms {:field :search-meta.location.city.name.fi.keyword
                                                                   :size 1}}}}}})
                      :aggregations :sites :buckets)]
      (is (= 1 (count buckets)))
      (is (= "2025-03-15T10:00:00.000Z" (-> buckets first :first :value_as_string))
          "the site counts from its first revision that carries :ptv"))))
