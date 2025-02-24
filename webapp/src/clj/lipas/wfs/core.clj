(ns lipas.wfs.core
  (:require [clojure.data.csv :as csv]
            [clojure.set :as set]
            [clojure.string :as str]
            [honey.sql :as sql]
            [honey.sql.pg-ops]
            [lipas.backend.core :as core]
            [lipas.data.admins :as admins]
            [lipas.data.cities :as cities]
            [lipas.data.owners :as owners]
            [lipas.data.types :as types]
            [lipas.integration.old-lipas.sports-site :as legacy-utils]
            [next.jdbc :as jdbc]
            [taoensso.timbre :as log]))

(def helsinki-tz (java.time.ZoneId/of "Europe/Helsinki"))

(defn ->helsinki-time [s]
  (-> (java.time.Instant/parse s)
        (.atZone helsinki-tz)
        (.toLocalDateTime)
        (str)))

(def legacy-handle->prop legacy-utils/prop-mappings)

(def legacy-handle->legacy-prop
  {:liftsCount                   :hissit_lkm,
   :boxingRingsCount             :nyrkkeilykehat_lkm,
   :floorballFieldsCount         :salibandykentat_lkm,
   :pistolShooting               :pistooliammunta,
   :shotputCount                 :kuulantyonto_lkm,
   :swimmingPoolCount            :uima_altaiden_lkm,
   :pool1TemperatureC            :allas1_veden_lampo_c,
   :7m5PlatformsCount            :hyppytelineet_7m5_lkm,
   :classifiedRoute              :luokiteltu_reitti,
   :equipmentRental              :valinevuokraus,
   :ligthing                     :valaistus,
   :kiosk                        :kioski,
   :fieldsCount                  :kenttien_lkm,
   :kPoint                       :hyppyrimaki_k_piste,
   :bikeOrienteering?            :pyöräsuunnistus_mahdollista,
   :skiService                   :suksihuolto,
   :fieldLengthM                 :kentan_pituus_m,
   :showJumping                  :esteratsastus,
   :skiTrack                     :latu,
   :cosmicBowling                :hohtokeilaus,
   :field2FlexibleRink?          :2._kenttä:_onko_joustokaukalo?,
   :field3AreaM2                 :kentta3_ala_m2,
   :polevaultPlacesCount         :seivashyppy_lkm,
   :spinningHall                 :spinning_sali,
   :waterslidesTotalLengthM      :liukumaet_kokonaispituus_m,
   :climbingWall                 :kiipeilyseina,
   :sportSpecification           :lajitarkenne,
   :iceClimbing                  :jaakiipeily_mahdollisuus,
   :travelModeInfo               :kulkutavat_lisätieto,
   :sauna                        :sauna,
   :trackWidthM                  :radan_leveys_m,
   :routeWidthM                  :reitin_leveys_m,
   :basketballFieldsCount        :koripallokentat_lkm,
   :pier                         :laituri,
   :radioAndTvCapabilities       :radio_tv_valmius,
   :10mPlatformsCount            :hyppytelineet_10m_lkm,
   :pool4TemperatureC            :allas4_veden_lampo_c,
   :spaceDivisible               :tila_jaettavissa_osiin,
   :auxiliaryTrainingArea?       :oheisharjoittelutila,
   :bowlingLanesCount            :keilaradat_lkm,
   :pyramidTablesCount           :pyramidipöydät_lkm,
   :squashCourtsCount            :squashkentat_lkm,
   :altitudeDifference           :korkeusero_m,
   :gymnasticsSpace              :telinevoimistelutila,
   :climbingWallWidthM           :kiipeilyseina_leveys_m,
   :shower                       :suihku,
   :pool5LengthM                 :allas5_pituus_m,
   :skijumpHillMaterial          :vauhtimaen_rakenne,
   :summerUsage                  :kesakaytto,
   :lightingInfo                 :valaistuksen_lisätieto,
   :hammerThrowPlacesCount       :moukarinheitto_lkm,
   :parkingPlace                 :parkkipaikka,
   :trackLengthM                 :radan_pituus_m,
   :climbingRoutesCount          :kiipeilyreittien_lkm,
   :customerServicePoint?        :myynti_tai_asiakaspalvelupiste,
   :parkourHallEquipmentAndStructures
   :parkour_salin_varustelu_ja_rakenteet,
   :field3WidthM                 :kentta3_leveys_m,
   :skiTrackTraditional          :latu_perinteinen,
   :litSlopesCount               :valaistut_rinteet_lkm,
   :coveredStandPersonCount      :katsomosta_katettua_hlo,
   :surfaceMaterial              :pintamateriaali,
   :iceReduction                 :jaatymisenesto_jarjestelma,
   :pool1MaxDepthM               :allas1_syvyys_max_m,
   :waterPoint                   :vesipiste,
   :poolWaterAreaM2              :allaspinta_ala_m2,
   :activeSpaceWidthM            :liikuntakäytössä_olevan_tilan_leveys_m,
   :shortestSlopeM               :lyhin_rinne_m,
   :fitnessStairsLengthM         :kuntoportaiden_pituus_m,
   :badmintonCourtsCount         :sulkapallokentat_lkm,
   :green                        :puttiviherio,
   :5mPlatformsCount             :hyppytelineet_5m_lkm,
   :fieldWidthM                  :kentan_leveys_m,
   :oldLipasTypecode             :liikuntapaikkatyyppi_vanha,
   :pool5WidthM                  :allas5_leveys_m,
   :mirrorWall?                  :peiliseinä,
   :accessibilityInfo            :esteettomyys_tietoa,
   :trainingWall                 :lyontiseina,
   :lightRoof                    :kevytkate,
   :3mPlatformsCount             :hyppytelineet_3m_lkm,
   :basketballFieldType          :koripallokentan_tyyppi,
   :pool5MinDepthM               :allas5_syvyys_min_m,
   :pool4MinDepthM               :allas4_syvyys_min_m,
   :javelinThrowPlacesCount      :keihaanheittopaikkojen_lkm,
   :waterSlidesCount             :liukumaet_lkm,
   :field1LengthM                :kentta1_pituus_m,
   :areaKm2                      :pinta_alakm2,
   :airGunShooting               :ilma_aseammunta,
   :skiTrackFreestyle            :latu_vapaa,
   :jumpsCount                   :hyppyrit_lkm,
   :pool1LengthMM                :allas1_pituus_m,
   :automatedTiming              :automaattinen_ajanotto,
   :surfaceMaterialInfo          :pintamateriaali_lisatieto,
   :sleddingHill?                :pulkkamäki,
   :pool5MaxDepthM               :allas5_syvyys_max_m,
   :pool2LengthM                 :allas2_pituus_m,
   :adpReadiness                 :tulospalv_atk_valmius,
   :weightLiftingSpotsCount      :painonnostopaikat_lkm,
   :euBeach                      :eu_ranta,
   :infoFi                       :lisatieto_fi,
   :pool3TracksCount             :allas3_ratojen_lkm,
   :snowparkOrStreet             :temppurinne,
   :restPlacesCount              :taukopaikat_lkm,
   :highestObstacleM             :korkeimman_esteen_korkeus_m,
   :playground                   :leikkipuisto,
   :pool2MinDepthM               :allas2_syvyys_min_m,
   :longjumpPlacesCount          :pituus_ja_kolmiloikkapaikkojen_lkm,
   :tatamisCount                 :tatamit_lkm,
   :holesCount                   :vaylat_lkm,
   :skiOrienteering?             :hiihtosuunnistus_mahdollista,
   :halfpipeCount                :lumikouru_kpl,
   :beachLengthM                 :rannan_pituus_m,
   :totalBilliardTablesCount     :biljardipöydät_yhteensä_lkm,
   :handballFieldsCount          :kasipallokentat_lkm,
   :matchClock                   :ottelukello,
   :sprintLanesCount             :etusuorien_lkm,
   :boatingServiceClass          :venesatama_tai_laituriluokka,
   :hallLengthM                  :halli1_pituus_m,
   :kaisaTablesCount             :kaisapöydät_lkm,
   :exerciseMachinesCount        :kuntoilulaite_lkm,
   :yearRoundUse?                :ympärivuotinen_käyttö,
   :pool5TemperatureC            :allas5_veden_lampo_c,
   :field1FlexibleRink?          :1._kenttä:_onko_joustokaukalo?,
   :wrestlingMatsCount           :painimatot_lkm,
   :volleyballFieldsCount        :lentopallokentat_lkm,
   :tennisCourtsCount            :tenniskentat_lkm,
   :mayBeShownInExcursionMapFi   :saa_julkaista_retkikartassa,
   :mobileOrienteering?          :mobiilisuunnistusmahdollisuus,
   :pool3WidthM                  :allas3_leveys_m,
   :mayBeShownInHarrastuspassiFi :saa_julkaista_harrastuspassissa,
   :trainingSpotSurfaceMaterial  :suorituspaikan_pintamateriaali,
   :longestSlopeM                :pisin_rinne_m,
   :heightM                      :korkeus_m,
   :toilet                       :yleiso_wc,
   :winterSwimming               :talviuintipaikka,
   :field3FlexibleRink?          :3._kenttä:_onko_joustokaukalo?,
   :shotgunShooting              :haulikkoammunta,
   :curlingLanesCount            :curling_ratojen_lkm,
   :pool3LengthM                 :allas3_pituus_m,
   :climbingWallHeightM          :kiipeilyseinan_korkeus_m,
   :pool2TracksCount             :allas2_ratojen_lkm,
   :freeCustomerUse?             :vapaa_asiakaskäyttö,
   :litRouteLengthKm             :valaistua_km_reitti,
   :throwintSportsPlaces         :heittolajit_harjoituspaikat,
   :changingRooms                :pukukopit,
   :pool3MinDepthM               :allas3_syvyys_min_m,
   :groupExerciseRoomsCount      :ryhmaliikuntatila_lkm,
   :archery                      :jousiammunta,
   :maxVerticalDifference        :korkeusero_max_m,
   :futsalFieldsCount            :futsal_kenttien_lkm,
   :automatedScoring             :kirjanpitoautomaatit,
   :innerLaneLengthM             :sisaradan_pituus_m,
   :pool5TracksCount             :allas5_ratojen_lkm,
   :field1WidthM                 :kentta1_leveys_m,
   :pool4WidthM                  :allas4_leveys_m,
   :landingPlacesCount           :telinevoimistelun_alastulomonttu_lkm,
   :pool4MaxDepthM               :allas4_syvyys_max_m,
   :sprintTrackLengthM           :juoksuradan_etusuoran_pituus_m,
   :footballFieldsCount          :jalkapallokentat_lkm,
   :otherPoolsCount              :muut_altaat_lkm,
   :pool1TracksCount             :allas1_ratojen_lkm,
   :gymnasticRoutinesCount       :telinevoimistelun_telinesarja_lkm,
   :shootingPositionsCount       :ampumapaikat_lkm,
   :freestyleSlope               :kumparerinne,
   :1mPlatformsCount             :hyppytelineet_1m_lkm,
   :scoreboard                   :tulostaulu,
   :pool1WidthM                  :allas1_leveys_m,
   :rifleShooting                :kivaariammunta,
   :travelModes                  :kulkutavat,
   :finishLineCamera             :maalikamera,
   :pool3TemperatureC            :allas3_veden_lampo_c,
   :runningTrackSurfaceMaterial  :juoksuradan_pintamateriaali,
   :heightOfBasketOrNetAdjustable?
   :korin_tai_verkon_korkeus_säädettävissä,
   :field1AreaM2                 :kentta1_ala_m2,
   :horseCarriageAllowed         :karryilla_ajo_sallittu,
   :circularLanesCount           :kiertavien_juoksuratojen_lkm,
   :standCapacityPerson          :katsomo_kapasiteetti_hlo,
   :pool2WidthM                  :allas2_leveys_m,
   :discusThrowPlaces            :kiekonheittopaikkojen_lkm,
   :caromTablesCount             :karapöydät_lkm,
   :pPoint                       :hyppyrimaki_p_piste,
   :field2LengthM                :kentta2_pituus_m,
   :tableTennisCount             :poytatennis_lkm,
   :boatPlacesCount              :venepaikkojen_lkm,
   :changingRoomsM2              :pukukoppien_kokonaispinta_ala_m²,
   :field2AreaM2                 :kentta2_ala_m2,
   :otherPlatforms               :hyppytelineet_muut,
   :iceRinksCount                :kaukalo_lkm,
   :poolTablesCount              :poolpöydät_lkm,
   :boatLaunchingSpot            :veneen_vesillelaskup,
   :pool4TracksCount             :allas4_ratojen_lkm,
   :tobogganRun                  :ohjaskelkkamaki,
   :field2WidthM                 :kentta2_leveys_m,
   :areaM2                       :pinta_ala_m2,
   :pool3MaxDepthM               :allas3_syvyys_max_m,
   :trackType                    :ratatyyppi,
   :ringetteBoundaryMarkings?    :ringeten_rajamerkinnät,
   :routeLengthKm                :reitin_pituus_km,
   :loudspeakers                 :tulospalvelu_aanentoisto,
   :pool2MaxDepthM               :allas2_syvyys_max_m,
   :outdoorExerciseMachines      :kuntoilutelineet_boolean,
   :pool4LengthM                 :allas4_pituus_m,
   :slopesCount                  :rinteet_lkm,
   :field3LengthM                :kentta3_pituus_m,
   :fencingBasesCount            :miekkailu_alustat_lkm,
   :hallWidthM                   :halli1_leveys_m,
   :pool2TemperatureC            :allas2_veden_lampo_c,
   :heating                      :lammitys,
   :winterUsage                  :talvikaytto,
   :padelCourtsCount             :padelkentat_lkm,
   :plasticOutrun                :alastulo_muovitus,
   :highjumpPlacesCount          :korkeushyppypaikkojen_lkm,
   :freeRifleShooting            :pienoiskivaariammunta,
   :range                        :range_harjoitusalue,
   :hsPoint                      :hs_piste,
   :snookerTablesCount           :snookerpöydät_lkm,
   :pool1MinDepthM               :allas1_syvyys_min_m,
   :activeSpaceLengthM           :liikuntakäytössä_olevan_tilan_pituus_m,
   :inrunsMaterial               :vauhtimaen_latumateriaali,
   :skijumpHillType              :hyppyrimaen_tyyppi})

