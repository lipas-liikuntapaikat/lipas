(ns hooks.re-frame
  "Project hooks for re-frame registrations."
  (:require [clj-kondo.hooks-api :as api]))

(defn no-docstring
  "re-frame registrations take no docstring. A string after the id lands
   where re-frame expects interceptors, a handler or signal functions: it
   fails or logs an error only when the namespace loads, so catch it here.
   Comment the handler with `;;` instead."
  [{:keys [node]}]
  (let [[_ _id & args] (:children node)]
    (doseq [arg args
            :when (api/string-node? arg)]
      (api/reg-finding! (assoc (meta arg)
                               :message "re-frame registrations take no docstring; use a ;; comment"
                               :type :lipas/re-frame-docstring))))
  nil)
