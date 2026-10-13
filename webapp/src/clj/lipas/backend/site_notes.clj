(ns lipas.backend.site-notes
  "Per-site notes an org's members leave on the sites the org owns.

  Org-private: a note belongs to one org and only that org's members (and LIPAS
  admins) ever see it. Notes live in their own tables, never in the public
  sports-site document.

  Append-only (enforced by triggers, see the site-note migration): a note's text
  is written once, and its lifecycle is a log of `site_note_status` rows. The
  `site_note_current` view gives each note with its latest status (no status row
  = active). `deleted` is a soft delete — the note disappears from the UI but
  stays in the database as history."
  (:require [clojure.string :as str]
            [lipas.backend.core :as core]
            [lipas.utils :as utils]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]))

(def all-statuses #{"active" "archived" "deleted"})

(def ^:private transitions
  "Allowed status changes. Archived notes can come back; deleted is final."
  {"active"   #{"archived" "deleted"}
   "archived" #{"active" "deleted"}})

(def ^:private select-notes
  "SELECT n.id, n.lipas_id, n.org_id, n.body, n.created_at, n.status,
          n.status_changed_at, n.author_id,
          a.username AS author_username,
          a.user_data->>'firstname' AS author_firstname,
          a.user_data->>'lastname' AS author_lastname
   FROM site_note_current n
   LEFT JOIN account a ON a.id = n.author_id")

(defn- author-name [{:keys [author-firstname author-lastname author-username]}]
  (let [full (->> [author-firstname author-lastname]
                  (remove empty?)
                  (interpose " ")
                  (apply str))]
    (if (seq full) full author-username)))

(defn- ->note [row]
  (-> row
      (select-keys [:id :lipas-id :org-id :body :created-at :status
                    :status-changed-at :author-id])
      (assoc :author-name (author-name row))))

(defn- in-clause
  "`column IN (?, ?, …)` for `n` values."
  [column n]
  (str column " IN (" (str/join ", " (repeat n "?")) ")"))

(defn- query [db sql-and-params]
  (->> (jdbc/execute! db sql-and-params {:builder-fn rs/as-unqualified-kebab-maps})
       (mapv ->note)))

(defn get-note [db note-id]
  (first (query db [(str select-notes " WHERE n.id = ?") (utils/->uuid-safe note-id)])))

(defn site-notes
  "Notes on `lipas-id` belonging to any of `org-ids`, newest first. Deleted
  notes are left out."
  [db lipas-id org-ids]
  (if-let [org-ids (seq (keep utils/->uuid-safe org-ids))]
    (query db (into [(str select-notes
                          " WHERE n.lipas_id = ? AND " (in-clause "n.org_id" (count org-ids))
                          " AND n.status <> 'deleted'"
                          " ORDER BY n.created_at DESC")
                     lipas-id]
                    org-ids))
    []))

(defn- site-names
  "lipas-id → current site name, in one search query."
  [search lipas-ids]
  (if (empty? lipas-ids)
    {}
    (let [resp (core/search search {:size    (count lipas-ids)
                                    :_source ["lipas-id" "name"]
                                    :query   {:terms {:lipas-id (vec lipas-ids)}}})]
      (->> resp :body :hits :hits
           (into {} (map (comp (juxt :lipas-id :name) :_source)))))))

(defn org-notes
  "All of the org's notes in `statuses` (default: active), newest first, each
  with the site's current name."
  [db search org-id {:keys [statuses] :or {statuses ["active"]}}]
  (let [statuses (vec (distinct (filter all-statuses statuses)))
        notes    (if (empty? statuses)
                   []
                   (query db (into [(str select-notes
                                         " WHERE n.org_id = ? AND "
                                         (in-clause "n.status" (count statuses))
                                         " ORDER BY n.created_at DESC")
                                    (utils/->uuid-safe org-id)]
                                   statuses)))
        names (site-names search (distinct (map :lipas-id notes)))]
    (mapv #(assoc % :site-name (get names (:lipas-id %))) notes)))

(defn create-note!
  "Write a new active note. The caller authorizes (org member, org owns the
  site)."
  [db {:keys [lipas-id org-id body]} author-id]
  (let [row (jdbc/execute-one! db
                               ["INSERT INTO site_note (lipas_id, org_id, body, author_id)
                                 VALUES (?, ?, ?, ?) RETURNING id"
                                lipas-id (utils/->uuid-safe org-id) body
                                (utils/->uuid-safe author-id)]
                               {:builder-fn rs/as-unqualified-kebab-maps})]
    (get-note db (:id row))))

(defn set-status!
  "Append a status change for `note`. Throws ex-info `:invalid-note-status`
  when the change isn't allowed from the note's current status."
  [db note status author-id]
  (when-not (contains? (get transitions (:status note)) status)
    (throw (ex-info "Invalid note status change"
                    {:type :invalid-note-status
                     :from (:status note)
                     :to   status})))
  (jdbc/execute-one! db ["INSERT INTO site_note_status (note_id, status, author_id)
                          VALUES (?, ?, ?)"
                         (:id note) status (utils/->uuid-safe author-id)])
  (get-note db (:id note)))