(def legacy-prop->legacy-handle
  (set/map-invert legacy-handle->legacy-prop))

(def legacy-field->resolve-fn
  (merge
   {:id                    :lipas-id
    :nimi_fi               :name
    :nimi_se               (comp :se :name-localized)
    :nimi_en               (comp :en :name-localized)
    :sahkoposti            :email
    :www                   :www
    :puhelinnumero         :phone-number
    :koulun_liikuntapaikka (comp :school-use? :properties)
    :vapaa_kaytto          (comp :free-use? :properties)
    :rakennusvuosi         :construction-year
    :peruskorjausvuodet    (fn [m] (some->> m :renovation-years (str/join ",")))
    :omistaja              (comp :fi owners/all :owner)
    :yllapitaja            (comp :fi admins/all :admin)
    :katuosoite            (comp :address :location)
    :postinumero           (comp :postal-code :location)
    :postitoimipaikka      (comp :postal-office :location)
    :kunta_nimi_fi         (comp :fi :name cities/by-city-code :city-code :city :location)
    :kuntanumero           (comp :city-code :city :location)
    :kuntaosa              (comp :neighborhood :city :location)
    :tyyppikoodi           (comp :type-code :type)
    :tyyppi_nimi_fi        (comp :fi :name types/all :type-code :type)
    :tyyppi_nimi_se        (comp :se :name types/all :type-code :type)
    :tyyppi_nimi_en        (comp :en :name types/all :type-code :type)
    :muokattu_viimeksi     (comp ->helsinki-time :event-date)
    :lisatieto_fi          :comment
    :sijainti_id           :lipas-id

    ;; Following keys are "special ones" that don't take sports-site as
    ;; argument, but either a feature or idx. This is because for WFS
    ;; LineString and Polygon layers we need to explode the feature
    ;; collection to as many rows as there are features. LIPAS WFS
    ;; does not use MultiLineString or MultiPolygon for backwards
    ;; compatibility reasons.
    :the_geom              (fn [feature] (:geometry feature))
    :reitti_id             (fn [idx] (inc idx))
    :alue_id               (fn [idx] (inc idx))
    :kulkusuunta           (fn [feature] (-> feature :properties :travel-direction))}

   ;; Create lookup functions for all possible type-specific props.
   (update-vals legacy-prop->legacy-handle
                (fn [legacy-handle]
                  (fn [m]
                    (let [prop-k (legacy-handle->prop legacy-handle)]
                      (get-in m [:properties prop-k])))))))

(def common-fields
  "Fields present in all WFS views"
  #{:id
    :katuosoite
    :koulun_liikuntapaikka
    :kunta_nimi_fi
    :kuntanumero
    :kuntaosa
    :lisatieto_fi
    :muokattu_viimeksi
    :nimi_en
    :nimi_fi
    :nimi_se
    :omistaja
    :peruskorjausvuodet
    :postinumero
    :postitoimipaikka
    :puhelinnumero
    :rakennusvuosi
    :sahkoposti
    :the_geom
    :tyyppi_nimi_fi
    :tyyppikoodi
    :vapaa_kaytto
    :www
    :yllapitaja})

