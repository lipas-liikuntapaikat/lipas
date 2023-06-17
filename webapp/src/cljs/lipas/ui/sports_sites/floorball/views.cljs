(ns lipas.ui.sports-sites.floorball.views
  (:require
   [lipas.ui.components :as lui]
   [lipas.ui.sports-sites.floorball.events :as events]
   [lipas.ui.sports-sites.floorball.subs :as subs]
   [lipas.ui.utils :as utils :refer [<== ==>]]
   [lipas.ui.mui :as mui]))

(defn field-form
  [{:keys [tr read-only? visibility on-change edit-data display-data]}]
  (let [locale      (tr)
        field-types (<== [:lipas.ui.sports-sites.subs/field-types])]
    [lui/form
     {:read-only? read-only?}

     ;; Kentän tyyppi
     {:label "Kentän tyyppi"
      :value (-> display-data :type)
      :form-field
      [lui/select
       {:items     field-types
        :label-fn  (comp locale second)
        :value-fn  first
        :value     (-> edit-data :type)
        :on-change #(on-change :type %)}]}

     ;; Kentän nimi
     {:label "Kentän nimi"
      :value (-> display-data :name)
      :form-field
      [lui/text-field
       {:type      "text"
        :spec      :lipas.sports-site.fields.field/name
        :value     (-> edit-data :name)
        :on-change #(on-change :name %)}]}

     ;; Kentän pituus (m)
     {:label "Kentän pituus (m)"
      :value (-> display-data :length-m)
      :form-field
      [lui/text-field
       {:type      "number"
        :spec      :lipas.sports-site.fields.field/length-m
        :adornment "m"
        :value     (-> edit-data :length-m)
        :on-change #(on-change :length-m %)}]}

     ;; Kentän leveys (m)
     {:label "Kentän leveys (m)"
      :value (-> display-data :width-m)
      :form-field
      [lui/text-field
       {:type      "number"
        :spec      :lipas.sports-site.fields.field/width-m
        :adornment "m"
        :value     (-> edit-data :width-m)
        :on-change #(on-change :width-m %)}]}

     ;; Kentän pinta-ala (m2)
     {:label "Kentän pinta-ala (m²)"
      :value (-> display-data :surface-area-m2)
      :form-field
      [lui/text-field
       {:type      "number"
        :spec      :lipas.sports-site.fields.field/surface-area-m2
        :adornment "m²"
        :value     (-> edit-data :surface-area-m2)
        :on-change #(on-change :surface-area-m2 %)}]}

     ;; Kentän minimikorkeus(m)
     {:label "Kentän minimikorkeus (m)"
      :value (-> display-data :minimum-height-m)
      :form-field
      [lui/text-field
       {:type      "number"
        :spec      :lipas.sports-site.fields.floorball/minimum-height-m
        :adornment "m"
        :value     (-> edit-data :minimum-height-m)
        :on-change #(on-change :minimum-height-m %)}]}

     ;; Kiinteä lattiamateriaali(parketti, matto, massa)
     ;; TODO optiot app-db:stä
     {:label "Kiinteä lattiamateriaali"
      :value (-> display-data :surface-material)
      :form-field
      [lui/select
       {:items     [{:label "Parketti" :value "wood"}
                    {:label "Matto" :value "carpet"}
                    {:label "Massa" :value "resin"}]
        :value     (-> edit-data :surface-material)
        :on-change #(on-change :surface-material %)}]}

     ;; Lattian jousto-ominaisuudet
     ;; TODO optiot app-db:stä
     {:label "Jousto-ominaisuudet"
      :value (-> display-data :floor-elasticity)
      :form-field
      [lui/select
       {:type      "number"
        :items     [{:label "Piste" :value "point"}
                    {:label "Alue" :value "area"}
                    {:label "Ei tietoa" :value "unknown"}]
        :value     (-> edit-data :floor-elasticity)
        :on-change #(on-change :floor-elasticity %)}]}

     ;; Merkki, jos tiedossa(Taraflex, Pulastic, tms.)
     {:label "Lattiamateriaalin merkki (jos tiedossa)"
      :value (-> display-data :surface-material-product)
      :form-field
      [lui/text-field
       {:type      "text"
        :spec      :lipas.sports-site.fields.floorball/surface-material-product
        :value     (-> edit-data :surface-material-product)
        :on-change #(on-change :surface-material-product %)}]}

     ;; Lattian väri
     {:label "Lattian väri"
      :value (-> display-data :surface-material-color)
      :form-field
      [lui/text-field
       {:type      "text"
        :spec      :lipas.sports-site.fields.floorball/surface-material-color
        :value     (-> edit-data :surface-material-color)
        :on-change #(on-change :surface-material-color %)}]}

     ;; Valaistus, kulma 1/1
     (when (= :floorball visibility)
       {:label "Valaistus, kulma 1/1 (lux)"
        :value (-> display-data :lighting-corner-1-1-lux)
        :form-field
        [lui/text-field
         {:type      "number"
          :spec      :lipas.properties/lighting-lux
          :value     (-> edit-data :lighting-corner-1-1-lux)
          :on-change #(on-change :lighting-corner-1-1-lux %)}]})

     ;; Valaistus, kulma 1/2
     (when (= :floorball visibility)
       {:label "Valaistus, kulma 1/2 (lux)"
        :value (-> display-data :lighting-corner-1-2-lux)
        :form-field
        [lui/text-field
         {:type      "number"
          :spec      :lipas.properties/lighting-lux
          :adornment "lux"
          :value     (-> edit-data :lighting-corner-1-2-lux)
          :on-change #(on-change :lighting-corner-1-2-lux %)}]})

     ;; Valaistus, maali 1
     (when (= :floorball visibility)
       {:label "Valaistus, maali 1 (lux)"
        :value (-> display-data :lighting-goal-1-lux)
        :form-field
        [lui/text-field
         {:type      "number"
          :spec      :lipas.properties/lighting-lux
          :adornment "lux"
          :value     (-> edit-data :lighting-goal-1-lux)
          :on-change #(on-change :lighting-goal-1-lux %)}]})

     ;; Valaistus, keskipiste
     (when (= :floorball visibility)
       {:label "Valaistus, keskipiste (lux)"
        :value (-> display-data :lighting-center-point-lux)
        :form-field
        [lui/text-field
         {:type      "number"
          :spec      :lipas.properties/lighting-lux
          :adornment "lux"
          :value     (-> edit-data :lighting-center-point-lux)
          :on-change #(on-change :lighting-center-point-lux %)}]})

     ;; Valaistus, kulma 2/1
     (when (= :floorball visibility)
       {:label "Valaistus, kulma 2/1 (lux)"
        :value (-> display-data :lighting-corner-2-1-lux)
        :form-field
        [lui/text-field
         {:type      "number"
          :spec      :lipas.properties/lighting-lux
          :adornment "lux"
          :value     (-> edit-data :lighting-corner-2-1-lux)
          :on-change #(on-change :lighting-corner-2-1-lux %)}]})

     ;; Valaistus, kulma 2/2
     (when (= :floorball visibility)
       {:label "Valaistus, kulma 2/2 (lux)"
        :value (-> display-data :lighting-corner-2-2-lux)
        :form-field
        [lui/text-field
         {:type      "number"
          :spec      :lipas.properties/lighting-lux
          :adornment "lux"
          :value     (-> edit-data :lighting-corner-2-2-lux)
          :on-change #(on-change :lighting-corner-2-2-lux %)}]})

     ;; Valaistus, maali 2
     (when (= :floorball visibility)
       {:label "Valaistus, maali 2 (lux)"
        :value (-> display-data :lighting-goal-2-lux)
        :form-field
        [lui/text-field
         {:type      "number"
          :spec      :lipas.properties/lighting-lux
          :adornment "lux"
          :value     (-> edit-data :lighting-goal-2-lux)
          :on-change #(on-change :lighting-goal-2-lux %)}]})

     ;; Valaistus keskiarvo
     {:label "Valaistus, keskiarvo (lux)"
      :value (-> display-data :lighting-average-lux)
      :form-field
      [lui/text-field
       {:type      "number"
        :spec      :lipas.properties/lighting-lux
        :adornment "lux"
        :value     (-> edit-data :lighting-average-lux)
        :on-change #(on-change :lighting-average-lux %)}]}

     ;; Turva-alue pääty 1
     {:label "Turva-alue, pääty 1"
      :value (-> display-data :safety-area-end-1-m)
      :form-field
      [lui/text-field
       {:type      "number"
        :spec      :lipas.sports-site.fields.floorball/safety-area-end-1-m
        :adornment "m"
        :value     (-> edit-data :safety-area-end-1-m)
        :on-change #(on-change :safety-area-end-1-m %)}]}

     ;; Turva-alue pääty 2
     {:label "Turva-alue, pääty 2"
      :value (-> display-data :safety-area-end-2-m)
      :form-field
      [lui/text-field
       {:type      "number"
        :spec      :lipas.sports-site.fields.floorball/safety-area-end-2-m
        :adornment "m"
        :value     (-> edit-data :safety-area-end-2-m)
        :on-change #(on-change :safety-area-end-2-m %)}]}

     ;; Turva-alue sivu 1
     {:label "Turva-alue, sivu 1"
      :value (-> display-data :safety-area-side-1-m)
      :form-field
      [lui/text-field
       {:type      "number"
        :spec      :lipas.sports-site.fields.floorball/safety-area-side-1-m
        :adornment "m"
        :value     (-> edit-data :safety-area-side-1-m)
        :on-change #(on-change :safety-area-side-1-m %)}]}

     ;; Turva-alue sivu 2
     {:label "Turva-alue, sivu 2"
      :value (-> display-data :safety-area-side-2-m)
      :form-field
      [lui/text-field
       {:type      "number"
        :spec      :lipas.sports-site.fields.floorball/safety-area-side-2-m
        :adornment "m"
        :value     (-> edit-data :safety-area-side-2-m)
        :on-change #(on-change :safety-area-side-2-m %)}]}

     ;; Kaukalon merkki
     {:label "Kaukalon merkki"
      :value (-> display-data :rink-product)
      :form-field
      [lui/text-field
       {:type      "text"
        :spec      :lipas.sports-site.fields.floorball/rink-product
        :value     (-> edit-data :rink-product)
        :on-change #(on-change :rink-product %)}]}

     ;; Kaukalon väri
     {:label "Kaukalon Väri"
      :value (-> display-data :rink-color)
      :form-field
      [lui/text-field
       {:type          "text"
        :spec          string?
        #_#_:adornment (tr :units/hours-per-day)
        :value         (-> edit-data :rink-color)
        :on-change     #(on-change :rink-color %)}]}

     ;; Katsomokapasiteetti yhteensä
     {:label "Katsomokapasiteetti yhteensä"
      :value (-> display-data :stands-total-capacity-person)
      :form-field
      [lui/text-field
       {:type      "number"
        :spec      :lipas.sports-site.fields.floorball/stands-total-capacity-person
        :value     (-> edit-data :stands-total-capacity-person)
        :on-change #(on-change :stands-total-capacity-person %)}]}

     ;; Istumapaikat
     {:label "Istumapaikkojen lukumäärä"
      :value (-> display-data :seating-area-capacity-person)
      :form-field
      [lui/text-field
       {:type      "number"
        :spec      :lipas.sports-site.fields.floorball/seating-area-capacity-person
        :value     (-> edit-data :seating-area-capacity-person)
        :on-change #(on-change :seating-area-capacity-person %)}]}

     ;; Seisomapaikat
     {:label "Seisomapaikkojen lukumäärä"
      :value (-> display-data :standing-area-capacity-person)
      :form-field
      [lui/text-field
       {:type      "number"
        :spec      :lipas.sports-site.fields.floorball/standing-area-capacity-person
        :value     (-> edit-data :standing-area-capacity-person)
        :on-change #(on-change :standing-area-capacity-person %)}]}

     ;; Invapaikat
     {:label "Invapaikat lukumäärä"
      :value (-> display-data :accessible-seating-capacity-person)
      :form-field
      [lui/text-field
       {:type      "number"
        :spec      :lipas.sports-site.fields.floorball/accessible-seating-capacity-person
        :value     (-> edit-data :accessible-seating-capacity-person)
        :on-change #(on-change :accessible-seating-capacity-person %)}]}

     ;; Tulostaulu näkyy vaihtopenkeille
     (when (= :floorball visibility)
       {:label "Tulostaulu näkyy vaihtopenkeille"
        :value (-> display-data :scoreboard-visible-to-benches?)
        :form-field
        [lui/checkbox
         {:value     (-> edit-data :scoreboard-visible-to-benches?)
          :on-change #(on-change :scoreboard-visible-to-benches? %)}]})

     ;; Tulostaulu näkyy toimitsijapöydälle (KYLLÄ/EI)
     (when (= :floorball visibility)
       {:label "Tulostaulu näkyy toimitsijapöydälle"
        :value (-> display-data :scoreboard-visible-to-officials?)
        :form-field
        [lui/checkbox
         {:value     (-> edit-data :scoreboard-visible-to-officials?)
          :on-change #(on-change :scoreboard-visible-to-officials? %)}]})

     ;; Katsomokartta
     ;; => out of scope

     ;; Pääseekö kenttätasolle ilman rappusia (KYLLÄ/EI)
     (when (= :floorball visibility)
       {:label "Pääseekö kenttätasolle ilman rappusia"
        :value (-> display-data :field-accessible-without-strairs?)
        :form-field
        [lui/checkbox
         {:value     (-> edit-data :field-accessible-without-strairs?)
          :on-change #(on-change :field-accessible-without-strairs? %)}]})

     ;; Yleisön kulku katsomoon (Kenttätasolta / Yläkautta)
     (when (= :floorball visibility)
       {:label "Yleisön kulku katsomoon"
        :value (-> display-data :audience-stand-access)
        :form-field
        [lui/select
         {:items     [{:label "Kenttätasolta" :value "from-field-level"}
                      {:label "Yläkautta" :value "from-upper-level"}]
          :value     (-> edit-data :audience-stand-access)
          :on-change #(on-change :audience-stand-access %)}]})]))

(defn field-dialog
  [{:keys [tr lipas-id]}]
  (let [open?  (<== [::subs/dialog-open? :field])
        data   (<== [::subs/dialog-data :field])
        reset  #(==> [::events/reset-dialog :field])
        close  #(==> [::events/toggle-dialog :field])
        valid? (constantly true)]
    [lui/dialog
     {:open?         open?
      :title         (if (:id data)
                       "Muokkaa"
                       "Lisää")
      :save-label    (tr :actions/save)
      :cancel-label  (tr :actions/cancel)
      :on-close      #(==> [::events/toggle-dialog :field])
      :save-enabled? valid?
      :on-save       (comp reset
                           close
                           #(==> [::events/save-dialog :fields lipas-id data]))}
     [field-form
      {:tr           tr
       :edit-data    data
       :display-data data
       :on-change    (fn [field value]
                       (==> [::events/set-dialog-field :field field value]))}]]))

(def fields-table-headers
  [[:name {:fi "Nimi"}]
   [:length-m {:fi "Pituus (m)"}]
   [:width-m {:fi "Leveys (m)"}]
   [:surface-area-m2 {:fi "Pinta-ala (m²)"}]
   [:minimum-height-m {:fi "Minimikorkeus (m)"}]
   [:surface-material {:fi "Lattiamateriaali"}]
   [:floor-elasticity {:fi "Jousto-ominaisuudet"}]
   [:surface-material-product {:fi "Lattiamateriaalin merkki"}]
   [:surface-material-color {:fi "Lattian väri"}]
   [:rink-product {:fi "Kaukalon merkki"}]
   [:rink-color {:fi "Kaukalon Väri"}]
   [:lighting-corner-1-1-lux {:fi "Valaistus kulma 1/1 (lux)"}]
   [:lighting-corner-1-2-lux {:fi "Valaistus kulma 1/2 (lux)"}]
   [:lighting-corner-2-1-lux {:fi "Valaistus kulma 2/1 (lux)"}]
   [:lighting-corner-2-2-lux {:fi "Valaistus kulma 2/2 (lux)"}]
   [:lighting-goal-1-lux {:fi "Valaistus, maali 1 (lux)"}]
   [:lighting-goal-2-lux {:fi "Valaistus, maali 2 (lux)"}]
   [:lighting-center-point-lux {:fi "Valaistus, keskipiste (lux)"}]
   [:stands-total-capacity-person {:fi "Katsomokapasiteetti (yht)"}]
   [:seating-area-capacity-person {:fi "Istumapaikat"}]
   [:standing-area-capacity-person {:fi "Seisomapaikat"}]
   [:scoreboard-visible-to-benches? {:fi "Tulostaulu näkyy vaihtopenkeille"}]
   [:scoreboard-visible-to-officials? {:fi "Tulostaulu näkyy toimitsijapöydälle"}]
   [:audience-stand-access {:fi "Yleisön kulku katsomoon"}]
   [:field-accessible-without-strairs? {:fi "Pääseekö kenttätasolle ilman rappusia"}]])

(defn fields-table
  [{:keys [tr display-data edit-data read-only? lipas-id]}]
  (let [locale  (tr)
        headers (map (juxt first (comp locale second)) fields-table-headers)]
    (if read-only?
      (if (empty? display-data)
        [mui/typography "Ei tietoa"]
        [lui/table
         {:headers headers
          :items   display-data}])
      [lui/form-table
       {:read-only?      read-only?
        :headers         headers
        :items           (vals edit-data)
        :key-fn          :id
        :add-tooltip     "Lisää"
        :add-btn-size    "small"
        :edit-tooltip    (tr :actions/edit)
        :delete-tooltip  (tr :actions/delete)
        :confirm-tooltip (tr :confirm/press-again-to-delete)
        :on-add          #(==> [::events/toggle-dialog :field {:type "floorball-field"}])
        :on-edit         #(==> [::events/toggle-dialog :field (get edit-data (:id %))])
        :on-delete       #(==> [::events/remove-field lipas-id %])}])))

(defn locker-rooms-form
  [{:keys [tr read-only? on-change display-data edit-data]}]
  [lui/form {:read-only? read-only?}
   ;; Erotuomareille oma lukittava suihkullinen pukuhuone
   {:label "Erotuomareille oma lukittava suihkullinen pukuhuone"
    :value (-> display-data :separate-referee-locker-room?)
    :form-field
    [lui/checkbox
     {:value     (-> edit-data :separate-referee-locker-room?)
      :on-change #(on-change :separate-referee-locker-room? %)}]}

   ;; Dopingtestaustilat, sis WC, odotustila (KYLLÄ/EI)
   {:label "Dopingtestaustilat, sis WC, odotustila"
    :value (-> display-data :doping-test-facilities?)
    :form-field
    [lui/checkbox
     {:value     (-> edit-data :doping-test-facilities?)
      :on-change #(on-change :doping-test-facilities? %)}]}

   ;; Erityiset huomiot pukuhuoneiden laadusta
   {:label "Erityiset huomiot pukuhuoneiden laadusta"
    :value (-> display-data :locker-room-quality-comment)
    :form-field
    [lui/text-field
     {:type      "text"
      :spec      :lipas.sports-site.circumstances/locker-room-quality-comment
      :value     (-> edit-data :locker-room-quality-comment)
      :on-change #(on-change :locker-room-quality-comment %)}]}])

(defn locker-room-form
  [{:keys [tr read-only? on-change display-data edit-data]}]
  [lui/form {:read-only? read-only?}

   ;; Pukuhuoneiden pinta-ala m2
   {:label "Pinta-ala (m²)"
    :value (-> display-data :surface-area-m2)
    :form-field
    [lui/text-field
     {:type      "number"
      :spec      :lipas.sports-site.locker-room/surface-area-m2
      :adornment "m²"
      :value     (-> edit-data :surface-area-m2)
      :on-change #(on-change :surface-area-m2 %)}]}

   ;; Suihkujen määrä
   {:label "Suihkujen määrä"
    :value (-> display-data :showers-count)
    :form-field
    [lui/text-field
     {:type          "number"
      :spec          :lipas.sports-site.locker-room/showers-count
      :value         (-> edit-data :showers-count)
      :on-change     #(on-change :showers-count %)}]}

   ;; WC:iden määrä
   {:label "Vessojen määrä"
    :value (-> display-data :toilets-count)
    :form-field
    [lui/text-field
     {:type          "number"
      :spec          :lipas.sports-site.locker-room/toilets-count
      :value         (-> edit-data :toilets-count)
      :on-change     #(on-change :toilets-count %)}]}])

(defn locker-room-dialog
  [{:keys [tr lipas-id]}]
  (let [open?  (<== [::subs/dialog-open? :locker-room])
        data   (<== [::subs/dialog-data :locker-room])
        reset  #(==> [::events/reset-dialog :locker-room])
        close  #(==> [::events/toggle-dialog :locker-room])
        valid? (constantly true)]
    [lui/dialog
     {:open?         open?
      :title         (if (:id data)
                       "Muokkaa"
                       "Lisää")
      :save-label    (tr :actions/save)
      :cancel-label  (tr :actions/cancel)
      :on-close      #(==> [::events/toggle-dialog :locker-room])
      :save-enabled? valid?
      :on-save       (comp reset
                           close
                           #(==> [::events/save-dialog :locker-rooms lipas-id data]))}
     [locker-room-form
      {:tr           tr
       :edit-data    data
       :display-data data
       :on-change    (fn [field value]
                       (==> [::events/set-dialog-field :locker-room field value]))}]]))

(def locker-rooms-table-headers
  [[:surface-area-m2 {:fi "Pinta-ala (m²)"}]
   [:showers-count {:fi "Suihkujen lukumäärä"}]
   [:toilets-count {:fi "Vessojen määrä"}]])

(defn locker-rooms-table
  [{:keys [tr display-data edit-data read-only? lipas-id]}]
  (let [locale  (tr)
        headers (map (juxt first (comp locale second)) locker-rooms-table-headers)]
    (if read-only?
      (when (seq display-data)
        [lui/table
         {:headers headers
          :items   display-data}])
      [lui/form-table
       {:read-only?      read-only?
        :headers         headers
        :items           (vals edit-data)
        :key-fn          :id
        :add-tooltip     "Lisää"
        :add-btn-size    "small"
        :edit-tooltip    (tr :actions/edit)
        :delete-tooltip  (tr :actions/delete)
        :confirm-tooltip (tr :confirm/press-again-to-delete)
        :on-add          #(==> [::events/toggle-dialog :locker-room {}])
        :on-edit         #(==> [::events/toggle-dialog :locker-room (get edit-data (:id %))])
        :on-delete       #(==> [::events/remove-locker-room lipas-id %])}])))

(defn circumstances-form
  [{:keys [tr read-only? visibility on-change display-data edit-data]}]
  [lui/form
   {:read-only? read-only?}

   ;; National league teams who use the facility
   (when (= :floorball visibility)
     {:label "Pääsarjajoukkueet jotka käyttävät hallia"
      :value (-> display-data :teams-using)
      :form-field
      [lui/text-field
       {:type          "text"
        :spec          :lipas.sports-site.circumstances/teams-using
        :value         (-> edit-data :teams-using)
        :on-change     #(on-change :teams-using %)}]})

   ;; Varastotila- ja kapasiteetti
   (when (= :floorball visibility)
     {:label "Varastotila- ja kapasiteetti"
      :value (-> display-data :storage-capacity)
      :form-field
      [lui/text-field
       {:type          "text"
        :spec          :lipas.sports-site.circumstances/storage-capacity-comment
        :value         (-> edit-data :storage-capacity)
        :on-change     #(on-change :storage-capacity %)}]})

   ;; Vapaan lattiatilan pituus (m) katsomot auki
   {:label "Vapaan lattiatilan pituus (m) katsomot auki"
    :value (-> display-data :open-floor-space-length-m)
    :form-field
    [lui/text-field
     {:type      "number"
      :spec      :lipas.sports-site.circumstances/open-floor-space-length-m
      :adornment "m"
      :value     (-> edit-data :open-floor-space-length-m)
      :on-change #(on-change :open-floor-space-length-m %)}]}

   ;; Vapaan lattiatilan leveys (m) katsomot auki
   {:label "Vapaan lattiatilan leveys (m) katsomot auki"
    :value (-> display-data :open-floor-space-width-m)
    :form-field
    [lui/text-field
     {:type      "number"
      :spec      :lipas.sports-site.circumstances/open-floor-space-width-m
      :adornment "m"
      :value     (-> edit-data :open-floor-space-width-m)
      :on-change #(on-change :open-floor-space-width-m %)}]}

   ;; Vapaan lattiatilan pinta-ala (m2) katsomot auki
   {:label "Vapaan lattiatilan pinta-ala (m²) katsomot auki"
    :value (-> display-data :open-floor-space-area-m2)
    :form-field
    [lui/text-field
     {:type      "number"
      :spec      :lipas.sports-site.circumstances/open-floor-space-area-m2
      :adornment "m²"
      :value     (-> edit-data :open-floor-space-area-m2)
      :on-change #(on-change :open-floor-space-area-m2 %)}]}

   ;; Salibandymaalien lukumäärä hallilla
   (when (= :floorball visibility)
     {:label "Salibandymaalien lukumäärä hallilla"
      :value (-> display-data :available-goals-count)
      :form-field
      [lui/text-field
       {:type      "number"
        :spec      :lipas.sports-site.circumstances.floorball/available-goals-count
        :value     (-> edit-data :available-goals-count)
        :on-change #(on-change :available-goals-count %)}]})

   ;; IFF:n hyväksyntätarrat maaleissa
   (when (= :floorball visibility)
     {:label "IFF:n hyväksyntätarrat maaleissa"
      :value (-> display-data :iff-certification-stickers-in-goals?)
      :form-field
      [lui/checkbox
       {:value     (-> edit-data :iff-certification-stickers-in-goals?)
        :on-change #(on-change :iff-certification-stickers-in-goals? %)}]})

   ;; Maalinpienennyselementit
   (when (= :floorball visibility)
     {:label "Maalinpienennyselementit (lkm)"
      :value (-> display-data :goal-shrinking-elements-count)
      :form-field
      [lui/text-field
       {:type          "number"
        :spec          :lipas.sports-site.circumstances.floorball/goal-shrinking-elements-count
        :value         (-> edit-data :goal-shrinking-elements-count)
        :on-change     #(on-change :goal-shrinking-elements-count %)}]})

   ;; IFF:n hyväksyntä kaukalossa
   (when (= :floorball visibility)
     {:label "IFF:n hyväksyntä kaukalossa"
      :value (-> display-data :iff-certified-rink?)
      :form-field
      [lui/checkbox
       {:value     (-> edit-data :iff-certified-rink?)
        :on-change #(on-change :iff-certified-rink? %)}]})

   ;; Kulmapalojen määrä
   (when (= :floorball visibility)
     {:label "Kulmapalojen määrä"
      :value (-> display-data :corner-pieces-count)
      :form-field
      [lui/text-field
       {:type      "number"
        :spec      :lipas.sports-site.circumstances.floorball/corner-pieces-count
        :value     (-> edit-data :corner-pieces-count)
        :on-change #(on-change :corner-pieces-count %)}]})

   ;; Pukuhuoneiden määrä
   #_{:label "Pukuhuoneiden määrä"
      :value (-> display-data :locker-rooms-count)
      :form-field
      [lui/text-field
       {:type      "number"
        :spec      int?
        :value     (-> edit-data :locker-rooms-count)
        :on-change #(on-change :locker-rooms-count %)}]}

   ;; Saunat
   {:label "Saunojen lukumäärä"
    :value (-> display-data :saunas-count)
    :form-field
    [lui/text-field
     {:type          "number"
      :spec          :lipas.sports-site.circumstances/saunas-count
      #_#_:adornment "m"
      :value         (-> edit-data :saunas-count)
      :on-change     #(on-change :saunas-count %)}]}

   ;; Defibrillaattori
   {:label "Defibrillaattori"
    :value (-> display-data :defibrillator?)
    :form-field
    [lui/checkbox
     {:value     (-> edit-data :defibrillator?)
      :on-change #(on-change :defibrillator? %)}]}

   ;; Paarit
   (when (= :floorball visibility)
     {:label "Paarit"
      :value (-> display-data :stretcher?)
      :form-field
      [lui/checkbox
       {:value     (-> edit-data :stretcher?)
        :on-change #(on-change :stretcher? %)}]})

   ;; Muita huomioita ensiapuvalmiudesta
   (when (= :floorball visibility)
     {:label "Muita huomioita ensiapuvalmiudesta"
      :value (-> display-data :first-aid-comment)
      :form-field
      [lui/text-field
       {:type      "text"
        :spec      :lipas.sports-site.circumstances/first-aid-comment
        :value     (-> edit-data :first-aid-comment)
        :on-change #(on-change :first-aid-comment %)}]})

   ;; Tulostaulujen määrä hallissa
   {:label "Tulostaulujen määrä hallissa"
    :value (-> display-data :scoreboard-count)
    :form-field
    [lui/text-field
     {:type      "number"
      :spec      :lipas.sports-site.circumstances/scoreboard-count
      :value     (-> edit-data :scoreboard-count)
      :on-change #(on-change :scoreboard-count %)}]}

   ;; TODO poimi valinnat app-db:stä

   ;; Pelaajien kulku halliin (Oma sisäänkäynti / Katsojien kanssa samasta)
   (when (= :floorball visibility)
     {:label "Pelaajien kulku halliin"
      :value (-> display-data :player-entrance)
      :form-field
      [lui/select
       {:items     [{:label "Oma sisäänkäynti" :value "private-entrance"}
                    {:label "Katsojien kanssa samasta" :value "audience-entrance"}]
        :value     (-> edit-data :player-entrance)
        :on-change #(on-change :player-entrance %)}]})

   ;; Oheisharjoittelu/sisälämmittelytila
   {:label "Oheisharjoittelu / sisälämmittelytila"
    :value (-> display-data :side-training-space?)
    :form-field
    [lui/checkbox
     {:value     (-> edit-data :side-training-space?)
      :on-change #(on-change :side-training-space? %)}]}

   ;; Kuntosali (KYLLÄ/EI)
   {:label "Kuntosali"
    :value (-> display-data :gym?)
    :form-field
    [lui/checkbox
     {:value     (-> edit-data :gym?)
      :on-change #(on-change :gym? %)}]}

   ;; Yleisön WC-tilojen määrä
   {:label "Yleisön WC-tilojen määrä"
    :value (-> display-data :audience-toilets-count)
    :form-field
    [lui/text-field
     {:type      "number"
      :spec      :lipas.sports-site.circumstances/audience-toilets-count
      :value     (-> edit-data :audience-toilets-count)
      :on-change #(on-change :audience-toilets-count %)}]}

   ;; VIP-tilat (KYLLÄ/EI)
   (when (= :floorball visibility)
     {:label "VIP-tilat"
      :value (-> display-data :vip-area?)
      :form-field
      [lui/checkbox
       {:value     (-> edit-data :vip-area?)
        :on-change #(on-change :vip-area? %)}]})

   ;; Lisätietoja VIP-tiloista
   (when (= :floorball visibility)
     {:label "Lisätietoja VIP-tiloista"
      :value (-> display-data :vip-area-comment)
      :form-field
      [lui/text-field
       {:type      "text"
        :spec      :lipas.sports-site.circumstances/vip-area-comment
        :value     (-> edit-data :vip-area-comment)
        :on-change #(on-change :vip-area-comment %)}]})

   ;; Onko lastausovia kenttätasolla (KYLLÄ/EI)
   (when (= :floorball visibility)
     {:label "Onko lastausovia kenttätasolla"
      :value (-> display-data :field-level-loading-doors?)
      :form-field
      [lui/checkbox
       {:value         (-> edit-data :field-level-loading-doors?)
        :on-change     #(on-change :field-level-loading-doors? %)}]})

   ;; Onko pumppukärryjä/tms. (KYLLÄ/EI)
   (when (= :floorball visibility)
     {:label "Onko pumppukärryjä/tms"
      :value (-> display-data :loading-equipment-available?)
      :form-field
      [lui/checkbox
       {:value         (-> edit-data :loading-equipment-available?)
        :on-change     #(on-change :loading-equipment-available? %)}]})

   ;; Irtotuolien määrä (noin arvio)
   (when (= :floorball visibility)
     {:label "Irtotuolien määrä (noin arvio)"
      :value (-> display-data :detached-chair-quantity)
      :form-field
      [lui/text-field
       {:type          "number"
        :spec          :lipas.sports-site.circumstances/detached-chairs-quantity
        :value         (-> edit-data :detached-chair-quantity)
        :on-change     #(on-change :detached-chair-quantity %)}]})

   ;; Irtopöytien määrä (noin arvio)
   (when (= :floorball visibility)
     {:label "Irtopyötien määrä (noin arvio)"
      :value (-> display-data :detached-tables-quantity)
      :form-field
      [lui/text-field
       {:type      "number"
        :spec      :lipas.sports-site.circumstances/detached-tables-quantity
        :value     (-> edit-data :detached-tables-quantity)
        :on-change #(on-change :detached-tables-quantity %)}]})

   ;; Kahvio-/ravintolatilojen asiakaspaikat
   (when (= :floorball visibility)
     {:label "Kahvio-/ravintolatilojen asiakaspaikat"
      :value (-> display-data :cafeteria-and-restaurant-capacity-person)
      :form-field
      [lui/text-field
       {:type      "number"
        :spec      :lipas.sports-site.circumstances/cafeteria-and-restaurant-capacity-person
        :value     (-> edit-data :cafeteria-and-restaurant-capacity-person)
        :on-change #(on-change :cafeteria-and-restaurant-capacity-person %)}]})

   ;; Ravintoloitsijan yhteystiedot
   (when (= :floorball visibility)
     {:label "Ravintoloitsijan yhteystiedot"
      :value (-> display-data :restaurateur-contact-info)
      :form-field
      [lui/text-field
       {:type      "text"
        :spec      :lipas.sports-site.circumstances/restaurateur-contact-info
        :value     (-> edit-data :restaurateur-contact-info)
        :on-change #(on-change :restaurateur-contact-info %)}]})

   ;; Onko kahviossa/ravintolassa yksinoikeudet eri tuotteille (KYLLÄ/EI/EOS)
   (when (= :floorball visibility)
     {:label "Onko kahviossa/ravintolassa yksinoikeudet eri tuotteille"
      :value (-> display-data :cafe-or-restaurant-has-exclusive-rights-for-products?)
      :form-field
      [lui/checkbox
       {:value     (-> edit-data :cafe-or-restaurant-has-exclusive-rights-for-products?)
        :on-change #(on-change :cafe-or-restaurant-has-exclusive-rights-for-products? %)}]})

   ;; Kokoustilojen määrä
   (when (= :floorball visibility)
     {:label "Kokoustilojen määrä"
      :value (-> display-data :conference-space-quantity)
      :form-field
      [lui/text-field
       {:type      "number"
        :spec      :lipas.sports-site.circumstances/conference-space-quantity
        :value     (-> edit-data :conference-space-quantity)
        :on-change #(on-change :conference-space-quantity %)}]})

   ;; Kokoustilojen yhteenlaskettu henkilökapasiteetti
   (when (= :floorball visibility)
     {:label "Kokoustilojen yhteenlaskettu henkilökapasiteetti"
      :value (-> display-data :conference-space-total-capacity-person)
      :form-field
      [lui/text-field
       {:type      "number"
        :spec      :lipas.sports-site.circumstances/conference-space-total-capacity-person
        :value     (-> edit-data :conference-space-total-capacity-person)
        :on-change #(on-change :conference-space-total-capacity-person %)}]})

   ;; Tila lehdistötilaisuudelle (KYLLÄ/EI)
   (when (= :floorball visibility)
     {:label "Tila lehdistötilaisuudelle"
      :value (-> display-data :press-conference-space?)
      :form-field
      [lui/checkbox
       {:value     (-> edit-data :press-conference-space?)
        :on-change #(on-change :press-conference-space? %)}]})

   ;; Lipunmyyntioperaattori(yksinoikeus)
   (when (= :floorball visibility)
     {:label "Lipunmyyntioperaattori (yksinoikeus)"
      :value (-> display-data :ticket-sales-operator)
      :form-field
      [lui/text-field
       {:type      "text"
        :spec      :lipas.sports-site.circumstances/ticket-sales-operator
        :value     (-> edit-data :ticket-sales-operator)
        :on-change #(on-change :ticket-sales-operator %)}]})

   ;; Pysäköintipaikkojen määrä hallin pihassa
   {:label "Pysäköintipaikkojen määrä hallin pihassa"
    :value (-> display-data :car-parking-capacity)
    :form-field
    [lui/text-field
     {:type      "number"
      :spec      :lipas.sports-site.circumstances/car-parking-capacity
      :value     (-> edit-data :car-parking-capacity)
      :on-change #(on-change :car-parking-capacity %)}]}

   ;; Bussille varattujen pysäköintipaikkojen määrä
   {:label "Bussille varattujen pysäköintipaikkojen määrä"
    :value (-> display-data :bus-park-capacity)
    :form-field
    [lui/text-field
     {:type      "number"
      :spec      :lipas.sports-site.circumstances/bus-parking-capacity
      :value     (-> edit-data :bus-park-capacity)
      :on-change #(on-change :bus-park-capacity %)}]}

   ;; Pysäköinti on (Maksullinen/Maksuton)
   {:label "Pysäköinti on..."
    :value (-> display-data :car-parking-economics-model)
    :form-field
    [lui/select
     {:items     [{:label "Maksullinen" :value "paid"}
                  {:label "Maksuton" :value "free"}]
      :value     (-> edit-data :car-parking-economics-model)
      :on-change #(on-change :car-parking-economics-model %)}]}

   ;; Kattotrussit (KYLLÄ/EI)
   (when (= :floorball visibility)
     {:label "Kattotrussit"
      :value (-> display-data :roof-trusses?)
      :form-field
      [lui/checkbox
       {:value     (-> edit-data :roof-trusses?)
        :on-change #(on-change :roof-trusses? %)}]})

   ;; Kattotrussien kantavuus (kg)
   (when (= :floorball visibility)
     {:label "Kattotrussien kantavuus"
      :value (-> display-data :roof-trusses-capacity-kg)
      :form-field
      [lui/text-field
       {:type      "number"
        :spec      :lipas.sports-site.circumstances/roof-trusses-capacity-kg
        :adornment "kg"
        :value     (-> edit-data :roof-trusses-capacity-kg)
        :on-change #(on-change :roof-trusses-capacity-kg %)}]})

   ;; Kattotrussit (Saa laskettua alas / tarvitsee nostimen)
   ;; TODO vaihtoehdot app-db:stä
   (when (= :floorball visibility)
     {:label "Kattotrussit..."
      :value (-> display-data :roof-trusses-operation-model)
      :form-field
      [lui/select
       {:items     [{:label "Saa laskettua alas" :value "can-be-lowered"}
                    {:label "Tarvitsee nostimen" :value "lift-required"}]
        :value     (-> edit-data :roof-trusses-operation-model)
        :on-change #(on-change :roof-trusses-operation-model %)}]})

   ;; Onko kiinteät kaiuttimet katsomoa kohti (KYLLÄ/EI)
   {:label "Onko kiinteät kaiuttimet katsomoa kohti"
    :value (-> display-data :speakers-aligned-towards-stands?)
    :form-field
    [lui/checkbox
     {:value     (-> edit-data :speakers-aligned-towards-stands?)
      :on-change #(on-change :speakers-aligned-towards-stands? %)}]}

   ;; Onko mikseri, jolla saa äänentoiston yhdistettyä (KYLLÄ/EI)
   (when (= :floorball visibility)
     {:label "Onko mikseri, jolla saa äänentoiston yhdistettyä"
      :value (-> display-data :audio-mixer-available?)
      :form-field
      [lui/checkbox
       {:value     (-> edit-data :audio-mixer-available?)
        :on-change #(on-change :audio-mixer-available? %)}]})

   ;; Langattominen mikrofonien määrä
   (when (= :floorball visibility)
     {:label "Langattominen mikrofonien määrä"
      :value (-> display-data :wireless-microfone-quantity)
      :form-field
      [lui/text-field
       {:type      "number"
        :spec      :lipas.sports-site.circumstances/wireless-microphone-quantity
        :value     (-> edit-data :wireless-microfone-quantity)
        :on-change #(on-change :wireless-microfone-quantity %)}]})

   ;; Langallisten mikrofonien määrä
   (when (= :floorball visibility)
     {:label "Langallisten mikrofonien määrä"
      :value (-> display-data :wired-microfone-quantity)
      :form-field
      [lui/text-field
       {:type      "number"
        :spec      :lipas.sports-site.circumstances/wired-microphone-quantity
        :value     (-> edit-data :wired-microfone-quantity)
        :on-change #(on-change :wired-microfone-quantity %)}]})

   ;; Onko kameratasanteita (KYLLÄ/EI)
   {:label "Onko kameratasanteita"
    :value (-> display-data :camera-stands?)
    :form-field
    [lui/checkbox
     {:value     (-> edit-data :camera-stands?)
      :on-change #(on-change :camera-stands? %)}]}

   ;; Onko hallilla kiinteät kamerat (KYLLÄ/EI)
   {:label "Onko hallilla kiinteät kamerat"
    :value (-> display-data :fixed-cameras?)
    :form-field
    [lui/checkbox
     {:value     (-> edit-data :fixed-cameras?)
      :on-change #(on-change :fixed-cameras? %)}]}

   ;; Onko lähetysautolle paikka hallin vieressä (KYLLÄ/EI)
   {:label "Onko lähetysautolle paikka hallin vieressä"
    :value (-> display-data :broadcast-car-park?)
    :form-field
    [lui/checkbox
     {:value     (-> edit-data :broadcast-car-park?)
      :on-change #(on-change :broadcast-car-park? %)}]}

   ;; Onko hallilla käytettävissä salasanalla suojattuja/yleisiä langattomia verkkoja (KYLLÄ/EI)
   {:label "Onko hallilla käytettävissä salasanalla suojattuja/yleisiä langattomia verkkoja"
    :value (-> display-data :wifi-available?)
    :form-field
    [lui/checkbox
     {:value     (-> edit-data :wifi-available?)
      :on-change #(on-change :wifi-available? %)}]}

   ;; Riittääkö langattoman verkon kaista esim. striimaukseen (KYLLÄ/EI)
   (when (= :floorball visibility)
     {:label "Riittääkö langattoman verkon kaista esim. striimaukseen"
      :value (-> display-data :wifi-capacity-sufficient-for-streaming?)
      :form-field
      [lui/checkbox
       {:value     (-> edit-data :wifi-capacity-sufficient-for-streaming?)
        :on-change #(on-change :wifi-capacity-sufficient-for-streaming? %)}]})

   ;; Onko sähköjen sijainnista saatavilla tieto etukäteen, esim. karttapohja (KYLLÄ/EI)
   (when (= :floorball visibility)
     {:label "Onko sähköjen sijainnista saatavilla tieto etukäteen, esim. karttapohja"
      :value (-> display-data :electrical-plan-available?)
      :form-field
      [lui/checkbox
       {#_#_:adornment "m"
        :value         (-> edit-data :electrical-plan-available?)
        :on-change     #(on-change :electrical-plan-available? %)}]})

   ;; Onko voimavirtamahdollisuus (KYLLÄ/EI)
   (when (= :floorball visibility)
     {:label "Onko voimavirtamahdollisuus"
      :value (-> display-data :three-phase-electric-power?)
      :form-field
      [lui/checkbox
       {:value     (-> edit-data :three-phase-electric-power?)
        :on-change #(on-change :three-phase-electric-power? %)}]})

   ;; Onko LED-näyttöä/screeniä tai LED-pintoja mainoksille (KYLLÄ/EI)
   (when (= :floorball visibility)
     {:label "Onko LED-näyttöä/screeniä tai LED-pintoja mainoksille"
      :value (-> display-data :led-screens-or-surfaces-for-ads?)
      :form-field
      [lui/checkbox
       {:value     (-> edit-data :led-screens-or-surfaces-for-ads?)
        :on-change #(on-change :led-screens-or-surfaces-for-ads? %)}]})

   ;; Katselmus tehty
   (when (= :floorball visibility)
     {:label "Katselmus tehty"
      :value (-> display-data :audit-date)
      :form-field
      [lui/date-picker
       {:value     (-> edit-data :audit-date)
        :on-change #(on-change :audit-date %)}]})])

(defn form
  [{:keys [tr read-only? on-change display-data edit-data type-code lipas-id]}]
  (let [visibility (<== [::subs/visibility])]
    [:<>

     #_[field-dialog {:tr tr :lipas-id lipas-id}]
     [locker-room-dialog {:tr tr :lipas-id lipas-id}]

     [lui/sub-heading {:label "Kenttä"}]

     [fields-table
      {:tr           tr
       :lipas-id     lipas-id
       :read-only?   read-only?
       :display-data (:fields display-data)
       :edit-data    (:fields edit-data)}]

     #_[field-form
      {:tr           tr
       :read-only?   read-only?
       :visibility   visibility
       :edit-data    (-> edit-data :fields first second)
       :display-data (-> display-data :fields first)
       :on-change    (fn [field value]
                       (==> [::events/set-field-field lipas-id field value]))}]

     (when (= :floorball visibility)
       [lui/sub-heading {:label "Pukuhuoneet"}])

     (when (= :floorball visibility)
       [locker-rooms-table
        {:tr           tr
         :lipas-id     lipas-id
         :read-only?   read-only?
         :display-data (-> display-data :locker-rooms)
         :edit-data    (-> edit-data :locker-rooms)}])

     (when (= :floorball visibility)
       [locker-rooms-form
        {:tr           tr
         :lipas-id     lipas-id
         :read-only?   read-only?
         :on-change    (partial on-change :circumstances)
         :display-data (-> display-data :circumstances)
         :edit-data    (-> edit-data :circumstances)}])

     [lui/sub-heading {:label "Yleiset"}]

     [circumstances-form
      {:tr           tr
       :lipas-id     lipas-id
       :type-code    type-code
       :visibility   visibility
       :read-only?   read-only?
       :on-change    (partial on-change :circumstances)
       :display-data (:circumstances display-data)
       :edit-data    (:circumstances edit-data)
       :key          type-code}]]))
