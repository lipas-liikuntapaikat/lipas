(ns lipas.ui.components.forms
  (:require
   [lipas.ui.components.text-fields :as text-fields]
   [lipas.ui.mui :as mui]
   [lipas.ui.utils :as utils]))

(defn ->display-tf [{:keys [label value]} multiline?]
  (let [value (utils/display-value value :empty "-" :links? false)]
    [text-fields/text-field
     {:label      label
      :multiline  multiline?
      :value      value
      :disabled   true
      :read-only? true}]))

(defn form [{:keys [read-only?]} & data]
  (into
   [mui/form-group]
   (for [d     data
         :when (some? d)
         :let  [field (-> d :form-field)
                props (-> field second)]]
     (cond
       (vector? d) d
       read-only?  (->display-tf d (:multiline props))
       :else       (assoc field 1 (assoc props :label (:label d)))))))
