(ns lipas.data.status)

(def statuses
  {"planning"
   {:fi "Vedos"
    :se "Planering"
    :en "Planning"}
   "planned"
   {:fi "Suunniteltu"
    :se "Planerad"
    :en "Planned"}
   "active"
   {:fi "Toiminnassa"
    :se "Aktiv"
    :en "Active"}
   "out-of-service-temporarily"
   {:fi "Poistettu käytöstä väliaikaisesti"
    :se "Tillfälligt ur funktion"
    :en "Temporarily out of service"}
   "out-of-service-permanently"
   {:fi "Poistettu käytöstä pysyvästi"
    :se "Permanent ur funktion"
    :en "Out of service"}
   "incorrect-data"
   {:fi "Väärä tieto"
    :se "Fel information"
    :en "Out of service"}})
