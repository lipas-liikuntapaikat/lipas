(ns lipas.ui.components.selects
  (:require
   [cljsjs.react-autosuggest]
   [clojure.reader :refer [read-string]]
   [clojure.spec.alpha :as s]
   [lipas.ui.mui :as mui]
   [lipas.ui.utils :as utils]))

(defn error? [spec value required]
  (if (and spec (or value required))
    ((complement s/valid?) spec value)
    false))

(defn select [{:keys [label value items on-change value-fn label-fn
                      sort-fn sort-cmp deselect? spec required]
               :or   {value-fn :value
                      label-fn :label
                      sort-cmp compare}
               :as   props}]
  (let [on-change #(on-change (-> %
                                  .-target
                                  .-value
                                  read-string
                                  (as-> $ (if (and deselect? (= $ value))
                                            nil ; toggle
                                            $))))
        props   (-> props
                    (dissoc :value-fn :label-fn :label :sort-fn :sort-cmp
                            :deselect?)
                    (assoc :error (error? spec value required))
                    ;; Following fixes Chrome scroll issue
                    ;; https://github.com/mui-org/material-ui/pull/12003
                    (assoc :MenuProps
                           {:PaperProps
                            {:style
                             {:transform "translate2(0)"}}})
                    (assoc :value (if value (pr-str value) ""))
                    (assoc :on-change on-change))
        sort-fn (or sort-fn label-fn)]
    [mui/form-control
     (when label [mui/input-label label])
     (into [mui/select props]
           (for [i (sort-by sort-fn sort-cmp items)]
             (let [value (value-fn i)
                   label (label-fn i)]
               [mui/menu-item {:key   (pr-str value)
                               :value (pr-str value)}
                label])))]))

(defn multi-select [{:keys [label value items on-change value-fn
                            label-fn sort-fn sort-cmp]
                     :or   {value-fn :value
                            label-fn :label
                            sort-cmp compare}
                     :as   props}]
  (let [sort-fn (or sort-fn label-fn)]
    [mui/form-control
     (when label [mui/input-label label])
     [mui/select
      (merge (dissoc props :label :value-fn :label-fn :sort-fn :sort-cmp)
             {:multiple  true
              :value     (map pr-str value)
              :on-change #(on-change (->> %
                                          .-target
                                          .-value
                                          (map read-string)
                                          not-empty))})
      (for [i (sort-by sort-fn sort-cmp items)]
        [mui/menu-item
         {:key   (pr-str (value-fn i))
          :value (pr-str (value-fn i))}
         (label-fn i)])]]))

(defn year-selector [{:keys [label value on-change required years multi?]
                      :as   props}]
  (let [years     (or years (range 1900 (inc (.getFullYear (js/Date.)))))
        component (if multi? multi-select select)]
    [component (merge (dissoc props :multi?)
                      {:label     label
                       :items     (map #(hash-map :label % :value %) years)
                       :on-change on-change
                       :sort-cmp  utils/reverse-cmp
                       :value     value
                       :required  required})]))

(defn number-selector [{:keys [unit] :as props}]
  [select
   (merge
    {:sort-fn   identity
     :sort-cmp  utils/reverse-cmp
     :value-fn  identity
     :label-fn  #(str % unit)}
    props)])

(defn date-picker [{:keys [label value on-change]}]
  [mui/text-field
   {:type "date"
    :label label
    :value (or value "")
    :Input-label-props
    {:shrink true} ; This makes the label show actually
    :on-change #(on-change (-> % .-target .-value))}])
