(ns lipas.ui.components.text-fields
  (:require
   [clojure.reader :refer [read-string]]
   [clojure.set :as set]
   [clojure.spec.alpha :as s]
   [clojure.string :refer [trim] :as string]
   [goog.functions :as gfun]
   [lipas.ui.mui :as mui]
   [reagent.core :as r]))

(defn error? [spec value required]
  (if (and spec (or value required))
    ((complement s/valid?) spec value)
    false))

(defn ->adornment [s]
  {:end-adornment
   (r/as-element
    [mui/input-adornment s])})

;; TODO maybe one magic regexp needed here?
(defn coerce [type s]
  (if (= type "number")
    (-> s
        (string/replace "," ".")
        (string/replace #"[^\d.]" "")
        (as-> $ (if (or (string/ends-with? $ ".")
                        (and (string/includes? $ ".")
                             (string/ends-with? $ "0")))
                  $
                  (read-string $))))
    (not-empty s)))

(defn- patch-input [component]
  (r/reactify-component
   (fn [props]
     [component (-> props
                 (assoc :ref (:inputRef props))
                 (dissoc :inputRef))])))

(def patched-input (patch-input :input))
(def patched-textarea (patch-input :textarea))

(defn text-field-controlled
  [{:keys [value type on-change spec required defer-ms Input-props
           adornment multiline read-only? tooltip]
    :or   {defer-ms 200 tooltip ""}
    :as   props} & children]

  (let [on-change2 (fn [e]
                     (let [new-val (->> e .-target .-value (coerce type))]
                       (on-change new-val)))
        input      (if multiline
                     patched-textarea
                     patched-input)
        props      (-> (dissoc props :read-only? :defer-ms)
                       (set/rename-keys {:rows :min-rows})
                       (as-> $ (if (= "number" type) (dissoc $ :type) $))
                       (assoc :error (error? spec value required))
                       (assoc :Input-props
                              (merge Input-props
                                     {:input-component input}
                                     (when adornment
                                       (->adornment adornment))))
                       (assoc :on-blur #(if (= "number" type)
                                          (on-change (read-string (str value)))
                                          (when (string? value)
                                            (-> value
                                                string/trim
                                                not-empty
                                                on-change))))
                       (assoc :value value)
                       (assoc :on-change on-change2))]
    [mui/tooltip {:title tooltip}
     (into [mui/text-field props] children)]))

(def text-field text-field-controlled)
