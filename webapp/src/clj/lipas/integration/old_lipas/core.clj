(ns lipas.integration.old-lipas.core
  (:require
   [clojure.set :as set]
   [lipas.backend.db.db :as db]
   [lipas.integration.old-lipas.api :as api]
   [lipas.integration.old-lipas.transform :as transform]
   [lipas.utils :as utils]
   [taoensso.timbre :as log]))

(defn add-changed-to-out-queue!
  "Gets changed entries since last successful integration and adds
  them to integration out queue."
  [db since]
  (->> since
       (db/get-sports-sites-modified-since db)
       (map :lipas-id)
       (db/add-all-to-integration-out-queue! db)))

(defn- push-to-old-lipas! [m]
  (let [doc {:op   (if (= "active" (:status m)) "upsert" "delete")
             :id   (:lipas-id m)
             :data (transform/->old-lipas-sports-site m)}]
    (log/info "Pushing sports-site" (:lipas-id m) "to old Lipas...")
    (let [resp   (api/post-integration-doc! doc)
          status (:status resp)]
      (log/info "Status" status "from old Lipas!")
      resp)))

(defn process-integration-out-queue!
  "Reads lipas-ids from integration out queue, queries corresponding
  timestamps from old-Lipas and checks which one is newer. If db
  contains newer version they're pushed to old-Lipas, otherwise
  ignored. Whole queue is read and processed one by one.

  Possible errors are collected and returned as a map under
  key :errors where keys are lipas-ids and values are caught
  exceptions. Successfully pushed lipas-ids are appended to vector
  under :updated key and ignored ones under :ignored key.

  Successfully pushed and ignored lipas-ids are removed from the
  queue. Errored lipas-ids will remain in the queue."
  [db]
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
                        (try
                          (push-to-old-lipas! m)
                          (update res :updated conj lipas-id)
                          (catch Exception e
                            (assoc-in res [:errors lipas-id] e))))
                      {}
                      updates)]

    ;; Delete successfully integrated and ignored entries from queue
    (doseq [lipas-id (into ignores (:updated resps))]
      (db/delete-from-integration-out-queue! db lipas-id))

    (merge resps
           {:total   (+ (count (:updated resps)) (count ignores))
            :ignored ignores
            :latest  (->> changed vals (map :event-date) sort last)})))
