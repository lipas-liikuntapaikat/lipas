(ns lipas.integration.old-lipas.transform
  (:require
   [clojure.set :as set]
   [clojure.string :as string]
   [clojure.spec.alpha :as spec]
   [lipas.integration.old-lipas.sports-site :as old]
   [lipas.utils :as utils :refer [sreplace trim]]))

(defn ->old-lipas-sports-site [s]
  (-> s
      (select-keys [:name :email :www :phone-umber :renovation-years
                    :construction-year :type :location :properties])
      (assoc :admin (if (= "unknown" (:admin s)) "no-information" (:admin s))
             :owner (if (= "unknown" (:owner s)) "no-information" (:owner s))
             :school-use (-> s :properties :school-use?)
             :free-use (-> s :properties :free-use?))
      (update :properties #(-> %
                               (dissoc :school-use? :free-use?)
                               (update :surface-material
                                       (comp old/surface-materials first))
                               (set/rename-keys old/prop-mappings-reverse)))
      old/adapt-geoms
      utils/clean
      utils/->camel-case-keywords))

(defn ->sports-site [lipas-entry]
  (let [props (:properties lipas-entry)]
    {:lipas-id          (-> lipas-entry :sportsPlaceId)
     :event-date        (-> lipas-entry :lastModified (string/replace " " "T") (str "Z"))
     :status            "active"
     :name              (-> lipas-entry :name)
     :marketing-name    nil
     :admin             (get old/admins (:admin lipas-entry))
     :owner             (get old/owners (:owner lipas-entry))
     :www               (-> lipas-entry :www trim not-empty)
     :email             (-> lipas-entry :email trim
                            (sreplace " " "")
                            (sreplace "(at)" "@")
                            (sreplace "[at]" "@")
                            (as-> $ (if (spec/valid? :lipas/email $) $ ""))
                            not-empty)
     :comment           (-> lipas-entry :properties :infoFi trim not-empty)
     :properties        (-> props
                            (select-keys (keys old/prop-mappings))
                            (set/rename-keys old/prop-mappings)
                            (assoc :school-use? (-> lipas-entry :schoolUse))
                            (assoc :free-use? (-> lipas-entry :freeUse))
                            (assoc :surface-material
                                   (first (old/resolve-surface-material props)))
                            (assoc :surface-material-info
                                   (second (old/resolve-surface-material props))))
     :phone-number      (-> lipas-entry :phoneNumber trim not-empty)
     :construction-year (-> lipas-entry :constructionYear)
     :renovation-years  (-> lipas-entry :renovationYears)
     :location
     {:address       (-> lipas-entry :location :address trim not-empty)
      :geometries    (-> lipas-entry :location :geometries
                         (update :features
                                 (fn [fs] (mapv #(dissoc % :properties) fs))))
      :postal-code   (-> lipas-entry :location :postalCode trim not-empty)
      :postal-office (-> lipas-entry :location :postalOffice trim not-empty)
      :city
      {:city-code    (-> lipas-entry :location :city :cityCode)
       :neighborhood (-> lipas-entry :location :neighborhood trim not-empty)}}
     :type
     {:type-code (-> lipas-entry :type :typeCode)}}))

;; 'es' refers here to Old Lipas Elasticsearch
(defn es-dump->sports-site [m]
  (-> m
      (assoc :name (-> m :name :fi))
      (assoc :admin (-> m :admin :fi))
      (assoc :owner (-> m :owner :fi))
      (assoc-in [:location :address] (or (-> m :location :address)
                                         "-"))
      (assoc-in [:location :postalCode] (or (-> m :location :postalCode)
                                            "00000"))
      (assoc-in [:location :neighborhood] (-> m :location :neighborhood :fi))
      (assoc :lastModified (or (-> m :lastModified not-empty)
                               "1970-01-01T00:00:00.000"))
      ->sports-site
      utils/clean))
