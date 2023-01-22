(ns lipas.data.feedback)

(def types
  {:feedback.type/generic     {:fi "Yleinen palaute" :en "General feedback"}
   :feedback.type/bug         {:fi "Virhe sovelluksessa" :en "Bug or malfunction"}
   :feedback.type/improvement {:fi "Parannusehdotus" :en "Improvement"}
   :feedback.type/bad-data    {:fi "Väärä tai puutteellinen tieto" :en "Wrong information"}})
