(ns lipas.e2e.scripts
  "Browser-side e2e dispatchers and readers. Sync only — no Promises.

  These functions are designed to be driven from clj via
  `lipas.e2e.tools/cljs-eval` + `await-cljs`. The clj side does the polling
  via Thread/sleep; the browser side just dispatches events and exposes
  sync readers.

  See `lipas.e2e.tools/ui-create-site!` etc. for the high-level entry points
  agents normally call. This namespace is the substrate, not the user-facing
  API."
  (:require [re-frame.core :as rf]
            [re-frame.db :as rf-db]
            [re-frame.registrar :as rf-registrar]))

;; ── Discovery ──────────────────────────────────────────────────────────────

(defn list-handlers
  "List registered re-frame handler IDs. `kind` is :event/:sub/:fx/:cofx.
  No args → counts summary. With pattern, case-insensitive substring filter."
  ([] (into {} (map (fn [[k m]] [k (count m)]) @rf-registrar/kind->id->handler)))
  ([kind] (list-handlers kind nil))
  ([kind pattern]
   (let [ids (->> (get @rf-registrar/kind->id->handler kind) keys sort vec)]
     (if pattern
       (filterv #(re-find (re-pattern (str "(?i)" pattern)) (str %)) ids)
       ids))))

;; ── Login ──────────────────────────────────────────────────────────────────

(defn dispatch-login!
  "Kick off a username/password login. Returns immediately. Use
  `(login-result)` to check the outcome."
  [username password]
  (rf/dispatch [:lipas.ui.login.events/clear-errors])
  (rf/dispatch [:lipas.ui.login.events/submit-login-form
                {:username username :password password}])
  :dispatched)

(defn login-result
  "Returns :ok if logged in, an error map if login failed, nil if pending.
  Used by clj-side `await-cljs`."
  []
  (let [db @rf-db/app-db
        err (get-in db [:user :login-error])]
    (cond
      err {:error err}
      (:logged-in? db) :ok
      :else nil)))

(defn dispatch-logout! []
  (rf/dispatch [:lipas.ui.login.events/logout])
  :dispatched)

;; ── Create site ────────────────────────────────────────────────────────────

(defn dispatch-create!
  "Synchronously dispatch all events to create a sports site, including the
  HTTP save. Returns :dispatched. The actual save completes asynchronously
  in the browser; poll `(url-lipas-id)` to detect navigation."
  [{:keys [type-code coords city-code name owner admin address postal-code]}]
  (assert (and type-code coords name owner admin address postal-code)
          "Missing required key. See dispatch-create! docstring.")
  ;; Discard any prior wizard state
  (rf/dispatch-sync [:lipas.ui.sports-sites.events/discard-new-site])
  (rf/dispatch-sync [:lipas.ui.map.events/discard-drawing])

  (let [fcoll {:type "FeatureCollection"
               :features [{:type "Feature" :properties {}
                           :geometry {:type "Point" :coordinates coords}}]}]
    (rf/dispatch-sync [:lipas.ui.map.events/start-adding-new-site nil nil])
    (rf/dispatch-sync [:lipas.ui.sports-sites.events/select-new-site-type type-code])
    (rf/dispatch-sync [:lipas.ui.sports-sites.events/init-new-site type-code fcoll])

    (when city-code
      (rf/dispatch-sync [:lipas.ui.sports-sites.events/edit-new-site-field
                         [:location :city :city-code] city-code]))

    (doseq [[path value] [[[:name] name]
                          [[:owner] owner]
                          [[:admin] admin]
                          [[:location :address] address]
                          [[:location :postal-code] postal-code]]]
      (rf/dispatch-sync [:lipas.ui.sports-sites.events/edit-new-site-field path value]))

    (rf/dispatch [:lipas.ui.map.events/save-new-site
                  (get-in @rf-db/app-db [:new-sports-site :data])])
    :dispatched))

(defn url-lipas-id
  "Returns the lipas-id (int) once dispatch-create! has fully landed. Two
  conditions must hold: (1) the URL is /liikuntapaikat/<id>, AND (2) the
  wizard's adding? flag is false (save success clears it via discard-new-
  site). The flag check is what distinguishes 'save just landed' from
  'URL was already this way before we started'."
  []
  (when-not (get-in @rf-db/app-db [:new-sports-site :adding?])
    (when-let [m (.match (.. js/window -location -pathname) #"/liikuntapaikat/(\d+)")]
      (js/parseInt (aget m 1)))))

;; ── Update site ────────────────────────────────────────────────────────────

(defn dispatch-update!
  "Synchronously dispatch all events to update a sports site. The fetch and
  save complete async; poll `(update-done? lipas-id)`.

  :changes is a vector of [path value] pairs, e.g.
    [[[:name] \"New Name\"]
     [[:location :address] \"New addr\"]]"
  [{:keys [lipas-id changes]}]
  (assert (and lipas-id (seq changes)) ":lipas-id and non-empty :changes required")
  ;; Fetch current site (the SPA does this on navigation; we replicate)
  (rf/dispatch [:lipas.ui.sports-sites.events/get lipas-id nil])
  ;; Schedule the rest after the fetch lands. We can't synchronously wait
  ;; here, so use a tiny setTimeout poll.
  (letfn [(continue []
            (if (get-in @rf-db/app-db [:sports-sites lipas-id])
              (do
                (rf/dispatch-sync [:lipas.ui.sports-sites.events/edit-site lipas-id])
                (doseq [[path value] changes]
                  (rf/dispatch-sync [:lipas.ui.sports-sites.events/edit-field
                                     lipas-id path value]))
                (rf/dispatch [:lipas.ui.sports-sites.events/save-edits lipas-id nil nil]))
              (js/setTimeout continue 100)))]
    (continue))
  :dispatched)

(defn update-done?
  "True once the save-edits cycle for `lipas-id` is finished and editing
  state is cleared. Used by clj-side `await-cljs`."
  [lipas-id]
  (and (not (get-in @rf-db/app-db [:sports-sites :save-in-progress?]))
       (nil? (get-in @rf-db/app-db [:sports-sites lipas-id :editing]))
       ;; Also confirm the load+save round-trip happened
       (some? (get-in @rf-db/app-db [:sports-sites lipas-id :latest]))))

;; ── Read helpers ───────────────────────────────────────────────────────────

(defn current-rev
  "Latest revision doc for a saved site, from app-db. nil if not loaded."
  [lipas-id]
  (let [latest (get-in @rf-db/app-db [:sports-sites lipas-id :latest])]
    (get-in @rf-db/app-db [:sports-sites lipas-id :history latest])))

(defn current-name
  "Convenience: current name of a saved site (or nil if not loaded)."
  [lipas-id]
  (:name (current-rev lipas-id)))

(defn app-db-snapshot
  "Returns a snapshot of selected app-db keys. Pass a vector of paths.
  Useful for failure-debugging from clj."
  [paths]
  (into {} (map (fn [p] [p (get-in @rf-db/app-db p)])) paths))
