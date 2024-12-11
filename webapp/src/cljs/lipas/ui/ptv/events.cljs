(ns lipas.ui.ptv.events
  (:require [ajax.core :as ajax]
            [clojure.set :as set]
            [clojure.string :as str]
            [lipas.data.ptv :as ptv-data]
            [lipas.ui.utils :as utils]
            [re-frame.core :as rf]))

(rf/reg-event-fx ::open-dialog
  (fn [{:keys [db]} [_ _]]
    {:db (assoc-in db [:ptv :dialog :open?] true)
     :fx [(when (:selected-org (:ptv db))
            [:dispatch [::select-org (:selected-org (:ptv db))]])]}))

(rf/reg-event-db ::close-dialog
  (fn [db [_ _]]
    (assoc-in db [:ptv :dialog :open?] false)))

(rf/reg-event-fx ::select-org
  (fn [{:keys [db]} [_ org]]
    {:db (assoc-in db [:ptv :selected-org] org)
     :fx [[:dispatch [::fetch-org-data org]]
          [:dispatch [::fetch-integration-candidates org]]]}))

(rf/reg-event-db ::select-tab
  (fn [db [_ v]]
    (assoc-in db [:ptv :selected-tab] v)))

(rf/reg-event-fx ::fetch-integration-candidates
  (fn [{:keys [db]} [_ org]]
    (when org
      (let [token (-> db :user :login :token)]
        {:db (assoc-in db [:ptv :loading-from-lipas :candidates] true)
         :fx [[:http-xhrio
               {:method          :post
                :headers         {:Authorization (str "Token " token)}
                :uri             (str (:backend-url db) "/actions/get-ptv-integration-candidates")
                :params          (-> (get ptv-data/org-id->params (:id org))
                                     (select-keys [:city-codes :type-codes :owners]))
                :format          (ajax/transit-request-format)
                :response-format (ajax/transit-response-format)
                :on-success      [::fetch-integration-candidates-success (:id org)]
                :on-failure      [::fetch-integration-candidates-failure]}]]}))))

(rf/reg-event-fx ::fetch-integration-candidates-success
  (fn [{:keys [db]} [_ org-id resp]]
    {:db (-> db
             (assoc-in [:ptv :loading-from-lipas :candidates] false)
             (assoc-in [:ptv :org org-id :data :sports-sites] (utils/index-by :lipas-id resp)))}))

(rf/reg-event-fx ::fetch-integration-candidates-failure
  (fn [{:keys [db]} [_ resp]]
    (let [tr           (:translator db)
          notification {:message  (tr :notifications/get-failed)
                        :success? false}]
      {:db (-> db
               (assoc-in [:ptv :loading-from-lipas :candidates] false)
               (assoc-in [:ptv :errors :candidates] resp))
       :fx [[:dispatch [:lipas.ui.events/set-active-notification notification]]]})))

(rf/reg-event-fx ::fetch-org-data
  (fn [{:keys [_db]} [_ org]]
    (when org
      {:fx [[:dispatch [::fetch-integration-candidates org]]
            [:dispatch [::fetch-org org]]
            [:dispatch [::fetch-services org]]
            [:dispatch [::fetch-service-channels org]]
            [:dispatch [::fetch-service-collections org]]]})))

(rf/reg-event-fx ::fetch-org
  (fn [{:keys [db]} [_ org]]
    (let [token (-> db :user :login :token)]
      (when org
        {:db (assoc-in db [:ptv :loading-from-ptv :org] true)
         :fx [[:http-xhrio
               {:method          :post
                :headers         {:Authorization (str "Token " token)}
                :uri             (str (:backend-url db) "/actions/fetch-ptv-org")
                :params          {:org-id (:id org)}
                :format          (ajax/transit-request-format)
                :response-format (ajax/transit-response-format)
                :on-success      [::fetch-org-success (:id org)]
                :on-failure      [::fetch-org-failure]}]]}))))

(rf/reg-event-fx ::fetch-org-success
  (fn [{:keys [db]} [_ org-id resp]]
    {:db (-> db
             (assoc-in [:ptv :loading-from-ptv :org] false)
             (assoc-in [:ptv :org org-id :data :org org-id] resp))}))

(rf/reg-event-fx ::fetch-org-failure
  (fn [{:keys [db]} [_ resp]]
    (let [tr           (:translator db)
          notification {:message  (tr :notifications/get-failed)
                        :success? false}]
      {:db (-> db
               (assoc-in [:ptv :loading-from-ptv :org] false)
               (assoc-in [:ptv :errors :org] resp))
       :fx [[:dispatch [:lipas.ui.events/set-active-notification notification]]]})))

(rf/reg-event-fx ::fetch-services
  (fn [{:keys [db]} [_ org]]
    (let [token (-> db :user :login :token)]
      (when org
        {:db (assoc-in db [:ptv :loading-from-ptv :services] true)
         :fx [[:http-xhrio
               {:method          :post
                :headers         {:Authorization (str "Token " token)}
                :uri             (str (:backend-url db) "/actions/fetch-ptv-services")
                :params          {:org-id (:id org)}
                :format          (ajax/transit-request-format)
                :response-format (ajax/json-response-format {:keywords? true})
                :on-success      [::fetch-services-success (:id org)]
                :on-failure      [::fetch-services-failure]}]]}))))

