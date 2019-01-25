(ns lipas.data.sports-sites)

(def document-statuses
  {"draft"
   {:fi "Ehdotus"
    :se nil
    :en "Draft"}
   "published"
   {:fi "Julkaistu"
    :se nil
    :en "Published"}})

(def statuses
  {"active"
   {:fi "Toiminnassa"
    :se nil
    :en "Active"}
   "out-of-service-temporarily"
   {:fi "Poistettu käytöstä väliaikaisesti"
    :se nil
    :en "Temporarily out of service"}
   "out-of-service-permanently"
   {:fi "Poistettu käytöstä pysyvästi"
    :se nil
    :en "Out of service"}
   "incorrect-data"
   {:fi "Väärä tieto"
    :se nil
    :en "Out of service"}})
