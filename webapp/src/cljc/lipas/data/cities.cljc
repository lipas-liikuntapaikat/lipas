(ns lipas.data.cities)

(def all
  [{:name {:fi "Alahärmä", :se "Alahärmä", :en "Alahärmä"},
    :city-code 4,
    :status :abolished,
    :valid-until 2009}
   {:name {:fi "Alajärvi", :se "Alajärvi", :en "Alajärvi"},
    :city-code 5,
    :status :active,
    :valid-until nil}
   {:name {:fi "Alastaro", :se "Alastaro", :en "Alastaro"},
    :city-code 6,
    :status :abolished,
    :valid-until 2009}
   {:name {:fi "Alavieska", :se "Alavieska", :en "Alavieska"},
    :city-code 9,
    :status :active,
    :valid-until nil}
   {:name {:fi "Alavus", :se "Alavus", :en "Alavus"},
    :city-code 10,
    :status :active,
    :valid-until nil}
   {:name {:fi "Anttola", :se "Anttola", :en "Anttola"},
    :city-code 14,
    :status :abolished,
    :valid-until 2001}
   {:name {:fi "Artjärvi", :se "Artsjö", :en "Artjärvi"},
    :city-code 15,
    :status :abolished,
    :valid-until 2011}
   {:name {:fi "Asikkala", :se "Asikkala", :en "Asikkala"},
    :city-code 16,
    :status :active,
    :valid-until nil}
   {:name {:fi "Askainen", :se "Villnäs", :en "Askainen"},
    :city-code 17,
    :status :abolished,
    :valid-until 2009}
   {:name {:fi "Askola", :se "Askola", :en "Askola"},
    :city-code 18,
    :status :active,
    :valid-until nil}
   {:name {:fi "Aura", :se "Aura", :en "Aura"},
    :city-code 19,
    :status :active,
    :valid-until nil}
   {:name {:fi "Akaa", :se "Akaa", :en "Akaa"},
    :city-code 20,
    :status :active,
    :valid-until nil}
   {:name {:fi "Brändö", :se "Brändö", :en "Brändö"},
    :city-code 35,
    :status :active,
    :valid-until nil}
   {:name {:fi "Dragsfjärd", :se "Dragsfjärd", :en "Dragsfjärd"},
    :city-code 40,
    :status :abolished,
    :valid-until 2009}
   {:name {:fi "Eckerö", :se "Eckerö", :en "Eckerö"},
    :city-code 43,
    :status :active,
    :valid-until nil}
   {:name {:fi "Elimäki", :se "Elimä", :en "Elimäki"},
    :city-code 44,
    :status :abolished,
    :valid-until 2009}
   {:name {:fi "Eno", :se "Eno", :en "Eno"},
    :city-code 45,
    :status :abolished,
    :valid-until 2009}
   {:name {:fi "Enonkoski", :se "Enonkoski", :en "Enonkoski"},
    :city-code 46,
    :status :active,
    :valid-until nil}
   {:name {:fi "Enontekiö", :se "Enontekis", :en "Enontekiö"},
    :city-code 47,
    :status :active,
    :valid-until nil}
   {:name {:fi "Espoo", :se "Esbo", :en "Espoo"},
    :city-code 49,
    :status :active,
    :valid-until nil}
   {:name {:fi "Eura", :se "Eura", :en "Eura"},
    :city-code 50,
    :status :active,
    :valid-until nil}
   {:name {:fi "Eurajoki", :se "Euraåminne", :en "Eurajoki"},
    :city-code 51,
    :status :active,
    :valid-until nil}
   {:name {:fi "Evijärvi", :se "Evijärvi", :en "Evijärvi"},
    :city-code 52,
    :status :active,
    :valid-until nil}
   {:name {:fi "Finström", :se "Finström", :en "Finström"},
    :city-code 60,
    :status :active,
    :valid-until nil}
   {:name {:fi "Forssa", :se "Forssa", :en "Forssa"},
    :city-code 61,
    :status :active,
    :valid-until nil}
   {:name {:fi "Föglö", :se "Föglö", :en "Föglö"},
    :city-code 62,
    :status :active,
    :valid-until nil}
   {:name {:fi "Geta", :se "Geta", :en "Geta"},
    :city-code 65,
    :status :active,
    :valid-until nil}
   {:name {:fi "Haapajärvi", :se "Haapajärvi", :en "Haapajärvi"},
    :city-code 69,
    :status :active,
    :valid-until nil}
   {:name {:fi "Haapavesi", :se "Haapavesi", :en "Haapavesi"},
    :city-code 71,
    :status :active,
    :valid-until nil}
   {:name {:fi "Hailuoto", :se "Karlö", :en "Hailuoto"},
    :city-code 72,
    :status :active,
    :valid-until nil}
   {:name {:fi "Halikko", :se "Halikko", :en "Halikko"},
    :city-code 73,
    :status :abolished,
    :valid-until 2009}
   {:name {:fi "Halsua", :se "Halsua", :en "Halsua"},
    :city-code 74,
    :status :active,
    :valid-until nil}
   {:name {:fi "Hamina", :se "Fredrikshamn", :en "Hamina"},
    :city-code 75,
    :status :active,
    :valid-until nil}
   {:name {:fi "Hammarland", :se "Hammarland", :en "Hammarland"},
    :city-code 76,
    :status :active,
    :valid-until nil}
   {:name {:fi "Hankasalmi", :se "Hankasalmi", :en "Hankasalmi"},
    :city-code 77,
    :status :active,
    :valid-until nil}
   {:name {:fi "Hanko", :se "Hangö", :en "Hanko"},
    :city-code 78,
    :status :active,
    :valid-until nil}
   {:name {:fi "Harjavalta", :se "Harjavalta", :en "Harjavalta"},
    :city-code 79,
    :status :active,
    :valid-until nil}
   {:name {:fi "Hartola", :se "Hartola", :en "Hartola"},
    :city-code 81,
    :status :active,
    :valid-until nil}
   {:name {:fi "Hattula", :se "Hattula", :en "Hattula"},
    :city-code 82,
    :status :active,
    :valid-until nil}
   {:name {:fi "Hauho", :se "Hauho", :en "Hauho"},
    :city-code 83,
    :status :abolished,
    :valid-until 2009}
   {:name {:fi "Haukipudas", :se "Haukipudas", :en "Haukipudas"},
    :city-code 84,
    :status :abolished,
    :valid-until 2013}
   {:name {:fi "Haukivuori", :se "Haukivuori", :en "Haukivuori"},
    :city-code 85,
    :status :abolished,
    :valid-until 2007}
   {:name {:fi "Hausjärvi", :se "Hausjärvi", :en "Hausjärvi"},
    :city-code 86,
    :status :active,
    :valid-until nil}
   {:name {:fi "Heinävesi", :se "Heinävesi", :en "Heinävesi"},
    :city-code 90,
    :status :active,
    :valid-until nil}
   {:name {:fi "Helsinki", :se "Helsingfors", :en "Helsinki"},
    :city-code 91,
    :status :active,
    :valid-until nil}
   {:name {:fi "Vantaa", :se "Vanda", :en "Vantaa"},
    :city-code 92,
    :status :active,
    :valid-until nil}
   {:name {:fi "Himanka", :se "Himanka", :en "Himanka"},
    :city-code 95,
    :status :abolished,
    :valid-until 2010}
   {:name {:fi "Hirvensalmi", :se "Hirvensalmi", :en "Hirvensalmi"},
    :city-code 97,
    :status :active,
    :valid-until nil}
   {:name {:fi "Hollola", :se "Hollola", :en "Hollola"},
    :city-code 98,
    :status :active,
    :valid-until nil}
   {:name {:fi "Honkajoki", :se "Honkajoki", :en "Honkajoki"},
    :city-code 99,
    :status :active,
    :valid-until nil}
   {:name {:fi "Houtskari", :se "Houtskär", :en "Houtskari"},
    :city-code 101,
    :status :abolished,
    :valid-until 2009}
   {:name {:fi "Huittinen", :se "Huittinen", :en "Huittinen"},
    :city-code 102,
    :status :active,
    :valid-until nil}
   {:name {:fi "Humppila", :se "Humppila", :en "Humppila"},
    :city-code 103,
    :status :active,
    :valid-until nil}
   {:name {:fi "Hyrynsalmi", :se "Hyrynsalmi", :en "Hyrynsalmi"},
    :city-code 105,
    :status :active,
    :valid-until nil}
   {:name {:fi "Hyvinkää", :se "Hyvinge", :en "Hyvinkää"},
    :city-code 106,
    :status :active,
    :valid-until nil}
   {:name {:fi "Hämeenkyrö", :se "Tavastkyro", :en "Hämeenkyrö"},
    :city-code 108,
    :status :active,
    :valid-until nil}
   {:name {:fi "Hämeenlinna", :se "Tavastehus", :en "Hämeenlinna"},
    :city-code 109,
    :status :active,
    :valid-until nil}
   {:name {:fi "Heinola", :se "Heinola", :en "Heinola"},
    :city-code 111,
    :status :active,
    :valid-until nil}
   {:name {:fi "Ii", :se "Ii", :en "Ii"},
    :city-code 139,
    :status :active,
    :valid-until nil}
   {:name {:fi "Iisalmi", :se "Idensalmi", :en "Iisalmi"},
    :city-code 140,
    :status :active,
    :valid-until nil}
   {:name {:fi "Iitti", :se "Iitti", :en "Iitti"},
    :city-code 142,
    :status :active,
    :valid-until nil}
   {:name {:fi "Ikaalinen", :se "Ikalis", :en "Ikaalinen"},
    :city-code 143,
    :status :active,
    :valid-until nil}
   {:name {:fi "Ilmajoki", :se "Ilmajoki", :en "Ilmajoki"},
    :city-code 145,
    :status :active,
    :valid-until nil}
   {:name {:fi "Ilomantsi", :se "Ilomants", :en "Ilomantsi"},
    :city-code 146,
    :status :active,
    :valid-until nil}
   {:name {:fi "Inari", :se "Enare", :en "Inari"},
    :city-code 148,
    :status :active,
    :valid-until nil}
   {:name {:fi "Inkoo", :se "Ingå", :en "Inkoo"},
    :city-code 149,
    :status :active,
    :valid-until nil}
   {:name {:fi "Iniö", :se "Iniö", :en "Iniö"},
    :city-code 150,
    :status :abolished,
    :valid-until 2009}
   {:name {:fi "Isojoki", :se "Storå", :en "Isojoki"},
    :city-code 151,
    :status :active,
    :valid-until nil}
   {:name {:fi "Isokyrö", :se "Storkyro", :en "Isokyrö"},
    :city-code 152,
    :status :active,
    :valid-until nil}
   {:name {:fi "Imatra", :se "Imatra", :en "Imatra"},
    :city-code 153,
    :status :active,
    :valid-until nil}
   {:name {:fi "Jaala", :se "Jaala", :en "Jaala"},
    :city-code 163,
    :status :abolished,
    :valid-until 2009}
   {:name {:fi "Jalasjärvi", :se "Jalasjärvi", :en "Jalasjärvi"},
    :city-code 164,
    :status :abolished,
    :valid-until 2015}
   {:name {:fi "Janakkala", :se "Janakkala", :en "Janakkala"},
    :city-code 165,
    :status :active,
    :valid-until nil}
   {:name {:fi "Joensuu", :se "Joensuu", :en "Joensuu"},
    :city-code 167,
    :status :active,
    :valid-until nil}
   {:name {:fi "Jokioinen", :se "Jockis", :en "Jokioinen"},
    :city-code 169,
    :status :active,
    :valid-until nil}
   {:name {:fi "Jomala", :se "Jomala", :en "Jomala"},
    :city-code 170,
    :status :active,
    :valid-until nil}
   {:name {:fi "Joroinen", :se "Jorois", :en "Joroinen"},
    :city-code 171,
    :status :active,
    :valid-until nil}
   {:name {:fi "Joutsa", :se "Joutsa", :en "Joutsa"},
    :city-code 172,
    :status :active,
    :valid-until nil}
   {:name {:fi "Joutseno", :se "Joutseno", :en "Joutseno"},
    :city-code 173,
    :status :abolished,
    :valid-until 2009}
   {:name {:fi "Juankoski", :se "Juankoski", :en "Juankoski"},
    :city-code 174,
    :status :abolished,
    :valid-until 2016}
   {:name {:fi "Jurva", :se "Jurva", :en "Jurva"},
    :city-code 175,
    :status :abolished,
    :valid-until 2009}
   {:name {:fi "Juuka", :se "Juuka", :en "Juuka"},
    :city-code 176,
    :status :active,
    :valid-until nil}
   {:name {:fi "Juupajoki", :se "Juupajoki", :en "Juupajoki"},
    :city-code 177,
    :status :active,
    :valid-until nil}
   {:name {:fi "Juva", :se "Juva", :en "Juva"},
    :city-code 178,
    :status :active,
    :valid-until nil}
   {:name {:fi "Jyväskylä", :se "Jyväskylä", :en "Jyväskylä"},
    :city-code 179,
    :status :active,
    :valid-until nil}
   {:name
    {:fi "Jyväskylän mlk", :se "Jyväskylä lk", :en "Jyväskylän mlk"},
    :city-code 180,
    :status :abolished,
    :valid-until 2009}
   {:name {:fi "Jämijärvi", :se "Jämijärvi", :en "Jämijärvi"},
    :city-code 181,
    :status :active,
    :valid-until nil}
   {:name {:fi "Jämsä", :se "Jämsä", :en "Jämsä"},
    :city-code 182,
    :status :active,
    :valid-until nil}
   {:name {:fi "Jämsänkoski", :se "Jämsänkoski", :en "Jämsänkoski"},
    :city-code 183,
    :status :abolished,
    :valid-until 2009}
   {:name {:fi "Jäppilä", :se "Jäppilä", :en "Jäppilä"},
    :city-code 184,
    :status :abolished,
    :valid-until 2004}
   {:name {:fi "Järvenpää", :se "Träskända", :en "Järvenpää"},
    :city-code 186,
    :status :active,
    :valid-until nil}
   {:name {:fi "Kaarina", :se "S t Karins", :en "Kaarina"},
    :city-code 202,
    :status :active,
    :valid-until nil}
   {:name {:fi "Kaavi", :se "Kaavi", :en "Kaavi"},
    :city-code 204,
    :status :active,
    :valid-until nil}
   {:name {:fi "Kajaani", :se "Kajana", :en "Kajaani"},
    :city-code 205,
    :status :active,
    :valid-until nil}
   {:name {:fi "Kalajoki", :se "Kalajoki", :en "Kalajoki"},
    :city-code 208,
    :status :active,
    :valid-until nil}
   {:name {:fi "Kalvola", :se "Kalvola", :en "Kalvola"},
    :city-code 210,
    :status :abolished,
    :valid-until 2009}
   {:name {:fi "Kangasala", :se "Kangasala", :en "Kangasala"},
    :city-code 211,
    :status :active,
    :valid-until nil}
   {:name {:fi "Kangaslampi", :se "Kangaslampi", :en "Kangaslampi"},
    :city-code 212,
    :status :abolished,
    :valid-until 2005}
   {:name {:fi "Kangasniemi", :se "Kangasniemi", :en "Kangasniemi"},
    :city-code 213,
    :status :active,
    :valid-until nil}
   {:name {:fi "Kankaanpää", :se "Kankaanpää", :en "Kankaanpää"},
    :city-code 214,
    :status :active,
    :valid-until nil}
   {:name {:fi "Kannonkoski", :se "Kannonkoski", :en "Kannonkoski"},
    :city-code 216,
    :status :active,
    :valid-until nil}
   {:name {:fi "Kannus", :se "Kannus", :en "Kannus"},
    :city-code 217,
    :status :active,
    :valid-until nil}
   {:name {:fi "Karijoki", :se "Bötom", :en "Karijoki"},
    :city-code 218,
    :status :active,
    :valid-until nil}
   {:name {:fi "Karinainen", :se "Karinais", :en "Karinainen"},
    :city-code 219,
    :status :abolished,
    :valid-until 2005}
   {:name {:fi "Karjaa", :se "Karis", :en "Karjaa"},
    :city-code 220,
    :status :abolished,
    :valid-until 2009}
   {:name {:fi "Karjalohja", :se "Karislojo", :en "Karjalohja"},
    :city-code 223,
    :status :abolished,
    :valid-until 2013}
   {:name {:fi "Karkkila", :se "Högfors", :en "Karkkila"},
    :city-code 224,
    :status :active,
    :valid-until nil}
   {:name {:fi "Karstula", :se "Karstula", :en "Karstula"},
    :city-code 226,
    :status :active,
    :valid-until nil}
   {:name {:fi "Karttula", :se "Karttula", :en "Karttula"},
    :city-code 227,
    :status :abolished,
    :valid-until 2011}
   {:name {:fi "Karvia", :se "Karvia", :en "Karvia"},
    :city-code 230,
    :status :active,
    :valid-until nil}
   {:name {:fi "Kaskinen", :se "Kaskö", :en "Kaskinen"},
    :city-code 231,
    :status :active,
    :valid-until nil}
   {:name {:fi "Kauhajoki", :se "Kauhajoki", :en "Kauhajoki"},
    :city-code 232,
    :status :active,
    :valid-until nil}
   {:name {:fi "Kauhava", :se "Kauhava", :en "Kauhava"},
    :city-code 233,
    :status :active,
    :valid-until nil}
   {:name {:fi "Kauniainen", :se "Grankulla", :en "Kauniainen"},
    :city-code 235,
    :status :active,
    :valid-until nil}
   {:name {:fi "Kaustinen", :se "Kaustby", :en "Kaustinen"},
    :city-code 236,
    :status :active,
    :valid-until nil}
   {:name {:fi "Keitele", :se "Keitele", :en "Keitele"},
    :city-code 239,
    :status :active,
    :valid-until nil}
   {:name {:fi "Kemi", :se "Kemi", :en "Kemi"},
    :city-code 240,
    :status :active,
    :valid-until nil}
   {:name {:fi "Keminmaa", :se "Keminmaa", :en "Keminmaa"},
    :city-code 241,
    :status :active,
    :valid-until nil}
   {:name {:fi "Kemiö", :se "Kimito", :en "Kemiö"},
    :city-code 243,
    :status :abolished,
    :valid-until 2009}
   {:name {:fi "Kempele", :se "Kempele", :en "Kempele"},
    :city-code 244,
    :status :active,
    :valid-until nil}
   {:name {:fi "Kerava", :se "Kervo", :en "Kerava"},
    :city-code 245,
    :status :active,
    :valid-until nil}
   {:name {:fi "Kerimäki", :se "Kerimäki", :en "Kerimäki"},
    :city-code 246,
    :status :abolished,
    :valid-until 2013}
   {:name {:fi "Kestilä", :se "Kestilä", :en "Kestilä"},
    :city-code 247,
    :status :abolished,
    :valid-until 2009}
   {:name {:fi "Kesälahti", :se "Kesälax", :en "Kesälahti"},
    :city-code 248,
    :status :abolished,
    :valid-until 2013}
   {:name {:fi "Keuruu", :se "Keuruu", :en "Keuruu"},
    :city-code 249,
    :status :active,
    :valid-until nil}
   {:name {:fi "Kihniö", :se "Kihniö", :en "Kihniö"},
    :city-code 250,
    :status :active,
    :valid-until nil}
   {:name
    {:fi "Kiihtelysvaara", :se "Kiihtelysvaara", :en "Kiihtelysvaara"},
    :city-code 251,
    :status :abolished,
    :valid-until 2005}
   {:name {:fi "Kiikala", :se "Kiikala", :en "Kiikala"},
    :city-code 252,
    :status :abolished,
    :valid-until 2009}
   {:name {:fi "Kiikoinen", :se "Kikois", :en "Kiikoinen"},
    :city-code 254,
    :status :abolished,
    :valid-until 2013}
   {:name {:fi "Kiiminki", :se "Kiminge", :en "Kiiminki"},
    :city-code 255,
    :status :abolished,
    :valid-until 2013}
   {:name {:fi "Kinnula", :se "Kinnula", :en "Kinnula"},
    :city-code 256,
    :status :active,
    :valid-until nil}
   {:name {:fi "Kirkkonummi", :se "Kyrkslätt", :en "Kirkkonummi"},
    :city-code 257,
    :status :active,
    :valid-until nil}
   {:name {:fi "Kisko", :se "Kisko", :en "Kisko"},
    :city-code 259,
    :status :abolished,
    :valid-until 2009}
   {:name {:fi "Kitee", :se "Kitee", :en "Kitee"},
    :city-code 260,
    :status :active,
    :valid-until nil}
   {:name {:fi "Kittilä", :se "Kittilä", :en "Kittilä"},
    :city-code 261,
    :status :active,
    :valid-until nil}
   {:name {:fi "Kiukainen", :se "Kiukais", :en "Kiukainen"},
    :city-code 262,
    :status :abolished,
    :valid-until 2009}
   {:name {:fi "Kiuruvesi", :se "Kiuruvesi", :en "Kiuruvesi"},
    :city-code 263,
    :status :active,
    :valid-until nil}
   {:name {:fi "Kivijärvi", :se "Kivijärvi", :en "Kivijärvi"},
    :city-code 265,
    :status :active,
    :valid-until nil}
   {:name {:fi "Kodisjoki", :se "Kodisjoki", :en "Kodisjoki"},
    :city-code 266,
    :status :abolished,
    :valid-until 2007}
   {:name {:fi "Kokemäki", :se "Kumo", :en "Kokemäki"},
    :city-code 271,
    :status :active,
    :valid-until nil}
   {:name {:fi "Kokkola", :se "Karleby", :en "Kokkola"},
    :city-code 272,
    :status :active,
    :valid-until nil}
   {:name {:fi "Kolari", :se "Kolari", :en "Kolari"},
    :city-code 273,
    :status :active,
    :valid-until nil}
   {:name {:fi "Konnevesi", :se "Konnevesi", :en "Konnevesi"},
    :city-code 275,
    :status :active,
    :valid-until nil}
   {:name {:fi "Kontiolahti", :se "Kontiolahti", :en "Kontiolahti"},
    :city-code 276,
    :status :active,
    :valid-until nil}
   {:name {:fi "Korpilahti", :se "Korpilahti", :en "Korpilahti"},
    :city-code 277,
    :status :abolished,
    :valid-until 2009}
   {:name {:fi "Korppoo", :se "Korpo", :en "Korppoo"},
    :city-code 279,
    :status :abolished,
    :valid-until 2009}
   {:name {:fi "Korsnäs", :se "Korsnäs", :en "Korsnäs"},
    :city-code 280,
    :status :active,
    :valid-until nil}
   {:name {:fi "Kortesjärvi", :se "Kortesjärvi", :en "Kortesjärvi"},
    :city-code 281,
    :status :abolished,
    :valid-until 2009}
   {:name {:fi "Hämeenkoski", :se "Hämeenkoski", :en "Hämeenkoski"},
    :city-code 283,
    :status :abolished,
    :valid-until 2015}
   {:name {:fi "Koski Tl", :se "Koski Åbo l", :en "Koski Tl"},
    :city-code 284,
    :status :active,
    :valid-until nil}
   {:name {:fi "Kotka", :se "Kotka", :en "Kotka"},
    :city-code 285,
    :status :active,
    :valid-until nil}
   {:name {:fi "Kouvola", :se "Kouvola", :en "Kouvola"},
    :city-code 286,
    :status :active,
    :valid-until nil}
   {:name
    {:fi "Kristiinankaupunki",
     :se "Kristinestad",
     :en "Kristiinankaupunki"},
    :city-code 287,
    :status :active,
    :valid-until nil}
   {:name {:fi "Kruunupyy", :se "Kronoby", :en "Kruunupyy"},
    :city-code 288,
    :status :active,
    :valid-until nil}
   {:name {:fi "Kuhmalahti", :se "Kuhmalahti", :en "Kuhmalahti"},
    :city-code 289,
    :status :abolished,
    :valid-until 2011}
   {:name {:fi "Kuhmo", :se "Kuhmo", :en "Kuhmo"},
    :city-code 290,
    :status :active,
    :valid-until nil}
   {:name {:fi "Kuhmoinen", :se "Kuhmoinen", :en "Kuhmoinen"},
    :city-code 291,
    :status :active,
    :valid-until nil}
   {:name {:fi "Kuivaniemi", :se "Kuivaniemi", :en "Kuivaniemi"},
    :city-code 292,
    :status :abolished,
    :valid-until 2007}
   {:name {:fi "Kullaa", :se "Kulla", :en "Kullaa"},
    :city-code 293,
    :status :abolished,
    :valid-until 2005}
   {:name {:fi "Kumlinge", :se "Kumlinge", :en "Kumlinge"},
    :city-code 295,
    :status :active,
    :valid-until nil}
   {:name {:fi "Kuopio", :se "Kuopio", :en "Kuopio"},
    :city-code 297,
    :status :active,
    :valid-until nil}
   {:name {:fi "Kuorevesi", :se "Kuorevesi", :en "Kuorevesi"},
    :city-code 299,
    :status :abolished,
    :valid-until 2001}
   {:name {:fi "Kuortane", :se "Kuortane", :en "Kuortane"},
    :city-code 300,
    :status :active,
    :valid-until nil}
   {:name {:fi "Kurikka", :se "Kurikka", :en "Kurikka"},
    :city-code 301,
    :status :active,
    :valid-until nil}
   {:name {:fi "Kuru", :se "Kuru", :en "Kuru"},
    :city-code 303,
    :status :abolished,
    :valid-until 2009}
   {:name {:fi "Kustavi", :se "Gustavs", :en "Kustavi"},
    :city-code 304,
    :status :active,
    :valid-until nil}
   {:name {:fi "Kuusamo", :se "Kuusamo", :en "Kuusamo"},
    :city-code 305,
    :status :active,
    :valid-until nil}
   {:name {:fi "Kuusankoski", :se "Kuusankoski", :en "Kuusankoski"},
    :city-code 306,
    :status :abolished,
    :valid-until 2009}
   {:name {:fi "Kuusjoki", :se "Kuusjoki", :en "Kuusjoki"},
    :city-code 308,
    :status :abolished,
    :valid-until 2009}
   {:name {:fi "Outokumpu", :se "Outokumpu", :en "Outokumpu"},
    :city-code 309,
    :status :active,
    :valid-until nil}
   {:name {:fi "Kylmäkoski", :se "Kylmäkoski", :en "Kylmäkoski"},
    :city-code 310,
    :status :abolished,
    :valid-until 2011}
   {:name {:fi "Kyyjärvi", :se "Kyyjärvi", :en "Kyyjärvi"},
    :city-code 312,
    :status :active,
    :valid-until nil}
   {:name {:fi "Kälviä", :se "Kelviå", :en "Kälviä"},
    :city-code 315,
    :status :abolished,
    :valid-until 2009}
   {:name {:fi "Kärkölä", :se "Kärkölä", :en "Kärkölä"},
    :city-code 316,
    :status :active,
    :valid-until nil}
   {:name {:fi "Kärsämäki", :se "Kärsämäki", :en "Kärsämäki"},
    :city-code 317,
    :status :active,
    :valid-until nil}
   {:name {:fi "Kökar", :se "Kökar", :en "Kökar"},
    :city-code 318,
    :status :active,
    :valid-until nil}
   {:name {:fi "Köyliö", :se "Kjulo", :en "Köyliö"},
    :city-code 319,
    :status :abolished,
    :valid-until 2015}
   {:name {:fi "Kemijärvi", :se "Kemijärvi", :en "Kemijärvi"},
    :city-code 320,
    :status :active,
    :valid-until nil}
   {:name {:fi "Kemiönsaari", :se "Kimitoön", :en "Kemiönsaari"},
    :city-code 322,
    :status :active,
    :valid-until nil}
   {:name {:fi "Lahti", :se "Lahtis", :en "Lahti"},
    :city-code 398,
    :status :active,
    :valid-until nil}
   {:name {:fi "Laihia", :se "Laihela", :en "Laihia"},
    :city-code 399,
    :status :active,
    :valid-until nil}
   {:name {:fi "Laitila", :se "Laitila", :en "Laitila"},
    :city-code 400,
    :status :active,
    :valid-until nil}
   {:name {:fi "Lammi", :se "Lampis", :en "Lammi"},
    :city-code 401,
    :status :abolished,
    :valid-until 2009}
   {:name {:fi "Lapinlahti", :se "Lapinlahti", :en "Lapinlahti"},
    :city-code 402,
    :status :active,
    :valid-until nil}
   {:name {:fi "Lappajärvi", :se "Lappajärvi", :en "Lappajärvi"},
    :city-code 403,
    :status :active,
    :valid-until nil}
   {:name {:fi "Lappeenranta", :se "Villmanstrand", :en "Lappeenranta"},
    :city-code 405,
    :status :active,
    :valid-until nil}
   {:name {:fi "Lappi", :se "Lappi", :en "Lappi"},
    :city-code 406,
    :status :abolished,
    :valid-until 2009}
   {:name {:fi "Lapinjärvi", :se "Lappträsk", :en "Lapinjärvi"},
    :city-code 407,
    :status :active,
    :valid-until nil}
   {:name {:fi "Lapua", :se "Lappo", :en "Lapua"},
    :city-code 408,
    :status :active,
    :valid-until nil}
   {:name {:fi "Laukaa", :se "Laukaa", :en "Laukaa"},
    :city-code 410,
    :status :active,
    :valid-until nil}
   {:name {:fi "Lavia", :se "Lavia", :en "Lavia"},
    :city-code 413,
    :status :abolished,
    :valid-until 2014}
   {:name {:fi "Lehtimäki", :se "Lehtimäki", :en "Lehtimäki"},
    :city-code 414,
    :status :abolished,
    :valid-until 2009}
   {:name {:fi "Leivonmäki", :se "Leivonmäki", :en "Leivonmäki"},
    :city-code 415,
    :status :abolished,
    :valid-until 2008}
   {:name {:fi "Lemi", :se "Lemi", :en "Lemi"},
    :city-code 416,
    :status :active,
    :valid-until nil}
   {:name {:fi "Lemland", :se "Lemland", :en "Lemland"},
    :city-code 417,
    :status :active,
    :valid-until nil}
   {:name {:fi "Lempäälä", :se "Lempäälä", :en "Lempäälä"},
    :city-code 418,
    :status :active,
    :valid-until nil}
   {:name {:fi "Lemu", :se "Lemo", :en "Lemu"},
    :city-code 419,
    :status :abolished,
    :valid-until 2009}
   {:name {:fi "Leppävirta", :se "Leppävirta", :en "Leppävirta"},
    :city-code 420,
    :status :active,
    :valid-until nil}
   {:name {:fi "Lestijärvi", :se "Lestijärvi", :en "Lestijärvi"},
    :city-code 421,
    :status :active,
    :valid-until nil}
   {:name {:fi "Lieksa", :se "Lieksa", :en "Lieksa"},
    :city-code 422,
    :status :active,
    :valid-until nil}
   {:name {:fi "Lieto", :se "Lundo", :en "Lieto"},
    :city-code 423,
    :status :active,
    :valid-until nil}
   {:name {:fi "Liljendal", :se "Liljendal", :en "Liljendal"},
    :city-code 424,
    :status :abolished,
    :valid-until 2010}
   {:name {:fi "Liminka", :se "Limingo", :en "Liminka"},
    :city-code 425,
    :status :active,
    :valid-until nil}
   {:name {:fi "Liperi", :se "Liperi", :en "Liperi"},
    :city-code 426,
    :status :active,
    :valid-until nil}
   {:name {:fi "Lohtaja", :se "Lochteå", :en "Lohtaja"},
    :city-code 429,
    :status :abolished,
    :valid-until 2009}
   {:name {:fi "Loimaa", :se "Loimaa", :en "Loimaa"},
    :city-code 430,
    :status :active,
    :valid-until nil}
   {:name
    {:fi "Loimaan kunta", :se "Loimaa kommun", :en "Loimaan kunta"},
    :city-code 431,
    :status :abolished,
    :valid-until 2005}
   {:name {:fi "Loppi", :se "Loppi", :en "Loppi"},
    :city-code 433,
    :status :active,
    :valid-until nil}
   {:name {:fi "Loviisa", :se "Lovisa", :en "Loviisa"},
    :city-code 434,
    :status :active,
    :valid-until nil}
   {:name {:fi "Luhanka", :se "Luhanka", :en "Luhanka"},
    :city-code 435,
    :status :active,
    :valid-until nil}
   {:name {:fi "Lumijoki", :se "Lumijoki", :en "Lumijoki"},
    :city-code 436,
    :status :active,
    :valid-until nil}
   {:name {:fi "Lumparland", :se "Lumparland", :en "Lumparland"},
    :city-code 438,
    :status :active,
    :valid-until nil}
   {:name {:fi "Luopioinen", :se "Luopiois", :en "Luopioinen"},
    :city-code 439,
    :status :abolished,
    :valid-until 2007}
   {:name {:fi "Luoto", :se "Larsmo", :en "Luoto"},
    :city-code 440,
    :status :active,
    :valid-until nil}
   {:name {:fi "Luumäki", :se "Luumäki", :en "Luumäki"},
    :city-code 441,
    :status :active,
    :valid-until nil}
   {:name {:fi "Luvia", :se "Luvia", :en "Luvia"},
    :city-code 442,
    :status :abolished,
    :valid-until 2016}
   {:name {:fi "Längelmäki", :se "Längelmäki", :en "Längelmäki"},
    :city-code 443,
    :status :abolished,
    :valid-until 2007}
   {:name {:fi "Lohja", :se "Lojo", :en "Lohja"},
    :city-code 444,
    :status :active,
    :valid-until nil}
   {:name {:fi "Parainen", :se "Pargas", :en "Parainen"},
    :city-code 445,
    :status :active,
    :valid-until nil}
   {:name {:fi "Maalahti", :se "Malax", :en "Maalahti"},
    :city-code 475,
    :status :active,
    :valid-until nil}
   {:name {:fi "Maaninka", :se "Maaninka", :en "Maaninka"},
    :city-code 476,
    :status :abolished,
    :valid-until 2014}
   {:name
    {:fi "Maarianhamina - Mariehamn",
     :se "Mariehamn",
     :en "Maarianhamina - Mariehamn"},
    :city-code 478,
    :status :active,
    :valid-until nil}
   {:name {:fi "Maksamaa", :se "Maxmo", :en "Maksamaa"},
    :city-code 479,
    :status :abolished,
    :valid-until 2007}
   {:name {:fi "Marttila", :se "Marttila", :en "Marttila"},
    :city-code 480,
    :status :active,
    :valid-until nil}
   {:name {:fi "Masku", :se "Masku", :en "Masku"},
    :city-code 481,
    :status :active,
    :valid-until nil}
   {:name {:fi "Mellilä", :se "Mellilä", :en "Mellilä"},
    :city-code 482,
    :status :abolished,
    :valid-until 2009}
   {:name {:fi "Merijärvi", :se "Merijärvi", :en "Merijärvi"},
    :city-code 483,
    :status :active,
    :valid-until nil}
   {:name {:fi "Merikarvia", :se "Sastmola", :en "Merikarvia"},
    :city-code 484,
    :status :active,
    :valid-until nil}
   {:name {:fi "Merimasku", :se "Merimasku", :en "Merimasku"},
    :city-code 485,
    :status :abolished,
    :valid-until 2009}
   {:name {:fi "Miehikkälä", :se "Miehikkälä", :en "Miehikkälä"},
    :city-code 489,
    :status :active,
    :valid-until nil}
   {:name {:fi "Mietoinen", :se "Mietois", :en "Mietoinen"},
    :city-code 490,
    :status :abolished,
    :valid-until 2007}
   {:name {:fi "Mikkeli", :se "S t Michel", :en "Mikkeli"},
    :city-code 491,
    :status :active,
    :valid-until nil}
   {:name {:fi "Mikkelin mlk", :se "S t Michels lk", :en "Mikkelin mlk"},
    :city-code 492,
    :status :abolished,
    :valid-until 2001}
   {:name {:fi "Mouhijärvi", :se "Mouhijärvi", :en "Mouhijärvi"},
    :city-code 493,
    :status :abolished,
    :valid-until 2009}
   {:name {:fi "Muhos", :se "Muhos", :en "Muhos"},
    :city-code 494,
    :status :active,
    :valid-until nil}
   {:name {:fi "Multia", :se "Multia", :en "Multia"},
    :city-code 495,
    :status :active,
    :valid-until nil}
   {:name {:fi "Muonio", :se "Muonio", :en "Muonio"},
    :city-code 498,
    :status :active,
    :valid-until nil}
   {:name {:fi "Mustasaari", :se "Korsholm", :en "Mustasaari"},
    :city-code 499,
    :status :active,
    :valid-until nil}
   {:name {:fi "Muurame", :se "Muurame", :en "Muurame"},
    :city-code 500,
    :status :active,
    :valid-until nil}
   {:name {:fi "Muurla", :se "Muurla", :en "Muurla"},
    :city-code 501,
    :status :abolished,
    :valid-until 2009}
   {:name {:fi "Mynämäki", :se "Mynämäki", :en "Mynämäki"},
    :city-code 503,
    :status :active,
    :valid-until nil}
   {:name {:fi "Myrskylä", :se "Mörskom", :en "Myrskylä"},
    :city-code 504,
    :status :active,
    :valid-until nil}
   {:name {:fi "Mäntsälä", :se "Mäntsälä", :en "Mäntsälä"},
    :city-code 505,
    :status :active,
    :valid-until nil}
   {:name {:fi "Mänttä", :se "Mänttä", :en "Mänttä"},
    :city-code 506,
    :status :abolished,
    :valid-until 2009}
   {:name {:fi "Mäntyharju", :se "Mäntyharju", :en "Mäntyharju"},
    :city-code 507,
    :status :active,
    :valid-until nil}
   {:name {:fi "Mänttä-Vilppula", :se "Vilppula", :en "Mänttä-Vilppula"},
    :city-code 508,
    :status :active,
    :valid-until nil}
   {:name {:fi "Naantali", :se "Nådendal", :en "Naantali"},
    :city-code 529,
    :status :active,
    :valid-until nil}
   {:name {:fi "Nakkila", :se "Nakkila", :en "Nakkila"},
    :city-code 531,
    :status :active,
    :valid-until nil}
   {:name {:fi "Nastola", :se "Nastola", :en "Nastola"},
    :city-code 532,
    :status :abolished,
    :valid-until 2015}
   {:name {:fi "Nauvo", :se "Nagu", :en "Nauvo"},
    :city-code 533,
    :status :abolished,
    :valid-until 2009}
   {:name {:fi "Nilsiä", :se "Nilsiä", :en "Nilsiä"},
    :city-code 534,
    :status :abolished,
    :valid-until 2013}
   {:name {:fi "Nivala", :se "Nivala", :en "Nivala"},
    :city-code 535,
    :status :active,
    :valid-until nil}
   {:name {:fi "Nokia", :se "Nokia", :en "Nokia"},
    :city-code 536,
    :status :active,
    :valid-until nil}
   {:name {:fi "Noormarkku", :se "Norrmark", :en "Noormarkku"},
    :city-code 537,
    :status :abolished,
    :valid-until 2010}
   {:name {:fi "Nousiainen", :se "Nousis", :en "Nousiainen"},
    :city-code 538,
    :status :active,
    :valid-until nil}
   {:name {:fi "Nummi-Pusula", :se "Nummi-Pusula", :en "Nummi-Pusula"},
    :city-code 540,
    :status :abolished,
    :valid-until 2013}
   {:name {:fi "Nurmes", :se "Nurmes", :en "Nurmes"},
    :city-code 541,
    :status :active,
    :valid-until nil}
   {:name {:fi "Nurmijärvi", :se "Nurmijärvi", :en "Nurmijärvi"},
    :city-code 543,
    :status :active,
    :valid-until nil}
   {:name {:fi "Nurmo", :se "Nurmo", :en "Nurmo"},
    :city-code 544,
    :status :abolished,
    :valid-until 2009}
   {:name {:fi "Närpiö", :se "Närpes", :en "Närpiö"},
    :city-code 545,
    :status :active,
    :valid-until nil}
   {:name {:fi "Oravainen", :se "Oravais", :en "Oravainen"},
    :city-code 559,
    :status :abolished,
    :valid-until 2011}
   {:name {:fi "Orimattila", :se "Orimattila", :en "Orimattila"},
    :city-code 560,
    :status :active,
    :valid-until nil}
   {:name {:fi "Oripää", :se "Oripää", :en "Oripää"},
    :city-code 561,
    :status :active,
    :valid-until nil}
   {:name {:fi "Orivesi", :se "Orivesi", :en "Orivesi"},
    :city-code 562,
    :status :active,
    :valid-until nil}
   {:name {:fi "Oulainen", :se "Oulainen", :en "Oulainen"},
    :city-code 563,
    :status :active,
    :valid-until nil}
   {:name {:fi "Oulu", :se "Uleåborg", :en "Oulu"},
    :city-code 564,
    :status :active,
    :valid-until nil}
   {:name {:fi "Oulunsalo", :se "Oulunsalo", :en "Oulunsalo"},
    :city-code 567,
    :status :abolished,
    :valid-until 2013}
   {:name
    {:fi "Parainen (-2008)",
     :se "Pargas (-2008)",
     :en "Parainen (-2008)"},
    :city-code 573,
    :status :abolished,
    :valid-until 2009}
   {:name {:fi "Padasjoki", :se "Padasjoki", :en "Padasjoki"},
    :city-code 576,
    :status :active,
    :valid-until nil}
   {:name {:fi "Paimio", :se "Pemar", :en "Paimio"},
    :city-code 577,
    :status :active,
    :valid-until nil}
   {:name {:fi "Paltamo", :se "Paltamo", :en "Paltamo"},
    :city-code 578,
    :status :active,
    :valid-until nil}
   {:name {:fi "Parikkala", :se "Parikkala", :en "Parikkala"},
    :city-code 580,
    :status :active,
    :valid-until nil}
   {:name {:fi "Parkano", :se "Parkano", :en "Parkano"},
    :city-code 581,
    :status :active,
    :valid-until nil}
   {:name {:fi "Pattijoki", :se "Pattijoki", :en "Pattijoki"},
    :city-code 582,
    :status :abolished,
    :valid-until 2003}
   {:name
    {:fi "Pelkosenniemi", :se "Pelkosenniemi", :en "Pelkosenniemi"},
    :city-code 583,
    :status :active,
    :valid-until nil}
   {:name {:fi "Perho", :se "Perho", :en "Perho"},
    :city-code 584,
    :status :active,
    :valid-until nil}
   {:name {:fi "Pernaja", :se "Pernå", :en "Pernaja"},
    :city-code 585,
    :status :abolished,
    :valid-until 2010}
   {:name {:fi "Perniö", :se "Bjärnå", :en "Perniö"},
    :city-code 586,
    :status :abolished,
    :valid-until 2009}
   {:name {:fi "Pertteli", :se "S t Bertils", :en "Pertteli"},
    :city-code 587,
    :status :abolished,
    :valid-until 2009}
   {:name {:fi "Pertunmaa", :se "Pertunmaa", :en "Pertunmaa"},
    :city-code 588,
    :status :active,
    :valid-until nil}
   {:name
    {:fi "Peräseinäjoki", :se "Peräseinäjoki", :en "Peräseinäjoki"},
    :city-code 589,
    :status :abolished,
    :valid-until 2005}
   {:name {:fi "Petäjävesi", :se "Petäjävesi", :en "Petäjävesi"},
    :city-code 592,
    :status :active,
    :valid-until nil}
   {:name {:fi "Pieksämäki", :se "Pieksämäki", :en "Pieksämäki"},
    :city-code 593,
    :status :active,
    :valid-until nil}
   {:name
    {:fi "Pieksämäen mlk", :se "Pieksämäki lk", :en "Pieksämäen mlk"},
    :city-code 594,
    :status :abolished,
    :valid-until 2004}
   {:name {:fi "Pielavesi", :se "Pielavesi", :en "Pielavesi"},
    :city-code 595,
    :status :active,
    :valid-until nil}
   {:name {:fi "Pietarsaari", :se "Jakobstad", :en "Pietarsaari"},
    :city-code 598,
    :status :active,
    :valid-until nil}
   {:name
    {:fi "Pedersören kunta", :se "Pedersöre", :en "Pedersören kunta"},
    :city-code 599,
    :status :active,
    :valid-until nil}
   {:name {:fi "Pihtipudas", :se "Pihtipudas", :en "Pihtipudas"},
    :city-code 601,
    :status :active,
    :valid-until nil}
   {:name {:fi "Piikkiö", :se "Pikis", :en "Piikkiö"},
    :city-code 602,
    :status :abolished,
    :valid-until 2009}
   {:name {:fi "Piippola", :se "Piippola", :en "Piippola"},
    :city-code 603,
    :status :abolished,
    :valid-until 2009}
   {:name {:fi "Pirkkala", :se "Birkala", :en "Pirkkala"},
    :city-code 604,
    :status :active,
    :valid-until nil}
   {:name {:fi "Pohja", :se "Pojo", :en "Pohja"},
    :city-code 606,
    :status :abolished,
    :valid-until 2009}
   {:name {:fi "Polvijärvi", :se "Polvijärvi", :en "Polvijärvi"},
    :city-code 607,
    :status :active,
    :valid-until nil}
   {:name {:fi "Pomarkku", :se "Påmark", :en "Pomarkku"},
    :city-code 608,
    :status :active,
    :valid-until nil}
   {:name {:fi "Pori", :se "Björneborg", :en "Pori"},
    :city-code 609,
    :status :active,
    :valid-until nil}
   {:name {:fi "Pornainen", :se "Borgnäs", :en "Pornainen"},
    :city-code 611,
    :status :active,
    :valid-until nil}
   {:name {:fi "Posio", :se "Posio", :en "Posio"},
    :city-code 614,
    :status :active,
    :valid-until nil}
   {:name {:fi "Pudasjärvi", :se "Pudasjärvi", :en "Pudasjärvi"},
    :city-code 615,
    :status :active,
    :valid-until nil}
   {:name {:fi "Pukkila", :se "Pukkila", :en "Pukkila"},
    :city-code 616,
    :status :active,
    :valid-until nil}
   {:name {:fi "Pulkkila", :se "Pulkkila", :en "Pulkkila"},
    :city-code 617,
    :status :abolished,
    :valid-until 2009}
   {:name {:fi "Punkaharju", :se "Punkaharju", :en "Punkaharju"},
    :city-code 618,
    :status :abolished,
    :valid-until 2013}
   {:name {:fi "Punkalaidun", :se "Punkalaidun", :en "Punkalaidun"},
    :city-code 619,
    :status :active,
    :valid-until nil}
   {:name {:fi "Puolanka", :se "Puolanka", :en "Puolanka"},
    :city-code 620,
    :status :active,
    :valid-until nil}
   {:name {:fi "Puumala", :se "Puumala", :en "Puumala"},
    :city-code 623,
    :status :active,
    :valid-until nil}
   {:name {:fi "Pyhtää", :se "Pyttis", :en "Pyhtää"},
    :city-code 624,
    :status :active,
    :valid-until nil}
   {:name {:fi "Pyhäjoki", :se "Pyhäjoki", :en "Pyhäjoki"},
    :city-code 625,
    :status :active,
    :valid-until nil}
   {:name {:fi "Pyhäjärvi", :se "Pyhäjärvi", :en "Pyhäjärvi"},
    :city-code 626,
    :status :active,
    :valid-until nil}
   {:name {:fi "Pyhäntä", :se "Pyhäntä", :en "Pyhäntä"},
    :city-code 630,
    :status :active,
    :valid-until nil}
   {:name {:fi "Pyhäranta", :se "Pyhäranta", :en "Pyhäranta"},
    :city-code 631,
    :status :active,
    :valid-until nil}
   {:name {:fi "Pyhäselkä", :se "Pyhäselkä", :en "Pyhäselkä"},
    :city-code 632,
    :status :abolished,
    :valid-until 2009}
   {:name {:fi "Pylkönmäki", :se "Pylkönmäki", :en "Pylkönmäki"},
    :city-code 633,
    :status :abolished,
    :valid-until 2009}
   {:name {:fi "Pälkäne", :se "Pälkäne", :en "Pälkäne"},
    :city-code 635,
    :status :active,
    :valid-until nil}
   {:name {:fi "Pöytyä", :se "Pöytyä", :en "Pöytyä"},
    :city-code 636,
    :status :active,
    :valid-until nil}
   {:name {:fi "Porvoo", :se "Borgå", :en "Porvoo"},
    :city-code 638,
    :status :active,
    :valid-until nil}
   {:name {:fi "Pieksänmaa", :se "Pieksänmaa", :en "Pieksänmaa"},
    :city-code 640,
    :status :abolished,
    :valid-until 2007}
   {:name {:fi "Raahe", :se "Brahestad", :en "Raahe"},
    :city-code 678,
    :status :active,
    :valid-until nil}
   {:name {:fi "Raisio", :se "Reso", :en "Raisio"},
    :city-code 680,
    :status :active,
    :valid-until nil}
   {:name {:fi "Rantasalmi", :se "Rantasalmi", :en "Rantasalmi"},
    :city-code 681,
    :status :active,
    :valid-until nil}
   {:name {:fi "Rantsila", :se "Rantsila", :en "Rantsila"},
    :city-code 682,
    :status :abolished,
    :valid-until 2009}
   {:name {:fi "Ranua", :se "Ranua", :en "Ranua"},
    :city-code 683,
    :status :active,
    :valid-until nil}
   {:name {:fi "Rauma", :se "Raumo", :en "Rauma"},
    :city-code 684,
    :status :active,
    :valid-until nil}
   {:name {:fi "Rautalampi", :se "Rautalampi", :en "Rautalampi"},
    :city-code 686,
    :status :active,
    :valid-until nil}
   {:name {:fi "Rautavaara", :se "Rautavaara", :en "Rautavaara"},
    :city-code 687,
    :status :active,
    :valid-until nil}
   {:name {:fi "Rautjärvi", :se "Rautjärvi", :en "Rautjärvi"},
    :city-code 689,
    :status :active,
    :valid-until nil}
   {:name {:fi "Reisjärvi", :se "Reisjärvi", :en "Reisjärvi"},
    :city-code 691,
    :status :active,
    :valid-until nil}
   {:name {:fi "Renko", :se "Rengo", :en "Renko"},
    :city-code 692,
    :status :abolished,
    :valid-until 2009}
   {:name {:fi "Riihimäki", :se "Riihimäki", :en "Riihimäki"},
    :city-code 694,
    :status :active,
    :valid-until nil}
   {:name {:fi "Ristiina", :se "Ristiina", :en "Ristiina"},
    :city-code 696,
    :status :abolished,
    :valid-until 2013}
   {:name {:fi "Ristijärvi", :se "Ristijärvi", :en "Ristijärvi"},
    :city-code 697,
    :status :active,
    :valid-until nil}
   {:name {:fi "Rovaniemi", :se "Rovaniemi", :en "Rovaniemi"},
    :city-code 698,
    :status :active,
    :valid-until nil}
   {:name
    {:fi "Rovaniemen mlk", :se "Rovaniemi lk", :en "Rovaniemen mlk"},
    :city-code 699,
    :status :abolished,
    :valid-until 2006}
   {:name {:fi "Ruokolahti", :se "Ruokolahti", :en "Ruokolahti"},
    :city-code 700,
    :status :active,
    :valid-until nil}
   {:name {:fi "Ruotsinpyhtää", :se "Strömfors", :en "Ruotsinpyhtää"},
    :city-code 701,
    :status :abolished,
    :valid-until 2010}
   {:name {:fi "Ruovesi", :se "Ruovesi", :en "Ruovesi"},
    :city-code 702,
    :status :active,
    :valid-until nil}
   {:name {:fi "Rusko", :se "Rusko", :en "Rusko"},
    :city-code 704,
    :status :active,
    :valid-until nil}
   {:name {:fi "Rymättylä", :se "Rimito", :en "Rymättylä"},
    :city-code 705,
    :status :abolished,
    :valid-until 2009}
   {:name {:fi "Rääkkylä", :se "Rääkkylä", :en "Rääkkylä"},
    :city-code 707,
    :status :active,
    :valid-until nil}
   {:name {:fi "Ruukki", :se "Ruukki", :en "Ruukki"},
    :city-code 708,
    :status :abolished,
    :valid-until 2007}
   {:name {:fi "Raasepori", :se "Raseborg", :en "Raasepori"},
    :city-code 710,
    :status :active,
    :valid-until nil}
   {:name {:fi "Saari", :se "Saari", :en "Saari"},
    :city-code 728,
    :status :abolished,
    :valid-until 2005}
   {:name {:fi "Saarijärvi", :se "Saarijärvi", :en "Saarijärvi"},
    :city-code 729,
    :status :active,
    :valid-until nil}
   {:name {:fi "Sahalahti", :se "Sahalahti", :en "Sahalahti"},
    :city-code 730,
    :status :abolished,
    :valid-until 2005}
   {:name {:fi "Salla", :se "Salla", :en "Salla"},
    :city-code 732,
    :status :active,
    :valid-until nil}
   {:name {:fi "Salo", :se "Salo", :en "Salo"},
    :city-code 734,
    :status :active,
    :valid-until nil}
   {:name {:fi "Saltvik", :se "Saltvik", :en "Saltvik"},
    :city-code 736,
    :status :active,
    :valid-until nil}
   {:name {:fi "Sammatti", :se "Sammatti", :en "Sammatti"},
    :city-code 737,
    :status :abolished,
    :valid-until 2009}
   {:name {:fi "Sauvo", :se "Sagu", :en "Sauvo"},
    :city-code 738,
    :status :active,
    :valid-until nil}
   {:name {:fi "Savitaipale", :se "Savitaipale", :en "Savitaipale"},
    :city-code 739,
    :status :active,
    :valid-until nil}
   {:name {:fi "Savonlinna", :se "Nyslott", :en "Savonlinna"},
    :city-code 740,
    :status :active,
    :valid-until nil}
   {:name {:fi "Savonranta", :se "Savonranta", :en "Savonranta"},
    :city-code 741,
    :status :abolished,
    :valid-until 2009}
   {:name {:fi "Savukoski", :se "Savukoski", :en "Savukoski"},
    :city-code 742,
    :status :active,
    :valid-until nil}
   {:name {:fi "Seinäjoki", :se "Seinäjoki", :en "Seinäjoki"},
    :city-code 743,
    :status :active,
    :valid-until nil}
   {:name {:fi "Sievi", :se "Sievi", :en "Sievi"},
    :city-code 746,
    :status :active,
    :valid-until nil}
   {:name {:fi "Siikainen", :se "Siikainen", :en "Siikainen"},
    :city-code 747,
    :status :active,
    :valid-until nil}
   {:name {:fi "Siikajoki", :se "Siikajoki", :en "Siikajoki"},
    :city-code 748,
    :status :active,
    :valid-until nil}
   {:name {:fi "Siilinjärvi", :se "Siilinjärvi", :en "Siilinjärvi"},
    :city-code 749,
    :status :active,
    :valid-until nil}
   {:name {:fi "Simo", :se "Simo", :en "Simo"},
    :city-code 751,
    :status :active,
    :valid-until nil}
   {:name {:fi "Sipoo", :se "Sibbo", :en "Sipoo"},
    :city-code 753,
    :status :active,
    :valid-until nil}
   {:name {:fi "Anjalankoski", :se "Anjalankoski", :en "Anjalankoski"},
    :city-code 754,
    :status :abolished,
    :valid-until 2009}
   {:name {:fi "Siuntio", :se "Sjundeå", :en "Siuntio"},
    :city-code 755,
    :status :active,
    :valid-until nil}
   {:name {:fi "Sodankylä", :se "Sodankylä", :en "Sodankylä"},
    :city-code 758,
    :status :active,
    :valid-until nil}
   {:name {:fi "Soini", :se "Soini", :en "Soini"},
    :city-code 759,
    :status :active,
    :valid-until nil}
   {:name {:fi "Somero", :se "Somero", :en "Somero"},
    :city-code 761,
    :status :active,
    :valid-until nil}
   {:name {:fi "Sonkajärvi", :se "Sonkajärvi", :en "Sonkajärvi"},
    :city-code 762,
    :status :active,
    :valid-until nil}
   {:name {:fi "Sotkamo", :se "Sotkamo", :en "Sotkamo"},
    :city-code 765,
    :status :active,
    :valid-until nil}
   {:name {:fi "Sottunga", :se "Sottunga", :en "Sottunga"},
    :city-code 766,
    :status :active,
    :valid-until nil}
   {:name {:fi "Sulkava", :se "Sulkava", :en "Sulkava"},
    :city-code 768,
    :status :active,
    :valid-until nil}
   {:name {:fi "Sumiainen", :se "Sumiainen", :en "Sumiainen"},
    :city-code 770,
    :status :abolished,
    :valid-until 2007}
   {:name {:fi "Sund", :se "Sund", :en "Sund"},
    :city-code 771,
    :status :active,
    :valid-until nil}
   {:name {:fi "Suodenniemi", :se "Suodenniemi", :en "Suodenniemi"},
    :city-code 772,
    :status :abolished,
    :valid-until 2007}
   {:name {:fi "Suolahti", :se "Suolahti", :en "Suolahti"},
    :city-code 774,
    :status :abolished,
    :valid-until 2007}
   {:name {:fi "Suomenniemi", :se "Suomenniemi", :en "Suomenniemi"},
    :city-code 775,
    :status :abolished,
    :valid-until 2013}
   {:name {:fi "Suomusjärvi", :se "Suomusjärvi", :en "Suomusjärvi"},
    :city-code 776,
    :status :abolished,
    :valid-until 2009}
   {:name {:fi "Suomussalmi", :se "Suomussalmi", :en "Suomussalmi"},
    :city-code 777,
    :status :active,
    :valid-until nil}
   {:name {:fi "Suonenjoki", :se "Suonenjoki", :en "Suonenjoki"},
    :city-code 778,
    :status :active,
    :valid-until nil}
   {:name {:fi "Sysmä", :se "Sysmä", :en "Sysmä"},
    :city-code 781,
    :status :active,
    :valid-until nil}
   {:name {:fi "Säkylä", :se "Säkylä", :en "Säkylä"},
    :city-code 783,
    :status :active,
    :valid-until nil}
   {:name {:fi "Särkisalo", :se "Finby", :en "Särkisalo"},
    :city-code 784,
    :status :abolished,
    :valid-until 2009}
   {:name {:fi "Vaala", :se "Vaala", :en "Vaala"},
    :city-code 785,
    :status :active,
    :valid-until nil}
   {:name {:fi "Sastamala", :se "Sastamala", :en "Sastamala"},
    :city-code 790,
    :status :active,
    :valid-until nil}
   {:name {:fi "Siikalatva", :se "Siikalatva", :en "Siikalatva"},
    :city-code 791,
    :status :active,
    :valid-until nil}
   {:name {:fi "Taipalsaari", :se "Taipalsaari", :en "Taipalsaari"},
    :city-code 831,
    :status :active,
    :valid-until nil}
   {:name {:fi "Taivalkoski", :se "Taivalkoski", :en "Taivalkoski"},
    :city-code 832,
    :status :active,
    :valid-until nil}
   {:name {:fi "Taivassalo", :se "Tövsala", :en "Taivassalo"},
    :city-code 833,
    :status :active,
    :valid-until nil}
   {:name {:fi "Tammela", :se "Tammela", :en "Tammela"},
    :city-code 834,
    :status :active,
    :valid-until nil}
   {:name {:fi "Tammisaari", :se "Ekenäs", :en "Tammisaari"},
    :city-code 835,
    :status :abolished,
    :valid-until 2009}
   {:name {:fi "Tampere", :se "Tammerfors", :en "Tampere"},
    :city-code 837,
    :status :active,
    :valid-until nil}
   {:name {:fi "Tarvasjoki", :se "Tarvasjoki", :en "Tarvasjoki"},
    :city-code 838,
    :status :abolished,
    :valid-until 2014}
   {:name {:fi "Temmes", :se "Temmes", :en "Temmes"},
    :city-code 841,
    :status :abolished,
    :valid-until 2001}
   {:name {:fi "Tervo", :se "Tervo", :en "Tervo"},
    :city-code 844,
    :status :active,
    :valid-until nil}
   {:name {:fi "Tervola", :se "Tervola", :en "Tervola"},
    :city-code 845,
    :status :active,
    :valid-until nil}
   {:name {:fi "Teuva", :se "Östermark", :en "Teuva"},
    :city-code 846,
    :status :active,
    :valid-until nil}
   {:name {:fi "Tohmajärvi", :se "Tohmajärvi", :en "Tohmajärvi"},
    :city-code 848,
    :status :active,
    :valid-until nil}
   {:name {:fi "Toholampi", :se "Toholampi", :en "Toholampi"},
    :city-code 849,
    :status :active,
    :valid-until nil}
   {:name {:fi "Toivakka", :se "Toivakka", :en "Toivakka"},
    :city-code 850,
    :status :active,
    :valid-until nil}
   {:name {:fi "Tornio", :se "Torneå", :en "Tornio"},
    :city-code 851,
    :status :active,
    :valid-until nil}
   {:name {:fi "Turku", :se "Åbo", :en "Turku"},
    :city-code 853,
    :status :active,
    :valid-until nil}
   {:name {:fi "Pello", :se "Pello", :en "Pello"},
    :city-code 854,
    :status :active,
    :valid-until nil}
   {:name {:fi "Tuulos", :se "Tulois", :en "Tuulos"},
    :city-code 855,
    :status :abolished,
    :valid-until 2009}
   {:name {:fi "Tuupovaara", :se "Tuupovaara", :en "Tuupovaara"},
    :city-code 856,
    :status :abolished,
    :valid-until 2005}
   {:name {:fi "Tuusniemi", :se "Tuusniemi", :en "Tuusniemi"},
    :city-code 857,
    :status :active,
    :valid-until nil}
   {:name {:fi "Tuusula", :se "Tusby", :en "Tuusula"},
    :city-code 858,
    :status :active,
    :valid-until nil}
   {:name {:fi "Tyrnävä", :se "Tyrnävä", :en "Tyrnävä"},
    :city-code 859,
    :status :active,
    :valid-until nil}
   {:name {:fi "Töysä", :se "Töysä", :en "Töysä"},
    :city-code 863,
    :status :abolished,
    :valid-until 2013}
   {:name {:fi "Toijala", :se "Toijala", :en "Toijala"},
    :city-code 864,
    :status :abolished,
    :valid-until 2007}
   {:name {:fi "Ullava", :se "Ullava", :en "Ullava"},
    :city-code 885,
    :status :abolished,
    :valid-until 2009}
   {:name {:fi "Ulvila", :se "Ulvsby", :en "Ulvila"},
    :city-code 886,
    :status :active,
    :valid-until nil}
   {:name {:fi "Urjala", :se "Urjala", :en "Urjala"},
    :city-code 887,
    :status :active,
    :valid-until nil}
   {:name {:fi "Utajärvi", :se "Utajärvi", :en "Utajärvi"},
    :city-code 889,
    :status :active,
    :valid-until nil}
   {:name {:fi "Utsjoki", :se "Utsjoki", :en "Utsjoki"},
    :city-code 890,
    :status :active,
    :valid-until nil}
   {:name {:fi "Uukuniemi", :se "Uukuniemi", :en "Uukuniemi"},
    :city-code 891,
    :status :abolished,
    :valid-until 2005}
   {:name {:fi "Uurainen", :se "Uurainen", :en "Uurainen"},
    :city-code 892,
    :status :active,
    :valid-until nil}
   {:name {:fi "Uusikaarlepyy", :se "Nykarleby", :en "Uusikaarlepyy"},
    :city-code 893,
    :status :active,
    :valid-until nil}
   {:name {:fi "Uusikaupunki", :se "Nystad", :en "Uusikaupunki"},
    :city-code 895,
    :status :active,
    :valid-until nil}
   {:name {:fi "Vaasa", :se "Vasa", :en "Vaasa"},
    :city-code 905,
    :status :active,
    :valid-until nil}
   {:name {:fi "Vahto", :se "Vahto", :en "Vahto"},
    :city-code 906,
    :status :abolished,
    :valid-until 2009}
   {:name {:fi "Valkeakoski", :se "Valkeakoski", :en "Valkeakoski"},
    :city-code 908,
    :status :active,
    :valid-until nil}
   {:name {:fi "Valkeala", :se "Valkeala", :en "Valkeala"},
    :city-code 909,
    :status :abolished,
    :valid-until 2009}
   {:name {:fi "Valtimo", :se "Valtimo", :en "Valtimo"},
    :city-code 911,
    :status :active,
    :valid-until nil}
   {:name {:fi "Vammala", :se "Vammala", :en "Vammala"},
    :city-code 912,
    :status :abolished,
    :valid-until 2009}
   {:name {:fi "Vampula", :se "Vampula", :en "Vampula"},
    :city-code 913,
    :status :abolished,
    :valid-until 2009}
   {:name {:fi "Varkaus", :se "Varkaus", :en "Varkaus"},
    :city-code 915,
    :status :active,
    :valid-until nil}
   {:name {:fi "Varpaisjärvi", :se "Varpaisjärvi", :en "Varpaisjärvi"},
    :city-code 916,
    :status :abolished,
    :valid-until 2011}
   {:name {:fi "Vehkalahti", :se "Veckelax", :en "Vehkalahti"},
    :city-code 917,
    :status :abolished,
    :valid-until 2003}
   {:name {:fi "Vehmaa", :se "Vehmaa", :en "Vehmaa"},
    :city-code 918,
    :status :active,
    :valid-until nil}
   {:name {:fi "Vehmersalmi", :se "Vehmersalmi", :en "Vehmersalmi"},
    :city-code 919,
    :status :abolished,
    :valid-until 2005}
   {:name {:fi "Velkua", :se "Velkua", :en "Velkua"},
    :city-code 920,
    :status :abolished,
    :valid-until 2009}
   {:name {:fi "Vesanto", :se "Vesanto", :en "Vesanto"},
    :city-code 921,
    :status :active,
    :valid-until nil}
   {:name {:fi "Vesilahti", :se "Vesilahti", :en "Vesilahti"},
    :city-code 922,
    :status :active,
    :valid-until nil}
   {:name {:fi "Västanfjärd", :se "Västanfjärd", :en "Västanfjärd"},
    :city-code 923,
    :status :abolished,
    :valid-until 2009}
   {:name {:fi "Veteli", :se "Vetil", :en "Veteli"},
    :city-code 924,
    :status :active,
    :valid-until nil}
   {:name {:fi "Vieremä", :se "Vieremä", :en "Vieremä"},
    :city-code 925,
    :status :active,
    :valid-until nil}
   {:name {:fi "Vihanti", :se "Vihanti", :en "Vihanti"},
    :city-code 926,
    :status :abolished,
    :valid-until 2013}
   {:name {:fi "Vihti", :se "Vichtis", :en "Vihti"},
    :city-code 927,
    :status :active,
    :valid-until nil}
   {:name {:fi "Viiala", :se "Viiala", :en "Viiala"},
    :city-code 928,
    :status :abolished,
    :valid-until 2007}
   {:name {:fi "Viitasaari", :se "Viitasaari", :en "Viitasaari"},
    :city-code 931,
    :status :active,
    :valid-until nil}
   {:name {:fi "Viljakkala", :se "Viljakkala", :en "Viljakkala"},
    :city-code 932,
    :status :abolished,
    :valid-until 2007}
   {:name {:fi "Vilppula", :se "Filpula", :en "Vilppula"},
    :city-code 933,
    :status :abolished,
    :valid-until 2009}
   {:name {:fi "Vimpeli", :se "Vimpeli", :en "Vimpeli"},
    :city-code 934,
    :status :active,
    :valid-until nil}
   {:name {:fi "Virolahti", :se "Virolahti", :en "Virolahti"},
    :city-code 935,
    :status :active,
    :valid-until nil}
   {:name {:fi "Virrat", :se "Virdois", :en "Virrat"},
    :city-code 936,
    :status :active,
    :valid-until nil}
   {:name {:fi "Virtasalmi", :se "Virtasalmi", :en "Virtasalmi"},
    :city-code 937,
    :status :abolished,
    :valid-until 2004}
   {:name {:fi "Vuolijoki", :se "Vuolijoki", :en "Vuolijoki"},
    :city-code 940,
    :status :abolished,
    :valid-until 2007}
   {:name {:fi "Vårdö", :se "Vårdö", :en "Vårdö"},
    :city-code 941,
    :status :active,
    :valid-until nil}
   {:name {:fi "Vähäkyrö", :se "Lillkyro", :en "Vähäkyrö"},
    :city-code 942,
    :status :abolished,
    :valid-until 2013}
   {:name {:fi "Värtsilä", :se "Värtsilä", :en "Värtsilä"},
    :city-code 943,
    :status :abolished,
    :valid-until 2005}
   {:name {:fi "Vöyri (-2006)", :se "Vörå (-2006)", :en "Vöyri (-2006)"},
    :city-code 944,
    :status :abolished,
    :valid-until 2007}
   {:name {:fi "Vöyri-Maksamaa", :se "Vörå-Maxmo", :en "Vöyri-Maksamaa"},
    :city-code 945,
    :status :abolished,
    :valid-until 2011}
   {:name {:fi "Vöyri", :se "Vörå", :en "Vöyri"},
    :city-code 946,
    :status :active,
    :valid-until nil}
   {:name {:fi "Ylihärmä", :se "Ylihärmä", :en "Ylihärmä"},
    :city-code 971,
    :status :abolished,
    :valid-until 2009}
   {:name {:fi "Yli-Ii", :se "Yli-Ii", :en "Yli-Ii"},
    :city-code 972,
    :status :abolished,
    :valid-until 2013}
   {:name {:fi "Ylikiiminki", :se "Överkiminge", :en "Ylikiiminki"},
    :city-code 973,
    :status :abolished,
    :valid-until 2009}
   {:name {:fi "Ylistaro", :se "Ylistaro", :en "Ylistaro"},
    :city-code 975,
    :status :abolished,
    :valid-until 2009}
   {:name {:fi "Ylitornio", :se "Övertorneå", :en "Ylitornio"},
    :city-code 976,
    :status :active,
    :valid-until nil}
   {:name {:fi "Ylivieska", :se "Ylivieska", :en "Ylivieska"},
    :city-code 977,
    :status :active,
    :valid-until nil}
   {:name {:fi "Ylämaa", :se "Ylämaa", :en "Ylämaa"},
    :city-code 978,
    :status :abolished,
    :valid-until 2010}
   {:name {:fi "Yläne", :se "Yläne", :en "Yläne"},
    :city-code 979,
    :status :abolished,
    :valid-until 2009}
   {:name {:fi "Ylöjärvi", :se "Ylöjärvi", :en "Ylöjärvi"},
    :city-code 980,
    :status :active,
    :valid-until nil}
   {:name {:fi "Ypäjä", :se "Ypäjä", :en "Ypäjä"},
    :city-code 981,
    :status :active,
    :valid-until nil}
   {:name {:fi "Äetsä", :se "Äetsä", :en "Äetsä"},
    :city-code 988,
    :status :abolished,
    :valid-until 2009}
   {:name {:fi "Ähtäri", :se "Etseri", :en "Ähtäri"},
    :city-code 989,
    :status :active,
    :valid-until nil}
   {:name {:fi "Äänekoski", :se "Äänekoski", :en "Äänekoski"},
    :city-code 992,
    :status :active,
    :valid-until nil}])

(def active (filter #(= (:status %) :active) all))
