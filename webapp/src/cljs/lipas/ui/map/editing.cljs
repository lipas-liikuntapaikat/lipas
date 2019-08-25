(ns lipas.ui.map.editing
  (:require
   ["ol" :as ol]
   [goog.object :as gobj]
   [lipas.ui.map.events :as events]
   [lipas.ui.map.styles :as styles]
   [lipas.ui.map.utils :as map-utils]
   [lipas.ui.utils :refer [<== ==>] :as utils]))

(defn clear-edits! [{:keys [layers] :as map-ctx}]
  (-> layers :overlays :edits .getSource .clear)
  map-ctx)

;; The snap interaction must be added after the Modify and Draw
;; interactions in order for its map browser event handlers to be
;; fired first. Its handlers are responsible of doing the snapping.
(defn enable-snapping!
  [{:keys [^js/ol.Map lmap layers] :as map-ctx}]
  (let [source (-> layers :overlays :edits .getSource)
        snap   (ol/interaction.Snap. #js{:source source :pixelTolerance 5})]
    (.addInteraction lmap snap)
    (assoc-in map-ctx [:interactions :snap] snap)))

(defn enable-delete!
  [{:keys [^js/ol.Map lmap layers] :as map-ctx} on-delete]
  (let [layer  (-> layers :overlays :edits)
        delete (ol/interaction.Select. #js{:layers #js[layer]
                                           :style  styles/hover-style})
        source (.getSource layer)]
    (.addInteraction lmap delete)
    (.on delete "select"
         (fn [e]
           (let [selected (gobj/get e "selected")]
             (when (not-empty selected)
               (==> [:lipas.ui.map.events/confirm-remove-segment
                     (fn []
                       (doseq [f selected]
                         (.removeFeature source f))
                       (doto (.getFeatures delete)
                         (.clear))
                       (on-delete (map-utils/->geoJSON-clj (.getFeatures source))))])))))
    (-> map-ctx
        map-utils/enable-edits-hover!
        (assoc-in[:interactions :delete] delete))))

(defn enable-splitting!
  [{:keys [^js/ol.Map lmap layers] :as map-ctx} on-modify]
  (let [layer  (-> layers :overlays :edits)
        split  (ol/interaction.Select. #js{:layers #js[layer]
                                           :style  styles/hover-style})
        source (.getSource layer)]
    (.addInteraction lmap split)
    (.on split "select"
         (fn [e]
           (let [selected (gobj/get e "selected")
                 euref    (gobj/getValueByKeys e "mapBrowserEvent" "coordinate")
                 wgs      (ol/proj.toLonLat euref "EPSG:3067")]
             (when (not-empty selected)
               (doseq [f selected]
                 (when-let [splitted (map-utils/split-at-coords f wgs)]
                   (.removeFeature source f)
                   (.addFeatures source splitted)))
               (doto (.getFeatures split)
                 (.clear))
               (on-modify (map-utils/->geoJSON-clj (.getFeatures source)))))))
    (-> map-ctx
        map-utils/enable-edits-hover!
        (assoc-in [:interactions :split] split))))

