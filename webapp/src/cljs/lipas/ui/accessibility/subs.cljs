(ns lipas.ui.accessibility.subs
  (:require
   [lipas.utils :as cutils]
   [re-frame.core :as re-frame]))

(re-frame/reg-sub
 ::all-statements
 (fn [db _]
   (get-in db [:accessibility :statements])))

(re-frame/reg-sub
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