(def type-code->legacy-fields
  "Excluding common-fields"
  {1530
   #{:pinta_ala_m2
     :pintamateriaali_lisatieto
     :ottelukello
     :pukukopit
     :kevytkate
     :kentan_pituus_m
     :yleiso_wc
     :kentan_leveys_m
     :katsomo_kapasiteetti_hlo
     :kaukalo_lkm
     :pintamateriaali
     :valaistus},
   1520
   #{:kenttien_lkm
     :pinta_ala_m2
     :pintamateriaali_lisatieto
     :ottelukello
     :pukukopit
     :kevytkate
     :kentan_pituus_m
     :yleiso_wc
     :kioski
     :kentan_leveys_m
     :katsomo_kapasiteetti_hlo
     :pintamateriaali
     :valaistus},
   2320
   #{:pinta_ala_m2
     :pintamateriaali_lisatieto
     :korkeus_m
     :telinevoimistelun_alastulomonttu_lkm
     :telinevoimistelun_telinesarja_lkm
     :pintamateriaali},
   6130
   #{:pinta_ala_m2
     :pintamateriaali_lisatieto
     :kentan_pituus_m
     :yleiso_wc
     :kioski
     :kentan_leveys_m
     :pintamateriaali
     :valaistus},
   1395 #{:pinta_ala_m2 :yleiso_wc :valaistus :poytatennis_lkm},
   6210
   #{:pinta_ala_m2
     :pintamateriaali_lisatieto
     :kentan_pituus_m
     :radan_pituus_m
     :yleiso_wc
     :kentan_leveys_m
     :pintamateriaali
     :valaistus},
   1370
   #{:kenttien_lkm
     :pinta_ala_m2
     :pintamateriaali_lisatieto
     :kevytkate
     :lammitys
     :kentan_pituus_m
     :yleiso_wc
     :kentan_leveys_m
     :lyontiseina
     :pintamateriaali
     :valaistus},
   1360
   #{:kenttien_lkm
     :pinta_ala_m2
     :pintamateriaali_lisatieto
     :ottelukello
     :pukukopit
     :lammitys
     :kentan_pituus_m
     :tulostaulu
     :katsomosta_katettua_hlo
     :yleiso_wc
     :kioski
     :kentan_leveys_m
     :tulospalvelu_aanentoisto
     :katsomo_kapasiteetti_hlo
     :pintamateriaali
     :valaistus},
   110 #{:alue_id :pinta_alakm2},
   2360
   #{:pinta_ala_m2
     :pintamateriaali_lisatieto
     :ampumapaikat_lkm
     :ilma_aseammunta
     :radan_pituus_m
     :kivaariammunta
     :pintamateriaali
     :pienoiskivaariammunta
     :pistooliammunta},
   5310
   #{:pinta_ala_m2
     :pintamateriaali_lisatieto
     :maalikamera
     :radan_pituus_m
     :tulostaulu
     :katsomosta_katettua_hlo
     :yleiso_wc
     :kioski
     :tulospalvelu_aanentoisto
     :katsomo_kapasiteetti_hlo
     :automaattinen_ajanotto
     :pintamateriaali
     :radan_leveys_m
     :valaistus},
   1560
   #{:pintamateriaali_lisatieto
     :radan_pituus_m
     :hissit_lkm
     :yleiso_wc
     :korkeusero_m
     :pintamateriaali
     :radan_leveys_m
     :valaistus},
   205
   #{:saa_julkaista_retkikartassa
     :veneen_vesillelaskup
     :yleiso_wc
     :laituri},
   2150
   #{:pinta_ala_m2
     :jalkapallokentat_lkm
     :pintamateriaali_lisatieto
     :korkeus_m
     :ottelukello
     :futsal_kenttien_lkm
     :tenniskentat_lkm
     :lentopallokentat_lkm
     :kentan_pituus_m
     :kasipallokentat_lkm
     :kentan_leveys_m
     :pintamateriaali
     :spinning_sali
     :sulkapallokentat_lkm
     :koripallokentat_lkm
     :salibandykentat_lkm
     :telinevoimistelutila},
   2210
   #{:kiipeilyseina
     :pinta_ala_m2
     :jalkapallokentat_lkm
     :pintamateriaali_lisatieto
     :esteettomyys_tietoa
     :korkeus_m
     :ottelukello
     :etusuorien_lkm
     :futsal_kenttien_lkm
     :korkeushyppypaikkojen_lkm
     :juoksuradan_pintamateriaali
     :pituus_ja_kolmiloikkapaikkojen_lkm
     :kiertavien_juoksuratojen_lkm
     :tenniskentat_lkm
     :moukarinheitto_lkm
     :lentopallokentat_lkm
     :kentan_pituus_m
     :seivashyppy_lkm
     :tulostaulu
     :kasipallokentat_lkm
     :sisaradan_pituus_m
     :yleiso_wc
     :keihaanheittopaikkojen_lkm
     :kioski
     :kentan_leveys_m
     :ryhmaliikuntatila_lkm
     :katsomo_kapasiteetti_hlo
     :pintamateriaali
     :squashkentat_lkm
     :sulkapallokentat_lkm
     :koripallokentat_lkm
     :salibandykentat_lkm
     :kuulantyonto_lkm
     :telinevoimistelutila
     :kiekonheittopaikkojen_lkm},
   101 #{:leikkipuisto :alue_id :pinta_alakm2},
   102
   #{:saa_julkaista_retkikartassa
     :leikkipuisto
     :alue_id
     :pinta_alakm2},
   7000
   #{:pinta_ala_m2
     :sauna
     :pukukopit
     :suksihuolto
     :yleiso_wc
     :kioski
     :suihku
     :valinevuokraus},
   1110
   #{:kenttien_lkm
     :pinta_ala_m2
     :esteettomyys_tietoa
     :leikkipuisto
     :alue_id
     :valaistus},
   6220
   #{:pinta_ala_m2
     :pintamateriaali_lisatieto
     :lammitys
     :kentan_pituus_m
     :yleiso_wc
     :kioski
     :kentan_leveys_m
     :pintamateriaali
     :valaistus},
   4530 #{:alue_id :pinta_alakm2},
   4720
   #{:saa_julkaista_retkikartassa
     :pinta_ala_m2
     :pintamateriaali_lisatieto
     :kiipeilyseinan_korkeus_m
     :kiipeilyreittien_lkm
     :kiipeilyseina_leveys_m
     :pintamateriaali
     :jaakiipeily_mahdollisuus
     :valaistus},
   1330
   #{:pinta_ala_m2
     :pintamateriaali_lisatieto
     :kentan_pituus_m
     :yleiso_wc
     :kentan_leveys_m
     :pintamateriaali
     :valaistus},
   206 #{:saa_julkaista_retkikartassa :yleiso_wc},
   4830
   #{:pinta_ala_m2
     :ampumapaikat_lkm
     :radan_pituus_m
     :yleiso_wc
     :kioski
     :radan_leveys_m
     :valaistus},
   1180
   #{:saa_julkaista_retkikartassa
     :pintamateriaali_lisatieto
     :esteettomyys_tietoa
     :ratatyyppi
     :radan_pituus_m
     :vaylat_lkm
     :korkeusero_m
     :pintamateriaali
     :valaistus
     :range_harjoitusalue},
   4422
   #{:saa_julkaista_retkikartassa
     :reitin_leveys_m
     :reitin_pituus_km
     :taukopaikat_lkm
     :reitti_id},
   4430
   #{:reitin_leveys_m
     :pintamateriaali_lisatieto
     :reitin_pituus_km
     :taukopaikat_lkm
     :valaistua_km_reitti
     :reitti_id
     :pintamateriaali},
   204 #{:saa_julkaista_retkikartassa :yleiso_wc},
   106
   #{:saa_julkaista_retkikartassa
     :leikkipuisto
     :alue_id
     :pinta_alakm2},
   4610
   #{:reitin_leveys_m
     :ampumapaikat_lkm
     :reitin_pituus_km
     :taukopaikat_lkm
     :suksihuolto
     :maalikamera
     :tulostaulu
     :valaistua_km_reitti
     :yleiso_wc
     :kioski
     :tulospalvelu_aanentoisto
     :katsomo_kapasiteetti_hlo
     :automaattinen_ajanotto
     :latu_vapaa
     :latu_perinteinen
     :kesakaytto},
   2610
   #{:pinta_ala_m2
     :tulostaulu
     :kirjanpitoautomaatit
     :yleiso_wc
     :kioski
     :tulospalvelu_aanentoisto
     :keilaradat_lkm
     :hohtokeilaus},
   2110
   #{:painonnostopaikat_lkm
     :pinta_ala_m2
     :pintamateriaali_lisatieto
     :kuntoilulaite_lkm
     :kioski
     :ryhmaliikuntatila_lkm
     :pintamateriaali
     :spinning_sali
     :nyrkkeilykehat_lkm},
   3120
   #{:esteettomyys_tietoa
     :allas1_pituus_m
     :allas1_syvyys_max_m
     :allaspinta_ala_m2
     :allas1_veden_lampo_c
     :uima_altaiden_lkm
     :allas1_syvyys_min_m
     :allas1_ratojen_lkm
     :allas1_leveys_m},
   104 #{:saa_julkaista_retkikartassa :alue_id :pinta_alakm2},
   2330
   #{:pinta_ala_m2
     :pintamateriaali_lisatieto
     :korkeus_m
     :kioski
     :pintamateriaali
     :poytatennis_lkm},
   2280
   #{:pinta_ala_m2
     :pintamateriaali_lisatieto
     :korkeus_m
     :tenniskentat_lkm
     :lentopallokentat_lkm
     :kentan_pituus_m
     :tulostaulu
     :yleiso_wc
     :kioski
     :kentan_leveys_m
     :katsomo_kapasiteetti_hlo
     :pintamateriaali
     :squashkentat_lkm
     :sulkapallokentat_lkm
     :salibandykentat_lkm},
   6140
   #{:pintamateriaali_lisatieto
     :maalikamera
     :radan_pituus_m
     :tulostaulu
     :katsomosta_katettua_hlo
     :yleiso_wc
     :kioski
     :tulospalvelu_aanentoisto
     :katsomo_kapasiteetti_hlo
     :automaattinen_ajanotto
     :pintamateriaali
     :radan_leveys_m
     :valaistus},
   2140
   #{:painonnostopaikat_lkm
     :pinta_ala_m2
     :pintamateriaali_lisatieto
     :kuntoilulaite_lkm
     :kioski
     :ryhmaliikuntatila_lkm
     :pintamateriaali
     :tatamit_lkm
     :nyrkkeilykehat_lkm
     :painimatot_lkm},
   4220
   #{:pinta_ala_m2
     :reitin_leveys_m
     :pintamateriaali_lisatieto
     :esteettomyys_tietoa
     :reitin_pituus_km
     :suksihuolto
     :yleiso_wc
     :kioski
     :korkeusero_m
     :pintamateriaali
     :valinevuokraus},
   2230
   #{:pinta_ala_m2
     :jalkapallokentat_lkm
     :pintamateriaali_lisatieto
     :korkeus_m
     :ottelukello
     :tenniskentat_lkm
     :lammitys
     :lentopallokentat_lkm
     :kentan_pituus_m
     :tulostaulu
     :yleiso_wc
     :kioski
     :kentan_leveys_m
     :tulospalvelu_aanentoisto
     :katsomo_kapasiteetti_hlo
     :pintamateriaali
     :sulkapallokentat_lkm
     :koripallokentat_lkm
     :juoksuradan_etusuoran_pituus_m},
   1350
   #{:kenttien_lkm
     :pinta_ala_m2
     :pintamateriaali_lisatieto
     :ottelukello
     :kevytkate
     :lammitys
     :kentan_pituus_m
     :maalikamera
     :tulostaulu
     :katsomosta_katettua_hlo
     :yleiso_wc
     :kioski
     :kentan_leveys_m
     :tulospalvelu_aanentoisto
     :katsomo_kapasiteetti_hlo
     :pintamateriaali
     :valaistus},
   4840 #{:ampumapaikat_lkm :radan_pituus_m :yleiso_wc :valaistus},
   1510
   #{:kenttien_lkm
     :pinta_ala_m2
     :pintamateriaali_lisatieto
     :ottelukello
     :pukukopit
     :kevytkate
     :kentan_pituus_m
     :yleiso_wc
     :kioski
     :kentan_leveys_m
     :katsomo_kapasiteetti_hlo
     :kaukalo_lkm
     :pintamateriaali
     :valaistus},
   5350
   #{:pinta_ala_m2
     :pintamateriaali_lisatieto
     :radan_pituus_m
     :tulostaulu
     :katsomosta_katettua_hlo
     :kioski
     :tulospalvelu_aanentoisto
     :katsomo_kapasiteetti_hlo
     :automaattinen_ajanotto
     :pintamateriaali
     :radan_leveys_m
     :valaistus},
   4440
   #{:reitin_leveys_m
     :pintamateriaali_lisatieto
     :reitin_pituus_km
     :taukopaikat_lkm
     :valaistua_km_reitti
     :reitti_id
     :yleiso_wc
     :latu_vapaa
     :pintamateriaali
     :latu_perinteinen},
   2520
   #{:curling_ratojen_lkm
     :pinta_ala_m2
     :kentta1_ala_m2
     :pintamateriaali_lisatieto
     :ottelukello
     :kentta3_pituus_m
     :kentta1_leveys_m
     :lammitys
     :maalikamera
     :tulostaulu
     :kentta1_pituus_m
     :yleiso_wc
     :kioski
     :kentta2_leveys_m
     :kentta3_ala_m2
     :tulospalvelu_aanentoisto
     :katsomo_kapasiteetti_hlo
     :automaattinen_ajanotto
     :kaukalo_lkm
     :kentta3_leveys_m
     :pintamateriaali
     :kentta2_ala_m2
     :kentta2_pituus_m},
   4710
   #{:pinta_ala_m2
     :pintamateriaali_lisatieto
     :kiipeilyseinan_korkeus_m
     :kiipeilyreittien_lkm
     :kiipeilyseina_leveys_m
     :pintamateriaali
     :jaakiipeily_mahdollisuus
     :valaistus},
   304 #{:saa_julkaista_retkikartassa :yleiso_wc},
   4412
   #{:saa_julkaista_retkikartassa
     :reitin_leveys_m
     :pintamateriaali_lisatieto
     :reitin_pituus_km
     :taukopaikat_lkm
     :valaistua_km_reitti
     :reitti_id
     :yleiso_wc
     :pintamateriaali},
   4820
   #{:pinta_ala_m2
     :ampumapaikat_lkm
     :esteettomyys_tietoa
     :ilma_aseammunta
     :radan_pituus_m
     :tulostaulu
     :haulikkoammunta
     :yleiso_wc
     :kioski
     :tulospalvelu_aanentoisto
     :katsomo_kapasiteetti_hlo
     :kivaariammunta
     :pienoiskivaariammunta
     :pistooliammunta
     :radan_leveys_m
     :talvikaytto
     :valaistus},
   1170
   #{:pinta_ala_m2
     :pintamateriaali_lisatieto
     :radan_pituus_m
     :pintamateriaali
     :radan_leveys_m
     :valaistus},
   4404
   #{:saa_julkaista_retkikartassa
     :reitin_leveys_m
     :pintamateriaali_lisatieto
     :esteettomyys_tietoa
     :reitin_pituus_km
     :taukopaikat_lkm
     :valaistua_km_reitti
     :reitti_id
     :pintamateriaali},
   108 #{:alue_id :pinta_alakm2},
   4401
   #{:saa_julkaista_retkikartassa
     :reitin_leveys_m
     :pintamateriaali_lisatieto
     :ampumapaikat_lkm
     :esteettomyys_tietoa
     :reitin_pituus_km
     :taukopaikat_lkm
     :valaistua_km_reitti
     :reitti_id
     :yleiso_wc
     :kuntoilutelineet_boolean
     :pintamateriaali},
   2350
   #{:pinta_ala_m2
     :pintamateriaali_lisatieto
     :korkeus_m
     :kioski
     :ryhmaliikuntatila_lkm
     :pintamateriaali},
   2340
   #{:pinta_ala_m2
     :pintamateriaali_lisatieto
     :korkeus_m
     :kioski
     :pintamateriaali
     :miekkailu_alustat_lkm},
   2120
   #{:painonnostopaikat_lkm
     :pinta_ala_m2
     :pintamateriaali_lisatieto
     :kuntoilulaite_lkm
     :kioski
     :ryhmaliikuntatila_lkm
     :pintamateriaali
     :spinning_sali},
   109 #{:alue_id :pinta_alakm2},
   5160 #{:pinta_ala_m2},
   1550
   #{:saa_julkaista_retkikartassa
     :pintamateriaali_lisatieto
     :radan_pituus_m
     :yleiso_wc
     :kioski
     :pintamateriaali
     :radan_leveys_m
     :valaistus
     :valinevuokraus},
   3230
   #{:saa_julkaista_retkikartassa
     :sauna
     :pukukopit
     :yleiso_wc
     :kioski
     :hyppytelineet_muut
     :suihku
     :rannan_pituus_m
     :laituri},
   5130 #{:venepaikkojen_lkm :pinta_alakm2 :laituri},
   5110
   #{:pinta_ala_m2
     :radan_pituus_m
     :tulostaulu
     :yleiso_wc
     :katsomo_kapasiteetti_hlo
     :automaattinen_ajanotto
     :laituri},
   3240
   #{:sauna
     :pukukopit
     :jaatymisenesto_jarjestelma
     :yleiso_wc
     :kioski
     :suihku
     :laituri},
   4510 #{:alue_id :pinta_alakm2},
   4240
   #{:pinta_ala_m2
     :pintamateriaali_lisatieto
     :reitin_pituus_km
     :suksihuolto
     :yleiso_wc
     :kioski
     :korkeusero_m
     :pintamateriaali
     :valinevuokraus},
   2270
   #{:pinta_ala_m2
     :pintamateriaali_lisatieto
     :korkeus_m
     :tenniskentat_lkm
     :lentopallokentat_lkm
     :kentan_pituus_m
     :tulostaulu
     :yleiso_wc
     :kioski
     :kentan_leveys_m
     :katsomo_kapasiteetti_hlo
     :pintamateriaali
     :squashkentat_lkm
     :sulkapallokentat_lkm
     :salibandykentat_lkm},
   4210
   #{:kenttien_lkm
     :pinta_ala_m2
     :pintamateriaali_lisatieto
     :yleiso_wc
     :kioski
     :pintamateriaali},
   301 #{:saa_julkaista_retkikartassa :yleiso_wc},
   111 #{:alue_id :pinta_alakm2},
   4630
   #{:reitin_leveys_m
     :esteettomyys_tietoa
     :sauna
     :pukukopit
     :reitin_pituus_km
     :taukopaikat_lkm
     :suksihuolto
     :maalikamera
     :tulostaulu
     :valaistua_km_reitti
     :yleiso_wc
     :kioski
     :tulospalvelu_aanentoisto
     :katsomo_kapasiteetti_hlo
     :automaattinen_ajanotto
     :latu_vapaa
     :suihku
     :latu_perinteinen},
   4810
   #{:pinta_ala_m2
     :ampumapaikat_lkm
     :esteettomyys_tietoa
     :ilma_aseammunta
     :radan_pituus_m
     :tulostaulu
     :haulikkoammunta
     :yleiso_wc
     :kioski
     :tulospalvelu_aanentoisto
     :katsomo_kapasiteetti_hlo
     :kivaariammunta
     :pienoiskivaariammunta
     :pistooliammunta
     :radan_leveys_m
     :talvikaytto
     :valaistus},
   1540
   #{:pinta_ala_m2
     :pintamateriaali_lisatieto
     :radan_pituus_m
     :yleiso_wc
     :kioski
     :katsomo_kapasiteetti_hlo
     :pintamateriaali
     :radan_leveys_m
     :valaistus},
   5320
   #{:pinta_ala_m2
     :pintamateriaali_lisatieto
     :radan_pituus_m
     :tulostaulu
     :katsomosta_katettua_hlo
     :kioski
     :tulospalvelu_aanentoisto
     :katsomo_kapasiteetti_hlo
     :automaattinen_ajanotto
     :pintamateriaali
     :radan_leveys_m
     :valaistus},
   3210
   #{:allas5_ratojen_lkm
     :allas2_veden_lampo_c
     :allas5_syvyys_max_m
     :liukumaet_kokonaispituus_m
     :allas1_pituus_m
     :allas3_leveys_m
     :hyppytelineet_5m_lkm
     :allas4_ratojen_lkm
     :allas3_pituus_m
     :allas5_veden_lampo_c
     :liukumaet_lkm
     :allas1_syvyys_max_m
     :allaspinta_ala_m2
     :allas1_veden_lampo_c
     :muut_altaat_lkm
     :hyppytelineet_10m_lkm
     :allas5_pituus_m
     :hyppytelineet_1m_lkm
     :allas2_leveys_m
     :allas3_syvyys_max_m
     :allas5_syvyys_min_m
     :hyppytelineet_3m_lkm
     :allas5_leveys_m
     :uima_altaiden_lkm
     :allas4_veden_lampo_c
     :allas3_veden_lampo_c
     :allas1_syvyys_min_m
     :yleiso_wc
     :allas4_syvyys_min_m
     :kioski
     :allas4_pituus_m
     :tulospalvelu_aanentoisto
     :allas4_syvyys_max_m
     :katsomo_kapasiteetti_hlo
     :allas2_pituus_m
     :allas2_ratojen_lkm
     :allas3_ratojen_lkm
     :allas1_ratojen_lkm
     :allas4_leveys_m
     :hyppytelineet_7m5_lkm
     :allas2_syvyys_min_m
     :allas3_syvyys_min_m
     :allas1_leveys_m
     :allas2_syvyys_max_m},
   4640
   #{:esteettomyys_tietoa
     :reitin_pituus_km
     :taukopaikat_lkm
     :suksihuolto
     :tulostaulu
     :valaistua_km_reitti
     :yleiso_wc
     :kioski
     :tulospalvelu_aanentoisto
     :latu_vapaa
     :latu_perinteinen
     :valinevuokraus},
   1150
   #{:pinta_ala_m2
     :pintamateriaali_lisatieto
     :pintamateriaali
     :valaistus},
   2310
   #{:pinta_ala_m2
     :pintamateriaali_lisatieto
     :korkeus_m
     :korkeushyppypaikkojen_lkm
     :pituus_ja_kolmiloikkapaikkojen_lkm
     :moukarinheitto_lkm
     :seivashyppy_lkm
     :sisaradan_pituus_m
     :keihaanheittopaikkojen_lkm
     :pintamateriaali
     :kuulantyonto_lkm
     :juoksuradan_etusuoran_pituus_m
     :kiekonheittopaikkojen_lkm},
   5210
   #{:pinta_ala_m2
     :pintamateriaali_lisatieto
     :radan_pituus_m
     :pintamateriaali
     :radan_leveys_m},
   2380
   #{:pinta_ala_m2
     :pintamateriaali_lisatieto
     :korkeus_m
     :kiipeilyseinan_korkeus_m
     :telinevoimistelun_alastulomonttu_lkm
     :kiipeilyreittien_lkm
     :kioski
     :telinevoimistelun_telinesarja_lkm
     :pintamateriaali},
   103
   #{:saa_julkaista_retkikartassa
     :leikkipuisto
     :alue_id
     :pinta_alakm2},
   201 #{:yleiso_wc},
   1220
   #{:pinta_ala_m2
     :pintamateriaali_lisatieto
     :ottelukello
     :etusuorien_lkm
     :korkeushyppypaikkojen_lkm
     :juoksuradan_pintamateriaali
     :pituus_ja_kolmiloikkapaikkojen_lkm
     :kiertavien_juoksuratojen_lkm
     :lammitys
     :moukarinheitto_lkm
     :kentan_pituus_m
     :maalikamera
     :seivashyppy_lkm
     :tulostaulu
     :sisaradan_pituus_m
     :katsomosta_katettua_hlo
     :yleiso_wc
     :keihaanheittopaikkojen_lkm
     :kioski
     :kentan_leveys_m
     :tulospalvelu_aanentoisto
     :katsomo_kapasiteetti_hlo
     :automaattinen_ajanotto
     :pintamateriaali
     :kuulantyonto_lkm
     :juoksuradan_etusuoran_pituus_m
     :valaistus
     :suorituspaikan_pintamateriaali
     :kiekonheittopaikkojen_lkm},
   4411
   #{:saa_julkaista_retkikartassa
     :reitin_leveys_m
     :pintamateriaali_lisatieto
     :reitin_pituus_km
     :valaistua_km_reitti
     :reitti_id
     :pintamateriaali},
   1140
   #{:pinta_ala_m2
     :pintamateriaali_lisatieto
     :pintamateriaali
     :valaistus},
   4520 #{:alue_id :pinta_alakm2},
   107 #{:alue_id :pinta_alakm2},
   6110
   #{:pinta_ala_m2
     :pintamateriaali_lisatieto
     :kentan_pituus_m
     :yleiso_wc
     :kioski
     :kentan_leveys_m
     :pintamateriaali
     :esteratsastus
     :valaistus},
   1120
   #{:kenttien_lkm
     :pinta_ala_m2
     :pintamateriaali_lisatieto
     :esteettomyys_tietoa
     :leikkipuisto
     :kuntoilulaite_lkm
     :kaukalo_lkm
     :pintamateriaali
     :valaistus},
   1390
   #{:kenttien_lkm
     :pinta_ala_m2
     :pintamateriaali_lisatieto
     :kevytkate
     :kentan_pituus_m
     :yleiso_wc
     :kentan_leveys_m
     :pintamateriaali
     :valaistus},
   5340
   #{:pinta_ala_m2
     :pintamateriaali_lisatieto
     :radan_pituus_m
     :automaattinen_ajanotto
     :pintamateriaali
     :radan_leveys_m
     :valaistus},
   302 #{:saa_julkaista_retkikartassa :yleiso_wc},
   4405
   #{:saa_julkaista_retkikartassa
     :reitin_leveys_m
     :pintamateriaali_lisatieto
     :esteettomyys_tietoa
     :reitin_pituus_km
     :taukopaikat_lkm
     :valaistua_km_reitti
     :reitti_id
     :yleiso_wc
     :pintamateriaali},
   6120
   #{:pinta_ala_m2
     :pintamateriaali_lisatieto
     :lammitys
     :kentan_pituus_m
     :yleiso_wc
     :kioski
     :kentan_leveys_m
     :katsomo_kapasiteetti_hlo
     :pintamateriaali
     :esteratsastus
     :valaistus},
   1310
   #{:kenttien_lkm
     :pinta_ala_m2
     :pintamateriaali_lisatieto
     :ottelukello
     :kentan_pituus_m
     :tulostaulu
     :yleiso_wc
     :kentan_leveys_m
     :pintamateriaali
     :valaistus
     :koripallokentan_tyyppi},
   202 #{:saa_julkaista_retkikartassa :yleiso_wc},
   1620
   #{:puttiviherio
     :yleiso_wc
     :vaylat_lkm
     :valaistus
     :range_harjoitusalue},
   2250
   #{:pinta_ala_m2
     :pintamateriaali_lisatieto
     :korkeus_m
     :tulostaulu
     :yleiso_wc
     :kioski
     :pintamateriaali},
   2530
   #{:pinta_ala_m2
     :kentta1_ala_m2
     :pintamateriaali_lisatieto
     :ottelukello
     :kentta3_pituus_m
     :kentta1_leveys_m
     :lammitys
     :maalikamera
     :tulostaulu
     :kentta1_pituus_m
     :yleiso_wc
     :kioski
     :kentta2_leveys_m
     :kentta3_ala_m2
     :tulospalvelu_aanentoisto
     :katsomo_kapasiteetti_hlo
     :automaattinen_ajanotto
     :kaukalo_lkm
     :kentta3_leveys_m
     :pintamateriaali
     :kentta2_ala_m2
     :kentta2_pituus_m},
   112 #{:saa_julkaista_retkikartassa :alue_id :pinta_alakm2},
   2130
   #{:painonnostopaikat_lkm
     :pinta_ala_m2
     :pintamateriaali_lisatieto
     :kuntoilulaite_lkm
     :kioski
     :ryhmaliikuntatila_lkm
     :pintamateriaali
     :tatamit_lkm
     :nyrkkeilykehat_lkm
     :painimatot_lkm},
   3220
   #{:saa_julkaista_retkikartassa
     :sauna
     :pukukopit
     :yleiso_wc
     :kioski
     :hyppytelineet_muut
     :suihku
     :rannan_pituus_m
     :eu_ranta
     :laituri},
   5330
   #{:pintamateriaali_lisatieto
     :maalikamera
     :radan_pituus_m
     :tulostaulu
     :kioski
     :tulospalvelu_aanentoisto
     :katsomo_kapasiteetti_hlo
     :automaattinen_ajanotto
     :pintamateriaali
     :radan_leveys_m
     :valaistus},
   4230
   #{:pinta_ala_m2
     :reitin_leveys_m
     :pintamateriaali_lisatieto
     :korkeus_m
     :reitin_pituus_km
     :suksihuolto
     :yleiso_wc
     :kioski
     :korkeusero_m
     :pintamateriaali
     :valinevuokraus},
   4320
   #{:vauhtimaen_rakenne
     :suksihuolto
     :hissit_lkm
     :yleiso_wc
     :hyppyrimaki_k_piste
     :vauhtimaen_latumateriaali
     :kesakaytto
     :alastulo_muovitus
     :hyppyrimaen_tyyppi
     :hyppyrimaki_p_piste},
   3130
   #{:allas5_ratojen_lkm
     :allas2_veden_lampo_c
     :allas5_syvyys_max_m
     :esteettomyys_tietoa
     :liukumaet_kokonaispituus_m
     :allas1_pituus_m
     :allas3_leveys_m
     :hyppytelineet_5m_lkm
     :allas4_ratojen_lkm
     :allas3_pituus_m
     :allas5_veden_lampo_c
     :liukumaet_lkm
     :allas1_syvyys_max_m
     :allaspinta_ala_m2
     :allas1_veden_lampo_c
     :muut_altaat_lkm
     :hyppytelineet_10m_lkm
     :allas5_pituus_m
     :hyppytelineet_1m_lkm
     :allas2_leveys_m
     :allas3_syvyys_max_m
     :allas5_syvyys_min_m
     :hyppytelineet_3m_lkm
     :allas5_leveys_m
     :uima_altaiden_lkm
     :allas4_veden_lampo_c
     :allas3_veden_lampo_c
     :allas1_syvyys_min_m
     :yleiso_wc
     :allas4_syvyys_min_m
     :kioski
     :allas4_pituus_m
     :allas4_syvyys_max_m
     :allas2_pituus_m
     :allas2_ratojen_lkm
     :allas3_ratojen_lkm
     :allas1_ratojen_lkm
     :allas4_leveys_m
     :hyppytelineet_7m5_lkm
     :allas2_syvyys_min_m
     :allas3_syvyys_min_m
     :allas1_leveys_m
     :allas2_syvyys_max_m},
   3110
   #{:allas5_ratojen_lkm
     :allas2_veden_lampo_c
     :allas5_syvyys_max_m
     :esteettomyys_tietoa
     :liukumaet_kokonaispituus_m
     :allas1_pituus_m
     :allas3_leveys_m
     :ottelukello
     :hyppytelineet_5m_lkm
     :allas4_ratojen_lkm
     :allas3_pituus_m
     :allas5_veden_lampo_c
     :liukumaet_lkm
     :allas1_syvyys_max_m
     :allaspinta_ala_m2
     :allas1_veden_lampo_c
     :muut_altaat_lkm
     :hyppytelineet_10m_lkm
     :allas5_pituus_m
     :hyppytelineet_1m_lkm
     :allas2_leveys_m
     :allas3_syvyys_max_m
     :allas5_syvyys_min_m
     :maalikamera
     :hyppytelineet_3m_lkm
     :allas5_leveys_m
     :uima_altaiden_lkm
     :tulostaulu
     :allas4_veden_lampo_c
     :allas3_veden_lampo_c
     :allas1_syvyys_min_m
     :yleiso_wc
     :allas4_syvyys_min_m
     :kioski
     :allas4_pituus_m
     :tulospalvelu_aanentoisto
     :allas4_syvyys_max_m
     :katsomo_kapasiteetti_hlo
     :automaattinen_ajanotto
     :allas2_pituus_m
     :allas2_ratojen_lkm
     :allas3_ratojen_lkm
     :allas1_ratojen_lkm
     :allas4_leveys_m
     :hyppytelineet_7m5_lkm
     :allas2_syvyys_min_m
     :allas3_syvyys_min_m
     :allas1_leveys_m
     :allas2_syvyys_max_m},
   203
   #{:saa_julkaista_retkikartassa
     :veneen_vesillelaskup
     :yleiso_wc
     :laituri},
   4620
   #{:reitin_leveys_m
     :ampumapaikat_lkm
     :esteettomyys_tietoa
     :sauna
     :pukukopit
     :reitin_pituus_km
     :taukopaikat_lkm
     :suksihuolto
     :maalikamera
     :tulostaulu
     :valaistua_km_reitti
     :yleiso_wc
     :kioski
     :tulospalvelu_aanentoisto
     :katsomo_kapasiteetti_hlo
     :automaattinen_ajanotto
     :latu_vapaa
     :suihku
     :latu_perinteinen},
   5360
   #{:pinta_ala_m2
     :pintamateriaali_lisatieto
     :maalikamera
     :radan_pituus_m
     :tulostaulu
     :katsomosta_katettua_hlo
     :yleiso_wc
     :kioski
     :tulospalvelu_aanentoisto
     :katsomo_kapasiteetti_hlo
     :automaattinen_ajanotto
     :pintamateriaali
     :radan_leveys_m
     :valaistus},
   2290
   #{:kenttien_lkm
     :pinta_ala_m2
     :pintamateriaali_lisatieto
     :korkeus_m
     :tulostaulu
     :yleiso_wc
     :kioski
     :pintamateriaali},
   2260
   #{:pinta_ala_m2
     :pintamateriaali_lisatieto
     :korkeus_m
     :tenniskentat_lkm
     :lentopallokentat_lkm
     :kentan_pituus_m
     :tulostaulu
     :yleiso_wc
     :kioski
     :kentan_leveys_m
     :katsomo_kapasiteetti_hlo
     :pintamateriaali
     :squashkentat_lkm
     :sulkapallokentat_lkm
     :salibandykentat_lkm},
   1160
   #{:pinta_ala_m2
     :pintamateriaali_lisatieto
     :pintamateriaali
     :valaistus},
   1210
   #{:pinta_ala_m2
     :pintamateriaali_lisatieto
     :etusuorien_lkm
     :korkeushyppypaikkojen_lkm
     :juoksuradan_pintamateriaali
     :pituus_ja_kolmiloikkapaikkojen_lkm
     :kiertavien_juoksuratojen_lkm
     :lammitys
     :moukarinheitto_lkm
     :kentan_pituus_m
     :seivashyppy_lkm
     :sisaradan_pituus_m
     :yleiso_wc
     :keihaanheittopaikkojen_lkm
     :kentan_leveys_m
     :pintamateriaali
     :kuulantyonto_lkm
     :juoksuradan_etusuoran_pituus_m
     :valaistus
     :suorituspaikan_pintamateriaali
     :kiekonheittopaikkojen_lkm},
   5140 #{:pinta_alakm2 :laituri},
   4310
   #{:vauhtimaen_rakenne
     :suksihuolto
     :hissit_lkm
     :yleiso_wc
     :hyppyrimaki_k_piste
     :vauhtimaen_latumateriaali
     :kesakaytto
     :alastulo_muovitus
     :hyppyrimaen_tyyppi
     :hyppyrimaki_p_piste},
   207 #{:saa_julkaista_retkikartassa :yleiso_wc :parkkipaikka},
   1130
   #{:pinta_ala_m2
     :pintamateriaali_lisatieto
     :leikkipuisto
     :kuntoilulaite_lkm
     :pintamateriaali
     :valaistus},
   5120 #{:venepaikkojen_lkm :pinta_alakm2 :laituri},
   4110
   #{:rinteet_lkm
     :lumikouru_kpl
     :esteettomyys_tietoa
     :temppurinne
     :hyppyrit_lkm
     :valaistut_rinteet_lkm
     :suksihuolto
     :pisin_rinne_m
     :hissit_lkm
     :lyhin_rinne_m
     :yleiso_wc
     :kioski
     :korkeusero_max_m
     :ohjaskelkkamaki
     :valinevuokraus
     :kumparerinne},
   4452
   #{:saa_julkaista_retkikartassa
     :reitin_pituus_km
     :taukopaikat_lkm
     :reitti_id},
   5370
   #{:pintamateriaali_lisatieto
     :maalikamera
     :radan_pituus_m
     :tulostaulu
     :kioski
     :tulospalvelu_aanentoisto
     :katsomo_kapasiteetti_hlo
     :automaattinen_ajanotto
     :pintamateriaali
     :radan_leveys_m
     :valaistus},
   2240
   #{:pinta_ala_m2
     :pintamateriaali_lisatieto
     :korkeus_m
     :kentan_pituus_m
     :tulostaulu
     :yleiso_wc
     :kioski
     :kentan_leveys_m
     :katsomo_kapasiteetti_hlo
     :pintamateriaali
     :sulkapallokentat_lkm
     :salibandykentat_lkm},
   2510
   #{:curling_ratojen_lkm
     :pinta_ala_m2
     :kentta1_ala_m2
     :pintamateriaali_lisatieto
     :ottelukello
     :kentta3_pituus_m
     :kentta1_leveys_m
     :lammitys
     :maalikamera
     :tulostaulu
     :kentta1_pituus_m
     :yleiso_wc
     :kioski
     :kentta2_leveys_m
     :kentta3_ala_m2
     :tulospalvelu_aanentoisto
     :katsomo_kapasiteetti_hlo
     :automaattinen_ajanotto
     :kaukalo_lkm
     :kentta3_leveys_m
     :pintamateriaali
     :kentta2_ala_m2
     :kentta2_pituus_m},
   1640
   #{:puttiviherio
     :yleiso_wc
     :vaylat_lkm
     :valaistus
     :range_harjoitusalue},
   1380
   #{:kenttien_lkm
     :pinta_ala_m2
     :pintamateriaali_lisatieto
     :ottelukello
     :kentan_pituus_m
     :yleiso_wc
     :kentan_leveys_m
     :pintamateriaali
     :valaistus},
   4451
   #{:saa_julkaista_retkikartassa
     :reitin_pituus_km
     :taukopaikat_lkm
     :reitti_id},
   4403
   #{:saa_julkaista_retkikartassa
     :reitin_leveys_m
     :pintamateriaali_lisatieto
     :esteettomyys_tietoa
     :reitin_pituus_km
     :taukopaikat_lkm
     :valaistua_km_reitti
     :reitti_id
     :yleiso_wc
     :kuntoilutelineet_boolean
     :pintamateriaali},
   5150 #{:korkeusero_m :valinevuokraus :laituri},
   1630
   #{:pinta_ala_m2
     :korkeus_m
     :puttiviherio
     :yleiso_wc
     :kioski
     :vaylat_lkm
     :range_harjoitusalue},
   2295
   #{:pinta_ala_m2
     :pintamateriaali_lisatieto
     :kentan_pituus_m
     :tulostaulu
     :padelkentat_lkm
     :yleiso_wc
     :kentan_leveys_m
     :katsomo_kapasiteetti_hlo
     :pintamateriaali},
   2370
   #{:pinta_ala_m2
     :pintamateriaali_lisatieto
     :kiipeilyseinan_korkeus_m
     :kiipeilyreittien_lkm
     :kioski
     :kiipeilyseina_leveys_m
     :pintamateriaali},
   1340
   #{:kenttien_lkm
     :pinta_ala_m2
     :pintamateriaali_lisatieto
     :pukukopit
     :kevytkate
     :lammitys
     :kentan_pituus_m
     :yleiso_wc
     :kioski
     :kentan_leveys_m
     :katsomo_kapasiteetti_hlo
     :pintamateriaali
     :valaistus},
   1610
   #{:puttiviherio
     :yleiso_wc
     :vaylat_lkm
     :valaistus
     :range_harjoitusalue},
   4421
   #{:saa_julkaista_retkikartassa
     :reitin_leveys_m
     :reitin_pituus_km
     :taukopaikat_lkm
     :reitti_id},
   2220
   #{:kiipeilyseina
     :pinta_ala_m2
     :jalkapallokentat_lkm
     :pintamateriaali_lisatieto
     :esteettomyys_tietoa
     :korkeus_m
     :ottelukello
     :etusuorien_lkm
     :korkeushyppypaikkojen_lkm
     :juoksuradan_pintamateriaali
     :pituus_ja_kolmiloikkapaikkojen_lkm
     :kiertavien_juoksuratojen_lkm
     :tenniskentat_lkm
     :moukarinheitto_lkm
     :lentopallokentat_lkm
     :kentan_pituus_m
     :seivashyppy_lkm
     :tulostaulu
     :kasipallokentat_lkm
     :sisaradan_pituus_m
     :yleiso_wc
     :keihaanheittopaikkojen_lkm
     :kioski
     :kentan_leveys_m
     :tulospalvelu_aanentoisto
     :katsomo_kapasiteetti_hlo
     :automaattinen_ajanotto
     :pintamateriaali
     :squashkentat_lkm
     :sulkapallokentat_lkm
     :koripallokentat_lkm
     :salibandykentat_lkm
     :kuulantyonto_lkm
     :juoksuradan_etusuoran_pituus_m
     :telinevoimistelutila
     :kiekonheittopaikkojen_lkm},
   1320
   #{:kenttien_lkm
     :pinta_ala_m2
     :pintamateriaali_lisatieto
     :kentan_pituus_m
     :yleiso_wc
     :kentan_leveys_m
     :pintamateriaali
     :valaistus},
   4402
   #{:saa_julkaista_retkikartassa
     :reitin_leveys_m
     :pintamateriaali_lisatieto
     :ampumapaikat_lkm
     :reitin_pituus_km
     :taukopaikat_lkm
     :valaistua_km_reitti
     :reitti_id
     :yleiso_wc
     :kuntoilutelineet_boolean
     :latu_vapaa
     :pintamateriaali
     :latu_perinteinen
     :kulkusuunta}})

