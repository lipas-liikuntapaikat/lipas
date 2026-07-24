(ns lipas.ui.lazy
  "Code-splitting infrastructure.

   The release build is split into shadow-cljs :modules (see
   shadow-cljs.edn): the always-loaded base module :app plus lazy
   feature modules (:map, :stats, :admin, :org, :ptv, :analysis,
   :charts, :help-manage, :xlsx, :i18n-en, :i18n-se, :geo).

   This namespace is the single registry of shadow.lazy loadables the
   base module uses to reach code living in lazy modules. Loading any
   loadable loads its whole module plus the modules it depends on.

   Three integration points:
   - Route views: reitit route data holds a loadable in :view; routes/on-navigate
     loads it before ::navigated runs controllers, so feature event handlers are
     registered before the first dispatch reaches them.
   - Components: [lazy-view {:loadable x} & args] renders a spinner until the
     module is in, then renders [@x & args].
   - Events: {:fx [[::load-fx {:module :map :events [[…]]}]]} (or dispatch
     [::load-then :map […]]) for event chains that cross into a lazy module."
  (:require ["@mui/material/CircularProgress$default" :as CircularProgress]
            [re-frame.core :as rf]
            [reagent.core :as r]
            [shadow.lazy :as lazy]))

;; Route/panel entry points
(def map-view (lazy/loadable lipas.ui.map.views/main))
(def stats-view (lazy/loadable lipas.ui.stats.views/main))
(def admin-view (lazy/loadable lipas.ui.admin.views/main))
(def org-detail-page (lazy/loadable lipas.ui.org.views/org-detail-page))
(def orgs-list-page (lazy/loadable lipas.ui.org.views/orgs-list-page))
(def org-bulk-operations-page (lazy/loadable lipas.ui.org.views/org-bulk-operations-page))

;; Components rendered from the base or :map module but living in lazy modules
(def ptv-dialog (lazy/loadable lipas.ui.ptv.views/dialog))
(def ptv-site-view (lazy/loadable lipas.ui.ptv.site-view/site-view))
(def org-editing-rights-panel (lazy/loadable lipas.ui.org.views/editing-rights-panel))
(def analysis-view (lazy/loadable lipas.ui.analysis.views/view))
(def help-manage-view (lazy/loadable lipas.ui.help.manage/view))
(def elevation-area-chart (lazy/loadable lipas.ui.charts/elevation-area-chart))

;; Non-component module handles
(def modules
  {:map map-view
   :stats stats-view
   :admin admin-view
   :org orgs-list-page
   :ptv ptv-dialog
   :analysis analysis-view
   :charts elevation-area-chart
   :help-manage help-manage-view
   :xlsx (lazy/loadable lipas.ui.excel/download!)
   :i18n-en (lazy/loadable lipas.i18n.register-en/translations)
   :i18n-se (lazy/loadable lipas.i18n.register-se/translations)})

(defn loadable? [x]
  (instance? lazy/Loadable x))

(defn ready? [x]
  (lazy/ready? (if (keyword? x) (get modules x) x)))

(defn load!
  "Loads the module of `x` (a loadable or a key of `modules`), then calls
   on-ready (no args). No-op callback-wise if already loaded. Failures
   (network) surface as a sticky notification so a dead click isn't
   silent — note goog's module manager gives up on a module after its
   internal retries, so a later load! of the same module won't refetch;
   recovery is a page reload (which the notification text says)."
  ([x] (load! x nil))
  ([x on-ready]
   (let [ll (if (keyword? x) (get modules x) x)]
     (lazy/load ll
                (fn [_] (when on-ready (on-ready)))
                (fn [err]
                  (js/console.error "Failed to load app module"
                                    (pr-str (.-modules ^js ll)) err)
                  (rf/dispatch [:lipas.ui.events/module-load-failed]))))))

(rf/reg-fx ::load-fx
  (fn [{:keys [module events]}]
    (load! module (fn [] (doseq [e events] (rf/dispatch e))))))

(rf/reg-event-fx ::load-then
  ;; [::load-then :map [:lipas.ui.map.events/set-zoom 12] …]
  (fn [_ [_ module & events]]
    {::load-fx {:module module :events events}}))

(defn- spinner []
  [:div {:style {:display "flex" :justify-content "center" :padding "1em"}}
   [:> CircularProgress {:size 24}]])

(defn lazy-view
  "Renders the component behind `loadable` with `args` once its module is
   loaded; a small spinner (or :fallback) until then. In dev all modules
   are loaded eagerly, so this renders directly.

   The loadable is captured on mount — re-rendering the same instance
   with a *different* loadable would deref the new one against the old
   ready-state (and throw if its module isn't in). Use a React :key or a
   separate call site per loadable instead."
  [{:keys [loadable]} & _args]
  (let [ready?* (r/atom (lazy/ready? loadable))]
    (when-not @ready?*
      (load! loadable #(reset! ready?* true)))
    (fn [{:keys [loadable fallback]} & args]
      (if @ready?*
        (into [@loadable] args)
        (or fallback [spinner])))))
