(ns lipas.ui.components.forms
  (:require [lipas.ui.components.text-fields :as text-fields]
            [lipas.ui.mui :as mui]
            [lipas.ui.utils :as utils]))

(defn ->display-tf [{:keys [label value]} multiline? rows]
  (let [value (utils/display-value value :empty "-" :links? false)]
    [text-fields/text-field
     {:label      label
      :multiline  multiline?
      :min-rows   rows
      :value      value
      :disabled   true
      :read-only? true}]))

(defn ->link
  "Layout is similar to text-field but contains a link."
  [{:keys [label value] :as d}]
  (let [value (utils/display-value value :empty "-" :links? false)]
    (if-not (= "-" value)
      [:<>
       [mui/input-label
        {:shrink true
         :margin "dense"
         :style  {:transform "translate(0px, -1.5px) scale(0.75)"
                  :color     "rgba(0, 0, 0, 0.88)"}}
        label]
       [mui/link
        {:href    (if (utils/link-strict? value)
                    value
                    (str "http://" value))
         :variant "body1"
         :noWrap  true
         :style   {:line-height    "1.1876em"
                   :padding-top    "1.1876em"
                   :padding-bottom "6px"}}
        (if (> (count value) 50)
          (str (subs value 0 50) " ...")
          value)]
       [mui/divider
        {:style {:border-top "1px dotted" :color "rgba(0, 0, 0, 0.12)"}}]]
      (->display-tf d false 1))))

(defn- ->field [read-only? d]
  (let [field (-> d :form-field)
        link? (= :link (:type d))
        props (-> field second)]
    (cond
      (nil? (first d))  nil
      (= (first d) :<>) (into
                          [:<>]
                          (map (partial ->field read-only?) (rest d)))
      (vector? d)       d

      read-only? (if link?
                   (->link d)
                   (->display-tf d (:multiline props) (:rows props)))
      :else      (assoc field 1 (assoc props :label (:label d))))))

(defn form [{:keys [read-only?]} & data]
  (into
    [mui/grid {:container true :spacing 2}]
    (for [elem  data
          :let  [ms (if (= (first elem) :<>) (rest elem) [elem])]
          m     ms
          :when (some? m)]
      [mui/grid {:item true :xs 12}
       [mui/form-control {:full-width true}
        (->field read-only? m)]])))
