(ns lipas.backend.db.versioned-data
  (:require [hugsql.core :as hugsql]
            [lipas.schema.help :as help-schema]
            [malli.core :as m]
            [malli.transform :as mt]))

(defn marshall [type status body]
  {:type   type
   :status status
   :body   body})

(defmulti unmarshall :type)

(defmethod unmarshall "help" [{:keys [body]}]
  #_(m/decode help-schema/HelpData body mt/json-transformer)
  (let [paths (for [section-k (keys body)
                    page-k    (-> body section-k :pages keys)]
                [section-k :pages page-k :blocks])]
    (reduce
     (fn [m path]
       (update-in m path (fn [blocks]
                           (mapv (fn [{:keys [type] :as block}]
                                   (cond-> (update block :type keyword)
                                     (= "video" type) (update :provider keyword)))
                                 blocks))))
     body
     paths)))

(defmethod unmarshall :default [{:keys [body]}]
  body)

(hugsql/def-db-fns "sql/versioned_data.sql")
