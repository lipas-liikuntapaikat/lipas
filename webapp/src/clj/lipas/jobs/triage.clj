(ns lipas.jobs.triage
  "Pure helpers for dead letter triage.

  Classifies raw error messages into coarse error classes so the admin
  UI can group failures by problem type. Kept server-side so future
  alerting can reuse the same classification."
  (:require
    [clojure.string :as str]))

(def error-classes
  "Ordered [class pattern] pairs; the first matching pattern wins.
  Patterns are matched case-insensitively against the error message.
  :mml-api comes before :timeout so 'SocketTimeoutException: Read timed
  out' classifies as an MML connectivity error, while the worker
  watchdog's 'Job execution timed out after N minutes' stays :timeout."
  [[:oom #"(?i)OutOfMemory"]
   [:mml-api #"(?i)clj-http|connection reset|sockettimeout|circuit breaker"]
   [:search #"(?i)spandex|response exception|elasticsearch"]
   [:timeout #"(?i)timed out"]
   [:site-not-found #"(?i)not found"]])

(defn classify-error
  "Classify an error message into a coarse error class keyword.
  Returns :other when no pattern matches or the message is blank."
  [error-message]
  (or (when-not (str/blank? error-message)
        (some (fn [[class pattern]]
                (when (re-find pattern error-message)
                  class))
              error-classes))
      :other))