(defn ->wfs-row [sports-site idx feature]
  (let [type-code (-> sports-site :type :type-code)
        geom-type (-> type-code types/all :geometry-type)
        fields    (set/union common-fields (get type-code->legacy-fields type-code))]
    (->>
     (for [field fields]
       (let [resolver-fn (legacy-field->resolve-fn field)]
         [field (case field
                  (:the_geom) (resolver-fn feature)
                  (:reitti_id :alue_id) (resolver-fn idx)
                  (resolver-fn sports-site))]))
     (into {}))))

(defn ->wfs-rows [sports-site]
  (->> sports-site
       :location
       :geometries
       :features
       (map-indexed (fn [idx feature]
                      (->wfs-row sports-site idx feature)))))

;;; Creating the tables ;;;

(def type-code->view-name
  {1530 "lipas_1530_kaukalo",
   1520 "lipas_1520_luistelukentta",
   2320 "lipas_2320_telinevoimistelutila",
   6130 "lipas_6130_esteratsastuskentta",
   1395 "lipas_1395_poytatennisalue",
   6210 "lipas_6210_koiraurheilualue",
   1370 "lipas_1370_tenniskentta_alue",
   1360 "lipas_1360_pesapallostadion",
   110 "lipas_110_eramaa_alue",
   2360 "lipas_2360_sisa_ampumarata",
   5310 "lipas_5310_moottoriurheilukeskus",
   1560 "lipas_1560_alamakiluistelurata",
   205 "lipas_205_rantautumispaikka",
   2150 "lipas_2150_liikuntasali",
   2210 "lipas_2210_liikuntahalli",
   101 "lipas_101_lahipuisto",
   102 "lipas_102_ulkoilupuisto",
   7000 "lipas_7000_huoltorakennukset",
   1110 "lipas_1110_liikuntapuisto",
   6220 "lipas_6220_koiraurheiluhalli",
   4530 "lipas_4530_pyorasuunnistusalue",
   4720 "lipas_4720_kiipeilykallio",
   1330 "lipas_1330_beachvolleykentta",
   206 "lipas_206_ruoanlaittopaikka",
   4830 "lipas_4830_jousiammuntarata",
   1180 "lipas_1180_frisbeegolf_rata",
   4422 "lipas_4422_moottorikelkkaura",
   4430 "lipas_4430_hevosreitti",
   204 "lipas_204_luontotorni",
   106 "lipas_106_monikayttoalue",
   4610 "lipas_4610_ampumahiihdon_harjoittelualue",
   2610 "lipas_2610_keilahalli",
   2110 "lipas_2110_kuntokeskus",
   3120 "lipas_3120_uima_allas",
   104 "lipas_104_retkeilyalue",
   2330 "lipas_2330_poytatennistila",
   2280 "lipas_2280_tennishalli",
   6140 "lipas_6140_ravirata",
   2140 "lipas_2140_kamppailulajien_sali",
   4220 "lipas_4220_hiihtotunneli",
   2230 "lipas_2230_jalkapallohalli",
   1350 "lipas_1350_jalkapallostadion",
   4840 "lipas_4840_jousiammuntamaastorata",
   1510 "lipas_1510_tekojaakentta",
   5350 "lipas_5350_kiihdytysrata",
   4440 "lipas_4440_koirahiihtolatu",
   2520 "lipas_2520_kilpajaahalli",
   4710 "lipas_4710_ulkokiipeilyseina",
   304 "lipas_304_ulkoilumaja_hiihtomaja",
   4412 "lipas_4412_pyorailyreitti",
   4820 "lipas_4820_ampumaurheilukeskus",
   1170 "lipas_1170_pyorailurata",
   4404 "lipas_4404_luontopolku",
   108 "lipas_108_virkistysmetsa",
   4401 "lipas_4401_kuntorata",
   2350 "lipas_2350_tanssitila",
   2340 "lipas_2340_miekkailutila",
   2120 "lipas_2120_kuntosali",
   109 "lipas_109_valtion_retkeilyalue",
   5160 "lipas_5160_soudun_ja_melonnan_sisaharjoittelutila",
   1550 "lipas_1550_luistelureitti",
   3230 "lipas_3230_uimapaikka",
   5130 "lipas_5130_moottirveneurheilualue",
   5110 "lipas_5110_soutustadion",
   3240 "lipas_3240_talviuintipaikka",
   4510 "lipas_4510_suunnistusalue",
   4240 "lipas_4240_lasketteluhalli",
   2270 "lipas_2270_squash_halli",
   4210 "lipas_4210_curlingrata",
   301 "lipas_301_laavu_kota_kammi",
   111 "lipas_111_kansallispuisto",
   4630 "lipas_4630_kilpahiihtokeskus",
   4810 "lipas_4810_ampumarata",
   1540 "lipas_1540_pikaluistelurata",
   5320 "lipas_5320_moottoripyorailualue",
   3210 "lipas_3210_maauimala",
   4640 "lipas_4640_hiihtomaa",
   1150 "lipas_1150_skeitti_rullaluistelupaikka",
   2310 "lipas_2310_yksittainen_yleisurheilun_suorituspaikka",
   5210 "lipas_5210_urheiluilmailualue",
   2380 "lipas_2380_parkour_sali",
   103 "lipas_103_ulkoilualue",
   201 "lipas_201_kalastusalue",
   1220 "lipas_1220_yleisurheilukentta",
   4411 "lipas_4411_maastopyorailyreitti",
   1140 "lipas_1140_parkouralue",
   4520 "lipas_4520_hiihtosuunnistusalue",
   107 "lipas_107_matkailupalveluiden_alue",
   6110 "lipas_6110_ratsastuskentta",
   1120 "lipas_1120_lahiliikuntapaikka",
   1390 "lipas_1390_padelkenttaalue",
   5340 "lipas_5340_karting_rata",
   302 "lipas_302_tupa",
   4405 "lipas_4405_retkeilyreitti",
   6120 "lipas_6120_ratsastusmaneesi",
   1310 "lipas_1310_koripallokentta",
   202 "lipas_202_telttailu_ja_leiriytyminen",
   1620 "lipas_1620_golfkentta",
   2250 "lipas_2250_skeittihalli",
   2530 "lipas_2530_pikaluisteluhalli",
   112 "lipas_112_muu_luonnonsuojelualue",
   2130 "lipas_2130_voimailusali",
   3220 "lipas_3220_uimaranta",
   5330 "lipas_5330_moottorirata",
   4230 "lipas_4230_lumilautatunneli",
   4320 "lipas_4320_hyppyrimaki",
   3130 "lipas_3130_kylpyla",
   3110 "lipas_3110_uimahalli",
   203 "lipas_203_veneilyn_palvelupaikka",
   4620 "lipas_4620_ampumahiihtokeskus",
   5360 "lipas_5360_jokamies_ja_rallicross_rata",
   2290 "lipas_2290_petanque_halli",
   2260 "lipas_2260_sulkapallohalli",
   1160 "lipas_1160_pyorailualue",
   1210 "lipas_1210_yleisurheilun_harjoitusalue",
   5140 "lipas_5140_vesihiihtoalue",
   4310 "lipas_4310_harjoitushyppyrimaki",
   207 "lipas_207_opastuspiste",
   1130 "lipas_1130_ulkokuntoilupaikka",
   5120 "lipas_5120_purjehdusalue",
   4110 "lipas_4110_laskettelun_suorituspaikat",
   4452 "lipas_4452_vesiretkeilyreitti",
   5370 "lipas_5370_jaaspeedway_rata",
   2240 "lipas_2240_saliandyhalli",
   2510 "lipas_2510_harjoitusjaahalli",
   1640 "lipas_1640_ratagolf",
   1380 "lipas_1380_rullakiekkokentta",
   4451 "lipas_4451_melontareitti",
   4403 "lipas_4403_kavelyreitti",
   5150 "lipas_5150_koskimelontakeskus",
   1630 "lipas_1630_golfin_harjoitushalli",
   2295 "lipas_2295_padelhalli",
   2370 "lipas_2370_sisakiipeilyseina",
   1340 "lipas_1340_pallokentta",
   1610 "lipas_1610_golfin_harjoitusalue",
   4421 "lipas_4421_moottorikelkkareitti",
   2220 "lipas_2220_monitoimihalli",
   1320 "lipas_1320_lentopallokentta",
   4402 "lipas_4402_latu"})

