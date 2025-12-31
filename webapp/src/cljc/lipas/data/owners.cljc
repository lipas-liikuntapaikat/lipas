(ns lipas.data.owners)

(def all
  {"city"                   {:fi "Kunta"
                             :en "Municipality"
                             :se "Kommun"}
   "registered-association" {:fi "Rekisteröity yhdistys"
                             :en "Registered association"
                             :se "Registrerad förening"}
   "company-ltd"            {:fi "Yritys"
                             :en "Company ltd"
                             :se "Aktiebolag"}
   "city-main-owner"        {:fi "Kuntaenemmistöinen yritys"
                             :en "Municipality major owner"
                             :se "Kommun majoritet ägare"}
   "municipal-consortium"   {:fi "Kuntayhtymä"
                             :en "Municipal consortium"
                             :se "Samkommun"}
   "foundation"             {:fi "Säätiö"
                             :en "Foundation"
                             :se "Stiftelse"}
   "state"                  {:fi "Valtio"
                             :en "State"
                             :se "Staten"}
   "other"                  {:fi "Muu"
                             :en "Other"
                             :se "Annan"}
   "unknown"                {:fi "Ei tietoa"
                             :en "Unknown"
                             :se "Okänt"}})

(def csv-headers
  ["Arvo" "Nimi fi" "Nimi Se" "Nimi En"])

(def csv-data
  (into [csv-headers] (for [[v {:keys [fi se en]}] all] [v fi se en])))
