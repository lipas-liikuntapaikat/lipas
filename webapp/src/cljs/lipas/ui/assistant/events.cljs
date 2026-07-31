(ns lipas.ui.assistant.events
  ;; Lives in the base module: must not require the lazy :map or :ptv
  ;; modules. Map/PTV interactions go through literal event keywords
  ;; dispatched with :lipas.ui.lazy/load-then, light helpers extracted to
  ;; lipas.ui.geom and lipas.ui.ptv.context.
  (:require [ajax.core :as ajax]
            [clojure.string :as str]
            [lipas.schema.sports-sites :as sports-site-schema]
            [lipas.ui.geom :as geom]
            [lipas.ui.help.events :as help-events]
            [lipas.ui.ptv.context :as ptv-context]
            [lipas.ui.utils :as utils]
            [malli.core :as m]
            [re-frame.core :as rf]))

(defn- round6 [x]
  (/ (js/Math.round (* x 1e6)) 1e6))

(defn- fcoll-vertex-count [fcoll]
  (transduce (map (fn [f]
                    (let [g (:geometry f)
                          c (:coordinates g)]
                      (case (:type g)
                        "Point" 1
                        "LineString" (count c)
                        "Polygon" (reduce + 0 (map count c))
                        0))))
             + 0 (:features fcoll)))