(def int-fields #{:id :rakennusvuosi :kuntanumero :tyyppikoodi :alue_id :reitti_id})
(def bool-fields #{:vapaa_kaytto :koulun_liikuntapaikka})

(defn resolve-geom-field-type [type-code]
  (let [geom-type (-> type-code types/all :geometry-type)]
    :geometry
    #_(case geom-type
      "Point"      (keyword "geometry(Point,3067)")
      "LineString" (keyword "geometry(LineString,3067)")
      "Polygon"    (keyword "geometry(Polygon,3067)"))))

(defn resolve-field-type
  [type-code field]
  (cond
    (= field :the_geom) (resolve-geom-field-type type-code)
    (= field :muokattu_viimeksi) :timestamp
    (contains? int-fields field) :integer
    (contains? bool-fields field) :boolean
    :else :text))

;; Tables may be a bad idea..
#_(def legacy-type-view-create-statements
  (for [type-code (keys types/all)
        :let [view-name (get type-code->view-name type-code)]
        :when view-name]
    {:create-table [(keyword (str "wfs." view-name)) :if-not-exists]
     :with-columns (for [field (set/union
                                common-fields
                                (type-code->legacy-fields type-code))]
                     [field (resolve-field-type type-code field)])}))

;; Materialized views instead...
(def mat-views
  (->>
   (for [type-code (keys types/all)
         :let [view-name (get type-code->view-name type-code)]
         :when view-name]
     [type-code
      {:create-materialized-view [(keyword (str "wfs." view-name)) :if-not-exists]
       :select (into
                [[[:cast :the_geom (resolve-field-type type-code :the_geom)] :the_geom]]
                (for [field (set/union
                             common-fields
                             (type-code->legacy-fields type-code))
                      :when (not= :the_geom field)]
                  (let [field-type (resolve-field-type type-code field)]
                    [[:cast [:->> :doc [:inline (name field)]] field-type] field])))
       :from [:wfs.kaikki]
       :where [:and
               [:= :type_code [:inline type-code]]
               [:= :status [:inline "active"]]]}])
   (into {})))

