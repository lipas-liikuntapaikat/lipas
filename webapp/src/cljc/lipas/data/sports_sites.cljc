(ns lipas.data.sports-sites
  (:require [lipas.data.status :as status]))

(def document-statuses
  {"draft"
   {:fi "Ehdotus"
    :se nil
    :en "Draft"}
   "published"
   {:fi "Julkaistu"
    :se nil
    :en "Published"}})

(def statuses status/statuses)

(def field-types
  {"floorball-field"
   {:fi "Salibandykenttä"
    :en "Floorball field"
    :se "Innebandyplan"}})
