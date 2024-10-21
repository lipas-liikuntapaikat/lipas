(ns lipas.ui.sports-sites.football.views
  (:require [lipas.ui.components :as lui]
            [lipas.ui.mui :as mui]
            [lipas.ui.sports-sites.football.events]
            [lipas.ui.sports-sites.football.subs]
            [lipas.ui.utils :refer [<== ==>] :as utils]
            [re-frame.core :as rf]))

(rf/reg-event-db ::save-pool
  (fn [db [_ lipas-id value]]
    (let [path (if lipas-id
                 [:sports-sites lipas-id :editing :pools]
                 [:new-sports-site :data :fields])]
      (utils/save-entity db path value))))

(rf/reg-event-db ::reset-dialog
  (fn [db [_ dialog]]
    (assoc-in db [:sports-sites :football :dialogs dialog] {})))

(rf/reg-event-db ::set-dialog-field
  (fn [db [_ dialog field value]]
    (let [path [:sports-sites :football :dialogs dialog :data field]]
      (utils/set-field db path value))))

(rf/reg-sub ::dialog-open?
  (fn [db _]
    (-> db :sports-sites :football :dialog-open?)))

(defn set-field [dialog field value]
  (#(==> [::set-dialog-field dialog field value])))

(rf/reg-sub ::pool-form
  (fn [db _]
    (-> db :sports-sites :football :dialogs :pool :data)))

(rf/reg-event-db ::toggle-dialog
  (fn [db [_ dialog data]]
    (let [data (or data (-> db :sports-sites :football :dialogs dialog :data))]
      (-> db
          (update-in [:sports-sites :football :dialog-open?] not)
          (assoc-in [:sports-sites :football :data] data)))))

(defn form [{:keys [tr data]}]
  (let [set-field (partial set-field :pool)
        locale    (tr)]
    [mui/form-group
     ;; Pool type
     [lui/select
      {:deselect? true
       :label     (tr :general/type)
       :value     (:type data)
       :items     [{:label "11x11" :value "11x11"}
                   {:label "8x8" :value "8x8"}
                   {:label "5x5" :value "5x5"}]
       :label-fn  :label
       :value-fn  :value
       :on-change #(set-field :type %)}]

     ;; Structure
     #_[lui/select
        {:label     (tr :general/structure)
         :deselect? true
         :value     (:structure data)
         :items     pool-structures
         :label-fn  (comp locale second)
         :value-fn  first
         :on-change #(set-field :structure %)}]

     ;; Outdoor pool?
     #_[lui/checkbox
        {:label     (tr :lipas.swimming-pool.pool/outdoor-pool?)
         :value     (:outdoor-pool? data)
         :on-change #(set-field :outdoor-pool? %)}]

     ;; Length m
     [lui/text-field
      {:type      "number"
       :label     (tr :dimensions/length-m)
       :adornment (tr :physical-units/m)
       :value     (:length-m data)
       #_#_:spec      :lipas.swimming-pool.pool/length-m
       :on-change #(set-field :length-m %)}]

     ;; Width m
     [lui/text-field
      {:type      "number"
       :label     (tr :dimensions/width-m)
       :adornment (tr :physical-units/m)
       :value     (:width-m data)
       #_#_:spec      :lipas.swimming-pool.pool/width-m
       :on-change #(set-field :width-m %)}]

     ;; Area m2
     [lui/text-field
      {:type      "number"
       :label     "Varoalue 1"
       #_#_:adornment (tr :physical-units/m2)
       :value     (:area-m2 data)
       #_#_:spec      :lipas.swimming-pool.pool/area-m2
       :on-change #(set-field :area-m2 %)}]

     ;; Depth min m
     [lui/text-field
      {:type      "number"
       :label     "Varoalue 2"
       :adornment (tr :physical-units/m)
       :value     (:depth-min-m data)
       :spec      :lipas.swimming-pool.pool/depth-min-m
       :on-change #(set-field :depth-min-m %)}]

     ;; Depth max m
     [lui/text-field
      {:type      "number"
       :label     "Varoalue 3"
       :adornment (tr :physical-units/m)
       :value     (:depth-max-m data)
       #_#_:spec      :lipas.swimming-pool.pool/depth-max-m
       :on-change #(set-field :depth-max-m %)}]

     ;; Temperature c
     [lui/text-field
      {:type      "number"
       :label     "Varoalue 4"
       #_#_:adornment (tr :physical-units/celsius)
       :value     (:temperature-c data)
       #_#_:spec      :lipas.swimming-pool.pool/temperature-c
       :on-change #(set-field :temperature-c %)}]

     ;; Volume m3
     #_[lui/text-field
        {:type      "number"
         :label     (tr :dimensions/volume-m3)
         :adornment (tr :physical-units/m3)
         :value     (:volume-m3 data)
         :spec      :lipas.swimming-pool.pool/volume-m3
         :on-change #(set-field :volume-m3 %)}]

     ;; Accessibility features
     #_[lui/multi-select
        {:label     (tr :lipas.swimming-pool.pool/accessibility)
         :items     accessibility
         :value     (:accessibility data)
         :value-fn  first
         :label-fn  (comp locale second)
         :on-change #(set-field :accessibility %)}]]))

(defn dialog [{:keys [tr lipas-id]}]
  (let [data   (<== [::pool-form])
        reset  #(==> [::reset-dialog :pool])
        close  #(==> [::toggle-dialog :pool])
        valid? (constantly true)]
    [lui/dialog {:title         (if (:id data)
                                  "Muokkaa"
                                  "Lisää")
                 :save-label    (tr :actions/save)
                 :cancel-label  (tr :actions/cancel)
                 :on-close      #(==> [::toggle-dialog :pool])
                 :save-enabled? valid?
                 :on-save       (comp reset
                                      close
                                      #(==> [::save-pool lipas-id data]))}
     [form {:tr tr :data data}]]))

(defn- make-headers [tr]
  [[:type (tr :general/type)]
   #_[:volume-m3 (tr :dimensions/volume-m3)]
   [:length-m (tr :dimensions/length-m)]
   [:width-m (tr :dimensions/width-m)]
   [:area-m2 "Varoalue 1"]
   [:depth-min-m "Varoalue 2"]
   [:depth-max-m "Varoalue 3"]
   [:temperature-c "Varoalue 4"]
   #_[:structure (tr :general/structure)]
   #_[:accessibility (tr :lipas.swimming-pool.pool/accessibility)]
   #_[:outdoor-pool? (tr :lipas.swimming-pool.pool/outdoor-pool?)]])

(defn- localize-accessibility [tr pool]
  (update pool :accessibility
          #(map (fn [f] (tr (keyword :accessibility f))) %)))

(defn table [{:keys [tr items lipas-id add-btn-size max-width]}]
  (let [localize (partial utils/localize-field tr)]
    [lui/form-table
     {:headers         (make-headers tr)
      :items
      (->> (vals items)
           #_(map (partial localize :type :pool-types))
           #_(map (partial localize :structure :pool-structures))
           #_(map (partial localize-accessibility tr))
           (sort-by :length-m utils/reverse-cmp))
      :max-width       max-width
      :add-tooltip     "Lisää"
      :add-btn-size    add-btn-size
      :edit-tooltip    (tr :actions/edit)
      :delete-tooltip  (tr :actions/delete)
      :confirm-tooltip (tr :confirm/press-again-to-delete)
      :on-add          #(==> [::toggle-dialog :pool {}])
      :on-edit         #(==> [::toggle-dialog :pool (get items (:id %))])
      :on-delete       #(==> [::remove-pool lipas-id %])}]))

(defn read-only-table [{:keys [tr items]}]
  [lui/table {:headers (make-headers tr)
              :items   (sort-by :length-m utils/reverse-cmp items)
              :key-fn  #(gensym)}])

(defn fields-field
  [{:keys [tr read-only? width] :as props}]
  (let [dialog-open? (<== [::dialog-open?])
        add-data     (<== [:lipas.ui.sports-sites.subs/new-site-data])
        data         (if add-data
                       {:edit-data add-data}
                       (<== [:lipas.ui.map.subs/selected-sports-site]))
        max-width    (<== [:lipas.ui.map.subs/drawer-width width])
        lipas-id     (-> data :edit-data :lipas-id)]
    [:<>
     (when dialog-open?
       [dialog {:tr tr :lipas-id lipas-id}])

     (if read-only?
       [:<>
        [read-only-table
         {:tr    tr
          :items (-> data :display-data :pools)}]
        [:span {:style {:margin-top "1em"}}]]
       [table
        {:tr           tr
         :add-btn-size "small"
         :items        (-> data :edit-data :pools)
         :max-width    max-width
         :lipas-id     (-> data :edit-data :lipas-id)}])]))

(defn circumstances-form
  [{:keys [tr read-only? on-change display-data edit-data]}]
  [lui/form
   {:read-only? read-only?}

   ;; National league teams who use the facility
   {:label "Seura"
    :value (-> display-data :teams-using)
    :form-field
    [lui/text-field
     {:type          "text"
      :spec          :lipas.football.circumstances/teams-using
      #_#_:adornment (tr :duration/month)
      :value         (-> edit-data :teams-using)
      :on-change     #(on-change :teams-using %)}]}

   ;; fields
   {:label "Kentät"
    :value (-> display-data :fields)
    :form-field
    [fields-field
     {#_#_:adornment (tr :units/hours-per-day)
      :read-only?    read-only?
      :tr            tr
      :width         "xl"
      :value         (-> edit-data :storage-capacity)
      :on-change     #(on-change :storage-capacity %)}]}

   {:label "Kentän pituus (m)"
    :value (-> display-data :field-length-m)
    :form-field
    [lui/text-field
     {:type          "number"
      :spec          int?
      #_#_:adornment "m"
      :value         (-> edit-data :field-length-m)
      :on-change     #(on-change :field-length-m %)}]}

   ;; Kentän leveys (m)
   {:label "Kentän leveys (m)"
    :value (-> display-data :field-width-m)
    :form-field
    [lui/text-field
     {:type          "number"
      :spec          int?
      #_#_:adornment "m"
      :value         (-> edit-data :field-width-m)
      :on-change     #(on-change :field-width-m %)}]}

   ;; Kentän minimikorkeus(m)
   {:label "Kentän minimikorkeus (m)"
    :value (-> display-data :field-minimum-height-m)
    :form-field
    [lui/text-field
     {:type          "number"
      :spec          number?
      #_#_:adornment "m"
      :value         (-> edit-data :field-minimum-height-m)
      :on-change     #(on-change :field-minimum-height-m %)}]}

   ;; Kiinteä lattiamateriaali(parketti, matto, massa)
   {:label "Pintamateriaali"
    :value (-> display-data :field-minimum-height-m)
    :form-field
    [lui/select
     {:items     [{:label "Tekonurmi" :value "parquet"}
                  {:label "Hiekka" :value "carpet"}
                  {:label "Jotain muuta" :value "clay"}]
      :value     (-> edit-data :field-surface-material)
      :on-change #(on-change :field-surface-material %)}]}

   {:label "Päällysteen asennusvuosi"
    :value (-> display-data :field-surface-material-bounce-properties)
    :form-field
    [lui/select
     {:items     (range 1900 2024)
      :label-fn  identity
      :value-fn  identity
      :value     (-> edit-data :field-surface-material-bounce-properties)
      :on-change #(on-change :field-surface-material-bounce-properties %)}]}

   ;; Merkki, jos tiedossa(Taraflex, Pulastic, tms.)
   {:label "Pintamateriaalin merkki"
    :value (-> display-data :field-surface-material-brand)
    :form-field
    [lui/text-field
     {:type          "text"
      :spec          string?
      #_#_:adornment (tr :units/hours-per-day)
      :value         (-> edit-data :field-surface-material-brand)
      :on-change     #(on-change :field-surface-material-brand %)}]}

   ;; Valaistus, kulma 1/1
   {:label "Valaistus, kulma 1/1 (lux)"
    :value (-> display-data :lighting-corner-1-1-lux)
    :form-field
    [lui/text-field
     {:type          "number"
      :spec          int?
      #_#_:adornment "m"
      :value         (-> edit-data :lighting-corner-1-1-lux)
      :on-change     #(on-change :lighting-corner-1-1-lux %)}]}

   ;; Valaistus, kulma 1/2
   {:label "Valaistus, kulma 1/2 (lux)"
    :value (-> display-data :lighting-corner-1-2-lux)
    :form-field
    [lui/text-field
     {:type          "number"
      :spec          int?
      #_#_:adornment "m"
      :value         (-> edit-data :lighting-corner-1-2-lux)
      :on-change     #(on-change :lighting-corner-1-2-lux %)}]}

   ;; Valaistus, maali 1
   {:label "Valaistus, maali 1 (lux)"
    :value (-> display-data :lighting-goal-1-lux)
    :form-field
    [lui/text-field
     {:type          "number"
      :spec          int?
      #_#_:adornment "m"
      :value         (-> edit-data :lighting-goal-1-lux)
      :on-change     #(on-change :lighting-goal-1-lux %)}]}

   ;; Valaistus, keskipiste
   {:label "Valaistus, keskipiste (lux)"
    :value (-> display-data :lighting-center-point-lux)
    :form-field
    [lui/text-field
     {:type          "number"
      :spec          int?
      #_#_:adornment "m"
      :value         (-> edit-data :lighting-center-point-lux)
      :on-change     #(on-change :lighting-center-point-lux %)}]}

;; Valaistus, kulma 2/1
   {:label "Valaistus, kulma 2/1 (lux)"
    :value (-> display-data :lighting-corner-2-1-lux)
    :form-field
    [lui/text-field
     {:type          "number"
      :spec          int?
      #_#_:adornment "m"
      :value         (-> edit-data :lighting-corner-2-1-lux)
      :on-change     #(on-change :lighting-corner-2-1-lux %)}]}

   ;; Valaistus, kulma 2/2
   {:label "Valaistus, kulma 2/2 (lux)"
    :value (-> display-data :lighting-corner-2-2-lux)
    :form-field
    [lui/text-field
     {:type          "number"
      :spec          int?
      #_#_:adornment "m"
      :value         (-> edit-data :lighting-corner-2-2-lux)
      :on-change     #(on-change :lighting-corner-2-2-lux %)}]}

   ;; Valaistus, maali 2
   {:label "Valaistus, maali 2 (lux)"
    :value (-> display-data :lighting-goal-2-lux)
    :form-field
    [lui/text-field
     {:type          "number"
      :spec          int?
      #_#_:adornment "m"
      :value         (-> edit-data :lighting-goal-2-lux)
      :on-change     #(on-change :lighting-goal-2-lux %)}]}

   ;; Pukuhuoneiden määrä
   {:label "Pukuhuoneiden määrä"
    :value (-> display-data :dressing-rooms-count)
    :form-field
    [lui/text-field
     {:type          "number"
      :spec          int?
      #_#_:adornment "m"
      :value         (-> edit-data :dressing-rooms-count)
      :on-change     #(on-change :dressing-rooms-count %)}]}

   ;; Pukuhuoneiden pinta-ala m2
   {:label "Pukuhuoneiden pinta-ala (m²)"
    :value (-> display-data :dressing-rooms-surface-area-m2)
    :form-field
    [lui/text-field
     {:type          "number"
      :spec          number?
      #_#_:adornment "m"
      :value         (-> edit-data :dressing-rooms-surface-area-m2)
      :on-change     #(on-change :dressing-rooms-surface-area-m2 %)}]}

   ;; Suihkujen määrä
   {:label "Suihkujen määrä pukuhuoneissa"
    :value (-> display-data :dressing-room-showers-count)
    :form-field
    [lui/text-field
     {:type          "number"
      :spec          int?
      #_#_:adornment "m"
      :value         (-> edit-data :dressing-room-showers-count)
      :on-change     #(on-change :dressing-room-showers-count %)}]}

   ;; WC:iden määrä
   {:label "Vessojen määrä pukuhuoneissa"
    :value (-> display-data :dressing-room-toilets-count)
    :form-field
    [lui/text-field
     {:type          "number"
      :spec          int?
      #_#_:adornment "m"
      :value         (-> edit-data :dressing-room-toilets-count)
      :on-change     #(on-change :dressing-room-toilets-count %)}]}

   ;; Saunat
   {:label "Saunojen lukumäärä"
    :value (-> display-data :saunas-count)
    :form-field
    [lui/text-field
     {:type          "number"
      :spec          int?
      #_#_:adornment "m"
      :value         (-> edit-data :saunas-count)
      :on-change     #(on-change :saunas-count %)}]}

   ;; Erotuomareille oma lukittava suihkullinen pukuhuone
   {:label "Erotuomareille oma lukittava suihkullinen pukuhuone"
    :value (-> display-data :referee-dressing-room?)
    :form-field
    [lui/checkbox
     {#_#_:adornment "m"
      :value         (-> edit-data :referee-dressing-room?)
      :on-change     #(on-change :referee-dressing-room? %)}]}

   ;; Erityiset huomiot pukuhuoneiden laadusta
   {:label "Erityiset huomiot pukuhuoneiden laadusta"
    :value (-> display-data :dressing-room-quality-info)
    :form-field
    [lui/text-field
     {:type          "text"
      :spec          string?
      #_#_:adornment (tr :duration/month)
      :value         (-> edit-data :dressing-room-quality-info)
      :on-change     #(on-change :dressing-room-quality-info %)}]}

   ;; Defibrillaattori
   {:label "Defibrillaattori"
    :value (-> display-data :defibrillator?)
    :form-field
    [lui/checkbox
     {#_#_:adornment "m"
      :value         (-> edit-data :defibrillator?)
      :on-change     #(on-change :defibrillator? %)}]}

   ;; Paarit
   {:label "Paarit"
    :value (-> display-data :stretcher?)
    :form-field
    [lui/checkbox
     {#_#_:adornment "m"
      :value         (-> edit-data :stretcher?)
      :on-change     #(on-change :stretcher? %)}]}

   ;; Muita huomioita ensiapuvalmiudesta
   {:label "Muita huomioita ensiapuvalmiudesta"
    :value (-> display-data :first-aid-info)
    :form-field
    [lui/text-field
     {:type          "text"
      :spec          string?
      #_#_:adornment (tr :duration/month)
      :value         (-> edit-data :first-aid-info)
      :on-change     #(on-change :first-aid-info %)}]}

   ;; Tulostaulujen määrä hallissa
   {:label "Tulostaulujen määrä"
    :value (-> display-data :scoreboard-count)
    :form-field
    [lui/text-field
     {:type          "number"
      :spec          int?
      #_#_:adornment "m"
      :value         (-> edit-data :scoreboard-count)
      :on-change     #(on-change :scoreboard-count %)}]}

   ;; Tulostaulu näkyy vaihtopenkeille
   {:label "Tulostaulu näkyy vaihtopenkeille"
    :value (-> display-data :scoreboard-visible-to-benches?)
    :form-field
    [lui/checkbox
     {#_#_:adornment "m"
      :value         (-> edit-data :scoreboard-visible-to-benches?)
      :on-change     #(on-change :scoreboard-visible-to-benches? %)}]}

   ;; Tulostaulu näkyy toimitsijapöydälle (KYLLÄ/EI)
   {:label "Tulostaulu näkyy toimitsijapöydälle"
    :value (-> display-data :scoreboard-visible-to-officials?)
    :form-field
    [lui/checkbox
     {#_#_:adornment "m"
      :value         (-> edit-data :scoreboard-visible-to-officials?)
      :on-change     #(on-change :scoreboard-visible-to-officials? %)}]}

   ;; Oheisharjoittelu/sisälämmittelytila
   {:label "Oheisharjoittelu / sisälämmittelytila"
    :value (-> display-data :side-training-space?)
    :form-field
    [lui/checkbox
     {#_#_:adornment "m"
      :value         (-> edit-data :side-training-space?)
      :on-change     #(on-change :side-training-space? %)}]}

   ;; Kuntosali (KYLLÄ/EI)
   {:label "Kuntosali"
    :value (-> display-data :gym?)
    :form-field
    [lui/checkbox
     {#_#_:adornment "m"
      :value         (-> edit-data :gym?)
      :on-change     #(on-change :gym? %)}]}

   ;; Yleisön WC-tilojen määrä
   {:label "Yleisön WC-tilojen määrä"
    :value (-> display-data :audience-toilets-count)
    :form-field
    [lui/text-field
     {:type          "number"
      :spec          number?
      #_#_:adornment "m"
      :value         (-> edit-data :audience-toilets-count)
      :on-change     #(on-change :audience-toilets-count %)}]}

   ;; VIP-tilat (KYLLÄ/EI)
   {:label "VIP-tilat"
    :value (-> display-data :vip-area?)
    :form-field
    [lui/checkbox
     {#_#_:adornment "m"
      :value         (-> edit-data :vip-area?)
      :on-change     #(on-change :vip-area? %)}]}

   ;; Lisätietoja VIP-tiloista
   {:label "Lisätietoja VIP-tiloista"
    :value (-> display-data :vip-area-info)
    :form-field
    [lui/text-field
     {:type          "text"
      :spec          string?
      #_#_:adornment (tr :duration/month)
      :value         (-> edit-data :vip-area-info)
      :on-change     #(on-change :vip-area-info %)}]}

   ;; Katsomokapasiteetti yhteensä
   {:label "Katsomokapasiteetti yhteensä"
    :value (-> display-data :stand-capacity-person)
    :form-field
    [lui/text-field
     {:type          "number"
      :spec          number?
      #_#_:adornment "m"
      :value         (-> edit-data :stand-capacity-person)
      :on-change     #(on-change :stand-capacity-person %)}]}

   ;; Istumapaikat
   {:label "Istumapaikkojen lukumäärä"
    :value (-> display-data :seat-capacity-person)
    :form-field
    [lui/text-field
     {:type          "number"
      :spec          number?
      #_#_:adornment "m"
      :value         (-> edit-data :seat-capacity-person)
      :on-change     #(on-change :seat-capacity-person %)}]}

   ;; Seisomapaikat
   {:label "Seisomapaikkojen lukumäärä"
    :value (-> display-data :stand-capacity-person)
    :form-field
    [lui/text-field
     {:type          "number"
      :spec          number?
      #_#_:adornment "m"
      :value         (-> edit-data :stand-capacity-person)
      :on-change     #(on-change :stand-capacity-person %)}]}

   ;; Invapaikat
   ;; TODO googlaa korrekti termi englanniksi
   {:label "Invapaikat lukumäärä"
    :value (-> display-data :disability-capacity-person)
    :form-field
    [lui/text-field
     {:type          "number"
      :spec          number?
      #_#_:adornment "m"
      :value         (-> edit-data :disability-capacity-person)
      :on-change     #(on-change :disability-capacity-person %)}]}

   ;; Katsomokartta
   ;; TODO selvennä

   ;; Kahvio-/ravintolatilojen asiakaspaikat
   {:label "Kahvio-/ravintolatilojen asiakaspaikat"
    :value (-> display-data :cafe-and-restaurant-capacity-person)
    :form-field
    [lui/text-field
     {:type          "number"
      :spec          number?
      #_#_:adornment "m"
      :value         (-> edit-data :cafe-and-restaurant-capacity-person)
      :on-change     #(on-change :cafe-and-restaurant-capacity-person %)}]}

   ;; Ravintoloitsijan yhteystiedot
   {:label "Ravintoloitsijan yhteystiedot"
    :value (-> display-data :restaurant-contact-info)
    :form-field
    [lui/text-field
     {:type          "text"
      :spec          string?
      #_#_:adornment (tr :duration/month)
      :value         (-> edit-data :restaurant-contact-info)
      :on-change     #(on-change :restaurant-contact-info %)}]}

   ;; Onko kahviossa/ravintolassa yksinoikeudet eri tuotteille (KYLLÄ/EI/EOS)
   {:label "Onko kahviossa/ravintolassa yksinoikeudet eri tuotteille"
    :value (-> display-data :cafe-or-restaurant-has-exclusive-rights-for-products?)
    :form-field
    [lui/checkbox
     {#_#_:adornment "m"
      :value         (-> edit-data :cafe-or-restaurant-has-exclusive-rights-for-products?)
      :on-change     #(on-change :cafe-or-restaurant-has-exclusive-rights-for-products? %)}]}

   ;; Kokoustilojen määrä
   {:label "Kokoustilojen määrä"
    :value (-> display-data :conference-space-quantity)
    :form-field
    [lui/text-field
     {:type          "number"
      :spec          number?
      #_#_:adornment "m"
      :value         (-> edit-data :conference-space-quantity)
      :on-change     #(on-change :conference-space-quantity %)}]}

   ;; Tila lehdistötilaisuudelle (KYLLÄ/EI)
   {:label "Tila lehdistötilaisuudelle"
    :value (-> display-data :press-conference-space?)
    :form-field
    [lui/checkbox
     {#_#_:adornment "m"
      :value         (-> edit-data :press-conference-space?)
      :on-change     #(on-change :press-conference-space? %)}]}

   ;; Lipunmyyntioperaattori(yksinoikeus)
   {:label "Lipunmyyntioperaattori (yksinoikeus)"
    :value (-> display-data :ticket-sales-operatoor)
    :form-field
    [lui/text-field
     {:type          "text"
      :spec          string?
      #_#_:adornment (tr :duration/month)
      :value         (-> edit-data :ticket-sales-operatoor)
      :on-change     #(on-change :ticket-sales-operatoor %)}]}

   ;; Pysäköintipaikkojen määrä hallin pihassa
   {:label "Pysäköintipaikkojen määrä hallin pihassa"
    :value (-> display-data :car-park-capacity)
    :form-field
    [lui/text-field
     {:type          "number"
      :spec          number?
      #_#_:adornment "m"
      :value         (-> edit-data :car-park-capacity)
      :on-change     #(on-change :car-park-capacity %)}]}

   ;; Bussille varattujen pysäköintipaikkojen määrä
   {:label "Bussille varattujen pysäköintipaikkojen määrä"
    :value (-> display-data :bus-park-capacity)
    :form-field
    [lui/text-field
     {:type          "number"
      :spec          number?
      #_#_:adornment "m"
      :value         (-> edit-data :bus-park-capacity)
      :on-change     #(on-change :bus-park-capacity %)}]}

   ;; Pysäköinti on (Maksullinen/Maksuton)
   {:label "Pysäköinti on..."
    :value (-> display-data :car-park-economics-model)
    :form-field
    [lui/select
     {:items     [{:label "Maksullinen" :value "paid"}
                  {:label "Maksuton" :value "free"}]
      :value     (-> edit-data :car-park-economics-model)
      :on-change #(on-change :car-park-economics-model %)}]}

   ;; Onko hallilla kiinteät kamerat (KYLLÄ/EI)
   {:label "Kamerat"
    :value (-> display-data :fixed-cameras?)
    :form-field
    [lui/checkbox
     {#_#_:adornment "m"
      :value         (-> edit-data :fixed-cameras?)
      :on-change     #(on-change :fixed-cameras? %)}]}

   ;; Onko lähetysautolle paikka hallin vieressä (KYLLÄ/EI)
   {:label "Onko lähetysautolle paikka"
    :value (-> display-data :broadcast-car-park?)
    :form-field
    [lui/checkbox
     {#_#_:adornment "m"
      :value         (-> edit-data :broadcast-car-park?)
      :on-change     #(on-change :broadcast-car-park? %)}]}

   ;; Onko hallilla käytettävissä salasanalla suojattuja/yleisiä langattomia verkkoja (KYLLÄ/EI)
   {:label "WiFi"
    :value (-> display-data :wifi-available?)
    :form-field
    [lui/checkbox
     {#_#_:adornment "m"
      :value         (-> edit-data :wifi-available?)
      :on-change     #(on-change :wifi-available? %)}]}

   ;; Onko LED-näyttöä/screeniä tai LED-pintoja mainoksille (KYLLÄ/EI)
   {:label "LED-pintoja mainoksille"
    :value (-> display-data :led-screens-or-surfaces-for-ads?)
    :form-field
    [lui/checkbox
     {#_#_:adornment "m"
      :value         (-> edit-data :led-screens-or-surfaces-for-ads?)
      :on-change     #(on-change :led-screens-or-surfaces-for-ads? %)}]}

;; Muita Lipas-järjestelmässä kysyttyjä tietoja (jotka voisivat olla tässä kiinnostavia)
   ])
