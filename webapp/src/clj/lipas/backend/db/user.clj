(ns lipas.backend.db.user
  (:require
   [hugsql.core :as hugsql]
   [lipas.backend.db.utils :as utils]))

(comment (marshall {:kissa_koira "Kana"}))
(defn marshall [user]
  (-> user
      (utils/->snake-case-keywords)))

(comment (unmarshall {:kissa_koira "Kana"}))
(comment (unmarshall nil))
(comment (unmarshall {}))
(defn unmarshall [user]
  (some-> (not-empty user)
          (utils/->kebab-case-keywords)))

(hugsql/def-db-fns "sql/user.sql")
