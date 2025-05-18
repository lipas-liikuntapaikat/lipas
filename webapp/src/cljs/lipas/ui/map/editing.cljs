(ns lipas.ui.map.editing
  (:require ["ol-ext/interaction/DrawHole$default" :as DrawHole]
            ["ol/events/condition" :as events-condition]
            ["ol/interaction/Draw$default" :as Draw]
            ["ol/interaction/Modify$default" :as Modify]
            ["ol/interaction/Select$default" :as Select]
            ["ol/interaction/Snap$default" :as Snap]
            ["ol/proj" :as proj]
            [lipas.ui.map.events :as events]
            [lipas.ui.map.styles :as styles]
            [lipas.ui.map.utils :as map-utils]
            [lipas.ui.utils :refer [==>] :as utils]
            [re-frame.core :as rf]
            [re-frame.db :as db]))

(defn clear-edits!
  [{:keys [layers] :as map-ctx}]
  (let [^js layer  (-> layers :overlays :edits)]
    (-> layer .getSource .clear)
    map-ctx))

;; The snap interaction must be added after the Modify and Draw
;; interactions in order for its map browser event handlers to be
;; fired first. Its handlers are responsible of doing the snapping.
(defn enable-snapping!
  [{:keys [^js lmap layers] :as map-ctx}]
  (let [^js layer (-> layers :overlays :edits)
        source    (.getSource layer)
        snap      (Snap. #js {:source source
                              :pixelTolerance 5})]
    (.addInteraction lmap snap)
    (assoc-in map-ctx [:interactions :snap] snap)))