(defn start-drawing-hole!
  [{:keys [^js/ol.Map lmap layers] :as map-ctx} on-modifyend]
  (let [layer     (-> layers :overlays :edits)
        draw-hole (ol/interaction.DrawHole. #js{:layers #js[layer]})
        source    (.getSource layer)]
    (.addInteraction lmap draw-hole)
    (.on draw-hole "drawend"
         (fn [_]
           (on-modifyend (map-utils/->geoJSON-clj (.getFeatures source)))))
    (assoc-in map-ctx [:interactions :draw-hole] draw-hole)))

(defn start-editing!
  [{:keys [^js/ol.Map lmap layers] :as map-ctx} geoJSON-feature on-modifyend]
  (let [layer    (-> layers :overlays :edits)
        source   (.getSource layer)
        _        (.clear source)
        features (-> geoJSON-feature clj->js map-utils/->ol-features)
        _        (.addFeatures source features)
        modify   (ol/interaction.Modify. #js{:source source})
        hover    (ol/interaction.Select.
                  #js{:layers    #js[layer]
                      :style     #js[styles/editing-hover-style styles/vertices-style]
                      :condition ol/events.condition.pointerMove})]

    (.addInteraction lmap hover)
    (.addInteraction lmap modify)

    (.on modify "modifyend"
         (fn [_]
           (let [fixed (-> source .getFeatures map-utils/fix-features)]

             (.clear source)
             (.addFeatures source fixed)

             (-> source
                 .getFeatures
                 map-utils/->geoJSON-clj
                 on-modifyend))))

    (-> map-ctx
        (assoc-in [:interactions :modify] modify)
        (assoc-in [:interactions :hover] hover)
        ;;(map-utils/fit-to-extent! (.getExtent source))
        enable-snapping!)))

(defn start-editing-site!
  [{:keys [layers] :as map-ctx} lipas-id geoms on-modifyend]
  (let [layer    (-> layers :overlays :vectors)
        source   (.getSource layer)
        features (map-utils/find-features-by-lipas-id map-ctx lipas-id)]
    ;; Remove from original source so we won't display duplicate when
    ;; feature is added to :edits layer.
    (.forEach features
              (fn [f]
                (.removeFeature source f)))
    (start-editing! map-ctx geoms on-modifyend)))

(defn start-drawing!
  [{:keys [^js/ol.Map lmap layers]
    :as   map-ctx} geom-type on-draw-end]
  (let [layer  (-> layers :overlays :edits)
        source (.getSource layer)
        draw   (ol/interaction.Draw.
                #js{:snapTolerance 0 :source source :type geom-type})]


    (.addInteraction lmap draw)
    (.on draw "drawend"
         (fn [e]
           (let [f        (gobj/get e "feature")
                 _        (.setId f (str (gensym map-utils/temp-fid-prefix)))
                 fs       (.getFeatures source)
                 _        (.push fs f)
                 fixed    (map-utils/fix-features fs)]

             (.clear source)
             (.addFeatures source fixed)

             (-> source
                 .getFeatures
                 map-utils/->geoJSON-clj
                 on-draw-end))))

    (-> map-ctx
        (assoc-in [:interactions :draw] draw)
        map-utils/clear-markers!
        enable-snapping!)))

(defn refresh-edits!
  [{:keys [layers] :as map-ctx}
   {:keys [lipas-id geoms]}]
  (let [source   (-> layers :overlays :edits .getSource)
        features (-> geoms clj->js map-utils/->ol-features)]

    ;; Remove existing features
    (doseq [f (.getFeatures source)]
      (.removeFeature source f))

    ;; Add geoms from props
    (.addFeatures source features)
    (==> [::events/update-geometries lipas-id geoms])
    (==> [:lipas.ui.map.events/continue-editing])
    (map-utils/fit-to-extent! map-ctx (.getExtent source))))

;; Adding new feature collection ;;

(defn set-adding-mode!
  [map-ctx {:keys [problems geoms geom-type undo-geoms] :as mode}]
  (let [map-ctx   (-> map-ctx
                      map-utils/clear-interactions!
                      map-utils/clear-problems!
                      map-utils/enable-marker-hover!
                      (map-utils/show-problems! problems))
        on-modify (fn [f] (==> [::events/update-new-geom f]))
        old-sm    (-> map-ctx :mode :sub-mode)]
    (case (:sub-mode mode)
      :drawing   (start-drawing! map-ctx geom-type
                                 (fn [f] (==> [::events/new-geom-drawn f])))
      :editing   (-> map-ctx
                     (cond->
                         (nil? old-sm) (map-utils/fit-to-fcoll! geoms))
                     (start-editing! geoms on-modify))
      :deleting  (enable-delete! map-ctx on-modify)
      :splitting (enable-splitting! map-ctx on-modify)
      :undo      (or (==> [::events/undo-done "new" undo-geoms]) map-ctx)
      :finished  (map-utils/show-feature! map-ctx geoms))))

(defn update-adding-mode!
  [map-ctx {:keys [problems] :as mode}]
  (let [old-mode (:mode map-ctx)
        map-ctx  (-> map-ctx
                     map-utils/clear-problems!
                     (map-utils/show-problems! problems))]
    (if (= (:sub-mode mode) (:sub-mode old-mode))
      map-ctx ;; Noop
      (set-adding-mode! map-ctx mode))))

;; Editing existing feature collection ;;

(defn continue-editing!
  [{:keys [layers] :as map-ctx} on-modifyend]
  (let [layer (-> layers :overlays :edits)
        fs    (-> layer .getSource .getFeatures map-utils/->geoJSON-clj)]
    (-> map-ctx
        clear-edits!
        (start-editing! fs on-modifyend))))

(defn undo-edits!
  [{:keys [layers] :as map-ctx}
   {:keys [lipas-id undo-geoms]}]
  (let [source   (-> layers :overlays :edits .getSource)
        features (-> undo-geoms clj->js map-utils/->ol-features)]

    (doseq [f (.getFeatures source)]
      (.removeFeature source f))

    (.addFeatures source features)
    (==> [::events/undo-done lipas-id undo-geoms])
    map-ctx))

(defn set-editing-mode!
  ([map-ctx mode]
   (set-editing-mode! map-ctx mode false))
  ([map-ctx {:keys [lipas-id geoms geom-type sub-mode problems] :as
  mode} continue?]
   (let [map-ctx      (-> map-ctx
                          map-utils/clear-interactions!
                          map-utils/clear-problems!
                          map-utils/clear-population!
                          map-utils/enable-marker-hover!)
         on-modifyend (fn [f]
                        (==> [::events/update-geometries lipas-id f])
                        (when (#{:drawing :drawing-hole :deleting :splitting} sub-mode)
                          ;; Switch back to editing normal :editing mode
                          (==> [::events/continue-editing lipas-id :editing geom-type])))]

     (case sub-mode
       :drawing      (start-drawing! map-ctx geom-type on-modifyend)
       :drawing-hole (start-drawing-hole! map-ctx on-modifyend) ; For polygons
       :editing      (if continue?
                       (-> map-ctx
                           (continue-editing! on-modifyend)
                           (map-utils/show-problems! problems))
                       (-> map-ctx
                           (start-editing-site! lipas-id geoms on-modifyend)
                           (map-utils/show-problems! problems)))
       :deleting     (-> map-ctx
                         ;;(continue-editing! on-modifyend)
                         (enable-delete! on-modifyend))
       :splitting    (-> map-ctx
                         (enable-splitting! on-modifyend))
       :undo         (undo-edits! map-ctx mode)
       :importing    (refresh-edits! map-ctx mode)))))

(defn update-editing-mode! [map-ctx {:keys [problems] :as mode}]
  (let [old-mode (:mode map-ctx)
        map-ctx  (-> map-ctx
                     map-utils/clear-problems!
                     (map-utils/show-problems! problems))]
    (if (= (:sub-mode mode) (:sub-mode old-mode))
      map-ctx ;; Noop
      (set-editing-mode! map-ctx mode :continue))))
