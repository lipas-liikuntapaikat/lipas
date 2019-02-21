(ns lipas.ui.map.subs
  (:require
   [lipas.ui.utils :as utils]
   [re-frame.core :as re-frame]
   [reagent.ratom :as ratom]))

(re-frame/reg-sub
 ::basemap
 (fn [db _]
   (-> db :map :basemap)))

(re-frame/reg-sub
 ::center
 (fn [db _]
   (-> db :map :center)))

(re-frame/reg-sub
 ::zoom
 (fn [db _]
   (-> db :map :zoom)))

(re-frame/reg-sub
 ::popup
 (fn [db _]
   (-> db :map :popup)))

(re-frame/reg-sub-raw
 ::selected-sports-site
 (fn [app-db event]
   (ratom/reaction
    (let [lipas-id (-> @app-db :map :mode :lipas-id)]
      (when lipas-id
        {:display-data @(re-frame/subscribe
                         [:lipas.ui.sports-sites.subs/display-site lipas-id])
         :edit-data    @(re-frame/subscribe
                         [:lipas.ui.sports-sites.subs/editing-rev lipas-id])})))))

(re-frame/reg-sub
 ::geometries
 :<- [:lipas.ui.search.subs/search-results]
 :<- [:lipas.ui.sports-sites.subs/latest-sports-site-revs]
 :<- [::editing-lipas-id]
 (fn [[results sites lipas-id] _]
   (let [ids (map (comp :lipas-id :_source) (-> results :hits :hits))
         ids (disj (set ids) lipas-id)] ; To avoid displaying
                                        ; duplicates when editing
     (->> (select-keys sites ids)
          vals
          (map utils/->feature)
          not-empty))))

(re-frame/reg-sub
 ::content-padding
 :<- [:lipas.ui.subs/screen-size]
 :<- [::drawer-open?]
 (fn [[screen-size drawer-open?] _]
   (let [margin 20]
     (if (and (#{"xs"} screen-size) (not drawer-open?))
       [margin margin margin margin]
       [margin margin margin (+ margin 430)])))) ;; drawer width is 430px

(re-frame/reg-sub
 ::mode*
 (fn [db _]
   (-> db :map :mode)))

(re-frame/reg-sub
 ::mode
 :<- [::content-padding]
 :<- [::mode*]
 (fn [[content-padding mode] _]
   (assoc mode :content-padding content-padding)))

(re-frame/reg-sub
 ::editing-lipas-id
 (fn [db _]
   (when (#{:editing :drawing} (-> db :map :mode :name))
         (-> db :map :mode :lipas-id))))

(re-frame/reg-sub
 ::zoomed-for-drawing?
 :<- [::zoom]
 (fn [zoom _]
   (> zoom 12)))

(re-frame/reg-sub
 ::drawing-geom-type
 (fn [db _]
   (-> db :map :drawing :geom-type)))

(re-frame/reg-sub
 ::new-geom
 (fn [db _]
   (when (= :adding (-> db :map :mode :name))
     (-> db :map :mode :geom))))

(re-frame/reg-sub
 ::drawer-open?
 (fn [db _]
   (-> db :map :drawer-open?)))


;; Import geoms ;;

(re-frame/reg-sub
 ::import-dialog-open?
 (fn [db _]
   (-> db :map :import :dialog-open?)))

(re-frame/reg-sub
 ::selected-import-file-encoding
 (fn [db _]
   (-> db :map :import :selected-encoding)))

(re-frame/reg-sub
 ::import-data
 (fn [db _]
   (-> db :map :import :data)))

(re-frame/reg-sub
 ::import-candidates
 :<- [::import-data]
 (fn [data _]
   data))

(re-frame/reg-sub
 ::import-candidates-headers
 :<- [::import-data]
 (fn [data _]
   (when (not-empty data)
         (-> data keys first data :properties
             (->> (mapv (juxt first (comp #(assoc {} :label %)  name first))))))))

(re-frame/reg-sub
 ::selected-import-items
 (fn [db _]
   (-> db :map :import :selected-items)))

(re-frame/reg-sub
 ::replace-existing-geoms?
 (fn [db _]
   (-> db :map :import :replace-existing?)))
