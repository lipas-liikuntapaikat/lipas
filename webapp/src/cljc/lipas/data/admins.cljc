(ns lipas.data.admins)

(def old
  {"city-sports"             {:fi "Kunta / liikuntatoimi"
                              :en "Municipality / Sports"
                              :se "Kommun / idrottsväsende"}
   "city-education"          {:fi "Kunta / opetustoimi"
                              :en "Municipality / Education"
                              :se "Kommun / utbildningsväsende"}
   "city-technical-services" {:fi "Kunta / tekninen toimi"
                              :en "Municipality / Technical services"
                              :se "Kommun / teknisk väsende"}
   "city-other"              {:fi "Kunta / muu"
                              :en "Municipality / Other"
                              :se "Kommun / annan"}
   "municipal-consortium"    {:fi "Kuntayhtymä"
                              :en "Municipal consortium"
                              :se "Samkommun"}
   "private-association"     {:fi "Yksityinen / yhdistys"
                              :en "Private / Association"
                              :se "Privat / förening"}
   "private-company"         {:fi "Yksityinen / yritys"
                              :en "Private / Company"
                              :se "Privat / aktiebolag"}
   "private-foundation"      {:fi "Yksityinen / säätiö"
                              :en "Private / Foundation"
                              :se "Privat / stiftelse"}
   "state"                   {:fi "Valtio"
                              :en "State"
                              :se "Staten"}
   "other"                   {:fi "Muu"
                              :en "Other"
                              :se "Annan"}
   "no-information"          {:fi "Ei tietoa"
                              :en "No information"
                              :se "Ingen information"}})

(def all
  (-> old
      (assoc-in ["private-association" :fi] "Rekisteröity yhdistys")
      (assoc-in ["private-company" :fi] "Yritys")
      (assoc-in ["private-foundation" :fi] "Säätiö")))

(def csv-headers
  ["Arvo" "Nimi fi" "Nimi Se" "Nimi En"])

(def csv-data
  (into [csv-headers] (for [[v {:keys [fi se en]}] all] [v fi se en])))
