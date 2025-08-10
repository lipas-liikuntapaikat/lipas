(ns lipas.ui.sports-sites.activities.events
  (:require [ajax.core :as ajax]
            [clojure.string :as str]
            [goog.object :as gobj]
            [goog.string.path :as gpath]
            [lipas.ui.sports-sites.activities.itrs-migration :as itrs-migration]
            [lipas.utils :as utils]
            [re-frame.core :as rf]))

(rf/reg-event-fx ::init-edit-view
                 (fn [{:keys [db]} [_ lipas-id edit-data]]
                   #_(let [first-route (->> edit-data :activities vals (keep :routes) first first)]
                       {:fx [[:dispatch [::select-route lipas-id first-route]]]})
                   {}))

(rf/reg-event-fx ::add-route
                 (fn [{:keys [db]} [_ lipas-id]]
                   {:db (-> db
                            (assoc-in [:sports-sites :activities :mode] :add-route)
                            (assoc-in [:sports-sites :activities :selected-route-id] (str (random-uuid)))
                            ;; Clear any previously selected features to prevent ghost segments
                            (assoc-in [:map :mode :selected-features] #{}))
                    :fx [[:dispatch [:lipas.ui.map.events/start-editing lipas-id :selecting "LineString"]]]}))

(rf/reg-event-fx ::finish-route
                 (fn [{:keys [db]} _]
                   {:db (assoc-in db [:sports-sites :activities :mode] :route-details)
                    :fx []}))

(rf/reg-event-fx ::clear
                 (fn [{:keys [db]} _]
                   {:db (-> db
                            (assoc-in [:sports-sites :activities :mode] :default)
                            (assoc-in [:sports-sites :activities :selected-route-id] nil)
                            (assoc-in [:sports-sites :activities :route-view] nil))}))

(rf/reg-event-fx ::add-route-metadata
                 (fn [{:keys [db]} [_ lipas-id activity-k route-id]]
                   (let [new-route {:id (or route-id (str (random-uuid)))
                                    :route-name {}
                                    :fids #{}}
                         edits (get-in db [:sports-sites lipas-id :editing])
                         current-routes (get-in edits [:activities activity-k :routes] [])]
                     {:db db
                      :fx [[:dispatch [:lipas.ui.sports-sites.events/edit-field
                                       lipas-id
                                       [:activities activity-k :routes]
                                       (conj current-routes new-route)]]]})))

(rf/reg-event-fx ::save-route-attributes
                 (fn [{:keys [db]} [_ {:keys [lipas-id activity-k route-id attributes]}]]
                   (let [current-routes (get-in db [:sports-sites lipas-id :editing :activities activity-k :routes] [])
                         route-idx (first (keep-indexed #(when (= (:id %2) route-id) %1) current-routes))
                         ;; Handle both update and create cases
                         new-routes (if route-idx
                                      ;; Update existing route
                                      (let [current-route (nth current-routes route-idx)
                                            ;; Merge attributes while preserving geometry data (fids, segments)
                                            updated-route (merge current-route attributes {:id route-id})]
                                        (assoc current-routes route-idx updated-route))
                                      ;; Create new route if it doesn't exist (simple mode with no routes yet)
                                      (conj current-routes (assoc attributes :id route-id)))]
                     {:fx [[:dispatch [:lipas.ui.sports-sites.events/edit-field
                                       lipas-id
                                       [:activities activity-k :routes]
                                       new-routes]]]})))

(rf/reg-event-fx ::finish-route-details
                 (fn [{:keys [db]} [_ {:keys [fids route id lipas-id activity-k segments]}]]
                   (let [edits (get-in db [:sports-sites lipas-id :editing])
                         current-routes (get-in edits [:activities activity-k :routes] [])
                         current-routes-by-id (utils/index-by :id current-routes)

                         ;; Check if we're actually in editing mode
                         map-mode (get-in db [:map :mode :name])
                         in-editing-mode? (= map-mode :editing)

                         ;; Build the complete route with all data
                         ;; Note: route-length-km is now calculated via subscription and saved on commit
                         complete-route (merge route
                                               {:fids fids
                                                :id id}
                                               ;; Include segments if provided (from ordering)
                                               (when segments {:segments segments}))

                         mode (if (get current-routes-by-id id) :update :add)
                         new-routes (condp = mode
                                      :add (conj current-routes complete-route)
                                      :update (-> current-routes-by-id
                                                  (assoc id complete-route)
                                                  vals
                                                  vec))]
                     {:db (-> db
                              (assoc-in [:sports-sites :activities :mode] :default)
                              (assoc-in [:sports-sites :activities :selected-route-id] nil))
                      :fx (cond-> [[:dispatch [:lipas.ui.sports-sites.events/edit-field lipas-id [:activities activity-k :routes]
                                               new-routes]]]
                            ;; Only call continue-editing if we're actually in editing mode
                            in-editing-mode?
                            (conj [:dispatch [:lipas.ui.map.events/continue-editing :view-only]]))})))

(rf/reg-event-fx ::cancel-route-details
                 (fn [{:keys [db]} _]
                   {:db (assoc-in db [:sports-sites :activities :mode] :default)
                    :fx [[:dispatch [:lipas.ui.map.events/continue-editing]]]}))

(rf/reg-event-fx ::select-route
                 (fn [{:keys [db]} [_ lipas-id {:keys [fids id] :as route}]]
                   {:db (-> db
                            (assoc-in [:sports-sites :activities :mode] :route-details)
                            (assoc-in [:sports-sites :activities :selected-route-id] id))
                    :fx [[:dispatch [:lipas.ui.map.events/highlight-features (set fids)]]
                         #_[:dispatch [:lipas.ui.map.events/start-editing lipas-id :selecting "LineString"]]]}))

(rf/reg-event-fx ::delete-route
                 (fn [{:keys [db]} [_ lipas-id activity-k route-id]]
                   (let [current-routes (get-in db [:sports-sites lipas-id :editing :activities activity-k :routes] [])
                         current-routes-by-id (utils/index-by :id current-routes)
                         new-routes (-> (dissoc current-routes-by-id route-id) vals vec)

                         ;; Check if we're actually in editing mode
                         map-mode (get-in db [:map :mode :name])
                         in-editing-mode? (= map-mode :editing)]
                     {:db (-> db
                              (assoc-in [:sports-sites :activities :mode] :default))
                      :fx (cond-> [[:dispatch [:lipas.ui.sports-sites.events/edit-field lipas-id [:activities activity-k :routes]
                                               new-routes]]
                                   [:dispatch [:lipas.ui.map.events/clear-highlight]]]
                            ;; Only call continue-editing if we're actually in editing mode
                            in-editing-mode?
                            (conj [:dispatch [:lipas.ui.map.events/continue-editing]]))})))

(defn parse-ext [file]
  (-> file
      (gobj/get "name" "")
      gpath/extension
      str/lower-case))

(rf/reg-event-fx ::upload-utp-image
                 (fn [{:keys [db]} [_ files lipas-id cb]]
                   (let [file (aget files 0)
                         form-data (doto (js/FormData.)
                                     (.append "filename" (gobj/get file "name" ""))
                                     (.append "lipas-id" lipas-id)
                                     (.append "file" file))
                         token (-> db :user :login :token)]
                     {:http-xhrio
                      {:method :post
                       :uri (str (:backend-url db) "/actions/upload-utp-image")
                       :headers {:Authorization (str "Token " token)}
                       :body form-data
                       :response-format (ajax/transit-response-format)
                       :on-success [::upload-utp-image-success cb]
                       :on-failure [::upload-utp-image-failure]}})))

(rf/reg-event-fx ::upload-utp-image-success
                 (fn [{:keys [_db]} [_ cb resp]]
                   (cb resp)
                   {}))

(rf/reg-event-fx ::upload-utp-image-failure
                 (fn [{:keys [db]} [event-k resp]]
                   (let [tr (:translator db)
                         notification {:message (tr :notifications/save-failed)
                                       :success? false}]
                     {:dispatch [:lipas.ui.events/set-active-notification notification]})))

(rf/reg-event-fx ::upload-image
                 (fn [{:keys [db]} [_ files lipas-id cb]]
                   (let [file (aget files 0)
                         ext (parse-ext file)
                         ext (if (= "jpg" ext) "jpeg" ext)
                         token (-> db :user :login :token)]
                     {:http-xhrio
                      {:method :post
                       :uri (str (:backend-url db) "/actions/create-upload-url")
                       :headers {:Authorization (str "Token " token)}
                       :params {:extension ext
                                :lipas-id lipas-id}
                       :format (ajax/transit-request-format)
                       :response-format (ajax/transit-response-format)
                       :on-success [::upload-image-success file ext cb]
                       :on-failure [::upload-image-failure]}})))

(rf/reg-event-fx ::upload-image-failure
                 (fn [{:keys [db]} _]
                   (let [tr (:translator db)
                         notification {:message (tr :notifications/save-failed)
                                       :success? false}]
                     {:dispatch [:lipas.ui.events/set-active-notification notification]})))

(rf/reg-event-fx ::upload-image-success
                 (fn [{:keys [db]} [_ file ext cb resp]]
                   (let [eventual-file-url (-> resp
                                               :presigned-url
                                               (str/split "?")
                                               first)]
                     {:http-xhrio
                      {:method :put
                       :uri (:presigned-url resp)
                       :headers (merge
                                 {:Content-type (str "image/" ext)}
                                 (:meta resp))
                       :body file
                       :format :raw
                       :response-format (ajax/text-response-format)
                       :on-success [::upload-image-s3-success cb eventual-file-url]
                       :on-failure [::upload-image-s3-failure]}})))

(rf/reg-event-fx ::upload-image-s3-success
                 (fn [{:keys [_db]} [_ cb file-url _resp]]
                   (cb file-url)
                   {}))

(rf/reg-event-fx ::upload-image-s3-failure
                 (fn [{:keys [db]} [event-k resp]]
                   (let [tr (:translator db)
                         notification {:message (tr :notifications/save-failed)
                                       :success? false}]
                     {:dispatch [:lipas.ui.events/set-active-notification notification]})))

(rf/reg-event-fx ::select-route-view
                 (fn [{:keys [db]} [_ v]]
                   {:db (assoc-in db [:sports-sites :activities :route-view] v)}))

(rf/reg-event-fx ::migrate-route-to-itrs
                 (fn [{:keys [db]} [_ lipas-id activity-k route-id on-success]]
                   (let [itrs-migration itrs-migration/migrate-route-to-itrs
                         current-routes (get-in db [:sports-sites lipas-id :editing :activities activity-k :routes] [])
                         route-idx (first (keep-indexed #(when (= (:id %2) route-id) %1) current-routes))
                         route (nth current-routes route-idx)
                         migrated-route (itrs-migration route)
                         new-routes (assoc current-routes route-idx migrated-route)]
                     {:fx [[:dispatch [:lipas.ui.sports-sites.events/edit-field lipas-id [:activities activity-k :routes]
                                       new-routes]]
                           [:dispatch [:lipas.ui.events/set-active-notification
                                       {:message "Vaativuustiedot siirretty ITRS-luokitukseen"
                                        :success? true}]]]
                      :dispatch-later [{:ms 100 :dispatch (when on-success [on-success])}]})))