#_(def legacy-point-collection-view-create-statement
  {:create-view [(keyword "lipas_kaikki_pisteet") :if-not-exists]
   :union (for [])})

#_(defn create-legacy-tables!
  [db]
  (doseq [ddl legacy-type-view-create-statements]
    (jdbc/execute! db (sql/format ddl))))

(defn create-legacy-mat-views!
  [db]
  (doseq [[type-code ddl] mat-views]
    (log/info "Creating mat-view for" type-code)
    (jdbc/execute! db (sql/format ddl))))

(defn refresh-legacy-mat-views!
  [db]
  (doseq [view-name (vals type-code->view-name)]
    (log/info "refreshing mat-view" view-name)
    (jdbc/execute! db (sql/format {:refresh-materialized-view (str "wfs." view-name)}))))

(defn refresh-wfs-master-table!
  [db]

  ;; Truncate
  (log/info "Truncating table :wfs.kaikki")
  (jdbc/execute! db (sql/format {:truncate :wfs.kaikki}))

  ;; Populate master table
  (log/info "Populating table :wfs.kaikki")
  (doseq [type-code (keys types/all)]
    (log/info "Populating table :wfs.kaikki with sites" type-code)
    (let [sites (core/get-sports-sites-by-type-code db type-code)]
      (doseq [part (->> sites
                        (mapcat ->wfs-rows)
                        (partition-all 100))]
        (jdbc/execute!
         db
         (sql/format
          {:insert-into [:wfs.kaikki]
           :values (for [row part]
                     {:lipas-id (:id row)
                      :type_code (:tyyppikoodi row)
                      :geom_type (:type (:the_geom row))
                      :doc [:lift row]
                      ;; Geometries are in 3067 coordinate system in Legacy WFS
                      :the_geom [:st_transform
                                 [:st_setsrid
                                  [:st_geomfromgeojson [:lift (:the_geom row)]]
                                  [:cast 4326 :int]]
                                 [:cast 3067 :int]]
                      :status "active"})}))))))

