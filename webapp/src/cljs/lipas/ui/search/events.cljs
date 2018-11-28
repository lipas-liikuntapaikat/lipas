(ns lipas.ui.search.events
  (:require [ajax.core :as ajax]
            [lipas.utils :as cutils]
            [lipas.ui.utils :as utils]
            [re-frame.core :as re-frame]))

(defn- add-filter [m filter]
  (-> m
      (update-in [:query :function_score :query :bool :filter] conj filter)
      ;;(update-in [:query :function_score :functions 0 :filter] conj filter)
      ))

(defn ->es-search-body [{:keys [filters string center distance]}]
  (let [string            (or (not-empty string) "*")
        type-codes        (-> filters :type-codes not-empty)
        city-codes        (-> filters :city-codes not-empty)
        area-min          (-> filters :area-min)
        area-max          (-> filters :area-max)
        {:keys [lon lat]} center
        params            {:size      200
                           ;; :min_score 1
                           :_source   {:excludes ["search-meta"]}
                           :query
                           {:function_score
                            {:score_mode "sum"
                             :query
                             {:bool
                              {:must
                               [{:query_string
                                 {:query string}}]}}
                             :functions  [{:gauss
                                           {:search-meta.location.wgs84-point
                                            {:origin (str lat "," lon)
                                             :offset (str distance "m")
                                             :scale  (str (* 2 distance) "m")}}}]}}}]
    (cond-> params
      string     (cutils/deep-merge
                  {:query
                   {:function_score
                    {:query
                     {:bool
                      {:must
                       [{:query_string
                         {:query string}}]}}}}})
      type-codes (add-filter {:terms {:type.type-code type-codes}})
      city-codes (add-filter {:terms {:location.city.city-code city-codes}})
      area-min   (add-filter {:range {:properties.areaM2 {:gte area-min}}})
      area-max   (add-filter {:range {:properties.areaM2 {:lte area-max}}}))))

(re-frame/reg-event-fx
 ::search
 (fn [{:keys [db]} [_ params]]
   {:http-xhrio
    {:method          :post
     :uri             (str (:backend-url db) "/actions/search")
     :params          (->es-search-body params)
     :format          (ajax/json-request-format)
     :response-format (ajax/json-response-format {:keywords? true})
     :on-success      [::search-success]
     :on-failure      [::search-failure]}}))

(re-frame/reg-event-fx
 ::search-success
 (fn [{:keys [db]} [_ resp]]
   (let [hits  (-> resp :hits :hits)
         sites (map :_source hits)]
     {:db (-> (reduce utils/add-to-db db sites)
              (assoc-in [:search :results] resp))})))

(re-frame/reg-event-fx
 ::search-failure
 (fn [{:keys [db]} [_ error]]
   (let [tr (:translator db)]
     {:db       (assoc-in db [:errors :search (utils/timestamp)] error)
      :dispatch [:lipas.ui.events/set-active-notification
                 {:message  (tr :notifications/get-failed)
                  :success? false}]})))

(re-frame/reg-event-db
 ::update-search-string
 (fn [db [_ s]]
   (assoc-in db [:search :string] s)))

(re-frame/reg-event-fx
 ::submit-search
 (fn [{:keys [db]} _]
   (let [params (-> db
                    :search
                    (select-keys [:string :filters])
                    (assoc :center (-> db :map :center-wgs84))
                    (assoc :distance (/ (-> db :map :width) 2)))]
     {:dispatch [::search params]})))

(re-frame/reg-event-fx
 ::set-type-filter
 (fn [{:keys [db]} [_ type-codes append?]]
   {:db       (if append?
                (update-in db [:search :filters :type-codes] into type-codes)
                (assoc-in db [:search :filters :type-codes] type-codes))
    :dispatch [::submit-search]}))

(re-frame/reg-event-fx
 ::set-city-filter
 (fn [{:keys [db]} [_ city-codes append?]]
   {:db       (if append?
                (update-in db [:search :filters :city-codes] into city-codes)
                (assoc-in db [:search :filters :city-codes] city-codes))
    :dispatch [::submit-search]}))

(re-frame/reg-event-fx
 ::set-area-min-filter
 (fn [{:keys [db]} [_ v]]
   {:db       (assoc-in db [:search :filters :area-min] v)
    :dispatch [::submit-search]}))

(re-frame/reg-event-fx
 ::set-area-max-filter
 (fn [{:keys [db]} [_ v]]
   {:db       (assoc-in db [:search :filters :area-max] v)
    :dispatch [::submit-search]}))

(re-frame/reg-event-fx
 ::clear-filters
 (fn [{:keys [db]} _]
   {:db       (assoc-in db [:search :filters] {})
    :dispatch [::submit-search]}))
