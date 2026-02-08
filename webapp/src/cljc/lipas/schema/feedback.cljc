(ns lipas.schema.feedback
  (:require [lipas.data.feedback :as feedback]
            [lipas.schema.users :as users]))

(def feedback-type
  (into [:enum] (keys feedback/types)))

(def feedback-text
  [:string {:min 2 :max 10000}])

(def feedback-payload
  "Feedback payload schema. Uses qualified keys to match the existing API contract."
  [:map
   [:lipas.feedback/type feedback-type]
   [:lipas.feedback/text feedback-text]
   [:lipas.feedback/sender {:optional true} users/email-schema]])
