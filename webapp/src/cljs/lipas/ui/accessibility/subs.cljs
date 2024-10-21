(ns lipas.ui.accessibility.subs
  (:require [re-frame.core :as rf]))

(def accessibility-types
  #{1130
    1120
    2220
    2120
    2150
    3220
    3110
    2210
    3230
    2330
    2310
    2130
    2140
    3210
    2520
    2230
    2340
    2320
    3120
    2510
    1630
    2350
    2370
    1140
    4220
    2360})

(rf/reg-sub
 ::accessibility-type?
 (fn [_ [_ type-code]]
   (accessibility-types type-code)))

(rf/reg-sub
 ::all-statements
 (fn [db _]
   (get-in db [:accessibility :statements])))

(rf/reg-sub
 ::statements
 :<- [::all-statements]
 :<- [:lipas.ui.subs/locale]
 (fn [[statements locale] [_ lipas-id]]
   (let [locale     (if (= :se locale) :sv locale)
         statements (get statements lipas-id)]
     (if (empty? statements)
       [["Kohteelle ei ole esteettömyystietoja"]]
       (->> statements
            (group-by (comp locale :sentenceGroups))
            (reduce-kv
             (fn [res group coll]
               (assoc res group (mapcat (comp #(map :value %) locale :sentences) coll)))
             {}))))))

(rf/reg-sub
 ::loading?
 (fn [db _]
   (get-in db [:accessibility :loading?])))