(rf/reg-event-fx ::fetch-services-success
  (fn [{:keys [db]} [_ org-id resp]]
    (let [services (->> resp :itemList (utils/index-by :id))]
      {:db (-> db
               (assoc-in [:ptv :loading-from-ptv :services] false)
               (assoc-in [:ptv :org org-id :data :services] services))
       :fx [[:dispatch [::assign-services-to-sports-sites]]]})))

(rf/reg-event-fx ::fetch-services-failure
  (fn [{:keys [db]} [_ resp]]
    (let [tr           (:translator db)
          notification {:message  (tr :notifications/get-failed)
                        :success? false}]
      {:db (-> db
               (assoc-in [:ptv :loading-from-ptv :services] false)
               (assoc-in [:ptv :errors :services] resp))
       :fx [[:dispatch [:lipas.ui.events/set-active-notification notification]]]})))

(rf/reg-event-fx ::fetch-service-channels
  (fn [{:keys [db]} [_ org]]
    (when org
      (let [token (-> db :user :login :token)]
        {:db (assoc-in db [:ptv :loading-from-ptv :service-channels] true)
         :fx [[:http-xhrio
               {:method          :post
                :headers         {:Authorization (str "Token " token)}
                :uri             (str (:backend-url db) "/actions/fetch-ptv-service-channels")
                :params          {:org-id (:id org)}
                :format          (ajax/transit-request-format)
                :response-format (ajax/json-response-format {:keywords? true})
                :on-success      [::fetch-service-channels-success (:id org)]
                :on-failure      [::fetch-service-channels-failure]}]]}))))

(rf/reg-event-fx ::fetch-service-channels-success
  (fn [{:keys [db]} [_ org-id resp]]
    (let [service-channels (->> resp :itemList (utils/index-by :id))]
      {:db (-> db
               (assoc-in [:ptv :loading-from-ptv :service-channels] false)
               (assoc-in [:ptv :org org-id :data :service-channels] service-channels))})))

(rf/reg-event-fx ::fetch-service-channels-failure
  (fn [{:keys [db]} [_ resp]]
    (let [tr           (:translator db)
          notification {:message  (tr :notifications/get-failed)
                        :success? false}]
      {:db (-> db
               (assoc-in [:ptv :loading-from-ptv :service-channels] false)
               (assoc-in [:ptv :errors :service-channels] resp))
       :fx [[:dispatch [:lipas.ui.events/set-active-notification notification]]]})))

(rf/reg-event-fx ::fetch-service-collections
  (fn [{:keys [db]} [_ org]]
    (when org
      (let [token (-> db :user :login :token)]
        {:db (assoc-in db [:ptv :loading-from-ptv :service-collections] true)
         :fx [[:http-xhrio
               {:method          :post
                :headers         {:Authorization (str "Token " token)}
                :uri             (str (:backend-url db) "/actions/fetch-ptv-service-collections")
                :params          {:org-id (:id org)}
                :format          (ajax/transit-request-format)
                :response-format (ajax/json-response-format {:keywords? true})
                :on-success      [::fetch-service-collections-success (:id org)]
                :on-failure      [::fetch-service-collections-failure]}]]}))))

(rf/reg-event-fx ::fetch-service-collections-success
  (fn [{:keys [db]} [_ org-id resp]]
    (let [service-collections (->> resp :itemList (utils/index-by :id))]
      {:db (-> db
               (assoc-in [:ptv :loading-from-ptv :service-collections] false)
               (assoc-in [:ptv :org org-id :data :service-collections] service-collections))})))

(rf/reg-event-fx ::fetch-service-collections-failure
  (fn [{:keys [db]} [_ resp]]
    (let [tr           (:translator db)
          notification {:message  (tr :notifications/get-failed)
                        :success? false}]
      {:db (-> db
               (assoc-in [:ptv :loading-from-ptv :service-collections] false)
               (assoc-in [:ptv :errors :service-collections] resp))
       :fx [[:dispatch [:lipas.ui.events/set-active-notification notification]]]})))

;;; Services views and manipulation ;;;

(rf/reg-event-db ::toggle-services-filter
  (fn [db _]
    (let [current-val (get-in db [:ptv :services-filter])
          new-val     (if (= current-val "lipas-managed") "lol" "lipas-managed")]
      (assoc-in db [:ptv :services-filter] new-val))))

;;; Service locations views and manipulation ;;;

(rf/reg-event-db ::toggle-sync-enabled
  (fn [db [_ {:keys [lipas-id]} v]]
    (let [org-id (get-in db [:ptv :selected-org :id])]
      (assoc-in db [:ptv :org org-id :data :sports-sites lipas-id :ptv :sync-enabled] v))))

