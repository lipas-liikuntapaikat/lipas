(ns lipas.ui.help.subs
  (:require [clojure.string :as str]
            [lipas.ui.help.events :as events]
            [re-frame.core :as rf]))

(rf/reg-sub ::help
  (fn [db _]
    (:help db)))

(rf/reg-sub ::help-data
  :<- [::help]
  (fn [help _]
    (:data help)))

(rf/reg-sub ::dialog-open?
  :<- [::help]
  (fn [help _]
    (get-in help [:dialog :open?])))

(rf/reg-sub ::mode
  :<- [::help]
  (fn [help _]
    (get-in help [:dialog :mode])))

;; The tree the reader sees: user's locale, or fi when their locale has
;; no published content.
(rf/reg-sub ::display-tree
  (fn [db _]
    (events/display-tree db)))

(rf/reg-sub ::fallback?
  ;; true when the user's locale has no published content and fi is
  ;; shown instead
  (fn [db _]
    (let [locale ((:translator db))
          data (get-in db [:help :data])]
      (boolean (and (not= :fi locale)
                    (empty? (get data locale))
                    (seq (:fi data)))))))

(rf/reg-sub ::selected-section-slug
  :<- [::help]
  (fn [help _]
    (get-in help [:dialog :selected-section-slug])))

(rf/reg-sub ::selected-page-slug
  :<- [::help]
  (fn [help _]
    (get-in help [:dialog :selected-page-slug])))

(rf/reg-sub ::expanded-sections
  :<- [::help]
  (fn [help _]
    (get-in help [:dialog :expanded-sections] #{})))

(rf/reg-sub ::search-term
  :<- [::help]
  (fn [help _]
    (get-in help [:dialog :search-term] "")))

(rf/reg-sub ::selected-section
  :<- [::display-tree]
  :<- [::selected-section-slug]
  (fn [[tree slug] _]
    (events/find-section tree slug)))

(rf/reg-sub ::selected-page
  :<- [::selected-section]
  :<- [::selected-page-slug]
  (fn [[section slug] _]
    (events/find-page section slug)))

(defn- page-haystack [page]
  (-> (str (:title page) " "
           (:summary page) " "
           (->> (:blocks page)
                (keep (fn [block]
                        (case (:type block)
                          :text (:content block)
                          (:video :pdf) (:title block)
                          nil)))
                (str/join " ")))
      (str/lower-case)))

(defn- section-haystack [section]
  (str/lower-case (str (:title section) " " (:summary section))))

(rf/reg-sub ::search-results
  ;; [{:section-slug .. :section-title .. :pages [..]} ...] in tree
  ;; order. A hit on the section title/summary surfaces the whole
  ;; section (all its pages), not just pages whose own content matches.
  :<- [::display-tree]
  :<- [::search-term]
  (fn [[tree term] _]
    (when-not (str/blank? term)
      (let [q (str/lower-case (str/trim term))]
        (vec
         (for [section tree
               :let [section-hit? (str/includes? (section-haystack section) q)
                     pages (if section-hit?
                             (:pages section)
                             (filterv #(str/includes? (page-haystack %) q)
                                      (:pages section)))]
               :when (seq pages)]
           {:section-slug (:slug section)
            :section-title (:title section)
            :pages pages}))))))
