(ns lipas.ui.subs
  (:require [clojure.string :refer [upper-case]]
            [lipas.data.types :as types]
            [lipas.ui.lazy :as lazy]
            [re-frame.core :as rf]))

(rf/reg-sub ::current-route
  (fn [db _]
    (:current-route db)))

(rf/reg-sub ::current-view
  :<- [::current-route]
  (fn [route _]
    (let [view (-> route :data :view)]
      ;; Lazy routes carry a shadow.lazy loadable; routes/on-navigate
      ;; guarantees its module is loaded before the match reaches app-db.
      (if (lazy/loadable? view)
        @view
        view))))

(rf/reg-sub ::parameters
  :<- [::current-route]
  (fn [route _]
    (-> route :parameters)))

(rf/reg-sub ::query-params
  :<- [::parameters]
  (fn [params _]
    (-> params :query)))

(rf/reg-sub ::query-param
  :<- [::query-params]
  (fn [params [_ k default-val]]
    (or (get params k)
        default-val)))

(rf/reg-sub ::account-menu-anchor
  (fn [db _]
    (:account-menu-anchor db)))

(rf/reg-sub ::drawer-open?
  (fn [db _]
    (:drawer-open? db)))

(rf/reg-sub ::translator
  (fn [db _]
    (:translator db)))

(rf/reg-sub ::locale
  :<- [::translator]
  (fn [tr _]
    (tr)))

;;; Cross-module root subs ;;;
;;
;; Root (extractor) subs for db regions owned by lazy-module features
;; but read by always-loaded UI. Placement rule: a root sub lives in the
;; shallowest module that needs its data; subs in deeper modules layer
;; on it with :<-. That direction is registration-race-free — the module
;; loader loads a module's dependencies (ultimately :app) before the
;; module itself — whereas base code must never subscribe to subs
;; registered in lazy modules.

(rf/reg-sub ::ptv-dialog-open?
  ;; Read by always-mounted UI (assistant, map controls);
  ;; lipas.ui.ptv.subs/dialog-open? layers on this.
  (fn [db _]
    (get-in db [:ptv :dialog :open?])))

(rf/reg-sub ::admin-users
  ;; Read by the dev-tools impersonation selector (project-devtools);
  ;; lipas.ui.admin.subs/users layers on this.
  (fn [db _]
    (-> db :admin :users)))

(rf/reg-sub ::admin-orgs
  ;; Read by the profile view's role-context name display
  ;; (lipas.ui.user.subs/admin-org); lipas.ui.admin.subs/orgs layers on
  ;; this.
  (fn [db _]
    (-> db :admin :orgs)))

(rf/reg-sub ::active-notification
  (fn [db _]
    (:active-notification db)))

(rf/reg-sub ::active-confirmation
  (fn [db _]
    (:active-confirmation db)))

(rf/reg-sub ::active-disclaimer
  (fn [db _]
    (:active-disclaimer db)))

(rf/reg-sub ::logged-in?
  (fn [db _]
    (:logged-in? db)))

(rf/reg-sub ::user-data
  (fn [db _]
    (-> db :user :login :user-data)))

(rf/reg-sub ::sports-site-types
  (fn [_]
    [(rf/subscribe [:lipas.ui.sports-sites.subs/active-types])])
  (fn [[active-types] _]
    (for [[type-code type-data] active-types]
      (types/->type (assoc type-data :type-code type-code)))))

(comment ((comp (fnil upper-case "?") first) ""))
(comment ((comp (fnil upper-case "?") first) "kis"))
(rf/reg-sub ::user-initials
  :<- [::user-data]
  (fn [{:keys [firstname lastname]} _ _]
    (let [initial (comp (fnil upper-case "?") first)]
      (str (initial firstname) (initial lastname)))))

(rf/reg-sub ::show-nav?
  :<- [::current-route]
  (fn [current-route _]
    (-> current-route :data :hide-nav? not)))

(rf/reg-sub ::screen-size
  (fn [db _]
    (-> db :screen-size)))
