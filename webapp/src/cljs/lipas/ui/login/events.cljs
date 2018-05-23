(ns lipas.ui.login.events
  (:require [ajax.core :as ajax]
            [goog.crypt.base64 :as b64]
            [camel-snake-kebab.core :refer [->kebab-case]]
            [camel-snake-kebab.extras :refer [transform-keys]]
            [lipas.ui.local-storage :refer [ls-set ls-get]]
            [re-frame.core :as re-frame]))

(re-frame/reg-event-db
 ::set-login-form-field
 (fn [db [_ path value]]
   (let [path (into [:user :login-form] path)]
     (assoc-in db path value))))

(def body->kebab-case
  (re-frame/->interceptor
   :id :kebab-case-interceptor
   :before (fn [context]
               (let [transform-fn (partial transform-keys ->kebab-case)]
                 (update-in context [:coeffects :event] transform-fn)))))

(re-frame/reg-event-fx
 ::login-success
 [(re-frame/after (fn [db _]
                    (ls-set :login-data (-> db :user :login))))
  body->kebab-case]
 (fn [{:keys [db]} [_ body]]
   (let [body body]
     {:db (-> db
              (assoc-in [:logged-in?] true)
              (assoc-in [:user :login] body))})))

(re-frame/reg-event-db
 ::login-failure
 (fn [db [_ result]]
   (assoc-in db [:user :login-error] result)))

(comment (->basic-auth {:username "kissa" :password "koira"}))
(defn ->basic-auth
  "Creates base64 encoded Authorization header value"
  [{:keys [username password]}]
  (str "Basic " (b64/encodeString (str username ":" password))))

(re-frame/reg-event-db
 ::clear-errors
 (fn [db [_ _]]
   (update-in db [:user] dissoc :login-error)))

(re-frame/reg-event-fx
 ::submit-login-form
 (fn [_ [_ form-data]]
   {:http-xhrio {:method          :post
                 :uri             "http://localhost:8091/actions/login"
                 :headers         {:Authorization (->basic-auth form-data)}
                 :format          (ajax/json-request-format)
                 :response-format (ajax/json-response-format {:keywords? true})
                 :on-success      [::login-success]
                 :on-failure      [::login-failure]}

    :dispatch    [::clear-errors]}))
