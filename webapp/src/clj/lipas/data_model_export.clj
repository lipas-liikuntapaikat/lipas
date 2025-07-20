(ns lipas.data-model-export
  (:require [dk.ative.docjure.spreadsheet :as excel]
            [lipas.data.activities :as activities]
            [lipas.data.admins :as admins]
            [lipas.data.loi :as loi]
            [lipas.data.owners :as owners]
            [lipas.data.prop-types :as prop-types]
            [lipas.data.sports-sites :as sports-sites]
            [lipas.data.types :as types]
            [lipas.integration.old-lipas.sports-site :as legacy-utils]
            [lipas.schema.sports-sites.circumstances :as circumastances]
            [lipas.wfs.mappings :as wfs]))

(def legacy-mappings-headers
  ["REST v2" "REST v1" "WFS"])

(def common-field-mapping
  {:properties {:legacy-api :properties :wfs nil}
   :email {:legacy-api :email :wfs :sahkoposti}
   :circumstances {:legacy-api nil :wfs nil}
   :phone-number {:legacy-api :phoneNumber :wfs :puhelinnumero}
   :admin {:legacy-api :admin :wfs :yllapitaja}
   :location.city.city-code {:legacy-api :location.city-cityCode :wfs :kuntanumero}
   :www {:legacy-api :www :wfs :www}
   :location.geometries {:legacy-api :location.geometries :wfs :the_geom}
   :name {:legacy-api :name :wfs :nimi_fi}
   :reservations-link {:legacy-api nil :wfs nil}
   :location.postal-office {:legacy-api :location.postalOffice :wfs :postitoimipaikka}
   :construction-year {:legacy-api :constructionYear :wfs :rakennusvuosi}
   :name-localized.se {:legacy-api :nameSe :wfs :nimi_se}
   :lipas-id {:legacy-api :id :wfs :id}
   :renovation-years {:legacy-api :renovationYears :wfs :peruskorjausvuodet}
   :status {:legacy-api nil :wfs nil}
   :comment {:legacy-api :properties.infoFi :wfs :lisatieto_fi}
   :event-date {:legacy-api :lastModified :wfs :muokattu_viimeksi}
   :name-localized.en {:legacy-api :nameEn :wfs :nimi_en}
   :activities {:legacy-api nil :wfs nil}
   :type.type-code {:legacy-api :type.typeCode :wfs :tyyppikoodi}
   :location.postal-code {:legacy-api :location.postalCode :wfs :postinumero}
   :location.neighborhood {:legacy-api :location.city.neighborhood :wfs :kuntaosa}
   :owner {:legacy-api :owner :wfs :omistaja}
   :marketing-name {:legacy-api :marketingName :wfs nil}
   :location.address {:legacy-api :location.address :wfs :katuosoite}})

(def legacy-mappings-csv
  (into [legacy-mappings-headers] cat
        [(for [k (sort (into (keys common-field-mapping)))]
           [(name k)
            (name (or (get-in common-field-mapping [k :legacy-api]) ""))
            (name (or (get-in common-field-mapping [k :wfs]) ""))])
         (for [k (sort (keys prop-types/all))]
           (let [legacy-prop (or (get legacy-utils/prop-mappings-reverse k) "")
                 wfs-prop    (or (get wfs/legacy-handle->legacy-prop legacy-prop) "")]
             [(name k)
              (name legacy-prop)
              (name wfs-prop)]))]))

(defn create-excel
  [os]
  (excel/save-workbook-into-stream! os
   (excel/create-workbook
    "Liikuntapaikka" sports-sites/csv-data
    "Liikuntapaikkatyypit" types/csv-data
    "Ominaisuudet" prop-types/csv-data
    "Luokitellut ominaisuudet" prop-types/enum-csv-data
    "Tyypit+Ominaisuudet" types/csv-data-with-props
    "Omistajaluokat" owners/csv-data
    "Ylläpitäjäluokat" admins/csv-data
    "Aktiviteetit (UTP)" activities/csv-data
    "Muut kohteet" loi/csv-data
    "Salibandyn olosuhdetiedot" (circumastances/schema->csv-data)
    "WFS-tasot" wfs/csv-data
    "Tekniset mäppäykset" legacy-mappings-csv)))