(defn- invalid-fields
  "Top-level field paths failing schema validation for the current
   edits — the concrete reason the save button is disabled."
  [schema data]
  (when data
    (when-let [errors (:errors (m/explain schema (utils/make-saveable data)))]
      (->> errors
           (map (fn [{:keys [in]}]
                  (->> (take 2 in)
                       (map #(if (keyword? %) (name %) (str %)))
                       (str/join "."))))
           distinct
           (take 10)
           vec))))

(defn- edit-context
  "Live map-editor state: what is being drawn/edited right now, its
   geometry stats and problems, and what blocks saving. Mirrors the
   backend's context-schema :edit block. Must never throw — a broken
   snapshot must not block sending a message."
  [db]
  (try
    (let [mode (-> db :map :mode)
          new? (= :adding (:name mode))
          geoms (:geoms mode)
          problem-fs (-> mode :problems :data :features)
          geom-type (or (-> geoms :features first :geometry :type)
                        (some-> (:geom-type mode) str))
          data (if new?
                 (-> db :new-sports-site :data)
                 (get-in db [:sports-sites (:lipas-id mode) :editing]))
          schema (if new?
                   sports-site-schema/new-sports-site
                   sports-site-schema/sports-site)
          length-km (when (and geoms (= "LineString" geom-type))
                      (geom/calculate-length-km geoms))]
      (cond-> {}
        new? (assoc :new-site? true)
        (:sub-mode mode) (assoc :sub-mode (name (:sub-mode mode)))
        geom-type (assoc :geometry-type geom-type)
        geoms (assoc :segments (count (:features geoms))
                     :vertices (fcoll-vertex-count geoms))
        length-km (assoc :length-km length-km)
        problem-fs (assoc :self-intersections (count problem-fs)
                          :problem-locations
                          (mapv (fn [f]
                                  (let [[lon lat] (-> f :geometry :coordinates)]
                                    {:lon (round6 lon) :lat (round6 lat)}))
                                (take 5 problem-fs)))
        data (merge (when-let [inv (invalid-fields schema data)]
                      {:invalid-fields inv}))))
    (catch :default _ nil)))

(defn db->context
  "Snapshot of where the user is, sent with every message so the
   assistant can ground answers in the current view. Only the site id is
   sent — the agent fetches details with its own tools."
  [db]
  (let [tr (:translator db)
        locale (when tr (name (tr)))
        route (some-> db :current-route :data :name str)
        ptv-ctx (ptv-context/dialog-context db)
        view (cond
               ;; A fullscreen dialog covers whatever the route renders —
               ;; it is where the user actually is.
               ptv-ctx "PTV export dialog 'Vie Palvelutietovarantoon' (covers the map view)"
               (nil? route) nil
               (str/includes? route "routes.map") "map view (karttanäkymä)"
               (str/includes? route "front-page") "front page"
               (str/includes? route "stats") "statistics view"
               :else nil)
        lipas-id (-> db :map :mode :lipas-id)
        edit-mode? (contains? #{:editing :drawing :adding} (-> db :map :mode :name))
        edit-ctx (when edit-mode? (edit-context db))
        help-ctx (help-events/dialog-context db)]
    (cond-> {}
      locale (assoc :locale locale)
      route (assoc :route route)
      view (assoc :view view)
      lipas-id (assoc :site {:lipas-id lipas-id})
      edit-mode? (assoc :edit-mode? true)
      (seq edit-ctx) (assoc :edit edit-ctx)
      (seq help-ctx) (assoc :help help-ctx)
      (seq ptv-ctx) (assoc :ptv ptv-ctx))))

(rf/reg-event-db ::toggle-panel
  (fn [db _]
    (update-in db [:assistant :open?] not)))

(rf/reg-event-db ::set-input
  (fn [db [_ v]]
    (assoc-in db [:assistant :input] v)))

(rf/reg-event-db ::new-chat
  (fn [db _]
    (update db :assistant merge {:messages []
                                 :input ""
                                 :thinking? false
                                 :pending-escalation nil
                                 :escalation-in-progress? false})))

(rf/reg-event-fx ::send-message
  (fn [{:keys [db]} _]
    (let [message (str/trim (get-in db [:assistant :input] ""))
          token (-> db :user :login :token)
          history (->> (get-in db [:assistant :messages] [])
                       (mapv #(select-keys % [:role :text])))]
      (when-not (str/blank? message)
        {:db (-> db
                 (update-in [:assistant :messages] (fnil conj [])
                            {:role "user" :text message})
                 (assoc-in [:assistant :input] "")
                 (assoc-in [:assistant :thinking?] true)
                 (assoc-in [:assistant :pending-escalation] nil))
         :fx [[:http-xhrio
               {:method          :post
                :headers         {:Authorization (str "Token " token)}
                :uri             (str (:backend-url db) "/actions/assistant-chat")
                :params          {:message message
                                  :history history
                                  :context (db->context db)}
                :format          (ajax/transit-request-format)
                :response-format (ajax/transit-response-format)
                :on-success      [::receive-answer]
                :on-failure      [::chat-failure]}]]}))))

(rf/reg-event-db ::receive-answer
  (fn [db [_ {:keys [answer-md sources actions escalation]}]]
    (-> db
        (update-in [:assistant :messages] (fnil conj [])
                   {:role "assistant" :text answer-md :sources sources
                    :actions (vec actions)})
        (assoc-in [:assistant :thinking?] false)
        (assoc-in [:assistant :pending-escalation]
                  (when escalation (:summary escalation))))))

;; ——— UI actions proposed by the assistant ————————————————————————————
;;
;; The backend validates every action against the closed vocabulary in
;; lipas.schema.assistant before it reaches the widget; this translator
;; is the only place an action becomes a re-frame dispatch. The model
;; never produces event vectors.

(def view->route
  {"front-page" :lipas.ui.routes/front-page
   "map" :lipas.ui.routes.map/map
   "stats" :lipas.ui.routes.stats/front-page
   "stats-sport" :lipas.ui.routes.stats/sport
   "stats-age-structure" :lipas.ui.routes.stats/age-structure
   "stats-city" :lipas.ui.routes.stats/city
   "stats-finance" :lipas.ui.routes.stats/finance
   "stats-subsidies" :lipas.ui.routes.stats/subsidies
   "profile" :lipas.ui.routes/user})

(defn- action->fx
  "Whitelist translator: action map → dispatch vectors. Unknown action
   types translate to nothing."
  [{:keys [type] :as action}]
  ;; Map-touching actions dispatch through :lipas.ui.lazy/load-then :map —
  ;; their handlers live in the lazy :map module (no-op when already in,
  ;; e.g. every mid-edit action).
  (case type
    "apply-search"
    [[:dispatch [:lipas.ui.events/navigate :lipas.ui.routes.map/map]]
     [:dispatch [:lipas.ui.lazy/load-then :map
                 [:lipas.ui.search.events/replace-filters
                  {:search-text (:search-text action)
                   :city-codes (:city-codes action)
                   :type-codes (:type-codes action)
                   :edit-permission? (:only-editable action)}]]]]

    "show-site"
    [[:dispatch [:lipas.ui.lazy/load-then :map
                 [:lipas.ui.map.events/show-sports-site (:lipas-id action)]]]]

    "pan-to-location"
    [[:dispatch [:lipas.ui.events/navigate :lipas.ui.routes.map/map]]
     [:dispatch [::pan-to-location (:location action)]]]

    ;; Deliberately no navigation: this action is offered during
    ;; geometry editing (zoom to a problem spot) and navigating would
    ;; disturb the edit session.
    "pan-to-coordinates"
    [[:dispatch [:lipas.ui.lazy/load-then :map
                 [:lipas.ui.map.events/set-view-wgs84
                  [(:lon action) (:lat action)]
                  (or (:zoom action) 16)]]]]

    "navigate-to-view"
    (when-let [route (view->route (:view action))]
      [[:dispatch [:lipas.ui.events/navigate route]]])

    nil))

(rf/reg-event-fx ::run-action
  (fn [{:keys [db]} [_ msg-idx action-idx action]]
    (let [editing? (contains? #{:editing :drawing :adding} (-> db :map :mode :name))]
      ;; Never navigate away from unsaved edits — but pure map pan/zoom
      ;; (inspecting a geometry problem) is safe during editing.
      (if (and editing? (not= "pan-to-coordinates" (:type action)))
        {:dispatch [:lipas.ui.events/set-active-notification
                    {:message "Sulje ensin muokkaustila, niin avustaja voi ohjata näkymää."
                     :success? false}]}
        (when-let [fx (action->fx action)]
          {:db (assoc-in db [:assistant :messages msg-idx :actions action-idx :executed?] true)
           :fx (vec fx)})))))

(rf/reg-event-fx ::pan-to-location
  (fn [_ [_ location]]
    {:http-xhrio
     {:method :get
      :uri (str "https://" (utils/domain)
                "/digitransit/geocoding/v1/autocomplete?sources=oa,osm&text="
                (js/encodeURIComponent location))
      :response-format (ajax/json-response-format {:keywords? true})
      :on-success [::pan-to-location-success]
      :on-failure [::pan-to-location-failure]}}))

(rf/reg-event-fx ::pan-to-location-success
  (fn [_ [_ resp]]
    (let [f (-> resp :features first)]
      (if-let [coords (-> f :geometry :coordinates)]
        (let [address? (contains? #{"address" "venue" "street"}
                                  (-> f :properties :layer))]
          {:fx [[:dispatch [:lipas.ui.lazy/load-then :map
                            [:lipas.ui.map.events/set-view-wgs84
                             coords
                             (if address? 14 12)]]]]})
        {:dispatch [::pan-to-location-failure nil]}))))

(rf/reg-event-fx ::pan-to-location-failure
  (fn [_ _]
    {:dispatch [:lipas.ui.events/set-active-notification
                {:message "Paikkaa ei löytynyt kartalta."
                 :success? false}]}))

(rf/reg-event-db ::chat-failure
  (fn [db [_ resp]]
    ;; Don't echo the server's own 429 string into the chat: the shared rate
    ;; limiter answers with one generic English message for every endpoint,
    ;; whereas this bubble is Finnish user-facing copy. The backend owns the
    ;; status code, the UI owns the wording.
    (let [msg (if (= 429 (:status resp))
                "Viestiraja täynnä. Yritä myöhemmin uudelleen."
                "Avustajaan ei juuri nyt saada yhteyttä. Yritä hetken päästä uudelleen.")]
      (-> db
          (update-in [:assistant :messages] (fnil conj [])
                     {:role "assistant" :text msg :error? true})
          (assoc-in [:assistant :thinking?] false)))))

(rf/reg-event-db ::edit-escalation
  (fn [db [_ v]]
    (assoc-in db [:assistant :pending-escalation] v)))

(rf/reg-event-db ::dismiss-escalation
  (fn [db _]
    (assoc-in db [:assistant :pending-escalation] nil)))

(rf/reg-event-fx ::confirm-escalation
  (fn [{:keys [db]} _]
    (let [summary (get-in db [:assistant :pending-escalation])
          token (-> db :user :login :token)
          transcript (->> (get-in db [:assistant :messages] [])
                          (mapv #(select-keys % [:role :text])))]
      {:db (assoc-in db [:assistant :escalation-in-progress?] true)
       :fx [[:http-xhrio
             {:method          :post
              :headers         {:Authorization (str "Token " token)}
              :uri             (str (:backend-url db) "/actions/assistant-escalate")
              :params          {:summary summary
                                :transcript transcript
                                :context (db->context db)}
              :format          (ajax/transit-request-format)
              :response-format (ajax/transit-response-format)
              :on-success      [::escalation-sent]
              :on-failure      [::escalation-failure]}]]})))

(rf/reg-event-db ::escalation-sent
  (fn [db _]
    (-> db
        (update-in [:assistant :messages] (fnil conj [])
                   {:role "assistant"
                    :text "Tukipyyntö on lähetetty LIPAS-tuelle. Saat vastauksen sähköpostiisi."})
        (assoc-in [:assistant :pending-escalation] nil)
        (assoc-in [:assistant :escalation-in-progress?] false))))

(rf/reg-event-db ::escalation-failure
  (fn [db [_ resp]]
    ;; See ::chat-failure — the UI owns the wording, not the server.
    (let [msg (if (= 429 (:status resp))
                "Tukipyyntöraja täynnä tälle päivälle."
                "Tukipyynnön lähetys epäonnistui. Voit lähettää sähköpostia osoitteeseen lipasinfo@jyu.fi.")]
      (-> db
          (update-in [:assistant :messages] (fnil conj [])
                     {:role "assistant" :text msg :error? true})
          (assoc-in [:assistant :escalation-in-progress?] false)))))

(rf/reg-fx ::open-window!
  (fn [url]
    (js/window.open url "_blank")))

(rf/reg-event-fx ::open-source
  (fn [_ [_ deep-link]]
    (if (and deep-link (str/starts-with? deep-link "?ohje="))
      (let [[section page] (-> deep-link (subs 6) (str/split #"/"))]
        {:fx [[:dispatch [:lipas.ui.help.events/navigate-to section page]]]})
      {::open-window! deep-link})))
