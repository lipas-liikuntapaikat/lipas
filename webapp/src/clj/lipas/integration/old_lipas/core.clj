(ns lipas.integration.old-lipas.core
  (:require
   [clojure.set :as set]
   [lipas.backend.db.db :as db]
   [lipas.integration.old-lipas.api :as api]
   [lipas.integration.old-lipas.transform :as transform]
   [lipas.utils :as utils]))

(defn- push-to-old-lipas! [m]
  (let [doc {:op   (if (= "active" (:status m)) "upsert" "delete")
             :id   (:lipas-id m)
             :data (transform/->old-lipas-sports-site m)}]
    (api/post-integration-doc! doc)))

;; Get changed entries since last integration.
(defn add-changed-to-out-queue! [db since]
  (->> since
       (db/get-sports-sites-modified-since db)
       (map :lipas-id)
       (db/add-all-to-integration-out-queue! db)))

(defn process-integration-out-queue! [db]
  (let [changed (->> (db/get-integration-out-queue db)
                     (map :lipas-id)
                     (db/get-sports-sites-by-lipas-ids db)
                     (utils/index-by :lipas-id))

        ;; Query timestamps from old Lipas.
        timestamps (->> (api/query-timestamps (keys changed))
                        (utils/index-by :sportsPlaceId))

        ;; Select only entries that are newer in this system.
        updates (utils/filter-newer changed
                                    :event-date
                                    timestamps
                                    #(-> % :lastModified transform/last-modified->UTC))

        ignores (map first (set/difference (set changed) (set updates)))

        ;; Attempt to push each change individually
        resps (reduce (fn [res [lipas-id m]]
                        (let [resp (push-to-old-lipas! m)]
                          (if (>= 200 (:status resp))
                            (update res :updated conj lipas-id)
                            (update res :failed conj lipas-id))))
                      {}
                      updates)]

    ;; Delete successfully integrated and ignored entries from queue
    (doseq [lipas-id (into ignores (:updated resps))]
      (db/delete-from-integration-out-queue! db lipas-id))

    (merge resps
           {:total   (count changed)
            :ignored ignores
            :latest  (->> changed vals (map :event-date) sort last)})))
