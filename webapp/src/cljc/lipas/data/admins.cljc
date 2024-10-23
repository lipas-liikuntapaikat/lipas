(ns lipas.data.admins)

(def old
  {"city-sports"             {:fi "Kunta / liikuntatoimi"
                              :en "City / sports"
                              :se "Kommun/ idrottsväsende"}
   "city-education"          {:fi "Kunta / opetustoimi"
                              :en "City / education"
                              :se "Kommun / utbildingsväsende"}
   "city-technical-services" {:fi "Kunta / tekninen toimi"
                              :en "City / technical services"
                              :se "Kommun / teknisk väsende"}
   "city-other"              {:fi "Kunta / muu"
                              :en "City / other"
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
