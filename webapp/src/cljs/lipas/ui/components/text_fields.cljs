(ns lipas.ui.components.text-fields
  (:require
   [clojure.reader :refer [read-string]]
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

(defn patched-input [props]
  [:input (-> props
              (assoc :ref (:inputRef props))
              (dissoc :inputRef))])

(defn patched-text-area [props]
  [:textarea (-> props
                 (assoc :ref (:inputRef props))
                 (dissoc :inputRef))])

(defn text-field-controlled [{:keys [value type on-change spec required defer-ms
                                     Input-props adornment multiline read-only?]
                              :or   {defer-ms 200}
                              :as   props} & children]
  (r/with-let [read-only*?   (r/atom read-only?)
               state (r/atom value)]
    (let [_          (when (not= @read-only*? read-only?)
                       (do ; fix stale state between read-only? switches
                         (reset! read-only*? read-only?)
                         (reset! state value)))
          on-change  (gfun/debounce on-change defer-ms)
          on-change* (fn [e]
                       (let [new-val (->> e .-target .-value (coerce type))]
                         (reset! state new-val)
                         (on-change @state)))
          input      (if multiline
                       patched-text-area
                       patched-input)
          props      (-> (dissoc props :read-only? :defer-ms)
                         (as-> $ (if (= "number" type) (dissoc $ :type) $))
                         (assoc :error (error? spec @state required))
                         (assoc :Input-props
                                (merge Input-props
                                       {:input-component (r/reactify-component
                                                          input)}
                                       (when adornment
                                         (->adornment adornment))))
                         (assoc :value @state)
                         (assoc :on-change on-change*))]
      (into [mui/text-field props] children))))

(def text-field text-field-controlled)
