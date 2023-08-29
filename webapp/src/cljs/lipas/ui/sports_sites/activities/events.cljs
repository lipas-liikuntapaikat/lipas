(ns lipas.ui.sports-sites.activities.events
  (:require
   #_[lipas.data.activities :as data]
   [ajax.core :as ajax]
   [clojure.string :as str]
   [goog.object :as gobj]
   [goog.string.path :as gpath]
   [lipas.utils :as utils]
   [re-frame.core :as re-frame]))

(re-frame/reg-event-fx
 ::add-route
 (fn [{:keys [db]} [_ lipas-id]]
   {:db (-> db
            (assoc-in [:sports-sites :activities :mode] :add-route)
            (assoc-in [:sports-sites :activities :selected-route-id] (str (random-uuid))))
    :fx [[:dispatch
          [:lipas.ui.map.events/start-editing lipas-id :selecting "LineString"]]]}))

(re-frame/reg-event-fx
 ::finish-route
 (fn [{:keys [db]} _]
   {:db (assoc-in db [:sports-sites :activities :mode] :route-details)
    :fx [#_[:dispatch [:lipas.ui.map.events/continue-editing]]]}))

(re-frame/reg-event-fx
 ::clear
 (fn [{:keys [db]} _]
   {:db (-> db
            (assoc-in [:sports-sites :activities :mode] :default)
            (assoc-in [:sports-sites :activities :selected-route-id] nil))}))

(re-frame/reg-event-fx
 ::finish-route-details
 (fn [{:keys [db]} [_ {:keys [fids route id lipas-id activity-k]}]]
   (let [edits                (get-in db [:sports-sites lipas-id :editing])
         current-routes       (get-in edits [:activities activity-k :routes] [])
         current-routes-by-id (utils/index-by :id current-routes)

         mode       (if (get current-routes-by-id id) :update :add)
         new-routes (condp = mode
                      :add    (conj current-routes
                                    (assoc route :fids fids :id id))
                      :update (-> current-routes-by-id
                                  (assoc id (assoc route :fids fids))
                                  vals
                                  vec))]
     {:db (-> db
              (assoc-in [:sports-sites :activities :mode] :default)
              (assoc-in [:sports-sites :activities :selected-route-id] nil))
      :fx [[:dispatch [:lipas.ui.map.events/continue-editing]]
           [:dispatch [:lipas.ui.sports-sites.events/edit-field lipas-id [:activities activity-k :routes]
                       new-routes]]]})))

(re-frame/reg-event-fx
 ::cancel-route-details
 (fn [{:keys [db]} _]
   {:db (assoc-in db [:sports-sites :activities :mode] :default)}))

(re-frame/reg-event-fx
 ::select-route
 (fn [{:keys [db]} [_ lipas-id {:keys [fids id] :as route}]]
   (println "Select route FIDS:" fids)
   {:db (-> db
            (assoc-in [:sports-sites :activities :mode] :route-details)
            (assoc-in [:sports-sites :activities :selected-route-id] id))
    :fx [[:dispatch [:lipas.ui.map.events/highlight-features fids]]]}))

(re-frame/reg-event-fx
 ::delete-route
 (fn [{:keys [db]} [_ lipas-id route-id]]
   (let [current-routes       (get-in db [:sports-sites lipas-id :editing :activities :routes] [])
         current-routes-by-id (utils/index-by :id current-routes)
         new-routes           (-> (dissoc current-routes-by-id route-id) vals vec)]
     {:db (-> db
              (assoc-in [:sports-sites :activities :mode] :default))
      :fx [[:dispatch [:lipas.ui.map.events/continue-editing]]
           [:dispatch [:lipas.ui.sports-sites.events/edit-field lipas-id [:activities :routes]
                       new-routes]]
           [:dispatch [:lipas.ui.map.events/highlight-features #{}]]]})))

(defn parse-ext [file]
  (-> file
      (gobj/get "name" "")
      gpath/extension
      str/lower-case))

(re-frame/reg-event-fx
 ::upload-image
 (fn [{:keys [db]} [_ files lipas-id cb]]
   (let [file  (aget files 0)
         ext   (parse-ext file)
         ext   (if (= "jpg" ext) "jpeg" ext)
         token (-> db :user :login :token)]
     {:http-xhrio
      {:method          :post
       :uri             (str (:backend-url db) "/actions/create-upload-url")
       :headers         {:Authorization (str "Token " token)}
       :params          {:extension ext
                         :lipas-id  lipas-id}
       :format          (ajax/transit-request-format)
       :response-format (ajax/transit-response-format)
       :on-success      [::upload-image-success file ext cb]
       :on-failure      [::upload-image-failure]}})))

(re-frame/reg-event-fx
 ::upload-image-failure
 (fn [{:keys [db]} _]
   (let [tr           (:translator db)
         notification {:message  (tr :notifications/save-failed)
                       :success? false}]
     {:dispatch [:lipas.ui.events/set-active-notification notification]})))

(re-frame/reg-event-fx
 ::upload-image-success
 (fn [{:keys [db]} [_ file ext cb resp]]
   (let [eventual-file-url (-> resp
                               :presigned-url
                               (str/split "?")
                               first)]
     {:http-xhrio
      {:method          :put
       :uri             (:presigned-url resp)
       :headers         (merge
                         {:Content-type (str "image/" ext)}
                         (:meta resp))
       :body            file
       :format          :raw
       :response-format (ajax/text-response-format)
       :on-success      [::upload-image-s3-success cb eventual-file-url]
       :on-failure      [::upload-image-s3-failure]}})))

(re-frame/reg-event-fx
 ::upload-image-s3-success
 (fn [{:keys [_db]} [_ cb file-url _resp]]
   (cb file-url)
   {}))

(re-frame/reg-event-fx
 ::upload-image-s3-failure
 (fn [{:keys [db]} [event-k resp]]
   (println event-k resp)
   (let [tr           (:translator db)
         notification {:message  (tr :notifications/save-failed)
                       :success? false}]
     {:dispatch [:lipas.ui.events/set-active-notification notification]})))
