(ns lipas.data.admins)

(def old
  {"city-sports"             {:fi "Kunta / liikuntatoimi"
                              :en "Municipality / Sports"
                              :se "Kommun/ idrottsväsende"}
   "city-education"          {:fi "Kunta / opetustoimi"
                              :en "Municipality / Education"
                              :se "Kommun / utbildingsväsende"}
   "city-technical-services" {:fi "Kunta / tekninen toimi"
                              :en "Municipality / Technical services"
                              :se "Kommun / teknisk väsende"}
   "city-other"              {:fi "Kunta / muu"
                              :en "Municipality / Other"
                              :se "Kommun / annat"}
   "municipal-consortium"    {:fi "Kuntayhtymä"
                              :en "Municipal consortium"
                              :se "Samkommun"}
   "private-association"     {:fi "Yksityinen / yhdistys"
                              :en "Private / association"
                              :se "Privat / förening"}
   "private-company"         {:fi "Yksityinen / yritys"
                              :en "Private / company"
                              :se "Privat / företag"}
   "private-foundation"      {:fi "Yksityinen / säätiö"
                              :en "Private / foundation"
                              :se "Privat / stiftelse"}
   "state"                   {:fi "Valtio"
                              :en "State"
                              :se "Stat"}
   "other"                   {:fi "Muu"
                              :en "Other"
                              :se "Annat"}
   "unknown"                 {:fi "Ei tietoa"
                              :en "Unkonwn"
                              :se "Okänt"}})

(def all
  (-> old
      (assoc-in ["private-association" :fi] "Rekisteröity yhdistys")
      (assoc-in ["private-company" :fi] "Yritys")
      (assoc-in ["private-foundation" :fi] "Säätiö")))

(def csv-headers
  ["Arvo" "Nimi fi" "Nimi Se" "Nimi En"])

(def csv-data
  (into [csv-headers] (for [[v {:keys [fi se en]}] all] [v fi se en])))
