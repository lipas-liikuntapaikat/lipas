(ns lipas.ui.sports-sites.activities.events
  (:require [ajax.core :as ajax]
            [clojure.string :as str]
            [goog.object :as gobj]
            [goog.string.path :as gpath]
            [lipas.utils :as utils]
            [re-frame.core :as rf]))

(defn- segments->direction-map
  "Convert segments to a map of fid -> travel direction string.
   When the same fid appears multiple times with different directions,
   the value is \"bidirectional\"."
  [segments]
  (reduce (fn [m {:keys [fid reversed?]}]
            (let [dir (if reversed? "end-to-start" "start-to-end")
                  existing (get m fid)]
              (cond
                (nil? existing)          (assoc m fid dir)
                (= existing dir)         m
                :else                    (assoc m fid "bidirectional"))))
          {} segments))

(defn- segments->label-map
  "Convert segments to a map of fid -> position label string.
   When the same fid appears multiple times, the label shows all positions (e.g. \"1, 3\")."
  [segments]
  (reduce (fn [m [idx {:keys [fid]}]]
            (let [label (str (inc idx))]
              (update m fid #(if % (str % ", " label) label))))
          {} (map-indexed vector segments)))

(defn- ensure-route-segments
  "Ensure a route has :segments derived from :fids if not already present.
   Also validates that segment fids reference existing features, dropping any
   stale references (e.g. when geometry was redrawn with new feature IDs)."
  [route all-features]
  (let [valid-fids (set (map :id all-features))]
    (if (seq (:segments route))
      ;; Validate existing segments against actual features
      (let [segments    (:segments route)
            valid-segs  (filterv #(contains? valid-fids (:fid %)) segments)]
        (if (= (count valid-segs) (count segments))
          route
          (assoc route :segments valid-segs)))
      ;; Legacy migration: generate segments from :fids
      (let [fids     (set (:fids route))
            segments (->> all-features
                          (filter #(contains? fids (:id %)))
                          (mapv (fn [f] {:fid (:id f) :reversed? false})))]
        (assoc route :segments segments)))))

(defn- get-routes-with-segments
  "Get routes from db with segments ensured from fids."
  [db lipas-id activity-k]
  (let [all-features (get-in db [:sports-sites lipas-id :editing :location :geometries :features] [])
        routes       (get-in db [:sports-sites lipas-id :editing :activities activity-k :routes] [])]
    (mapv #(ensure-route-segments % all-features) routes)))

(rf/reg-event-fx ::init-edit-view
                 (fn [{:keys [db]} [_ lipas-id edit-data]]
                   {}))

(rf/reg-event-fx ::init-routes
                 (fn [{:keys [db]} [_ lipas-id activity-k]]
                   ;; Don't reset map mode when user is actively selecting/editing route segments
                   (let [activities-mode (get-in db [:sports-sites :activities :mode])]
                     (when-not (#{:add-route :route-details} activities-mode)
                       (let [routes (get-in db [:sports-sites lipas-id :editing :activities activity-k :routes] [])
                             edit-data (get-in db [:sports-sites lipas-id :editing])]
                         (if (seq routes)
          ;; Routes already exist - set travel directions for the first route
                           (let [all-features (get-in edit-data [:location :geometries :features] [])
                                 first-route  (ensure-route-segments (first routes) all-features)
                                 segments     (:segments first-route)]
                             (when (seq segments)
                               {:fx [[:dispatch [:lipas.ui.map.events/continue-editing :view-only]]
                                     [:dispatch [:lipas.ui.map.events/set-segment-directions
                                                 (segments->direction-map segments)]]
                                     [:dispatch [:lipas.ui.map.events/set-segment-labels
                                                 (segments->label-map segments)]]]}))
          ;; No routes - create initial route
                           (let [all-features (get-in edit-data [:location :geometries :features] [])
                                 all-fids     (set (map :id all-features))
                                 segments     (mapv (fn [f] {:fid (:id f) :reversed? false}) all-features)
                                 initial-route {:id       (str (random-uuid))
                                                :route-name {:fi (:name edit-data)
                                                             :se (get-in edit-data [:name-localized :se])
                                                             :en (:name edit-data)}
                                                :fids     all-fids
                                                :segments segments}]
                             {:fx [[:dispatch [:lipas.ui.map.events/continue-editing :view-only]]
                                   [:dispatch [:lipas.ui.sports-sites.events/edit-field lipas-id
                                               [:activities activity-k :routes] [initial-route]]]
                                   [:dispatch [:lipas.ui.map.events/set-segment-directions
                                               (segments->direction-map segments)]]
                                   [:dispatch [:lipas.ui.map.events/set-segment-labels
                                               (segments->label-map segments)]]]})))))))

(rf/reg-event-fx ::add-route
                 (fn [{:keys [db]} [_ lipas-id activity-k]]
                   {:db (-> db
                            (assoc-in [:sports-sites :activities :mode] :add-route)
                            (assoc-in [:sports-sites :activities :selected-route-id] (str (random-uuid)))
                            ;; Clear stale mode data that would interfere with fresh selecting mode
                            (update-in [:map :mode] dissoc
                                       :segment-directions :segment-labels :selected-features))
                    :fx [[:dispatch
                          [:lipas.ui.map.events/start-editing lipas-id :selecting "LineString"]]]}))

(rf/reg-event-fx ::add-segments-to-route
                 (fn [{:keys [db]} [_ lipas-id activity-k route-id]]
                   (let [routes    (get-in db [:sports-sites lipas-id :editing :activities activity-k :routes] [])
                         route     (some #(when (= route-id (:id %)) %) routes)
                         fids      (set (map :fid (:segments route)))]
                     {:db (-> db
                              (assoc-in [:sports-sites :activities :mode] :add-route)
                              (assoc-in [:sports-sites :activities :selected-route-id] route-id)
                              (update-in [:map :mode] dissoc
                                         :segment-directions :segment-labels)
                              ;; Pre-select existing route segments so they show as highlighted
                              (assoc-in [:map :mode :selected-features] fids))
                      :fx [[:dispatch
                            [:lipas.ui.map.events/start-editing lipas-id :selecting "LineString"]]]})))

(rf/reg-event-fx ::finish-route
                 (fn [{:keys [db]} [_ lipas-id activity-k]]
                   (let [selected-fids (get-in db [:map :mode :selected-features])
                         route-id      (get-in db [:sports-sites :activities :selected-route-id])
                         ;; Check if this is adding segments to an existing route
                         existing-routes (get-in db [:sports-sites lipas-id :editing :activities activity-k :routes] [])
                         existing?       (some #(= route-id (:id %)) existing-routes)]
                     {:fx [[:dispatch [::finish-route-details
                                       {:fids             (vec selected-fids)
                                        :route            {}
                                        :id               route-id
                                        :lipas-id         lipas-id
                                        :activity-k       activity-k
                                        :return-to-route? existing?}]]]})))

(rf/reg-event-fx ::clear
                 (fn [{:keys [db]} _]
                   {:db (-> db
                            (assoc-in [:sports-sites :activities :mode] :default)
                            (assoc-in [:sports-sites :activities :selected-route-id] nil)
                            (assoc-in [:sports-sites :activities :route-view] nil))}))

(rf/reg-event-fx ::finish-route-details
                 (fn [{:keys [db]} [_ {:keys [fids route id lipas-id activity-k return-to-route?]}]]
                   (let [edits                (get-in db [:sports-sites lipas-id :editing])
                         current-routes       (get-in edits [:activities activity-k :routes] [])
                         current-routes-by-id (utils/index-by :id current-routes)

                         existing-route (get current-routes-by-id id)
                         mode           (if existing-route :update :add)

          ;; Build segments from fids, preserving existing segments
                         existing-segments (or (:segments existing-route) (:segments route))
                         existing-fid-set  (set (map :fid existing-segments))
                         new-fids          (remove existing-fid-set fids)
                         segments          (vec (concat (or existing-segments [])
                                                        (map (fn [fid] {:fid fid :reversed? false}) new-fids)))
                         all-fids          (set (map :fid segments))

                         route-with-segments (assoc route :fids all-fids :id id :segments segments)

                         new-routes (condp = mode
                                      :add    (conj current-routes route-with-segments)
                                      :update (-> current-routes-by-id
                                                  (assoc id route-with-segments)
                                                  vals
                                                  vec))]
                     {:db (-> db
                              (assoc-in [:sports-sites :activities :mode] :default)
                              (assoc-in [:sports-sites :activities :selected-route-id] nil))
                      :fx [[:dispatch [:lipas.ui.sports-sites.events/edit-field lipas-id [:activities activity-k :routes]
                                       new-routes]]
                           [:dispatch [:lipas.ui.map.events/continue-editing :view-only]]
                           [:dispatch [:lipas.ui.map.events/clear-segment-directions]]
                           [:dispatch [:lipas.ui.map.events/clear-segment-labels]]
                           (when return-to-route?
                             [:dispatch [::select-route lipas-id
                                         (assoc route-with-segments
                                                :segments segments)]])]})))

(rf/reg-event-fx ::cancel-route-details
                 (fn [{:keys [db]} _]
                   {:db (-> db
                            (assoc-in [:sports-sites :activities :mode] :default)
                            (assoc-in [:map :mode :selected-features] nil))
                    :fx [[:dispatch [:lipas.ui.map.events/continue-editing :view-only]]
                         [:dispatch [:lipas.ui.map.events/clear-segment-directions]]
                         [:dispatch [:lipas.ui.map.events/clear-segment-labels]]]}))

(rf/reg-event-fx ::select-route
                 (fn [{:keys [db]} [_ lipas-id {:keys [fids id segments] :as route}]]
                   {:db (-> db
                            (assoc-in [:sports-sites :activities :mode] :route-details)
                            (assoc-in [:sports-sites :activities :selected-route-id] id))
                    :fx [[:dispatch [:lipas.ui.map.events/highlight-features (set fids)]]
                         [:dispatch [:lipas.ui.map.events/continue-editing :view-only]]
                         (when (seq segments)
                           [:dispatch [:lipas.ui.map.events/set-segment-directions
                                       (segments->direction-map segments)]])
                         (when (seq segments)
                           [:dispatch [:lipas.ui.map.events/set-segment-labels
                                       (segments->label-map segments)]])]}))

(rf/reg-event-fx ::delete-route
                 (fn [{:keys [db]} [_ lipas-id activity-k route-id]]
                   (let [current-routes       (get-in db [:sports-sites lipas-id :editing :activities activity-k :routes] [])
                         current-routes-by-id (utils/index-by :id current-routes)
                         new-routes           (-> (dissoc current-routes-by-id route-id) vals vec)]
                     {:db (-> db
                              (assoc-in [:sports-sites :activities :mode] :default))
                      :fx [[:dispatch [:lipas.ui.map.events/continue-editing :view-only]]
                           [:dispatch [:lipas.ui.sports-sites.events/edit-field lipas-id [:activities activity-k :routes]
                                       new-routes]]
                           [:dispatch [:lipas.ui.map.events/clear-highlight]]
                           [:dispatch [:lipas.ui.map.events/clear-segment-directions]]
                           [:dispatch [:lipas.ui.map.events/clear-segment-labels]]]})))

(rf/reg-event-fx ::reorder-segments
                 (fn [{:keys [db]} [_ lipas-id activity-k route-id source-idx target-idx]]
                   (let [routes (get-routes-with-segments db lipas-id activity-k)
                         route    (first (filter #(= route-id (:id %)) routes))
                         segments (vec (:segments route))
                         item     (nth segments source-idx)
                         spliced  (into (subvec segments 0 source-idx)
                                        (subvec segments (inc source-idx)))
                         reordered (vec (concat (subvec spliced 0 target-idx)
                                                [item]
                                                (subvec spliced target-idx)))
                         new-routes (mapv (fn [r] (if (= route-id (:id r))
                                                    (assoc r :segments reordered)
                                                    r))
                                          routes)]
                     {:fx [[:dispatch [:lipas.ui.sports-sites.events/edit-field lipas-id [:activities activity-k :routes]
                                       new-routes]]
                           [:dispatch [:lipas.ui.map.events/set-segment-directions
                                       (segments->direction-map reordered)]]
                           [:dispatch [:lipas.ui.map.events/set-segment-labels
                                       (segments->label-map reordered)]]]})))

(rf/reg-event-fx ::toggle-segment-direction
                 (fn [{:keys [db]} [_ lipas-id activity-k route-id segment-idx]]
                   (let [routes (get-routes-with-segments db lipas-id activity-k)
                         new-routes (mapv (fn [r]
                                            (if (= route-id (:id r))
                                              (update-in r [:segments segment-idx :reversed?] not)
                                              r))
                                          routes)
                         active-route (first (filter #(= route-id (:id %)) new-routes))]
                     {:fx [[:dispatch [:lipas.ui.sports-sites.events/edit-field lipas-id [:activities activity-k :routes]
                                       new-routes]]
                           [:dispatch [:lipas.ui.map.events/set-segment-directions
                                       (segments->direction-map (:segments active-route))]]
                           [:dispatch [:lipas.ui.map.events/set-segment-labels
                                       (segments->label-map (:segments active-route))]]]})))

(rf/reg-event-fx ::remove-segment
                 (fn [{:keys [db]} [_ lipas-id activity-k route-id segment-idx]]
                   (let [routes (get-routes-with-segments db lipas-id activity-k)
                         new-routes (mapv (fn [r]
                                            (if (= route-id (:id r))
                                              (let [segments (vec (:segments r))
                                                    new-segs (into (subvec segments 0 segment-idx)
                                                                   (subvec segments (inc segment-idx)))]
                                                (assoc r :segments new-segs
                                                       :fids (vec (distinct (map :fid new-segs)))))
                                              r))
                                          routes)
                         active-route (first (filter #(= route-id (:id %)) new-routes))]
                     {:fx [[:dispatch [:lipas.ui.sports-sites.events/edit-field lipas-id [:activities activity-k :routes]
                                       new-routes]]
                           [:dispatch [:lipas.ui.map.events/set-segment-directions
                                       (segments->direction-map (:segments active-route))]]
                           [:dispatch [:lipas.ui.map.events/set-segment-labels
                                       (segments->label-map (:segments active-route))]]]})))

(rf/reg-event-fx ::duplicate-segment
                 (fn [{:keys [db]} [_ lipas-id activity-k route-id segment-idx]]
                   (let [routes (get-routes-with-segments db lipas-id activity-k)
                         new-routes (mapv (fn [r]
                                            (if (= route-id (:id r))
                                              (let [segments (vec (:segments r))
                                                    segment  (nth segments segment-idx)
                                                    dup      (update segment :reversed? not)
                                                    new-segs (vec (concat (subvec segments 0 (inc segment-idx))
                                                                          [dup]
                                                                          (subvec segments (inc segment-idx))))]
                                                (assoc r :segments new-segs))
                                              r))
                                          routes)
                         active-route (first (filter #(= route-id (:id %)) new-routes))]
                     {:fx [[:dispatch [:lipas.ui.sports-sites.events/edit-field lipas-id [:activities activity-k :routes]
                                       new-routes]]
                           [:dispatch [:lipas.ui.map.events/set-segment-directions
                                       (segments->direction-map (:segments active-route))]]
                           [:dispatch [:lipas.ui.map.events/set-segment-labels
                                       (segments->label-map (:segments active-route))]]]})))

(rf/reg-event-fx ::highlight-route
                 (fn [{:keys [db]} [_ lipas-id activity-k route-id]]
                   (if route-id
                     (let [routes   (get-routes-with-segments db lipas-id activity-k)
                           route    (first (filter #(= route-id (:id %)) routes))
                           segments (:segments route)]
                       {:fx [[:dispatch [:lipas.ui.map.events/highlight-features
                                         (set (map :fid segments))]]
                             [:dispatch [:lipas.ui.map.events/set-segment-directions
                                         (segments->direction-map segments)]]
                             [:dispatch [:lipas.ui.map.events/set-segment-labels
                                         (segments->label-map segments)]]]})
                     {:fx [[:dispatch [:lipas.ui.map.events/highlight-features #{}]]
                           [:dispatch [:lipas.ui.map.events/clear-segment-directions]]
                           [:dispatch [:lipas.ui.map.events/clear-segment-labels]]]})))

(rf/reg-event-fx ::highlight-segment
                 (fn [{:keys [db]} [_ lipas-id activity-k route-id segment-idx]]
                   (let [routes    (get-routes-with-segments db lipas-id activity-k)
                         route     (first (filter #(= route-id (:id %)) routes))
                         segments  (:segments route)
                         full-dir  (segments->direction-map segments)
                         full-lbl  (segments->label-map segments)]
                     (if-let [segment (when segment-idx (nth segments segment-idx nil))]
        ;; Hovering a specific segment: show only its direction for that fid
                       (let [{:keys [fid reversed?]} segment
                             dir (if reversed? "end-to-start" "start-to-end")]
                         {:fx [[:dispatch [:lipas.ui.map.events/highlight-features #{fid}]]
                               [:dispatch [:lipas.ui.map.events/set-segment-directions
                                           (assoc full-dir fid dir)]]
                               [:dispatch [:lipas.ui.map.events/set-segment-labels
                                           (assoc full-lbl fid (str (inc segment-idx)))]]]})
        ;; Mouse left: restore full direction and label maps
                       {:fx [[:dispatch [:lipas.ui.map.events/highlight-features #{}]]
                             [:dispatch [:lipas.ui.map.events/set-segment-directions full-dir]]
                             [:dispatch [:lipas.ui.map.events/set-segment-labels full-lbl]]]}))))

(defn parse-ext [file]
  (-> file
      (gobj/get "name" "")
      gpath/extension
      str/lower-case))

(rf/reg-event-fx ::upload-utp-image
                 (fn [{:keys [db]} [_ files lipas-id cb]]
                   (let [file      (aget files 0)
                         form-data (doto (js/FormData.)
                                     (.append "filename" (gobj/get file "name" ""))
                                     (.append "lipas-id" lipas-id)
                                     (.append "file" file))
                         token     (-> db :user :login :token)]
                     {:http-xhrio
                      {:method          :post
                       :uri             (str (:backend-url db) "/actions/upload-utp-image")
                       :headers         {:Authorization (str "Token " token)}
                       :body            form-data
                       :response-format (ajax/transit-response-format)
                       :on-success      [::upload-utp-image-success cb]
                       :on-failure      [::upload-utp-image-failure]}})))

(rf/reg-event-fx ::upload-utp-image-success
                 (fn [{:keys [_db]} [_ cb resp]]
                   (cb resp)
                   {}))

(rf/reg-event-fx ::upload-utp-image-failure
                 (fn [{:keys [db]} [event-k resp]]
                   (let [tr           (:translator db)
                         notification {:message  (tr :notifications/save-failed)
                                       :success? false}]
                     {:dispatch [:lipas.ui.events/set-active-notification notification]})))

(rf/reg-event-fx ::upload-image
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

(rf/reg-event-fx ::upload-image-failure
                 (fn [{:keys [db]} _]
                   (let [tr           (:translator db)
                         notification {:message  (tr :notifications/save-failed)
                                       :success? false}]
                     {:dispatch [:lipas.ui.events/set-active-notification notification]})))

(rf/reg-event-fx ::upload-image-success
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

(rf/reg-event-fx ::upload-image-s3-success
                 (fn [{:keys [_db]} [_ cb file-url _resp]]
                   (cb file-url)
                   {}))

(rf/reg-event-fx ::upload-image-s3-failure
                 (fn [{:keys [db]} [event-k resp]]
                   (let [tr           (:translator db)
                         notification {:message  (tr :notifications/save-failed)
                                       :success? false}]
                     {:dispatch [:lipas.ui.events/set-active-notification notification]})))

(rf/reg-event-fx ::select-route-view
                 (fn [{:keys [db]} [_ v]]
                   {:db (assoc-in db [:sports-sites :activities :route-view] v)}))
