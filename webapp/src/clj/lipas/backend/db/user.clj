(ns lipas.backend.db.user
  (:require [camel-snake-kebab.core :refer [->kebab-case ->snake_case]]
            [camel-snake-kebab.extras :refer [transform-keys]]
            [clojure.edn :as edn]
            [hugsql.core :as hugsql]))

(defn ->kebab-case-keywords [user]
  (transform-keys ->kebab-case user))

(defn ->snake-case-keywords [user]
  (transform-keys ->snake_case user))

(comment (marshall {:kissa_koira "Kana"}))
(defn marshall [user]
  (-> user
      (update :permissions pr-str)
      (update :user-data pr-str)
      (->snake-case-keywords)))

(comment (unmarshall {:kissa_koira "Kana"}))
(comment (unmarshall nil))
(comment (unmarshall {}))
(defn unmarshall [user]
  (some-> (not-empty user)
          (->kebab-case-keywords)
          (update :permissions edn/read-string)
          (update :user-data edn/read-string)))

(hugsql/def-db-fns "sql/user.sql")