(rf/reg-event-db ::select-services
  (fn [db [_ {:keys [lipas-id]} v]]
    (let [org-id (get-in db [:ptv :selected-org :id])]
      (assoc-in db [:ptv :org org-id :data :sports-sites lipas-id :ptv :service-ids] v))))

(rf/reg-event-db ::select-service-channels
  (fn [db [_ {:keys [lipas-id]} v]]
    (let [org-id (get-in db [:ptv :selected-org :id])]
      (assoc-in db [:ptv :org org-id :data :sports-sites lipas-id :ptv :service-channel-ids] v))))

(rf/reg-event-db ::select-service-integration
  (fn [db [_ {:keys [lipas-id]} v]]
    (let [org-id (get-in db [:ptv :selected-org :id])]
      (assoc-in db [:ptv :org org-id :data :sports-sites lipas-id :ptv :service-integration] v))))

(rf/reg-event-db ::select-service-channel-integration
  (fn [db [_ {:keys [lipas-id]} v]]
    (let [org-id (get-in db [:ptv :selected-org :id])]
      (assoc-in db [:ptv :org org-id :data :sports-sites lipas-id :ptv :service-channel-integration] v))))

(rf/reg-event-db ::select-descriptions-integration
  (fn [db [_ {:keys [lipas-id]} v]]
    (let [org-id (get-in db [:ptv :selected-org :id])]
      (assoc-in db [:ptv :org org-id :data :sports-sites lipas-id :ptv :descriptions-integration] v))))

(rf/reg-event-db ::select-service-integration-default
  (fn [db [_ v]]
    (let [org-id (get-in db [:ptv :selected-org :id])]
      (assoc-in db [:ptv :org org-id :default-settings :service-integration] v))))

(rf/reg-event-db ::select-service-channel-integration-default
  (fn [db [_ v]]
    (let [org-id (get-in db [:ptv :selected-org :id])]
      (assoc-in db [:ptv :org org-id :default-settings :service-channel-integration] v))))

(rf/reg-event-db ::select-descriptions-integration-default
  (fn [db [_ v]]
    (let [org-id (get-in db [:ptv :selected-org :id])]
      (assoc-in db [:ptv :org org-id :default-settings :descriptions-integration] v))))

(rf/reg-event-db ::select-integration-interval
  (fn [db [_ v]]
    (let [org-id (get-in db [:ptv :selected-org :id])]
      (assoc-in db [:ptv :org org-id :default-settings :integration-interval] v))))

(rf/reg-event-db ::set-summary
  (fn [db [_ {:keys [lipas-id]} locale v]]
    (let [org-id (get-in db [:ptv :selected-org :id])]
      (assoc-in db [:ptv :org org-id :data :sports-sites lipas-id :ptv :summary locale] v))))

(rf/reg-event-db ::set-description
  (fn [db [_ {:keys [lipas-id]} locale v]]
    (let [org-id (get-in db [:ptv :selected-org :id])]
      (assoc-in db [:ptv :org org-id :data :sports-sites lipas-id :ptv :description locale] v))))

;;; Service location descriptions generation ;;;

(rf/reg-event-fx ::generate-descriptions
  (fn [{:keys [db]} [_ lipas-id success-fx failure-fx]]
    (let [token (-> db :user :login :token)]
      {:db (assoc-in db [:ptv :loading-from-lipas :descriptions] true)
       :fx [[:http-xhrio
             {:method  :post
              :headers {:Authorization (str "Token " token)}
              :uri     (str (:backend-url db) "/actions/generate-ptv-descriptions")

              :params          {:lipas-id lipas-id}
              :format          (ajax/transit-request-format)
              :response-format (ajax/transit-response-format)
              :on-success      [::generate-descriptions-success lipas-id success-fx]
              :on-failure      [::generate-descriptions-failure lipas-id failure-fx]}]]})))

(rf/reg-event-fx ::generate-descriptions-success
  (fn [{:keys [db]} [_ lipas-id extra-fx resp]]
    (let [org-id (get-in db [:ptv :selected-org :id])]
      {:db (-> db
               (assoc-in [:ptv :loading-from-lipas :descriptions] false)
               (update-in [:ptv :org org-id :data :sports-sites lipas-id :ptv] merge resp))
       :fx extra-fx})))

(rf/reg-event-fx ::generate-descriptions-failure
  (fn [{:keys [db]} [_ _lipas-id extra-fx resp]]
    (let [tr           (:translator db)
          notification {:message  (tr :notifications/get-failed)
                        :success? false}]
      {:db (-> db
               (assoc-in [:ptv :loading-from-lipas :descriptions] false)
               (assoc-in [:ptv :errors :descriptions] resp))
       :fx (or extra-fx
               [[:dispatch [:lipas.ui.events/set-active-notification notification]]])})))

