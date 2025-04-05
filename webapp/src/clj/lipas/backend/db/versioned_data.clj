(ns lipas.backend.db.versioned-data
  (:require [hugsql.core :as hugsql]
            #_[lipas.schema.help :as help-schema]
            #_[malli.core :as m]
            #_[malli.transform :as mt]))

(defn marshall [type status body]
  {:type   type
   :status status
   :body   body})

(defmulti unmarshall :type)

(defmethod unmarshall "help" [{:keys [body]}]
  ;; TODO figure out why this doesn't work for kw values
  #_(m/decode help-schema/HelpData body mt/json-transformer)
  (mapv
   (fn [section]
     (-> section
         (update :slug keyword)
         (update :pages
                 (fn [pages]
                   (mapv (fn [page]
                           (-> page
                               (update :slug keyword)
                               (update :blocks
                                       (fn [blocks]
                                         (mapv (fn [{:keys [type] :as block}]
                                                 (cond-> (update block :type keyword)
                                                   (= "video" type) (update :provider keyword)))
                                               blocks)))))
                         pages)))))
        body))

(defmethod unmarshall :default [{:keys [body]}]
  body)

(hugsql/def-db-fns "sql/versioned_data.sql")
