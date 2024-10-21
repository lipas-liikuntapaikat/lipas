(ns lipas.ui.components.text-fields
  (:require ["react" :as react]
            [clojure.reader :refer [read-string]]
            [clojure.spec.alpha :as s]
            [clojure.string :as string]
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
  {:endAdornment
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
  [{:keys [value type on-change spec required
           InputProps InputLabelProps
           adornment multiline read-only? tooltip on-blur
           error]
    :or   {tooltip ""}
    :as   props} & children]
  (let [input      (if multiline
                     patched-textarea
                     patched-input)
        props      (-> (if (= "number" type) (dissoc props :type) props)
                       (dissoc :read-only? :defer-ms :spec)
                       (assoc
                         :error (or error (error? spec value required))
                         ;; Use MUI prop names as-is
                         :InputLabelProps
                         (merge {:shrink (some? value)}
                                InputLabelProps)
                         :InputProps
                         (merge InputProps
                                {:inputComponent input}
                                (when adornment
                                  (->adornment adornment)))
                         :on-blur #(if (= "number" type)
                                     ;; FIXME: Juho later, better to read with parseInt or such
                                     (on-change (read-string (str value)))
                                     (when (string? value)
                                       (let [value (-> value string/trim not-empty)]
                                         (on-change value)
                                         (when on-blur
                                           (on-blur value)))))
                         :value value
                         :on-change (fn [e]
                                      (on-change (->> e .-target .-value (coerce type))))))]

    [mui/tooltip {:title tooltip}
     (into [mui/text-field (merge {:variant "standard"}
                                  props)]
           children)]))

(def text-field text-field-controlled)

(defn expandable-text-area
  [props & children]
  (r/with-let [state (r/atom {:dialog-open? false})]
    [:<>

     [mui/dialog
      {:open      (:dialog-open? @state)
       :fullWidth true
       :maxWidth  "lg"
       :on-close  #(swap! state assoc :dialog-open? false)}
      [mui/dialog-content
       #_[mui/icon-button
          {:on-click #(swap! state assoc :dialog-open? false)}
          [mui/icon "open_in_full"]]
       [text-field (assoc props :rows 15) children]]
      [mui/dialog-actions
       [mui/button {:on-click #(swap! state assoc :dialog-open? false)}
        "OK"]]]

     [mui/stack {:align-items "flex-end"}
      [mui/icon-button
       {:size "small"
        :style {:margin-bottom "-35px" :z-index "1"}
        :on-click #(swap! state assoc :dialog-open? true)}
       [mui/icon "open_in_full"]]
      [text-field props children]]]))