(rf/reg-event-fx ::generate-descriptions-from-data
  (fn [{:keys [db]} [_ lipas-id]]
    (let [token (-> db :user :login :token)
          edit-data (-> db :sports-sites (get lipas-id) :editing)]
      {:db (assoc-in db [:ptv :loading-from-lipas :descriptions] true)
       :fx [[:http-xhrio
             {:method  :post
              :headers {:Authorization (str "Token " token)}
              :uri     (str (:backend-url db) "/actions/generate-ptv-descriptions-from-data")
              ;; :ptv data isn't used as AI input, and the data might not we valid spec yet?
              :params          (utils/make-saveable (dissoc edit-data :ptv))
              :format          (ajax/transit-request-format)
              :response-format (ajax/transit-response-format)
              :on-success      [::generate-descriptions-from-data-success lipas-id]
              :on-failure      [::generate-descriptions-from-data-failure lipas-id]}]]})))

(rf/reg-event-fx ::generate-descriptions-from-data-success
  (fn [{:keys [db]} [_ lipas-id resp]]
    {:db (-> db
             (assoc-in [:ptv :loading-from-lipas :descriptions] false)
             (update-in [:sports-sites lipas-id :editing :ptv] merge resp))}))

(rf/reg-event-fx ::generate-descriptions-from-data-failure
  (fn [{:keys [db]} [_]]
    (let [tr           (:translator db)
          notification {:message  (tr :notifications/get-failed)
                        :success? false}]
      {:db (-> db
               (assoc-in [:ptv :loading-from-lipas :descriptions] false))
       :fx [[:dispatch [:lipas.ui.events/set-active-notification notification]]]})))

(rf/reg-event-fx ::translate-to-other-langs
  (fn [{:keys [db]} [_ lipas-id y]]
    (let [token (-> db :user :login :token)
          edit-data (-> db :sports-sites (get lipas-id) :editing)
          ptv (-> edit-data :ptv)
          data (assoc y
                      :summary (-> (:summary ptv) (get (keyword (:from y))))
                      :description (-> (:description ptv) (get (keyword (:from y)))))]
      {:db (assoc-in db [:ptv :loading-from-lipas :descriptions] true)
       :fx [[:http-xhrio
             {:method  :post
              :headers {:Authorization (str "Token " token)}
              :uri     (str (:backend-url db) "/actions/translate-to-other-langs")
              :params          data
              :format          (ajax/transit-request-format)
              :response-format (ajax/transit-response-format)
              :on-success      [::translate-to-other-langs-success lipas-id]
              :on-failure      [::translate-to-other-langs-failure lipas-id]}]]})))

(rf/reg-event-fx ::translate-to-other-langs-success
  (fn [{:keys [db]} [_ lipas-id resp]]
    {:db (-> db
             (assoc-in [:ptv :loading-from-lipas :descriptions] false)
             (update-in [:sports-sites lipas-id :editing :ptv] merge resp))}))

(rf/reg-event-fx ::translate-to-other-langs-failure
  (fn [{:keys [db]} [_]]
    (let [tr           (:translator db)
          notification {:message  (tr :notifications/get-failed)
                        :success? false}]
      {:db (-> db
               (assoc-in [:ptv :loading-from-lipas :descriptions] false))
       :fx [[:dispatch [:lipas.ui.events/set-active-notification notification]]]})))

(rf/reg-event-db ::toggle-sync-all
  (fn [db [_ enabled]]
    (let [org-id (get-in db [:ptv :selected-org :id])]
      (update-in db
                 [:ptv :org org-id :data :sports-sites]
                 (fn [ms]
                   (reduce (fn [res lipas-id]
                             (assoc-in res [lipas-id :ptv :sync-enabled] enabled))
                           ms
                           (keys ms)))))))

(rf/reg-event-fx ::generate-all-descriptions*
  (fn [{:keys [db]} [_ org-id lipas-ids]]
    (let [halt?             (get-in db [:ptv :batch-descriptions-generation :halt?] false)
          lipas-ids*        (get-in db [:ptv :batch-descriptions-generation :lipas-ids])
          processed         (set/difference (set lipas-ids*) (set lipas-ids))
          on-single-success [[:dispatch [::generate-all-descriptions* org-id (rest lipas-ids)]]]
          on-single-failure [#_[:dispatch [::generate-all-descriptions* org-id (rest lipas-ids)]]
                             [:dispatch [::halt-descriptions-generation]]]]
      {:db (-> db
               (update-in [:ptv :batch-descriptions-generation]
                          merge
                          {:in-progress?        (if halt? false (boolean (seq lipas-ids)))
                           :processed-lipas-ids processed
                           :halt?               halt?}))
       :fx [(when (and (not halt?) (seq lipas-ids))
              [:dispatch [::generate-descriptions
                          (first lipas-ids)
                          on-single-success
                          on-single-failure]])]})))

(rf/reg-event-fx ::generate-all-descriptions
  (fn [{:keys [db]} [_ sports-sites]]
    (let [org-id    (-> db :ptv :selected-org :id)
          lipas-ids (map :lipas-id sports-sites)]
      {:db (update-in db [:ptv :batch-descriptions-generation]
                      merge
                      {:batch-size (count lipas-ids)
                       :halt?      false
                       :size       (count lipas-ids)
                       :lipas-ids  (set lipas-ids)})
       :fx [[:dispatch [::generate-all-descriptions* org-id lipas-ids]]]})))

