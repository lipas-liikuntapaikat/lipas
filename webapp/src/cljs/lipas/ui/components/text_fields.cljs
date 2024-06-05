(ns lipas.ui.components.text-fields
  (:require
   ["react" :as react]
   [clojure.reader :refer [read-string]]
   [clojure.spec.alpha :as s]
   [clojure.string :as string]
   [goog.functions :as gfun]
   [lipas.ui.mui :as mui]
   [malli.core :as m]
   [reagent.core :as r]))

(defn error? [spec value required]
  (if (and spec (or value required))
    (if (vector? spec)
      ((complement m/validate) spec value)
      ((complement s/valid?) spec value))
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
  (react/forwardRef (fn [props ref]
                      (r/as-element [component
                                     (-> (js->clj props :keywordize-keys true)
                                         (assoc :ref ref))]))))

(def patched-input (patch-input :input))
(def patched-textarea (patch-input :textarea))

(defn text-field-controlled
  [{:keys [value type on-change spec required defer-ms Input-props
           adornment multiline read-only? tooltip on-blur]
    :or   {defer-ms 200 tooltip ""}
    :as   props} & children]
  #_(r/with-let [tf-state (r/atom value)])
  (let [#_#_on-change2 (gfun/debounce #(on-change @tf-state) defer-ms)
        #_#_on-change3 (fn [e]
                     (let [new-val (->> e .-target .-value (coerce type))]
                       (reset! tf-state new-val)
                       (on-change2)))
        input      (if multiline
                     patched-textarea
                     patched-input)
        props      (-> (dissoc props :read-only? :defer-ms :spec)
                       (as-> $ (if (= "number" type) (dissoc $ :type) $))
                       (assoc :error (error? spec value required))
                       (assoc :InputLabelProps {:shrink (some? value)})
                       (assoc :Input-props
                              (merge Input-props
                                     {:input-component input}
                                     (when adornment
                                       (->adornment adornment))))
                       (assoc :on-blur #(if (= "number" type)
                                          ;; FIXME: Juho later, better to read with parseInt or such
                                          (on-change (read-string (str value)))
                                          (when (string? value)
                                            (let [value (-> value string/trim not-empty)]
                                              (on-change value)
                                              (when on-blur
                                                (on-blur value))))))
                       #_(assoc :value @tf-state)
                       #_(assoc :on-change on-change3)
                       (assoc :value value)
                       (assoc :on-change (fn [e]
                                           (on-change (->> e .-target .-value (coerce type))))))]

    [mui/tooltip {:title tooltip}
     (into [mui/text-field (merge {:variant "standard"}
                                  props)]
           children)]))

(def text-field text-field-controlled)
