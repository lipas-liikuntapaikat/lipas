(ns lipas.ui.admin.ptv-adoption.subs
  (:require [clojure.string :as str]
            [lipas.data.ptv :as ptv-data]
            [re-frame.core :as rf]))

(rf/reg-sub ::state
  (fn [db _]
    (get-in db [:admin :ptv-adoption])))

(rf/reg-sub ::stats
  :<- [::state]
  (fn [state _]
    (:stats state)))

(rf/reg-sub ::loading?
  :<- [::state]
  (fn [state _]
    (:loading? state)))

(rf/reg-sub ::error
  :<- [::state]
  (fn [state _]
    (:error state)))

(rf/reg-sub ::chart-data
  :<- [::stats]
  (fn [stats _]
    (mapv (fn [{:keys [by-municipality] :as m}]
            (-> (select-keys m [:month :new :total :left-total])
                ;; ever published - left = in PTV at the end of the month
                (assoc :in-ptv (- (:total m) (:left-total m 0)))
                ;; Pre-rendered for the custom tooltip
                (assoc :details (mapv (fn [{:keys [municipality new]}]
                                        (str municipality " " new))
                                      by-municipality))))
          (:monthly stats))))

(rf/reg-sub ::ptv-users
  :<- [:lipas.ui.admin.subs/users]
  :<- [::state]
  :<- [:lipas.ui.sports-sites.subs/cities-by-city-code]
  (fn [[users {:keys [ptv-orgs]} cities] _]
    (when (and users ptv-orgs)
      (mapv (fn [row]
              (assoc row :cities (mapv #(or (get-in cities [% :name :fi]) (str %))
                                       (:city-codes row))))
            (ptv-data/ptv-user-rows (vals users) ptv-orgs)))))

(rf/reg-sub ::users-filter
  :<- [::state]
  (fn [state _]
    (:users-filter state)))

(defn- matches-search? [q {:keys [name username email cities org orgs]}]
  (let [hay (str/lower-case (str/join " " (concat [name username email (:name org)]
                                                  cities
                                                  (map :name orgs))))]
    (every? #(str/includes? hay %) (str/split (str/lower-case q) #"\s+"))))

(def ^:private collator
  ;; Finnish order (å/ä/ö after z), case-insensitive, numbers by value
  (js/Intl.Collator. "fi" #js{:sensitivity "base" :numeric true}))

(defn- user-label [{:keys [name username]}]
  (if (seq name) name username))

(def ^:private sort-keys
  "Column -> the text it sorts by (same as shown)."
  {"user" user-label
   "cities" #(str/join ", " (:cities %))
   "orgs" #(str/join ", " (map :name (:orgs %)))
   "source" #(cond
               (and (:via-org? %) (:direct? %)) "Organisaatio, Suora"
               (:via-org? %) "Organisaatio"
               :else "Suora")})

(defn- sort-rows
  "Sorts by `column`, ties by user. Empty values go last either way."
  [rows column dir]
  (let [k (sort-keys column (sort-keys "user"))
        sign (if (= "desc" dir) -1 1)]
    (sort (fn [a b]
            (let [ka (k a) kb (k b)]
              (cond
                (and (str/blank? ka) (not (str/blank? kb))) 1
                (and (str/blank? kb) (not (str/blank? ka))) -1
                :else (let [c (* sign (.compare collator (str ka) (str kb)))]
                        (if (zero? c)
                          (.compare collator (str (user-label a)) (str (user-label b)))
                          c)))))
          rows)))

(rf/reg-sub ::filtered-ptv-users
  :<- [::ptv-users]
  :<- [::users-filter]
  (fn [[rows {:keys [search source not-member? sort-by sort-dir]}] _]
    (cond-> rows
      (not (str/blank? search)) (->> (filter #(matches-search? (str/trim search) %)))
      (= "org" source) (->> (filter :via-org?))
      (= "direct" source) (->> (filter :direct?))
      not-member? (->> (filter #(and (:org %) (not (:member? %)))))
      true (sort-rows (or sort-by "user") (or sort-dir "asc"))
      true vec)))

(rf/reg-sub ::adding?
  :<- [::state]
  (fn [state [_ user-id]]
    (boolean (get-in state [:adding user-id]))))

;;; Vaatii huomiota tab ;;;

(rf/reg-sub ::attention-filter
  :<- [::state]
  (fn [state _]
    (:attention-filter state)))

(defn- error-row? [{:keys [reasons]}]
  (some #(= "error" (name (:severity %))) reasons))

(rf/reg-sub ::attention-rows
  :<- [::stats]
  :<- [::attention-filter]
  (fn [[stats {:keys [search severity]}] _]
    (let [q (some-> search str/trim str/lower-case not-empty)]
      (cond->> (:attention stats)
        (= "error" severity) (filter error-row?)
        (= "warning" severity) (remove error-row?)
        q (filter #(str/includes? (str/lower-case (str (:name %) " " (:municipality %) " " (:lipas-id %))) q))
        true vec))))

(rf/reg-sub ::ptv-orgs
  :<- [::state]
  (fn [state _]
    (:ptv-orgs state)))