(rf/reg-event-db ::halt-descriptions-generation
  (fn [db _]
    (-> db
        (assoc-in [:ptv :batch-descriptions-generation :halt?] true))))

(rf/reg-event-db ::select-sports-sites-filter
  (fn [db [_ v]]
    (assoc-in db [:ptv :batch-descriptions-generation :sports-sites-filter] v)))

;;; Service descriptions generation ;;;

(rf/reg-event-fx ::generate-service-descriptions
  (fn [{:keys [db]} [_ org-id id overview success-fx failure-fx]]
    (let [token  (-> db :user :login :token)]
      {:db (assoc-in db [:ptv :loading-from-lipas :service-descriptions] true)
       :fx [[:http-xhrio
             {:method  :post
              :headers {:Authorization (str "Token " token)}
              :uri     (str (:backend-url db) "/actions/generate-ptv-service-descriptions")

              :params          {:city-codes      (:city-codes (ptv-data/org-id->params org-id))
                                :sub-category-id (parse-long (last (str/split id #"-")))
                                :overview        overview}
              :format          (ajax/transit-request-format)
              :response-format (ajax/transit-response-format)
              :on-success      [::generate-service-descriptions-success org-id id success-fx]
              :on-failure      [::generate-service-descriptions-failure org-id id failure-fx]}]]})))

(rf/reg-event-fx ::generate-service-descriptions-success
  (fn [{:keys [db]} [_ org-id id extra-fx resp]]
    {:db (-> db
             (assoc-in [:ptv :loading-from-lipas :lipas-managed-service-descriptions] false)
             (update-in [:ptv :org org-id :data :service-candidates id] merge resp))
     :fx extra-fx}))

(rf/reg-event-fx ::generate-service-descriptions-failure
  (fn [{:keys [db]} [_ _org-id _id extra-fx resp]]
    (let [tr           (:translator db)
          notification {:message  (tr :notifications/get-failed)
                        :success? false}]
      {:db (-> db
               (assoc-in [:ptv :loading-from-lipas :service-descriptions] false)
               (assoc-in [:ptv :errors :service-descriptions] resp))
       :fx (or extra-fx
               [[:dispatch [:lipas.ui.events/set-active-notification notification]]])})))

(rf/reg-event-fx ::generate-all-service-descriptions*
  (fn [{:keys [db]} [_ org-id ids]]
    (let [halt?             (get-in db [:ptv :service-descriptions-generation :halt?] false)
          ids*              (get-in db [:ptv :service-descriptions-generation :ids])
          processed         (set/difference (set ids*) (set ids))
          on-single-success [[:dispatch [::generate-all-service-descriptions* org-id (rest ids)]]]
          on-single-failure [[:dispatch [::halt-service-descriptions-generation]]]]
      {:db (-> db
               (update-in [:ptv :service-descriptions-generation]
                          merge
                          {:in-progress?  (if halt? false (boolean (seq ids)))
                           :processed-ids processed
                           :halt?         halt?}))
       :fx [(when (and (not halt?) (seq ids))
              [:dispatch [::generate-service-descriptions
                          org-id
                          (first ids)
                          nil
                          on-single-success
                          on-single-failure]])]})))

(rf/reg-event-fx ::generate-all-service-descriptions
  (fn [{:keys [db]} [_ ms]]
    (let [org-id (-> db :ptv :selected-org :id)
          ids    (map :source-id ms)]
      {:db (update-in db [:ptv :service-descriptions-generation]
                      merge
                      {:batch-size (count ids)
                       :halt?      false
                       :size       (count ids)
                       :ids        (set ids)})
       :fx [[:dispatch [::generate-all-service-descriptions* org-id ids]]]})))

(rf/reg-event-db ::halt-service-descriptions-generation
  (fn [db _]
    (-> db
        (assoc-in [:ptv :service-descriptions-generation :halt?] true))))

(rf/reg-event-db ::set-service-candidate-summary
  (fn [db [_ id locale v]]
    (let [org-id (get-in db [:ptv :selected-org :id])]
      (assoc-in db [:ptv :org org-id :data :service-candidates id :summary locale] v))))

(rf/reg-event-db ::set-service-candidate-description
  (fn [db [_ id locale v]]
    (let [org-id (get-in db [:ptv :selected-org :id])]
      (assoc-in db [:ptv :org org-id :data :service-candidates id :description locale] v))))

;;; Create Services in PTV ;;;

(rf/reg-event-fx ::create-ptv-service
  (fn [{:keys [db]} [_ org-id id data success-fx failure-fx]]
    (let [token  (-> db :user :login :token)]
      {:db (assoc-in db [:ptv :loading-from-lipas :services] true)
       :fx [[:http-xhrio
             {:method          :post
              :headers         {:Authorization (str "Token " token)}
              :uri             (str (:backend-url db) "/actions/save-ptv-service")
              :params          (merge (select-keys (ptv-data/org-id->params org-id) [:org-id :city-codes])
                                      data)
              :format          (ajax/transit-request-format)
              :response-format (ajax/transit-response-format)
              :on-success      [::create-ptv-service-success org-id id success-fx]
              :on-failure      [::create-ptv-service-failure org-id id failure-fx]}]]})))

(rf/reg-event-fx ::create-ptv-service-success
  (fn [{:keys [db]} [_ org-id id extra-fx resp]]
    {:db (-> db
             (assoc-in [:ptv :loading-from-lipas :services] false)
             (assoc-in [:ptv :org org-id :data :services (:id resp)] resp)
             (update-in [:ptv :org org-id :data :service-candidates id]
                        assoc :created-in-ptv true)
             (update-in [:ptv :org org-id :data :manual-services]
                        dissoc (:sourceId resp)))
     :fx extra-fx}))

(rf/reg-event-fx ::create-ptv-service-failure
  (fn [{:keys [db]} [_ _org-id _id extra-fx resp]]
    (let [tr           (:translator db)
          notification {:message  (tr :notifications/get-failed)
                        :success? false}]
      {:db (-> db
               (assoc-in [:ptv :loading-from-lipas :services] false)
               (assoc-in [:ptv :errors :services-creation] resp))
       :fx (or extra-fx
               [[:dispatch [:lipas.ui.events/set-active-notification notification]]])})))

(rf/reg-event-fx ::create-all-ptv-services*
  (fn [{:keys [db]} [_ org-id ids]]
    (let [halt?             (get-in db [:ptv :services-creation :halt?] false)
          ids*              (get-in db [:ptv :services-creation :ids])
          processed         (set/difference (set ids*) (set ids))
          on-single-success [[:dispatch [::create-all-ptv-services* org-id (rest ids)]]
                             [:dispatch [::assign-services-to-sports-sites]]]
          on-single-failure [[:dispatch [::halt-services-creation]]]]
      {:db (-> db
               (update-in [:ptv :services-creation]
                          merge
                          {:in-progress?  (if halt? false (boolean (seq ids)))
                           :processed-ids processed
                           :halt?         halt?}))
       :fx [(when (and (not halt?) (seq ids))
              [:dispatch [::create-ptv-service
                          org-id
                          (first ids)
                          (get-in db [:ptv :services-creation :data (first ids)])
                          on-single-success
                          on-single-failure]])]})))

(rf/reg-event-fx ::create-all-ptv-services
  (fn [{:keys [db]} [_ ms]]
    (let [org-id (-> db :ptv :selected-org :id)
          ids    (map :source-id ms)]
      {:db (update-in db [:ptv :services-creation]
                      merge
                      {:batch-size (count ids)
                       :halt?      false
                       :size       (count ids)
                       ;; TODO: Is this necessary? Maybe?
                       :data       (utils/index-by :source-id ms)
                       :ids        (set ids)})
       :fx [[:dispatch [::create-all-ptv-services* org-id ids]]]})))

(rf/reg-event-db ::halt-services-creation
  (fn [db _]
    (-> db
        (assoc-in [:ptv :services-creation :halt?] true))))

(rf/reg-event-db ::assign-services-to-sports-sites
  (fn [db _]
    (let [org-id   (get-in db [:ptv :selected-org :id])
          types    (get-in db [:sports-sites :types])
          source-id->service (->> (get-in db [:ptv :org org-id :data :services])
                                  vals
                                  (utils/index-by :sourceId))
          sports-sites (get-in db [:ptv :org org-id :data :sports-sites])
          sports-sites (reduce-kv
                         (fn [sports-sites lipas-id sports-site]
                           (let [;; the next function needs this!
                                 ;; this is "really" added to the :ptv data later in ::create-ptv-service-location
                                 sports-site (assoc-in sports-site [:ptv :org-id] org-id)
                                 lipas-service-ids (ptv-data/sports-site->service-ids types source-id->service sports-site)]
                             (if (seq lipas-service-ids)
                               (update-in sports-sites [lipas-id :ptv :service-ids] (fn [x]
                                                                                      (vec (into (set x) lipas-service-ids))))
                               sports-sites)))
                         sports-sites
                         sports-sites)]
      (assoc-in db [:ptv :org org-id :data :sports-sites] sports-sites))))

;;; Create service locations in PTV ;;;

(rf/reg-event-fx ::create-ptv-service-location
  (fn [{:keys [db]} [_ lipas-id success-fx failure-fx]]
    (let [token  (-> db :user :login :token)
          ;; Or per site?
          org-id (-> db :ptv :selected-org :id)

          sports-site  (get-in db [:ptv :org org-id :data :sports-sites lipas-id])

          ;; Add default org-id for service-ids linking
          sports-site  (update sports-site :ptv #(merge {:org-id org-id} %))

          ;; Add other defaults and merge with summary/description from the UI
          ptv-data     (merge (select-keys (:default-settings (:ptv db))
                                           [:sync-enabled])
                              {:service-channel-ids []}
                              (select-keys (:ptv sports-site)
                                           [:org-id
                                            :sync-enabled
                                            :service-channel-ids
                                            :service-ids
                                            :summary
                                            :description]))]
      {:db (assoc-in db [:ptv :loading-from-lipas :service-locations] true)
       :fx [[:http-xhrio
             {:method          :post
              :headers         {:Authorization (str "Token " token)}
              :uri             (str (:backend-url db) "/actions/save-ptv-service-location")
              :params          {:lipas-id lipas-id
                                :org-id org-id
                                :ptv ptv-data}
              :format          (ajax/transit-request-format)
              :response-format (ajax/transit-response-format)
              :on-success      [::create-ptv-service-location-success lipas-id success-fx]
              :on-failure      [::create-ptv-service-location-failure lipas-id failure-fx]}]]})))

(rf/reg-event-fx ::create-ptv-service-location-success
  (fn [{:keys [db]} [_ lipas-id extra-fx {:keys [ptv-resp ptv]}]]
    (let [org-id   (get-in db [:ptv :selected-org :id])]
      {:db (-> db
               (assoc-in [:ptv :loading-from-lipas :service-channels] false)
               (assoc-in [:ptv :org org-id :data :service-channels (:id ptv-resp)] ptv-resp)
               ;; Update the lipas TS also, it will be the same TS as PTV last-sync now
               (assoc-in [:ptv :org org-id :data :sports-sites lipas-id :event-date] (:last-sync ptv))
               (assoc-in [:ptv :org org-id :data :sports-sites lipas-id :ptv] ptv))
       :fx extra-fx})))

(rf/reg-event-fx ::create-ptv-service-location-failure
  (fn [{:keys [db]} [_ _id extra-fx resp]]
    (let [tr           (:translator db)
          notification {:message  (tr :notifications/get-failed)
                        :success? false}]
      {:db (-> db
               (assoc-in [:ptv :loading-from-lipas :service-locations] false)
               (assoc-in [:ptv :errors :service-locations-creation] resp))
       :fx (or extra-fx
               [[:dispatch [:lipas.ui.events/set-active-notification notification]]])})))

(rf/reg-event-fx ::create-all-ptv-service-locations*
  (fn [{:keys [db]} [_ org-id ids]]
    (let [halt?             (get-in db [:ptv :service-locations-creation :halt?] false)
          ids*              (get-in db [:ptv :service-locations-creation :ids])
          processed         (set/difference (set ids*) (set ids))
          on-single-success [[:dispatch [::create-all-ptv-service-locations* org-id (rest ids)]]]
          on-single-failure [[:dispatch [::halt-service-locations-creation]]]]
      {:db (-> db
               (update-in [:ptv :service-locations-creation]
                          merge
                          {:in-progress?  (if halt? false (boolean (seq ids)))
                           :processed-ids processed
                           :halt?         halt?}))
       :fx [(when (and (not halt?) (seq ids))
              [:dispatch [::create-ptv-service-location
                          (first ids)
                          on-single-success
                          on-single-failure]])]})))

(rf/reg-event-fx ::create-all-ptv-service-locations
  (fn [{:keys [db]} [_ sports-sites]]
    (let [org-id (-> db :ptv :selected-org :id)

          {:keys [to-sync to-save]} (group-by (fn [m]
                                                (if (:sync-enabled m)
                                                  :to-sync
                                                  :to-save))
                                              sports-sites)

          ids (map :lipas-id to-sync)]

      (println "To sync: " (count to-sync))
      (println "to save: " (count to-save))

      {:db (update-in db [:ptv :service-locations-creation]
                      merge
                      {:batch-size (count ids)
                       :halt?      false
                       :size       (count ids)
                       :ids        (set ids)})
       :fx [[:dispatch [::create-all-ptv-service-locations* org-id ids]]
            [:dispatch [::save-ptv-meta to-save]]]})))

(rf/reg-event-db ::halt-service-locations-creation
  (fn [db _]
    (-> db
        (assoc-in [:ptv :service-locations-creation :halt?] true))))

(rf/reg-event-fx ::save-ptv-meta
  (fn [{:keys [db]} [_ sports-sites]]
    ;; This event is used to save :ptv data for sites which have :sync-enabled false
    (when (seq sports-sites)
      (let [token  (-> db :user :login :token)
            ks [:languages
                :summary
                :description
                :last-sync
                :org-id
                :sync-enabled
                :service-integration
                :descriptions-integration
                :service-channel-integration
                :service-ids
                :service-channel-ids]]
        {:db (assoc-in db [:ptv :save-in-progress] true)
         :fx [[:http-xhrio
               {:method          :post
                :headers         {:Authorization (str "Token " token)}
                :uri             (str (:backend-url db) "/actions/save-ptv-meta")
                :params          (reduce (fn [m site]
                                           (assoc m (:lipas-id site) (select-keys site ks)))
                                         {}
                                         sports-sites)
                :format          (ajax/transit-request-format)
                :response-format (ajax/transit-response-format)
                :on-success      [::save-ptv-meta-success]
                :on-failure      [::save-ptv-meta-failure]}]]}))))

(rf/reg-event-fx ::save-ptv-meta-success
  (fn [{:keys [db]} _]
    (let [tr           (:translator db)
          notification {:message  (tr :notifications/save-success)
                        :success? true}]
      {:db (-> db (assoc-in [:ptv :save-in-progress] false))
       :fx [[:dispatch [:lipas.ui.events/set-active-notification notification]]]})))

(rf/reg-event-fx ::save-ptv-meta-failure
  (fn [{:keys [db]} [_ resp]]
    (let [tr           (:translator db)
          notification {:message  (tr :notifications/save-failed)
                        :success? false}]
      {:db (-> db
               (assoc-in [:ptv :save-in-progress] false)
               (assoc-in [:ptv :errors :save] resp))
       :fx [[:dispatch [:lipas.ui.events/set-active-notification notification]]]})))

(rf/reg-event-fx ::load-ptv-texts
  (fn [{:keys [db]} [_ lipas-id org-id service-channel-id]]
    (let [token  (-> db :user :login :token)]
      {;; :db (assoc-in db [:ptv :loading-from-ptv :ptv-text] true)
       :fx [[:http-xhrio
             {:method          :post
              :headers         {:Authorization (str "Token " token)}
              :uri             (str (:backend-url db) "/actions/fetch-ptv-service-channel")
              :params          {:org-id org-id
                                :service-channel-id service-channel-id}
              :format          (ajax/transit-request-format)
              :response-format (ajax/transit-response-format)
              :on-success      [::load-ptv-texts-success lipas-id org-id]
              :on-failure      [::load-ptv-texts-failure lipas-id org-id]}]]})))

(rf/reg-event-fx ::load-ptv-texts-success
  (fn [{:keys [db]} [_ lipas-id org-id resp]]
    {:db (-> db
             ;; (assoc-in [:ptv :loading-from-ptv :ptv-text] false)
             (update-in [:ptv :org org-id :data :sports-sites lipas-id :ptv] merge (ptv-data/ptv-service-channel->texts resp)))}))

(rf/reg-event-fx ::load-ptv-texts-failure
  (fn [{:keys [db]} [_ lipas-id org-id resp]]
    {:db (-> db
             ;; (assoc-in [:ptv :loading-from-ptv :ptv-text] false)
             )}))

(rf/reg-event-fx ::add-manual-service
  (fn [{:keys [db]} [_ org-id service]]
    {:db (update-in db [:ptv :org org-id :data :manual-services] assoc (:source-id service) service)}))

(comment

  (require '[re-frame.db])
  (-> re-frame.db/app-db
      deref
      (get-in [:ptv :selected-org :id]))
  ;; => "7b83257d-06ad-4e3b-985d-16a5c9d3fced"

  (-> re-frame.db/app-db
      deref
      (get-in [:ptv :org ptv-data/uta-org-id-test :data :sports-sites]))

  (def sdefs
    (-> re-frame.db/app-db
        deref
        (get-in [:ptv :org ptv-data/uta-org-id-test :data])
        :service-candidates))

  (def sss
    (-> re-frame.db/app-db
        deref
        (get-in [:ptv :org ptv-data/uta-org-id-test :data])
        :services
        vals
        (->> (filter :sourceId)
             (map #(select-keys % [:sourceId :serviceNames])))))

  (for [{:keys [sub-category] :as m} (vals sdefs)]
    (assoc m :id (->> sss
                      vals
                      (some (fn [x]
                              (when (->> x
                                         :serviceNames
                                         (some (fn [y] (= (:value y) sub-category))))
                                (:id x)))))))

  (-> re-frame.db/app-db
      deref
      (get-in [:sports-sites :types]))

  (-> re-frame.db/app-db
      deref
      :user :login :token)

  (-> re-frame.db/app-db
      deref
      :ptv :services-creation)

  (-> re-frame.db/app-db
      deref
      (get-in [:ptv :org ptv-data/uta-org-id-test :data])
      :sports-sites
      (get 506497)
      :ptv)

  (-> re-frame.db/app-db
      deref
      (get-in [:ptv :org ptv-data/uta-org-id-test :data])
      :sports-sites
      vals
      (->>
        (group-by (fn [{:keys [ptv]}]
                    (if (:sync-enabled ptv)
                      :sync
                      :save)))))

  (-> re-frame.db/app-db
      deref
      (get-in [:ptv :org ptv-data/uta-org-id-test :data])
      :service-channels
      (get "f0825665-edd3-4d9a-95a7-dacfe7a75f9b"))

  (-> re-frame.db/app-db
      deref
      (get-in [:ptv :org ptv-data/uta-org-id-test :data])
      :service-channels
      vals
      (->>
        (map :id)))

  (rf/dispatch [::fetch-services {:id ptv-data/uta-org-id-test}])
  (rf/dispatch [::select-org nil]))