(defn enable-delete!
  [{:keys [^js lmap layers] :as map-ctx} on-delete]
  (let [^js layer (-> layers :overlays :edits)
        delete    (Select. #js {:layers #js [layer]
                                :style  styles/hover-style})
        source    (.getSource layer)]
    (.addInteraction lmap delete)
    (.on delete "select"
         (fn [^js e]
           (let [selected (.-selected e)]
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
        (assoc-in [:interactions :delete] delete))))

(defn enable-splitting!
  [{:keys [^js lmap layers] :as map-ctx} geoms on-modify]
  (let [^js layer (-> layers :overlays :edits)
        source    (.getSource layer)
        _         (.clear source)
        features  (-> geoms clj->js map-utils/->ol-features)
        _         (.addFeatures source features)
        split     (Select. #js {:layers #js [layer]
                                :style  styles/hover-style})
        source    (.getSource layer)]
    (.addInteraction lmap split)
    (.on split "select"
         (fn [^js e]
           (let [selected (.-selected e)
                 euref    (.. e -mapBrowserEvent -coordinate)
                 wgs      (proj/toLonLat euref "EPSG:3067")]
             (when (not-empty selected)
               (doseq [f selected]
                 (when-let [splitted (map-utils/split-at-coords f wgs)]
                   ;; Attempting to preserve natural order with splitted geoms
                   (let [fs  #js[]]
                     (doseq [f1 (.getFeatures source)]
                       (if (= f1 f)
                         (do
                           (when-let [pt1 (first splitted)]
                             (.push fs pt1))
                           (when-let [pt2 (second splitted)]
                             (.push fs pt2)))
                         (.push fs f1)))
                     (.clear source)
                     (.addFeatures source fs))))

               (doto (.getFeatures split)
                 (.clear))
               (on-modify (map-utils/->geoJSON-clj (.getFeatures source)))))))
    (-> map-ctx
        map-utils/enable-edits-hover!
        (assoc-in [:interactions :split] split))))

(defn enable-highlighting!
  [{:keys [lmap layers] :as map-ctx}
   {:keys [selected-features] :as mode}]
  (let [^js edits-layer       (-> layers :overlays :edits)
        edits-source          (.getSource edits-layer)
        ^js highlights-layer  (-> layers :overlays :highlights)
        highlights-source     (.getSource highlights-layer)]

    #_(println "ENABLE HIGHLIGHTING")

    (-> highlights-source .clear)

    (doseq [fid selected-features]
      (when-let [f (.getFeatureById edits-source fid)]
        (.addFeature highlights-source (.clone f))))

    (-> map-ctx
        #_(map-utils/enable-edits-hover!))))

(defn start-drawing-hole!
  [{:keys [^js lmap layers] :as map-ctx} on-modifyend]
  (let [^js layer (-> layers :overlays :edits)
        draw-hole (DrawHole. #js {:layers #js [layer]})
        source    (.getSource layer)]
    (.addInteraction lmap draw-hole)
    (.on draw-hole "drawend"
         (fn [_]
           (on-modifyend (map-utils/->geoJSON-clj (.getFeatures source)))))
    (assoc-in map-ctx [:interactions :draw-hole] draw-hole)))

(defn start-editing!
  [{:keys [^js lmap layers] :as map-ctx} geoJSON-feature on-modifyend]
  (let [^js layer (-> layers :overlays :edits)
        source    (.getSource layer)
        _         (.clear source)
        features  (-> geoJSON-feature clj->js map-utils/->ol-features)
        _         (.addFeatures source features)
        modify    (Modify. #js {:source source})
        hover     (Select. #js {:layers    #js [layer]
                                :style     #js [styles/editing-hover-style styles/vertices-style]
                                :condition (fn [^js evt]
                                             ;; Without this check modify
                                             ;; control doesn't work properly
                                             ;; and linestrings / polygons
                                             ;; can't be edited
                                             (if (.-dragging evt)
                                               false
                                               (events-condition/pointerMove evt)))})]

    (.addInteraction lmap modify)
    (.addInteraction lmap hover)

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
  (let [^js layer (-> layers :overlays :vectors)
        source    (.getSource layer)
        features  (map-utils/find-features-by-lipas-id map-ctx lipas-id)
        geoms     (map-utils/strip-z geoms)]
    ;; Remove from original source so we won't display duplicate when
    ;; feature is added to :edits layer.
    (.forEach features
              (fn [f]
                (.removeFeature source f)))
    (start-editing! map-ctx geoms on-modifyend)))

(defn start-drawing!
  [{:keys [^js lmap layers]
    :as   map-ctx} geom-type on-draw-end]
  (let [^js layer (-> layers :overlays :edits)
        source    (.getSource layer)
        draw      (Draw. #js {:snapTolerance 0
                              :source source
                              :type geom-type})]

    (.addInteraction lmap draw)
    (.on draw "drawend"
         (fn [^js e]
           (let [f     (.-feature e)
                 _     (.setId f (str (random-uuid)))
                 fs    (.getFeatures source)
                 _     (.push fs f)
                 fixed (map-utils/fix-features fs)]

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
  (let [^js layer (-> layers :overlays :edits)
        source    (.getSource layer)
        features  (-> geoms clj->js map-utils/->ol-features)]

    ;; Remove existing features
    (doseq [f (.getFeatures source)]
      (.removeFeature source f))

    ;; Add geoms from props
    (.addFeatures source features)
    (==> [::events/update-geometries lipas-id geoms])
    (==> [:lipas.ui.map.events/continue-editing])
    (map-utils/fit-to-extent! map-ctx (.getExtent source))))

(defn simplify-edits!
  [{:keys [layers] :as map-ctx}
   {:keys [lipas-id geoms simplify]}]
  (let [^js layer (-> layers :overlays :edits)
        source    (.getSource layer)
        tolerance (map-utils/simplify-scale (:tolerance simplify))
        geoms     (map-utils/simplify geoms tolerance)
        features  (-> geoms clj->js map-utils/->ol-features)]

    ;; Remove existing features
    (doseq [f (.getFeatures source)]
      (.removeFeature source f))

    ;; Add geoms from props
    (.addFeatures source features)

    map-ctx))

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
      :drawing     (start-drawing! map-ctx geom-type
                                   (fn [f] (==> [::events/new-geom-drawn f])))
      :editing     (-> map-ctx
                       (cond->
                         (nil? old-sm) (map-utils/fit-to-fcoll! geoms))
                       (start-editing! geoms on-modify))
      :deleting    (enable-delete! map-ctx on-modify)
      :splitting   (enable-splitting! map-ctx geoms on-modify)
      :simplifying (simplify-edits! map-ctx mode)
      :undo        (or (==> [::events/undo-done "new" undo-geoms]) map-ctx)
      :finished    (map-utils/show-feature! map-ctx geoms))))

(defn update-adding-mode!
  [map-ctx {:keys [problems] :as mode}]
  (let [old-mode (:mode map-ctx)
        map-ctx  (-> map-ctx
                     map-utils/clear-problems!
                     (map-utils/show-problems! problems))]

    (cond-> map-ctx
      (not= (:sub-mode mode) (:sub-mode old-mode))
      (set-adding-mode! mode)

      (= :simplifying (:sub-mode mode))
      (simplify-edits! mode))))

;; Editing existing feature collection ;;

(defn continue-editing!
  [{:keys [layers] :as map-ctx} on-modifyend]
  (let [^js layer (-> layers :overlays :edits)
        fs        (-> layer .getSource .getFeatures map-utils/->geoJSON-clj)]
    (-> map-ctx
        clear-edits!
        (start-editing! fs on-modifyend))))

(defn undo-edits!
  [{:keys [layers] :as map-ctx}
   {:keys [lipas-id undo-geoms]}]
  (let [^js layer (-> layers :overlays :edits)
        source    (.getSource layer)
        features  (-> undo-geoms clj->js map-utils/->ol-features)]

    (doseq [f (.getFeatures source)]
      (.removeFeature source f))

    (.addFeatures source features)
    (==> [::events/undo-done lipas-id undo-geoms])
    map-ctx))

(defn set-view-only-edit-mode!
  [{:keys [layers] :as map-ctx} {:keys [geoms]}]
  (let [^js layer (-> layers :overlays :edits)
        source    (.getSource layer)
        _         (.clear source)
        features  (-> geoms clj->js map-utils/->ol-features)]

    (.addFeatures source features)

    map-ctx))

(defn set-travel-direction-edit-mode!
  [{:keys [layers ^js lmap] :as map-ctx} {:keys [geoms]}]
  (let [^js layer (-> layers :overlays :edits)
        source    (.getSource layer)
        _         (.clear source)
        features  (-> geoms clj->js map-utils/->ol-features)
        hover     (Select. #js {:layers    #js [layer]
                                :condition events-condition/pointerMove
                                :style     styles/line-direction-hover-style-fn})
        select    (Select. #js {:layers #js [layer]
                                :style  styles/line-direction-hover-style-fn})]

    (.on select "select" (fn [^js e]
                           (let [selected (.-selected e)]
                             (when (not-empty selected)
                               (let [f        (first selected)
                                     fid      (.getId f)
                                     lipas-id (.get f "lipas-id")]
                                 (==> [::events/toggle-travel-direction lipas-id fid]))))))

    (.addInteraction lmap hover)
    (.addInteraction lmap select)

    (doseq [^js f features]
      (.setStyle f styles/line-direction-style-fn))

    (.addFeatures source features)

    (-> map-ctx
        (assoc-in [:interactions :travel-direction-select] select)
        (assoc-in [:interactions :travel-direction-hover] hover))))

(defn set-route-part-difficulty-edit-mode
  [{:keys [layers ^js lmap] :as map-ctx} {:keys [geoms]}]
  (let [tr #_(:translator @db/app-db) (constantly :fi)
        ^js layer (-> layers :overlays :edits)
        source    (.getSource layer)
        _         (.clear source)
        features  (-> geoms clj->js map-utils/->ol-features)
        hover     (Select. #js {:layers    #js [layer]
                                :condition events-condition/pointerMove
                                :style     (fn [feature]
                                             (styles/route-part-difficulty-style-fn feature tr true false))})
        select    (Select. #js {:layers #js [layer]
                                :style  (fn [feature]
                                          (styles/route-part-difficulty-style-fn feature tr true true))})

        popup-overlay (-> map-ctx :overlays :popup)]

    (.on select "select" (fn [^js e]
                           (let [selected (.-selected e)]
                             (if (seq selected)
                               (let [^js f    (first selected)
                                     fid      (.getId f)
                                     lipas-id (.get f "lipas-id")
                                     coords   (.. f (getGeometry) (getCoordinateAt 0.5))]
                                 ;; TODO: Store the pos also to re-frame and control OL popup pos from React
                                 (.setPosition popup-overlay coords)
                                 (rf/dispatch [::events/show-popup
                                               {:type      :route-part-difficulty
                                                :placement "top"
                                                :data      {:lipas-id lipas-id
                                                            :fid fid}}]))
                               ;; Close popup on clicks outside of the Feature
                               (rf/dispatch [::events/show-popup nil])))))

    (.addInteraction lmap hover)
    (.addInteraction lmap select)

    (doseq [^js f features]
      (.setStyle f (fn [f]
                     (styles/route-part-difficulty-style-fn f tr false false))))

    (.addFeatures source features)

    (-> map-ctx
        (assoc-in [:interactions :route-part-difficulty-select] select)
        (assoc-in [:interactions :route-part-difficulty-hover] hover))))

(defn set-editing-mode!
  ([map-ctx mode]
   (set-editing-mode! map-ctx mode false))
  ([map-ctx {:keys [lipas-id geoms geom-type sub-mode problems] :as
             mode} continue?]
   #_(println "SET EDITING MODE " (:sub-mode mode))
   (let [map-ctx      (-> map-ctx
                          map-utils/clear-interactions!
                          map-utils/clear-problems!
                          map-utils/clear-population!
                          map-utils/clear-highlights!
                          map-utils/enable-marker-hover!)
         on-modifyend (fn [f]
                        (==> [::events/update-geometries lipas-id f])
                        (when (#{:drawing :drawing-hole :deleting :splitting} sub-mode)
                          ;; Switch back to editing normal :editing mode
                          ;;(==> [::events/continue-editing lipas-id :editing geom-type])
                          ))]
     (case sub-mode
       :view-only             (set-view-only-edit-mode! map-ctx mode)
       :drawing               (start-drawing! map-ctx geom-type on-modifyend)
       :drawing-hole          (start-drawing-hole! map-ctx on-modifyend) ; For polygons
       :editing               (if continue?
                       (-> map-ctx
                           (continue-editing! on-modifyend)
                           (map-utils/show-problems! problems)
                           (enable-highlighting! mode))
                       (-> map-ctx
                           (start-editing-site! lipas-id geoms on-modifyend)
                           (map-utils/show-problems! problems)
                           (enable-highlighting! mode)))
       :deleting              (-> map-ctx
                         ;;(continue-editing! on-modifyend)
                         (enable-delete! on-modifyend))
       :splitting             (-> map-ctx
                         (enable-splitting! geoms on-modifyend))
       :undo                  (undo-edits! map-ctx mode)
       :importing             (refresh-edits! map-ctx mode)
       :simplifying           (simplify-edits! map-ctx mode)
       :selecting             (-> map-ctx
                         (enable-highlighting! mode))
       :travel-direction      (-> map-ctx
                             (set-travel-direction-edit-mode! mode))
       :route-part-difficulty (-> map-ctx
                                  (set-route-part-difficulty-edit-mode mode))
       (do
         (js/console.warn "Unknown sub-mode: " sub-mode)
         map-ctx)))))

(defn update-editing-mode!
  [map-ctx {:keys [problems] :as mode}]
  #_(println "UPDATE EDITING MODE " (:sub-mode mode))
  (let [old-mode (:mode map-ctx)
        map-ctx  (-> map-ctx
                     (enable-highlighting! mode)
                     map-utils/clear-problems!
                     (map-utils/show-problems! problems))]
    #_(println "mode" (:name mode))
    #_(println "submode" (:sub-mode mode))
    (cond
      (= :simplifying (:sub-mode mode))
      (simplify-edits! map-ctx mode)

      (= :selecting (:sub-mode mode))
      (-> map-ctx
          (set-editing-mode! mode))

      (= :simplifying (:sub-mode old-mode))
      (-> map-ctx
          (refresh-edits! mode)
          (set-editing-mode! mode :continue))

      (= :travel-direction (:sub-mode mode))
      (set-editing-mode! map-ctx mode)

      (= :route-part-difficulty (:sub-mode mode))
      (-> map-ctx
          (set-editing-mode! mode))

      (= (:sub-mode mode) (:sub-mode old-mode))
      map-ctx

      :else
      (set-editing-mode! map-ctx mode :continue))))