(defn refresh-all!
  [db]
  (log/info "Starting full legacy wfs refresh")
  (refresh-wfs-master-table! db)
  (refresh-legacy-mat-views! db)
  (log/info "Full legacy wfs refresh DONE!"))

(comment

  (refresh-all! (user/db))

  (jdbc/execute! (user/db) (sql/format (mat-views 4402)))
  (refresh-legacy-mat-views! (user/db))

  (doseq [[type-code ddl] mat-views]
    (println "Creating mat-view for" type-code)
    (jdbc/execute! (user/db) (sql/format ddl)))

  (create-legacy-tables! (user/db))

  (require '[lipas.backend.core :as core])

  ;; Sisäampumarata (point)
  (->wfs-rows (core/get-sports-site (user/db) 510812))

  ;; Latu (linestring)
  (->wfs-rows (core/get-sports-site (user/db) 94714))
  (->wfs-rows (core/get-sports-site (user/db) 515522))

  ;; Ulkoilualue (polygon)
  (->wfs-rows (core/get-sports-site (user/db) 72648))

  (require '[clojure.data.csv :as csv])
  (->> (slurp "/Users/tipo/lipas/wfs-tasot-revamp/ominaisuustyypit.csv")
       csv/read-csv
       (drop 1)
       (map (fn [[nimi-fi handle]]
              [(keyword handle) (keyword nimi-fi)]))
       (into {}))

  (def legacy-fields
    (->> (slurp "/Users/tipo/lipas/wfs-tasot-revamp/mat_view_fields.csv")
         csv/read-csv
         (drop 1)
         (map (fn [[schema type-code view-name column-name data-type]]
                {:type-code    (parse-long type-code)
                 :view-name    view-name
                 :data-type    data-type
                 :legacy-field (keyword column-name)}))))

  (->> legacy-fields
       (map (fn [{:keys [view-name type-code]}] [type-code view-name]))
       distinct
       (into {}))

  (->> legacy-fields
       (map #(select-keys % [:legacy-field :data-type]))
       distinct
       (filter (fn [m]
                 (and (not= "text" (:data-type m))
                      (not (str/starts-with? (:data-type m) "character varying"))))))

  (update-vals (group-by :type-code legacy-fields)
   (fn [coll] (set/difference
               (->> coll (map :legacy-field) set)
               common-fields)))

  (require '[clojure.set :as set])

  (set/map-invert legacy-handle->legacy-prop)

  #_(update-)

  (def all-sites (atom []))
  (doseq [type-code (keys types/all)
          site (core/get-sports-sites-by-type-code (user/db) type-code)]
    (swap! all-sites conj site))

  (count @all-sites)

  (def as-rows
    (mapcat ->wfs-rows @all-sites))

  (take 3 as-rows)

  (doseq [part (partition-all 100 as-rows)]
    (println "Tsirp")
    (jdbc/execute!
     (user/db)
     (sql/format
      {:insert-into [:wfs.kaikki]
       :values (for [row part]
                 {:lipas-id (:id row)
                  :type_code (:tyyppikoodi row)
                  :geom_type (:type (:the_geom row))
                  :doc [:lift row]
                  :the_geom [:st_transform
                             [:st_setsrid
                              [:st_geomfromgeojson [:lift (:the_geom row)]]
                              [:cast 4326 :int]]
                             [:cast 3067 :int]]
                  :status "active"})})))

  (doseq [[_type-code view-name] type-code->view-name]
    (jdbc/execute! (user/db) [(str "DROP TABLE wfs." view-name)]))

  )
