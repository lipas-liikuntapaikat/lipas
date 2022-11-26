(ns lipas.ui.sports-sites.floorball
  (:require
   [lipas.ui.components :as lui]))

(def type-codes #{2240}) ; Salibandyhalli

(defn circumstances-form
  [{:keys [tr read-only? on-change display-data edit-data]}]
  [lui/form
   {:read-only? read-only?}

   ;; National league teams who use the facility
   {:label "Pääsarjajoukkueet jotka käyttävät hallia"
    :value (-> display-data :teams-using)
    :form-field
    [lui/text-field
     {:type          "text"
      :spec          :lipas.floorball.circumstances/teams-using
      #_#_:adornment (tr :duration/month)
      :value         (-> edit-data :teams-using)
      :on-change     #(on-change :teams-using %)}]}

   ;; Varastotila- ja kapasiteetti
   {:label "Varastotila- ja kapasiteetti"
    :value (-> display-data :storage-capacity)
    :form-field
    [lui/text-field
     {:type          "text"
      :spec          string?
      #_#_:adornment (tr :units/hours-per-day)
      :value         (-> edit-data :storage-capacity)
      :on-change     #(on-change :storage-capacity %)}]}

   ;; Vapaan lattiatilan pituus (m) katsomot auki
   {:label "Vapaan lattiatilan pituus (m) katsomot auki"
    :value (-> display-data :open-floor-space-length-m)
    :form-field
    [lui/text-field
     {:type          "number"
      :spec          int?
      #_#_:adornment "m"
      :value         (-> edit-data :open-floor-space-length-m)
      :on-change     #(on-change :open-floor-space-length-m %)}]}

   ;; Vapaan lattiatilan leveys (m) katsomot auki
   {:label "Vapaan lattiatilan leveys (m) katsomot auki"
    :value (-> display-data :open-floor-space-width-m)
    :form-field
    [lui/text-field
     {:type          "number"
      :spec          int?
      #_#_:adornment "m"
      :value         (-> edit-data :open-floor-space-width-m)
      :on-change     #(on-change :open-floor-space-width-m %)}]}

   ;; Vapaan lattiatilan pinta-ala (m2) katsomot auki
   {:label "Vapaan lattiatilan pinta-ala (m²) katsomot auki"
    :value (-> display-data :open-floor-space-area-m2)
    :form-field
    [lui/text-field
     {:type          "number"
      :spec          number?
      #_#_:adornment "m"
      :value         (-> edit-data :open-floor-space-area-m2)
      :on-change     #(on-change :open-floor-space-area-m2 %)}]}

   ;; Kentän pituus (m)
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

   ;; Kentän pinta-ala (m2)
   {:label "Kentän pinta-ala (m²)"
    :value (-> display-data :field-area-m2)
    :form-field
    [lui/text-field
     {:type          "number"
      :spec          number?
      #_#_:adornment "m"
      :value         (-> edit-data :field-area-m2)
      :on-change     #(on-change :field-area-m2 %)}]}

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
   {:label "Kiinteä lattiamateriaali"
    :value (-> display-data :field-minimum-height-m)
    :form-field
    [lui/select
     {:items     [{:label "Parketti" :value "parquet"}
                  {:label "Matto" :value "carpet"}
                  {:label "Massa" :value "clay"}]
      :value     (-> edit-data :field-surface-material)
      :on-change #(on-change :field-surface-material %)}]}

   ;; Lattian jousto-ominaisuudet
   {:label "Kiinteä lattiamateriaali"
    :value (-> display-data :field-surface-material-bounce-properties)
    :form-field
    [lui/select
     {:type      "number"
      :items     [{:label "Piste" :value "point"}
                  {:label "Alue" :value "area"}
                  {:label "Ei tietoa" :value "unknown"}]
      :value     (-> edit-data :field-surface-material-bounce-properties)
      :on-change #(on-change :field-surface-material-bounce-properties %)}]}

   ;; Merkki, jos tiedossa(Taraflex, Pulastic, tms.)
   {:label "Lattiamateriaalin merkki"
    :value (-> display-data :field-surface-material-brand)
    :form-field
    [lui/text-field
     {:type          "text"
      :spec          string?
      #_#_:adornment (tr :units/hours-per-day)
      :value         (-> edit-data :field-surface-material-brand)
      :on-change     #(on-change :field-surface-material-brand %)}]}

   ;; Lattian väri
   {:label "Lattian väri"
    :value (-> display-data :field-surface-material-color)
    :form-field
    [lui/text-field
     {:type          "text"
      :spec          string?
      #_#_:adornment (tr :units/hours-per-day)
      :value         (-> edit-data :field-surface-material-color)
      :on-change     #(on-change :field-surface-material-color %)}]}

   ;; Salibandymaalien lukumäärä hallilla
   {:label "Salibandymaalien lukumäärä hallilla"
    :value (-> display-data :available-goals-count)
    :form-field
    [lui/text-field
     {:type          "number"
      :spec          int?
      #_#_:adornment "m"
      :value         (-> edit-data :available-goals-count)
      :on-change     #(on-change :available-goals-count %)}]}

   ;; IFF:n hyväksyntätarrat maaleissa
   {:label "IFF:n hyväksyntätarrat maaleissa"
    :value (-> display-data :iff-certification-stickers-in-goals?)
    :form-field
    [lui/checkbox
     {#_#_:adornment "m"
      :value         (-> edit-data :iff-certification-stickers-in-goals?)
      :on-change     #(on-change :iff-certification-stickers-in-goals? %)}]}

   ;; Maalinpienennyselementit
   {:label "Maalinpienennyselementit (lkm)"
    :value (-> display-data :goal-decreasing-elements-count)
    :form-field
    [lui/text-field
     {:type          "number"
      :spec          int?
      #_#_:adornment "m"
      :value         (-> edit-data :goal-decreasing-elements-count)
      :on-change     #(on-change :goal-decreasing-elements-count %)}]}

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

   ;; Kaukalon merkki
   {:label "Kaukalon merkki"
    :value (-> display-data :rink-brand)
    :form-field
    [lui/text-field
     {:type          "text"
      :spec          string?
      #_#_:adornment (tr :units/hours-per-day)
      :value         (-> edit-data :rink-brand)
      :on-change     #(on-change :rink-brand %)}]}

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

   ;; IFF:n hyväksyntätarrat maaleissa
   {:label "IFF:n hyväksnyä kaukalossa"
    :value (-> display-data :iff-certified-rink?)
    :form-field
    [lui/checkbox
     {#_#_:adornment "m"
      :value         (-> edit-data :iff-certified-rink?)
      :on-change     #(on-change :iff-certified-rink? %)}]}

   ;; Kulmapalojen määrä
   {:label "Kulmapalojen määrä"
    :value (-> display-data :corner-pieces-count)
    :form-field
    [lui/text-field
     {:type          "number"
      :spec          int?
      #_#_:adornment "m"
      :value         (-> edit-data :corner-pieces-count)
      :on-change     #(on-change :corner-pieces-count %)}]}

   ;; TODO pukuhuoneille ehkä oma lista/alilomake

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
   {:label "Tulostaulujen määrä hallissa"
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

   ;; Pelaajien kulku halliin (Oma sisäänkäynti / Katsojien kanssa samasta)
   {:label "Pelaajien kulku halliin"
    :value (-> display-data :player-entrance)
    :form-field
    [lui/select
     {:items     [{:label "Oma sisäänkäynti" :value "private-entrance"}
                  {:label "Katsojien kanssa samasta" :value "audience-entrance"}]
      :value     (-> edit-data :player-entrance)
      :on-change #(on-change :player-entrance %)}]}

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

   ;; Yleisön kulku katsomoon (Kenttätasolta / Yläkautta)
   {:label "Yleisön kulku katsomoon"
    :value (-> display-data :audience-access-to-stands)
    :form-field
    [lui/select
     {:items     [{:label "Kenttätasolta" :value "from-field-level"}
                  {:label "Yläkautta" :value "from-upper-level"}]
      :value     (-> edit-data :audience-access-to-stands)
      :on-change #(on-change :audience-access-to-stands %)}]}

   ;; Pääseekö kenttätasolle ilman rappusia (KYLLÄ/EI)
   {:label "Pääseekö kenttätasolle ilman rappusia"
    :value (-> display-data :field-accessible-without-strairs?)
    :form-field
    [lui/checkbox
     {#_#_:adornment "m"
      :value         (-> edit-data :field-accessible-without-strairs?)
      :on-change     #(on-change :field-accessible-without-strairs? %)}]}

   ;; Onko lastausovia kenttätasolla (KYLLÄ/EI)
   {:label "Onko lastausovia kenttätasolla"
    :value (-> display-data :field-level-loading-doors?)
    :form-field
    [lui/checkbox
     {#_#_:adornment "m"
      :value         (-> edit-data :field-level-loading-doors?)
      :on-change     #(on-change :field-level-loading-doors? %)}]}

   ;; Onko pumppukärryjä/tms. (KYLLÄ/EI)
   {:label "Onko pumppukärryjä/tms"
    :value (-> display-data :loading-equipment-available?)
    :form-field
    [lui/checkbox
     {#_#_:adornment "m"
      :value         (-> edit-data :loading-equipment-available?)
      :on-change     #(on-change :loading-equipment-available? %)}]}

   ;; Irtotuolien määrä (noin arvio)
   {:label "Irtotuolien ja pöytien määrä (noin arvio)"
    :value (-> display-data :detached-chair-quantity)
    :form-field
    [lui/text-field
     {:type          "number"
      :spec          number?
      #_#_:adornment "m"
      :value         (-> edit-data :detached-chair-quantity)
      :on-change     #(on-change :detached-chair-quantity %)}]}

   {:label "Irtopyötien määrä (noin arvio)"
    :value (-> display-data :detached-table-quantity)
    :form-field
    [lui/text-field
     {:type          "number"
      :spec          number?
      #_#_:adornment "m"
      :value         (-> edit-data :detached-table-quantity)
      :on-change     #(on-change :detached-table-quantity %)}]}

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

   ;; Kokoustilojen yhteenlaskettu henkilökapasiteetti
   {:label "Kokoustilojen yhteenlaskettu henkilökapasiteetti"
    :value (-> display-data :conference-space-total-capacity-person)
    :form-field
    [lui/text-field
     {:type          "number"
      :spec          number?
      #_#_:adornment "m"
      :value         (-> edit-data :conference-space-total-capacity-person)
      :on-change     #(on-change :conference-space-total-capacity-person %)}]}

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

   ;; Kattotrussit (KYLLÄ/EI)
   {:label "Kattotrussit"
    :value (-> display-data :roof-truss?)
    :form-field
    [lui/checkbox
     {#_#_:adornment "m"
      :value         (-> edit-data :roof-truss?)
      :on-change     #(on-change :roof-truss? %)}]}

   ;; Kattotrussien kantavuus (kg)
   {:label "Kattotrussien kantavuus (kg)"
    :value (-> display-data :roof-truss-capacity-kg)
    :form-field
    [lui/text-field
     {:type          "number"
      :spec          number?
      #_#_:adornment "m"
      :value         (-> edit-data :roof-truss-capacity-kg)
      :on-change     #(on-change :roof-truss-capacity-kg %)}]}

   ;; Kattotrussit (Saa laskettua alas / tarvitsee nostimen)
   {:label "Kattotrussit..."
    :value (-> display-data :roof-truss-operation-model)
    :form-field
    [lui/select
     {:items     [{:label "Saa laskettua alas" :value "can-be-lowered"}
                  {:label "Tarvitsee nostimen" :value "lift-required"}]
      :value     (-> edit-data :roof-truss-operation-model)
      :on-change #(on-change :roof-truss-operation-model %)}]}

   ;; Onko kiinteät kaiuttimet katsomoa kohti (KYLLÄ/EI)
   {:label "Onko kiinteät kaiuttimet katsomoa kohti"
    :value (-> display-data :speakers-aligned-towards-stands?)
    :form-field
    [lui/checkbox
     {#_#_:adornment "m"
      :value         (-> edit-data :speakers-aligned-towards-stands?)
      :on-change     #(on-change :speakers-aligned-towards-stands? %)}]}

   ;; Onko mikseri, jolla saa äänentoiston yhdistettyä (KYLLÄ/EI)
   {:label "Onko mikseri, jolla saa äänentoiston yhdistettyä"
    :value (-> display-data :audio-mixer-available?)
    :form-field
    [lui/checkbox
     {#_#_:adornment "m"
      :value         (-> edit-data :audio-mixer-available?)
      :on-change     #(on-change :audio-mixer-available? %)}]}

   ;; Langattominen mikrofonien määrä
   {:label "Langattominen mikrofonien määrä"
    :value (-> display-data :wireless-microfone-quantity)
    :form-field
    [lui/text-field
     {:type          "number"
      :spec          number?
      #_#_:adornment "m"
      :value         (-> edit-data :wireless-microfone-quantity)
      :on-change     #(on-change :wireless-microfone-quantity %)}]}

   ;; Langallisten mikrofonien määrä
   {:label "Langallisten mikrofonien määrä"
    :value (-> display-data :wired-microfone-quantity)
    :form-field
    [lui/text-field
     {:type          "number"
      :spec          number?
      #_#_:adornment "m"
      :value         (-> edit-data :wired-microfone-quantity)
      :on-change     #(on-change :wired-microfone-quantity %)}]}

   ;; Onko kameratasanteita (KYLLÄ/EI)
   {:label "Onko kameratasanteita"
    :value (-> display-data :camera-stands?)
    :form-field
    [lui/checkbox
     {#_#_:adornment "m"
      :value         (-> edit-data :camera-stands?)
      :on-change     #(on-change :camera-stands? %)}]}

   ;; Onko hallilla kiinteät kamerat (KYLLÄ/EI)
   {:label "Onko hallilla kiinteät kamerat"
    :value (-> display-data :fixed-cameras?)
    :form-field
    [lui/checkbox
     {#_#_:adornment "m"
      :value         (-> edit-data :fixed-cameras?)
      :on-change     #(on-change :fixed-cameras? %)}]}

   ;; Onko lähetysautolle paikka hallin vieressä (KYLLÄ/EI)
   {:label "Onko lähetysautolle paikka hallin vieressä"
    :value (-> display-data :broadcast-car-park?)
    :form-field
    [lui/checkbox
     {#_#_:adornment "m"
      :value         (-> edit-data :broadcast-car-park?)
      :on-change     #(on-change :broadcast-car-park? %)}]}

   ;; Onko hallilla käytettävissä salasanalla suojattuja/yleisiä langattomia verkkoja (KYLLÄ/EI)
   {:label "Onko hallilla käytettävissä salasanalla suojattuja/yleisiä langattomia verkkoja"
    :value (-> display-data :wifi-available?)
    :form-field
    [lui/checkbox
     {#_#_:adornment "m"
      :value         (-> edit-data :wifi-available?)
      :on-change     #(on-change :wifi-available? %)}]}

   ;; Riittääkö langattoman verkon kaista esim. striimaukseen (KYLLÄ/EI)
   {:label "Riittääkö langattoman verkon kaista esim. striimaukseen"
    :value (-> display-data :wifi-capacity-sufficient-for-streaming?)
    :form-field
    [lui/checkbox
     {#_#_:adornment "m"
      :value         (-> edit-data :wifi-capacity-sufficient-for-streaming?)
      :on-change     #(on-change :wifi-capacity-sufficient-for-streaming? %)}]}

   ;; Onko sähköjen sijainnista saatavilla tieto etukäteen, esim. karttapohja (KYLLÄ/EI)
   {:label "Onko sähköjen sijainnista saatavilla tieto etukäteen, esim. karttapohja"
    :value (-> display-data :electrical-plan-available?)
    :form-field
    [lui/checkbox
     {#_#_:adornment "m"
      :value         (-> edit-data :electrical-plan-available?)
      :on-change     #(on-change :electrical-plan-available? %)}]}

   ;; Onko voimavirtamahdollisuus (KYLLÄ/EI)
   {:label "Onko voimavirtamahdollisuus"
    :value (-> display-data :three-phase-electric-power?)
    :form-field
    [lui/checkbox
     {#_#_:adornment "m"
      :value         (-> edit-data :three-phase-electric-power?)
      :on-change     #(on-change :three-phase-electric-power? %)}]}

   ;; Onko LED-näyttöä/screeniä tai LED-pintoja mainoksille (KYLLÄ/EI)
   {:label "Onko LED-näyttöä/screeniä tai LED-pintoja mainoksille"
    :value (-> display-data :led-screens-or-surfaces-for-ads?)
    :form-field
    [lui/checkbox
     {#_#_:adornment "m"
      :value         (-> edit-data :led-screens-or-surfaces-for-ads?)
      :on-change     #(on-change :led-screens-or-surfaces-for-ads? %)}]}

   ;; Katselmus tehty
   {:label "Katselmus tehty"
    :value (-> display-data :audit-date)
    :form-field
    [lui/date-picker
     {#_#_:adornment "m"
      :value         (-> edit-data :audit-date)
      :on-change     #(on-change :audit-date %)}]}

;; Muita Lipas-järjestelmässä kysyttyjä tietoja (jotka voisivat olla tässä kiinnostavia)

   ])
