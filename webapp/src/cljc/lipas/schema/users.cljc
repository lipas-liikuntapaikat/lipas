(ns lipas.schema.users
  (:require [lipas.schema.common :as common]))

;; Email validation
(def email-regex #"^[a-zA-Z0-9åÅäÄöÖ._%+-]+@[a-zA-Z0-9åÅäÄöÖ.-]+\.[a-zA-Z]{2,63}$")
(def two-consecutive-dots-regex #"\.{2,}")

(def email-schema
  [:and
   [:string]
   [:fn {:error/message "Not a valid email address"}
    #(re-matches email-regex %)]
   [:fn {:error/message "Email contains consecutive dots"}
    #(not (re-find two-consecutive-dots-regex %))]])

;; Common string validations
(defn string-length
  [min max]
  [:string {:min min :max max}])

;; User property schemas
(def username-schema (string-length 1 128))
(def password-schema (string-length 6 128))
(def firstname-schema (string-length 1 128))
(def lastname-schema (string-length 1 128))
(def permissions-request-schema (string-length 1 200))

;; User data schemas
(def user-data-schema
  [:map
   [:firstname firstname-schema]
   [:lastname lastname-schema]
   [:permissions-request {:optional true} permissions-request-schema]])

;; Main user schema for registration/validation
(def new-user-schema
  [:map
   [:email email-schema]
   [:username username-schema]
   [:password password-schema]
   [:user-data user-data-schema]])

;; Complete user schema with system fields
(def user-schema
  [:map
   [:id common/uuid]
   [:status [:enum "active" "archived"]]
   [:email email-schema]
   [:username username-schema]
   [:user-data user-data-schema]
   [:password {:optional true} password-schema]])
