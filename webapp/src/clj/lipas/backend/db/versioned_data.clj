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

(defn- unmarshall-help-v2-tree
  ;; JSONB roundtrip stringifies keywords; only block :type/:provider
  ;; need re-keywordizing (v2 slugs are plain strings by design).
  [tree]
  (mapv
    (fn [section]
      (update section :pages
              (fn [pages]
                (mapv (fn [page]
                        (update page :blocks
                                (fn [blocks]
                                  (mapv (fn [{:keys [type] :as block}]
                                          (cond-> (update block :type keyword)
                                            (= "video" type) (update :provider keyword)))
                                        blocks))))
                      pages))))
    tree))

(defmethod unmarshall "help-v2-fi" [{:keys [body]}]
  (unmarshall-help-v2-tree body))

(defmethod unmarshall "help-v2-se" [{:keys [body]}]
  (unmarshall-help-v2-tree body))

(defmethod unmarshall "help-v2-en" [{:keys [body]}]
  (unmarshall-help-v2-tree body))

(defmethod unmarshall :default [{:keys [body]}]
  body)

(hugsql/def-db-fns "sql/versioned_data.sql")
