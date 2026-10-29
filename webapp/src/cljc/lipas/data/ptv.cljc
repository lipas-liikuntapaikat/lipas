(ns lipas.data.ptv
  (:require [clojure.string :as str]
            [lipas.data.types :as types]
            [lipas.utils :as utils]
            [taoensso.timbre :as log]))

;; Utajärven jäähalli
;; https://api.palvelutietovaranto.suomi.fi/api/v11/ServiceChannel/8604a900-be6b-4f9d-8024-a272e07afba3?showHeader=false
;;

;; PTV:n käyttämä ontologiakoostehelvetti
;; https://finto.fi/koko/fi/page/p11070
;; https://finto.fi/koko/fi/search?clang=fi&q=uimahalli

;; json-patch https://github.com/borgeby/clj-json-pointer

;; org 10
#_(def uta-org-id-test "52e0f6dc-ec1f-48d5-a0a2-7a4d8b657d53")

;; org 8
#_(def uta-org-id-test "92374b0f-7d3c-4017-858e-666ee3ca2761")
#_(def uta-org-id-prod "7b83257d-06ad-4e3b-985d-16a5c9d3fced")

;; TODO: Tulossa 5 kuntaa, muut:
;; (Lumijoki. Pyhäjärvi, Ii, Liminka ja Oulu sekä tietenkin bonuksena Utajärvi).
;; TODO: Kuinka valita näytetäänkö test vai prod organisaatiot?

(def test-organizations
  [{:name "Utajärven kunta"
    :props {;; Testiorganisaatio 6 (Kunta)
            :org-id "3d1759a2-e47a-4947-9a31-cab1c1e2512b"
            :city-codes [889]
            :owners ["city" "city-main-owner"]
            :supported-languages ["fi"]}}
   {:name "Raahen kaupunki"
    :props {;; org 8
            :org-id "92374b0f-7d3c-4017-858e-666ee3ca2761"
            :city-codes [678]
            :owners ["city" "city-main-owner"]
            :supported-languages ["fi"]}}
   {:name "Limingan kunta"
    :props {;; org 9
            :org-id "7fdd7f84-e52a-4c17-a59a-d7c2a3095ed5"
            :city-codes [425]
            :owners ["city" "city-main-owner"]
            :supported-languages ["fi" #_#_"se" "en"]}}])

;; Production PTV orgs come from the database now (org table, :ptv-data
;; column); admins manage them via the org-admin UI. The hardcoded list
;; was a prototyping-era seed: the 20250707124910-ptv-organizations
;; migration already moved it into the DB. Kept as an empty vector so
;; existing code paths (the migration, the `organizations` and
;; `org-id->params` defs below) still compile.
(def prod-organizations [])

(def organizations
  (into prod-organizations test-organizations))

;; For adding default params to some requests from the FE
;; NOTE: This should eventually be replaced with Lipas organizations.
;; TODO: Not sure if e.g. owners and supported-languages should be
;; hardcoded to the same values for everyone?
(def org-id->params
  (reduce (fn [acc x]
            (assoc acc (:org-id (:props x))
                   (:props x)))
          {}
          organizations))

(def fallback-languages
  "Default languages when org config is missing. Finnish only."
  ["fi"])

(defn pick-style-reference
  "Pick a Finnish style reference from sites already in memory. Tiers, best
   to worst fallback:
   1. Same type-code
   2. Same sub-category-id
   3. Any site with Finnish summary + description
   Within a tier, most recently synced wins. Uses in-memory data so
   unpersisted AI-generated drafts qualify.

   `sports-sites` — seq of maps with :lipas-id :type-code :sub-category-id
   :summary {:fi …} :description {:fi …} :last-sync (from
   lipas.data.ptv-review/sports-site->ptv-input shape).

   Returns {:summary string :description string} in Finnish, or nil."
  [sports-sites {:keys [type-code sub-category-id exclude-lipas-id]}]
  (let [candidates (->> sports-sites
                        (remove #(= exclude-lipas-id (:lipas-id %)))
                        (filter (fn [s] (and (seq (get-in s [:summary :fi]))
                                             (seq (get-in s [:description :fi])))))
                        (sort-by :last-sync #(compare %2 %1)))
        tier1 (when type-code
                (first (filter #(= type-code (:type-code %)) candidates)))
        tier2 (when (and sub-category-id (not tier1))
                (first (filter #(= sub-category-id (:sub-category-id %)) candidates)))
        picked (or tier1 tier2 (first candidates))]
    (when picked
      {:summary (get-in picked [:summary :fi])
       :description (get-in picked [:description :fi])})))

(defn covering-orgs
  "The PTV-configured LIPAS orgs whose PTV config covers `site`: the site's
  city-code is among the org's `:city-codes` and, when the org restricts
  `:owners`, the site's owner is among them. `ptv-orgs` are LIPAS org maps
  carrying `:ptv-data`."
  [site ptv-orgs]
  (let [city-code (get-in site [:location :city :city-code])
        owner (:owner site)]
    (filterv (fn [{:keys [ptv-data]}]
               (and (:org-id ptv-data)
                    (contains? (set (:city-codes ptv-data)) city-code)
                    (or (empty? (:owners ptv-data))
                        (contains? (set (:owners ptv-data)) owner))))
             ptv-orgs)))

;;; PTV users (admin list) ;;;

(defn- ptv-manager-city-codes
  "City-codes of the `ptv-manager` roles in `roles` (stored wire shape or
  conformed). City-less (global) grants are skipped: they aren't tied to an org."
  [roles]
  (for [r roles
        :when (= :ptv-manager (keyword (:role r)))
        c (:city-code r)]
    (long c)))

(defn ptv-user-rows
  "Accounts holding `ptv-manager` for a municipality, by either route, as rows
  for the admin user list. `users` are admin user maps carrying the stored
  `[:permissions :roles]` plus the read-only `:orgs` / `:org-roles` (see
  `lipas.backend.org/with-org-info`); `ptv-orgs` are LIPAS orgs with
  `:ptv-data`.

  One row per (account, covering PTV org), or per (account, city) when no PTV
  org covers the city:
  - `:direct?`  a direct city-scoped role on the account (the legacy way)
  - `:via-org?` granted by an org's role template
  - `:org`      the PTV org covering the cities: the one the account is in,
                else the only one (nil when none, or several and no membership:
                `:ambiguous-org?`), and
    `:member?`  whether the account already belongs to it
  - `:orgs`     every org the account belongs to
  Inactive accounts are left out."
  [users ptv-orgs]
  (let [city->orgs (reduce (fn [acc o]
                             (reduce #(update %1 (long %2) (fnil conj []) o)
                                     acc
                                     (get-in o [:ptv-data :city-codes])))
                           {}
                           (filter #(get-in % [:ptv-data :org-id]) ptv-orgs))]
    (->> users
         (filter #(= "active" (:status %)))
         (mapcat
           (fn [u]
             (let [grants (concat (for [c (ptv-manager-city-codes (get-in u [:permissions :roles]))]
                                    [c :direct])
                                  (for [c (ptv-manager-city-codes (:org-roles u))]
                                    [c :org]))
                   member-ids (set (map (comp str :id) (:orgs u)))
                   ;; The org a city's PTV work belongs to for this account: one
                   ;; it's already in, else the only covering org. Several
                   ;; covering orgs and no membership: nothing to suggest.
                   org-for (fn [c]
                             (let [os (city->orgs c)]
                               (or (some #(when (member-ids (str (:id %))) %) os)
                                   (when (= 1 (count os)) (first os)))))]
               (for [[_ gs] (group-by (fn [[c _]]
                                        (if-let [o (org-for c)]
                                          (str (:id o))
                                          (str "city-" c)))
                                      grants)
                     :let [city-codes (vec (sort (distinct (map first gs))))
                           org (org-for (first city-codes))
                           sources (set (map second gs))]]
                 {:user-id (str (:id u))
                  :username (:username u)
                  :email (:email u)
                  :name (str/trim (str (get-in u [:user-data :firstname]) " "
                                       (get-in u [:user-data :lastname])))
                  :city-codes city-codes
                  :org (when org {:id (str (:id org)) :name (:name org)})
                  :ambiguous-org? (and (nil? org) (< 1 (count (city->orgs (first city-codes)))))
                  :member? (boolean (and org (member-ids (str (:id org)))))
                  :orgs (vec (:orgs u))
                  :direct? (contains? sources :direct)
                  :via-org? (contains? sources :org)}))))
         (sort-by (juxt #(get-in % [:org :name] "~")
                        #(str/lower-case (or (not-empty (:name %)) (:username %) ""))))
         vec)))

(defn resolve-org-id
  "The PTV org-id a site integrates under, or nil when it can't be determined.
   1. The persisted `[:ptv :org-id]` (authoritative once integrated).
   2. Otherwise the single org in `ptv-orgs` whose PTV config covers the site
      (see `covering-orgs`).

   The site decides the org, not the editing user: pass ALL PTV-configured orgs
   (backend) or the orgs the user may act for (frontend, see `get-ptv-orgs`).
   Whether the user may act for it is a separate privilege check."
  [site ptv-orgs]
  (or (get-in site [:ptv :org-id])
      ;; Distinct PTV org-ids, not LIPAS orgs: two LIPAS orgs may share one PTV
      ;; org, which is still unambiguous.
      (let [org-ids (distinct (map #(get-in % [:ptv-data :org-id])
                                   (covering-orgs site ptv-orgs)))]
        (when (= 1 (count org-ids))
          (first org-ids)))))

(def lang->locale
  "PTV language code -> LIPAS locale keyword"
  {"fi" :fi, "sv" :se, "en" :en})

(def lipas-lang->ptv-lang
  "LIPAS language code -> PTV language code"
  {"fi" "fi", "se" "sv", "en" "en"})

(def locale->lang
  "LIPAS locale keyword -> PTV language code"
  {:fi "fi", :se "sv", :en "en"})

(defn resolve-lang-pairs
  "Given a set/seq of LIPAS language codes (\"fi\" \"se\" \"en\"),
   returns seq of [ptv-lang lipas-locale] pairs for iteration."
  [languages]
  (for [lipas-lang languages
        :let [ptv-lang (get lipas-lang->ptv-lang lipas-lang)]
        :when ptv-lang]
    [ptv-lang (keyword lipas-lang)]))

(defn drop-blank-texts
  "`texts` ({locale text}) without the languages whose text is missing or
   blank. A cleared text field is stored as nil, which is no text, not an
   invalid one."
  [texts]
  (into {}
        (remove (fn [[_ v]] (or (not (string? v)) (str/blank? v))))
        texts))

(defn languages-with-texts
  "`org-langs` (LIPAS language codes) minus every language other than Finnish
   that lacks either a :summary or a :description in `texts`, a map of
   {:summary {locale text} :description {locale text} ...}."
  [texts org-langs]
  (filterv (fn [lang]
             (or (= "fi" lang)
                 (let [locale (keyword lang)]
                   (and (not (str/blank? (get-in texts [:summary locale])))
                        (not (str/blank? (get-in texts [:description locale])))))))
           org-langs))

(defn effective-languages
  "The LIPAS language codes a service location is published in: the org's
   `org-langs`, minus every language other than Finnish that lacks either a
   summary or a description on the site. A language is never published
   with the Finnish texts standing in for it — when an org enables English
   before its sites have English texts, those sites stay Finnish-only in PTV
   until the texts are written."
  [site org-langs]
  (languages-with-texts (:ptv site) org-langs))

(def placeholder "TODO: Value missing!")

(def default-langs ["fi"])

;; Per-language character limits enforced by the PTV API. Exceeding these
;; produces a 400 (e.g. "Maximum length of property 'Value' for 'Summary'
;; must be '150'."). Applies to serviceDescriptions (Service) and
;; serviceChannelDescriptions (ServiceLocation). ServiceLocation does not
;; support user-instruction at all.
(def max-description-length 5000)
(def max-summary-length 150)
(def max-user-instruction-length 5000)

;; ...and a minimum one, missing from PTV's API spec ("Value - At least five
;; characters are required for this field."), counted after trimming. It
;; covers every serviceDescriptions entry of a Service (Summary, Description,
;; UserInstruction, ChargeTypeAdditionalInfo, DeadLine, ProcessingTime,
;; ValidityTime, ServiceType), its requirements, and a ServiceLocation's
;; Summary and Description. Names, keywords, addresses and emails have none.
;; PTV's own UI saves shorter texts, so services made there can carry them.
;; Verified in the PTV test env 2026-10-16.
(def min-text-length 5)

(def ^:private edge-whitespace
  ;; The whitespace .NET's String.Trim (PTV's) strips, by char code:
  ;; str/trim keeps a no-break space on the JVM and strips it in JS, so
  ;; neither alone matches PTV, nor do they match each other. (Not a regex:
  ;; the ClojureScript build turns its \u escapes into raw characters, and a
  ;; raw U+2028 breaks the JS regex literal.)
  (into #{}
        (map char)
        (concat [9 10 11 12 13 32 0x85 0xA0 0x1680]
                (range 0x2000 0x200B)
                [0x2028 0x2029 0x202F 0x205F 0x3000])))

(defn- trimmed-length
  "The length of `s` without the leading and trailing edge-whitespace."
  [s]
  (let [ws? (partial contains? edge-whitespace)]
    (->> (seq s) (drop-while ws?) reverse (drop-while ws?) count)))

(defn text-too-short?
  "True when PTV rejects text `v` as too short, counting it as PTV does after
  trimming. A blank text is no text, which is a different problem (or none)."
  [v]
  (and (string? v)
       (let [n (trimmed-length v)]
         (and (pos? n) (< n min-text-length)))))

(defn ->service-source-id
  [org-id sub-category-id]
  (str "lipas-" org-id "-" sub-category-id))

(defn ->adopted-service-source-id
  "Source-id for a PTV service adopted for LIPAS management (freeform, no sub-category mapping)."
  [org-id service-id]
  (str "lipas-" org-id "-ptv-" service-id))

(defn adopted-service-source-id?
  "True if source-id represents an adopted (non-sub-category-mapped) PTV service."
  [source-id]
  (boolean (and source-id (re-find #"lipas-.*-ptv-" source-id))))

(defn ->ptv-service
  [{:keys [org-id city-codes source-id sub-category-id languages _description _summary]
    :or {languages default-langs} :as m}]
  (let [;; Like service locations: a language other than Finnish is published
        ;; only when the service has its own summary and description, so an
        ;; org enabling English doesn't publish half-empty English versions.
        lipas-languages (set (languages-with-texts m languages))
        ;; PTV language codes for the :languages field on the payload
        languages (->> lipas-languages (map lipas-lang->ptv-lang) (remove nil?) set)
        #_#_type (get types/all type-code)
        sub-cat (when sub-category-id (get types/sub-categories sub-category-id))
        main-cat (when-let [mc (:main-category sub-cat)] (get types/main-categories (parse-long mc)))]

    {:sourceId (or source-id
                   (let [ts (str/replace (utils/timestamp) #":" "-")
                         x (str "lipas-" org-id "-" sub-category-id "-" ts)]
                     (log/debugf "Creating new PTV Service source-id %s" x)
                     x))

     #_#_:keywords (let [tags (:tags type)]
                     (for [locale [:fi :se :en]
                           :let [kws (get tags locale)]
                           kw kws
                           :when (some? kw)]
                       {:language (locale->language locale) :value kw}))

     ;; List of ontology term urls (see http://finto.fi/koko/fi/)
     :ontologyTerms (into []
                          (comp cat (distinct))
                          [(-> main-cat :ptv :ontology-urls)
                           (-> sub-cat :ptv :ontology-urls)])

     :serviceClasses (into []
                           (comp (remove nil?) cat (distinct))
                           [(-> sub-cat :ptv :service-classes)
                            (-> main-cat :ptv :service-classes)])

     ;; https://stat.fi/fi/luokitukset/toimiala/
     ;;:industrialClasses []

     ;; List of valid identifiers can be retrieved from the endpoint
     ;; /api/GeneralDescription
     ;; :generalDescriptionId "..."

     ;;:validFrom "date-time when published"
     ;;:validTo "date-time when archived"

     ;; Undocumented??
     ;; :subType nil

     :type "Service" ; Service | PermitOrObligation | ProfessionalQualification

     ;; General description overrides this
     #_#_:serviceChargeType "Chargeable" ; Chargeable | FreeOfCharge

     :fundingType "PubliclyFunded" ;; PubliclyFunded | MarketFunded

     ;; The name edited in LIPAS, else the one PTV has (`:name`, so a name
     ;; changed in PTV is kept like its texts are), else the sub-category's
     :serviceNames (let [custom-name (:service-name m)]
                     (for [[lang locale] (resolve-lang-pairs lipas-languages)]
                       {:type "Name" ; Name | AlternativeName
                        :language lang
                        :value (or (when (and custom-name (= locale :fi))
                                     custom-name)
                                   (not-empty (get-in m [:name locale]))
                                   (get-in sub-cat [:name locale])
                                   "")}))

     ;; List of target group urls
     ;; https://koodistot.suomi.fi/codescheme;registryCode=ptv;schemeCode=ptvkohderyhmat
     ;; General description overrides this
     :targetGroups ["http://uri.suomi.fi/codelist/ptv/ptvkohderyhmat/code/KR1"] ;; Kansalaiset

     ;; Nationwide | NationwideExceptAlandIslands | LimitedType
     :areaType "LimitedType"

     :areas (for [city-code city-codes]
              ;; Type of the area. Possible values are: Municipality,
              ;; Region, BusinessSubRegion, HospitalDistrict or
              ;; WellbeingServiceCounties.
              {:type "Municipality"
               ;; List of area codes related to type. For example if
               ;; type = Municipality, areaCodes-list need to include
               ;; municipality codes like 491 or 091.
               :areaCodes [(utils/zero-left-pad city-code 3)]})

     ;; TODO is this the actual language in which the service is
     ;; provided? Maybe default to just "fi"?
     :languages languages

     ;; Only texts that exist: a missing one used to be sent as the
     ;; placeholder, which PTV then published as the text.
     :serviceDescriptions (for [[k v] {:summary "Summary"
                                       :description "Description"
                                       :user-instruction "UserInstruction"}
                                [lang locale] (resolve-lang-pairs lipas-languages)
                                :let [text (get-in m [k locale])]
                                :when (not (str/blank? text))]
                            {:type v
                             :language lang
                             :value text})

     ;; TODO can this be inferred from owner / admin info reliably or do we
     :serviceProducers [{;; SelfProducedServices | ProcuredServices | Other
                         :provisionType "SelfProducedServices"
                         :organizations [org-id]}]

     :publishingStatus "Published" ; Draft | Published

     ;; Attach with ServiceChannelId
     ;;:serviceChannels []

     :mainResponsibleOrganization org-id}))

(def RE-PREFIX #"^\+[0-9]{1,4}")

(def ^:private multi-phone-split-re
  ;; Recognized separators between multiple phone-numbers in a single field.
  ;; Comma/semicolon/pipe split with any surrounding whitespace; slash only
  ;; with whitespace on both sides because bare `/` is also used as in-number
  ;; formatting like "050/1234567".
  #"\s*[,;|]\s*|\s+/\s+|\s*\r?\n\s*")

(defn- first-of-multi-phone
  "If `s` contains a multi-phone separator, return the first non-blank piece
   that actually contains a digit (e.g. for fields like \"Name Surname, 040
   1234567\" the name piece is skipped). Falls back to the first non-blank
   piece if none have digits, and to s itself if there are no separators."
  [s]
  (let [pieces (->> (str/split s multi-phone-split-re)
                    (map str/trim)
                    (remove str/blank?))]
    (or (->> pieces (filter #(re-find #"\d" %)) first)
        (first pieces)
        s)))

(defn- digits-only [s]
  (str/replace s #"\D" ""))

(defn- normalize-phone-input
  "Trim, drop the conventional `(0)` international-trunk hint, and rewrite
   a leading `00<cc>` international prefix as `+<cc>`."
  [s]
  (-> s
      str/trim
      (str/replace "(0)" "")
      (str/replace #"^00(?=\d{1,4})" "+")))

(defn- parse-single-phone-number
  "Parse a single phone-number string. Returns a parsed map or nil for
   unparseable input. Pre-condition: `n` is a non-blank string."
  [n]
  (let [s (normalize-phone-input n)
        all-digits (digits-only s)]
    (cond
      (str/blank? all-digits) nil

      ;; Finnish service prefixes (0600/0700/0800/0900/116) — only when the
      ;; input doesn't already carry an international "+" prefix.
      ;; https://www.traficom.fi/fi/viestinta/laajakaista-ja-puhelin/mita-ovat-palvelunumerot
      (and (not (str/starts-with? s "+"))
           (re-find #"^(0[6789]00|116)" all-digits))
      {:is-finnish-service-number true
       :number all-digits}

      :else
      (let [prefix (or ;; Special-case +358 because the generic 1-4 digit
                       ;; pattern would greedily eat the leading "5" of e.g.
                       ;; "+358501234567" and produce "+3585".
                     (re-find #"^\+358" s)
                     (re-find RE-PREFIX s)
                     "+358")
            num-digits (-> (if (str/starts-with? s prefix)
                             (subs s (count prefix))
                             s)
                           digits-only
                           ;; Strip any leftover Finnish trunk 0(s).
                           (str/replace #"^0+" ""))]
        (when (re-matches #"\d{1,20}" num-digits)
          {:prefix prefix
           :number num-digits})))))

(defn parse-phone-number
  "Parse a free-form phone number into a PTV-compatible payload. PTV
   requires :number to match `^\\d{1,20}$` (digits only, 1–20 of them).
   Tolerates dashes, dots, slashes, parentheses, multiple spaces, and
   embedded labels (e.g. \"puh.\"). When the field contains multiple
   phone numbers separated by `,`, `;`, `|`, ` / ` (whitespace-bounded)
   or newlines, only the first is kept. Returns nil for blank or
   unparseable input."
  [n]
  (when (and (string? n) (not (str/blank? n)))
    (parse-single-phone-number (first-of-multi-phone n))))

(def ^:private RE-WWW-EXTRACT
  ;; Match the first http/https/ftp URL in a string, up to next whitespace.
  ;; Whitespace is the only delimiter between URLs — internal commas and
  ;; semicolons are kept (they appear legitimately inside query strings and
  ;; URL fragments, e.g. `#filter=r-fullyTranslatedLangus-,r-openState-`).
  #"(?i)(?:https?|ftp)://\S+")

(def ^:private RE-WWW-HOST-SHAPE
  ;; After a scheme, the host must have at least one dot (FQDN) before any
  ;; path. Rejects phone numbers, gibberish, and `https://` with no host.
  #"(?i)^(?:https?|ftp)://[^./\s]+\.[^/\s]")

(def ^:private RE-WWW-USERINFO
  ;; An `@` before the path means an email address got the scheme
  ;; prepended (`tilavaraukset@vihti.fi` → `https://tilavaraukset@vihti.fi`).
  ;; That's not a web page, so it's rejected rather than sent to PTV.
  #"(?i)^(?:https?|ftp)://[^/?#\s]*@")

(defn- fix-www-scheme-typo
  "Repair common scheme typos and adjacent host damage observed in real
  LIPAS data so they pass PTV's URL validator. Applied before scheme
  detection so the corrected form is used downstream.

  Handles, in order:
   - Doubled scheme prefixes (`https://whttps://foo`) → keep the second.
   - Missing colon after scheme (`http//`, `https//`, `http.//`).
   - Single-slash schemes (`https:/foo` where host isn't `/`).
   - Letter-doubling typos (`hhttps://`, `htpps://`, `hpps://`).
   - A space immediately after `www.` (`https://www. foo` → `https://www.foo`).
   - `www,host` typo where the dot was mistyped as a comma."
  [v]
  (-> v
      (str/replace #"(?i)^https?://[^/?#\s]*?(https?://)" "$1")
      (str/replace #"(?i)^(https?)//" "$1://")
      (str/replace #"(?i)^(https?)\.//?" "$1://")
      (str/replace #"(?i)^(https?|ftp):/(?!/)" "$1://")
      (str/replace #"(?i)^hhttps://" "https://")
      (str/replace #"(?i)^hhttp://"  "http://")
      (str/replace #"(?i)^htpps://"  "https://")
      (str/replace #"(?i)^htpp://"   "http://")
      (str/replace #"(?i)^hpps://"   "https://")
      (str/replace #"(?i)^hpp://"    "http://")
      (str/replace #"(?i)(://)www\.\s+" "$1www.")
      (str/replace #"(?i)^www\.\s+" "www.")
      (str/replace #"(?i)^ps://(?!www\.)" "https://")
      (str/replace #"(?i)^ps://www\." "https://www.")
      (str/replace #"(?i)^www,(?=[^,\s])" "www.")))

(defn parse-www
  "Parse a free-form website value into a PTV-compatible URL or nil.
  PTV requires a valid scheme (http/https/ftp), a valid FQDN host, and
  ≤500 chars (`PTVUrlAttribute` + `[MaxLength(500)]`).

  Real LIPAS data includes leading/trailing whitespace, common scheme
  typos, multiple URLs in one field, and pure garbage. This:
  - trims whitespace,
  - repairs known scheme typos (see `fix-www-scheme-typo`),
  - extracts the first URL when several appear separated by whitespace,
  - prepends `https://` to schemeless hosts,
  - validates the resulting URL has a real host and is within length,
  - rejects email addresses (an `@` before the path),
  - returns nil for blank or unparseable input.

  Returning nil triggers `:deleteAllWebPages true` upstream, so a
  malformed `:www` doesn't take down the rest of the PTV sync."
  [v]
  (when (and (string? v) (not (str/blank? v)))
    (let [trimmed (-> v str/trim fix-www-scheme-typo)
          extracted (or (some-> (re-find RE-WWW-EXTRACT trimmed)
                                (str/replace #"[,;.\)]+\z" ""))
                        ;; Schemeless: take the first token before any
                        ;; whitespace, comma, semicolon, pipe or arrow
                        ;; character. Handles `www.a.fi, www.b.fi`,
                        ;; `www.a.fi › breadcrumb`, `www.a.fi/x y.html`.
                        (some->> (str/split trimmed #"[\s,;|›→]+")
                                 first
                                 (str "https://")))]
      (when (and (re-find RE-WWW-HOST-SHAPE extracted)
                 (not (re-find RE-WWW-USERINFO extracted))
                 (<= (count extracted) 500))
        extracted))))

(def web-page-names
  "Name of each web page LIPAS sends to PTV, by LIPAS field and PTV language
  code (\"sv\", not LIPAS' \"se\" — in PTV \"se\" is Northern Sami). DVV
  makes web page names mandatory, and PTV keeps them per language."
  {:www {"fi" "Liikuntapaikan verkko-osoite"
         "sv" "Idrottsplatsens webbadress"
         "en" "Sports facility website"}
   :reservations-link {"fi" "Tilavarausten verkko-osoite"
                       "sv" "Webbplats för lokalbokning"
                       "en" "Facility booking website"}})

(def ^:private ptv-lang-order ["fi" "sv" "en"])

(defn ->ptv-web-pages
  "PTV webPages for the site's `:www` and `:reservations-link`: each page
  once per PTV language in `languages`, named in that language. A page in
  a language the channel doesn't declare makes PTV reject the whole update
  as an incomplete new language version, so `languages` must be exactly
  the payload's `:languages`. A reservations link equal to `:www` is left
  out."
  [languages sports-site]
  (let [www (parse-www (:www sports-site))
        reservations (parse-www (:reservations-link sports-site))
        pages (cond-> []
                www (conj [:www www])
                (and reservations (not= reservations www))
                (conj [:reservations-link reservations]))]
    (vec (for [lang ptv-lang-order
               :when (contains? languages lang)
               [i [k url]] (map-indexed vector pages)]
           {:url url
            :value (get-in web-page-names [k lang])
            :language lang
            :orderNumber (inc i)}))))

;; Mirrors PTV's PTVEmailAddressAttribute (see ptv-releases/.../PTVEmailAddressAttribute.cs).
;; Local part: ASCII letters case-insensitive, digits, the symbol set PTV
;; lists explicitly, plus the Unicode BMP ranges PTV allows
;; ( -퟿, 豈-﷏, ﷰ-￯) so Finnish-looking
;; addresses like `kenttämiehet@naantali.fi` validate. Domain: dot-
;; separated labels of alphanumerics or the same Unicode ranges (PTV's
;; test API accepts `info@itäharjunkuntosali.fi`), with internal hyphens.
;; The previous regex was strict-lowercase RFC 5322 and rejected 223 real
;; LIPAS addresses (capitalized locals like `Vantaa-info@vantaa.fi`,
;; Unicode locals like `kenttämiehet@naantali.fi`, and IDN domains like
;; `info@itäharjunkuntosali.fi`) that PTV itself accepts — verified by
;; dry-running each against the test API on 2026-05-14.
(def RE-EMAIL
  #"(?i)[A-Z0-9!#$%&'*+/=?^_`{|}~\u00A0-\uD7FF\uF900-\uFDCF\uFDF0-\uFFEF.-]+@(?:[A-Z0-9\u00A0-\uD7FF\uF900-\uFDCF\uFDF0-\uFFEF](?:[A-Z0-9\u00A0-\uD7FF\uF900-\uFDCF\uFDF0-\uFFEF-]*[A-Z0-9\u00A0-\uD7FF\uF900-\uFDCF\uFDF0-\uFFEF])?\.)+[A-Z0-9\u00A0-\uD7FF\uF900-\uFDCF\uFDF0-\uFFEF](?:[A-Z0-9\u00A0-\uD7FF\uF900-\uFDCF\uFDF0-\uFFEF-]*[A-Z0-9\u00A0-\uD7FF\uF900-\uFDCF\uFDF0-\uFFEF])?")

(defn parse-email
  "Trim and validate `v` against an email shape PTV accepts. Returns the
   trimmed address or nil. Returning nil triggers `:deleteAllEmails true`
   upstream so a malformed `:email` doesn't fail the rest of the sync."
  [v]
  (when (string? v)
    (let [trimmed (str/trim v)]
      (when (re-matches RE-EMAIL trimmed)
        trimmed))))

(defn parse-postal-code
  "Extract a 5-digit Finnish postal code from `v`, or nil. Trims, then
   pulls the first 5-digit run. Handles the common LIPAS data shapes
   `\"82300 Rääkkylä\"`, `\"Oulu 90670\"` and the plain `\"00100\"` form.
   Returning nil lets the upstream caller decide how to react — without
   a postal code, PTV will reject the whole ServiceLocation payload."
  [v]
  (when (string? v)
    (let [m (re-find #"\b\d{5}\b" (str/trim v))]
      (when m m))))

(def ^:private ptv-street-max-length 100)

(defn parse-address
  "Trim `v` to a PTV-compatible street-address value: blank → nil, ≤100
   chars → as-is, longer → truncate at the rightmost natural break
   (comma, period, semicolon or whitespace) that fits, falling back to
   a hard 100-char cut. `streetAddress.street[].value` has
   `[ListPropertyMaxLength(100, \"Value\")]` so anything longer fails
   PTV with `Maximum length of property 'Value' must be '100'.`"
  [v]
  (when (string? v)
    (let [trimmed (str/trim v)]
      (cond
        (str/blank? trimmed) nil
        (<= (count trimmed) ptv-street-max-length) trimmed
        :else
        (let [head (subs trimmed 0 ptv-street-max-length)
              cut (or (str/last-index-of head ",")
                      (str/last-index-of head ".")
                      (str/last-index-of head ";")
                      (str/last-index-of head " "))]
          (str/trimr (if (and cut (pos? cut))
                       (subs head 0 cut)
                       head)))))))

(defn site-wgs84-point
  "The point a sync sends for `site`, WGS84 [lon lat]: its search-meta
   point when enriched, else the first coordinate of its first geometry,
   which is what enrich derives it from. nil without either."
  [site]
  (let [coords (or (-> site :search-meta :location :wgs84-point)
                   (let [geom (-> site :location :geometries :features first :geometry)]
                     (case (:type geom)
                       "Point" (:coordinates geom)
                       "LineString" (first (:coordinates geom))
                       "Polygon" (ffirst (:coordinates geom))
                       nil)))]
    (when (and (sequential? coords) (number? (first coords)) (number? (second coords)))
      (vec (take 2 coords)))))

(defn ->ptv-service-location
  [org-id
   coord-transform-fn
   now
   {:keys [status ptv lipas-id location] :as sports-site}]
  (let [lipas-languages (effective-languages sports-site (get ptv :languages default-langs))
        ;; PTV language codes for the :languages field on the payload
        languages (->> lipas-languages (map lipas-lang->ptv-lang) (remove nil?) set)
        type (get types/all (get-in sports-site [:type :type-code]))
        _sub-cat (get types/sub-categories (:sub-category type))
        _main-cat (get types/main-categories (:main-category type))

        email (parse-email (:email sports-site))
        web-pages (->ptv-web-pages languages sports-site)
        phone-number (parse-phone-number (:phone-number sports-site))]

    (cond-> {:organizationId (or (:org-id ptv) org-id)
             ;; Keep using existing sourceId for sites that were already initialized in PTV,
             ;; generate a new unique ID (with timestamp) for new sites.
             :sourceId (or (:source-id ptv)
                           (let [ts (str/replace now #":" "-")
                                 x (str "lipas-" (:org-id ptv) "-" lipas-id "-" ts)]
                             (log/debugf "Creating new PTV ServiceLocation source-id %s" x)
                             x))
             ;; Per-language rule (one-way LIPAS→PTV): every declared language
             ;; gets full coverage in serviceChannelNames / displayNameType /
             ;; serviceChannelDescriptions. sv/en are declared only when the
             ;; site has their summary and description (`effective-languages`);
             ;; their names fall back to the Finnish name, which is usually a
             ;; proper noun. A change made to these fields in PTV is
             ;; overwritten unless taken into LIPAS in the sync review.
             :serviceChannelNames (keep identity
                                        (let [fi-name (:name sports-site)
                                              pick (fn [locale]
                                                     (let [v (get-in sports-site [:name-localized locale])]
                                                       (if (str/blank? v) fi-name v)))]
                                          [(when (contains? languages "fi")
                                             {:type "Name" :value fi-name :language "fi"})
                                           (when (contains? languages "sv")
                                             {:type "Name" :value (pick :se) :language "sv"})
                                           (when (contains? languages "en")
                                             {:type "Name" :value (pick :en) :language "en"})
                                           (when (and (contains? languages "fi")
                                                      (not (str/blank? (:marketing-name sports-site))))
                                             {:type "AlternativeName" :value (:marketing-name sports-site) :language "fi"})]))

             :displayNameType (keep identity
                                    [(when (contains? languages "fi") {:type "Name" :language "fi"})
                                     (when (contains? languages "sv") {:type "Name" :language "sv"})
                                     (when (contains? languages "en") {:type "Name" :language "en"})])

             :serviceChannelDescriptions (let [pick (fn [type-k locale]
                                                      (let [v (get-in ptv [type-k locale])]
                                                        (if (str/blank? v)
                                                          (or (get-in ptv [type-k :fi]) placeholder)
                                                          v)))]
                                           (vec (for [[type-k type-v] {:summary "Summary"
                                                                       :description "Description"}
                                                      [lang locale] (resolve-lang-pairs lipas-languages)]
                                                  {:type type-v
                                                   :value (pick type-k locale)
                                                   :language lang})))

             ;; TODO should this be controlled in org or sports-site level?
             :languages languages

             :addresses [{:type "Location" ; Location | Postal
                          :subType "Single" ; | Single | Street | PostOfficeBox | Abroad | Other.
                          :country "FI"
                          :streetAddress
                          (let [[lon lat] (some-> (site-wgs84-point sports-site) coord-transform-fn)
                                street-value (parse-address (:address location))
                                postal-code (parse-postal-code (:postal-code location))]
                            {:municipality (-> location
                                               :city
                                               :city-code
                                               (utils/zero-left-pad 3))
                             :street (for [lang languages]
                                       {:value street-value :language lang})
                             :postalCode postal-code
                             :latitude lat
                             :longitude lon})}]

             :publishingStatus (case status
                                 ("incorrect-data" "out-of-service-permanently") "Deleted"
                                 "Published")
             ; Draft | Published

             ;; Link services by serviceId
             :services (-> sports-site :ptv :service-ids)}

      ;; Sending both empty array and deleteAll* flag worked for email and phoneNumbers,
      ;; but not webPages! deleteAllWebPages only works if :webPages field isn't present at all.

      email (assoc :emails [{:value email
                             :language "fi"}])
      (not email) (assoc :deleteAllEmails true)

      (seq web-pages) (assoc :webPages web-pages)
      (empty? web-pages) (assoc :deleteAllWebPages true)

      phone-number (assoc :phoneNumbers (let [{:keys [number prefix is-finnish-service-number]} phone-number]
                                          [{:number number
                                            :prefixNumber prefix
                                            :isFinnishServiceNumber (boolean is-finnish-service-number)
                                            :language "fi"}]))
      (not phone-number) (assoc :deleteAllPhoneNumbers true))))

(comment

  (def uta-jh
    {:properties {:area-m2 1539, :surface-material []},
     :email "palaute@utajarvi.fi",
     :envelope
     {:insulated-ceiling? true,
      :insulated-exterior? false,
      :low-emissivity-coating? false},
     :phone-number "+358858755700",
     :building
     {:total-volume-m3 17700,
      :seating-capacity 250,
      :total-ice-area-m2 1539,
      :total-surface-area-m2 2457,
      :total-ice-surface-area-m2 1539},
     :ventilation
     {:dryer-type "munters",
      :heat-pump-type "none",
      :dryer-duty-type "automatic",
      :heat-recovery-type "thermal-wheel",
      :heat-recovery-efficiency 75},
     :admin "city-technical-services",
     :www "https://www.utajarvi.fi",
     :name "Utajärven jäähalli",
     :construction-year 1997,
     :type {:type-code 2520, :size-category "small"},
     :lipas-id 89913,
     :renovation-years [2014],
     :conditions
     {:open-months 6,
      :stand-temperature-c 7,
      :ice-average-thickness-mm 40,
      :air-humidity-min 60,
      :air-humidity-max 90,
      :maintenance-water-temperature-c 45,
      :ice-surface-temperature-c -4,
      :weekly-maintenances 12,
      :skating-area-temperature-c 7,
      :daily-open-hours 11,
      :average-water-consumption-l 700},
     :status "active",
     :event-date "2019-04-05T13:54:19.910Z",
     :refrigeration
     {:original? true,
      :refrigerant "R404A",
      :refrigerant-solution "freezium"},
     :location
     {:city {:city-code 889},
      :address "Laitilantie 5",
      :geometries
      {:type "FeatureCollection",
       :features
       [{:type "Feature",
         :geometry
         {:type "Point",
          :coordinates [26.4131256689191 64.7631112249574]}}]},
      :postal-code "91600",
      :postal-office "Utajärvi"},
     :owner "city",
     :hall-id "91600UT1"})

  (-> uta-jh
      :location
      :city
      :city-code
      (utils/zero-left-pad 3))

  (def uta-jh-with-ptv-meta
    (-> uta-jh
        (assoc :ptv {:languages ["fi" "en"]
                     :summary {:fi "Tiivistelmä suomeksi"
                               :se "Jätte tiivistelmä på svenska"
                               :en "English Summary text"}
                     :description {:fi "Kuvaus suomeksi"
                                   :se "Jätte deskription på svenska"
                                   :en "Description in English"}
                     :org-id "11111-aaaaa-bbbbb-cccccc-ddddd"
                     :sync-enabled true
                     :service-integration "manual"
                     :descriptions-integration "lipas-managed"
                     :service-channel-integration "lipas-managed"
                     :service-ids #{"sid-1"}
                     :service-channel-ids #{"ssid-1"}})))

  (->ptv-service-location nil (constantly [123 456]) nil uta-jh-with-ptv-meta))

(defn parse-service-source-id [source-id]
  ;; No source-id for example for non-Lipas services
  (when source-id
    (-> (re-find #"lipas-.*-(\d*)" source-id)
        second
        parse-long)))

(comment
  (parse-service-source-id "lipas-7fdd7f84-e52a-4c17-a59a-d7c2a3095ed5-6100"))

(defn index-services [_services])

(defn resolve-missing-services
  "Infer services (sub-categories) that need to be created in PTV and
  attached to sports-sites. `sports-sites` are flat maps with :service-ids,
  :sub-category-id and :sub-category (the shape of sports-site->ptv-input);
  a site that already has a service-id of any kind is covered."
  [org-id services sports-sites]
  (let [source-ids (->> services
                        vals
                        (keep :sourceId)
                        set)]
    (->> sports-sites
         (filter (fn [site] (empty? (:service-ids site))))
         (map (fn [site] {:source-id (->service-source-id org-id (:sub-category-id site))
                          :sub-category (-> site :sub-category)
                          :sub-category-id (-> site :sub-category-id)}))
         distinct
         (remove (fn [m] (contains? source-ids (:source-id m)))))))

(defn sub-category-id->service [org-id source-id->service sub-category-id]
  (get source-id->service (->service-source-id org-id sub-category-id)))

(defn parse-summary
  "Returns first line-delimited paragraph."
  [s]
  (when (string? s)
    (first (str/split s #"\r?\n"))))

(defn resolve-service-channel-name
  "Sometimes these seem to have the name under undocumented :name
  property and sometimes under documented :serviceChannelNames
  array. Wtf."
  [service-channel]
  (or (:name service-channel)
      (some (fn [m]
              (when (= "fi" (:language m))
                (:value m)))
            (:serviceChannelNames service-channel))))

(defn detect-name-conflict
  [sports-site service-channels]
  (let [s1 (some-> sports-site :name str/trim str/lower-case)
        attached-channels (-> sports-site :ptv :service-channel-ids set)]
    (some (fn [service-channel]
            (let [ssname (resolve-service-channel-name service-channel)
                  s2 (some-> ssname str/trim str/lower-case)]
              (when (and
                      (not (contains? attached-channels (:id service-channel)))
                      (= s1 s2))
                {:service-channel-id (:id service-channel)})))
          service-channels)))

(defn select-service-name [service-names]
  (some (fn [service-name]
          (when (and (= "fi" (:language service-name))
                     (= "Name" (:type service-name)))
            (:value service-name)))
        service-names))

;;; Whose-move audit workflow ;;;
;;
;; Auditors sample sites/services: an item is "in the sample" when an audit
;; record exists (scoping = saving an empty audit, which only stamps
;; :timestamp/:auditor-id). Verdicts are per field and per revision: each
;; verdict carries an :audited-content snapshot. What a later content edit
;; means depends on the verdict (DVV's process, confirmed 2026-09): a fix
;; in response to a changes request completes the municipality's move
;; (:fixed -> Valmiit, accepted as such without a re-review), and editing
;; approved content does not reopen anything either (:stale stays in
;; Valmiit, flagged "Muuttunut" with a diff) — DVV has reviewed the item as a
;; whole once, and re-auditing it is their explicit decision.
;;
;; DVV audits the Finnish text only, so a verdict covers — and is compared
;; on — the :fi entry alone. The snapshot still stores every language, but
;; editing a translation (or a language version missing on the PTV side)
;; neither invalidates an approval nor counts as a fix.

(declare normalize-ws)

(defn- audited-text
  "The part of a localized content map a verdict covers: the Finnish
   text, whitespace-normalized for comparison (PTV collapses whitespace
   on save; see normalize-ws). nil when blank."
  [localized]
  (normalize-ws (:fi localized)))

(defn audit-feedback-missing?
  "True for a change request without any feedback text. A change request
   is a request to the municipality, so it must say what to change; an
   approval needs no words. Enforced on save (schema audit-data) and
   flagged in the audit form."
  [{:keys [status feedback]}]
  (and (= "changes-requested" status)
       (str/blank? feedback)))

(defn audit-field-state
  "State of one audited field in the whose-move audit workflow.

   :pending           — no verdict yet
   :fixed             — changes were requested and the content has been
                        edited since: the municipality has responded, so
                        the field counts as done (per DVV's process a fix
                        is accepted as such, without a re-review)
   :stale             — approved but content changed since: still
                        counts as approved (DVV audited the item once),
                        the auditor sees the change flagged and may
                        re-audit (verdicts without an :audited-content
                        snapshot are grandfathered as unchanged)
   :approved          — approved and unchanged
   :changes-requested — changes requested and unchanged

   Only the Finnish text is compared (see audited-text)."
  [{:keys [status audited-content] :as _field-audit} current-content]
  (let [changed? (and audited-content
                      (not= (audited-text audited-content)
                            (audited-text current-content)))]
    (cond
      (nil? status) :pending
      (and changed? (= "approved" status)) :stale
      changed? :fixed
      (= "approved" status) :approved
      :else :changes-requested)))

(def site-audited-field-keys
  "The :ptv keys the site audit covers (see site-audit-fields). Also the
   keys the UI snapshots as :ptv-persisted so whose-move states can be
   derived from saved content instead of the live edit draft."
  [:summary :description])

(defn site-audit-fields
  "Auditable-fields spec for a sports site (see audit-bucket)."
  [site]
  [{:field :summary :content (get-in site [:ptv :summary]) :required? true}
   {:field :description :content (get-in site [:ptv :description]) :required? true}])

(defn with-persisted-audit-content
  "Site with its audited :ptv fields replaced by the :ptv-persisted
   snapshot (last content loaded from / saved to the backend), when one
   exists. Audit whose-move states must follow persisted content — the
   PTV views mutate the cached :ptv on every keystroke, and deriving the
   state from that draft would mark a field :fixed (or :stale) before
   anything is saved."
  [site]
  (if-let [persisted (:ptv-persisted site)]
    (update site :ptv #(merge (apply dissoc % site-audited-field-keys) persisted))
    site))

(defn service-audit-fields
  "Auditable-fields spec for a PTV Service (UI-shaped map with localized
   :summary/:description/:user-instruction). Toimintaohje is required only
   when the service has one in Finnish, the audited language."
  [service]
  [{:field :summary :content (:summary service) :required? true}
   {:field :description :content (:description service) :required? true}
   {:field :user-instruction :content (:user-instruction service)
    :required? (some? (audited-text (:user-instruction service)))}])

(defn audit-field-states
  "Map of field -> audit-field-state over the required fields."
  [audit fields]
  (into {}
        (comp (filter :required?)
              (map (fn [{:keys [field content]}]
                     [field (audit-field-state (get audit field) content)])))
        fields))

(defn anchor-audit-snapshots
  "Audit `draft` ready to be saved: every verdict the auditor gives in
   this save is anchored to the content it was given on by stamping
   `contents` ({field localized-content}, as currently shown) into
   :audited-content. `persisted` is the audit as last saved.

   A field whose verdict the auditor did not touch keeps its old snapshot
   when that verdict is a change request the municipality has already
   answered (:fixed). Re-anchoring it would turn the fix back into an
   open change request just because another field of the item was saved.
   An untouched :stale approval is re-anchored on purpose — re-auditing
   an item and saving it is how an auditor re-confirms it for the edited
   text."
  [draft persisted contents]
  (reduce-kv
    (fn [m field content]
      (let [verdict #(select-keys (get % field) [:status :feedback])
            untouched-fix? (and (= (verdict m) (verdict persisted))
                                (= :fixed (audit-field-state (get persisted field) content)))]
        (cond-> m
          (and (get-in m [field :status]) (not untouched-fix?))
          (assoc-in [field :audited-content] content))))
    draft
    contents))

(defn audit-bucket
  "Whose-move bucket for an item in the audit sample, or nil when the item
   is not in the sample (no audit record). `fields` as per
   site-audit-fields / service-audit-fields. The buckets map to the DVV
   process tabs: Odottavat / Katselmoidut / Valmiit.

   :waiting-audit — auditor's move: some field has no verdict yet
   :waiting-fixes — municipality's move: changes requested, content unchanged
   :done          — every required field approved or fixed; edits made
                    after an approval (:stale) keep the item here, flagged,
                    until DVV explicitly re-audits it"
  [audit fields]
  (when (:timestamp audit)
    (let [states (vals (audit-field-states audit fields))]
      (cond
        (some #{:pending} states) :waiting-audit
        (some #{:changes-requested} states) :waiting-fixes
        :else :done))))

(defn audit-notification-summary
  "Contents of the katselmointi notification sent to the municipality's
   PTV managers, derived from the current audit sample (so already-fixed
   items drop out by themselves): :action-items — fully audited items
   with an open change request on some field (whichever DVV tab they sit
   on: an approved field edited meanwhile must not hide the request),
   each entry's :ref merged with :fields naming those fields — and
   :approved-count, the number of items approved without change requests
   (edited since or not). Items whose audit is unfinished (some field
   without a verdict) count in neither, and nor do fixed items.
   Entries: {:ref map, :audit map, :fields as per site-audit-fields /
   service-audit-fields}. Used by both the backend email builder and the
   audit view's send-notification button, so the numbers always agree."
  [entries]
  (reduce
    (fn [acc {:keys [ref audit fields]}]
      (let [states (when (:timestamp audit)
                     (audit-field-states audit fields))
            unfinished? (some #{:pending} (vals states))
            open-requests (->> (when-not unfinished? states)
                               (keep (fn [[field state]]
                                       (when (= :changes-requested state)
                                         field)))
                               vec)]
        (cond
          (seq open-requests)
          (update acc :action-items conj (assoc ref :fields open-requests))

          (and (seq states)
               (every? #{:approved :stale} (vals states)))
          (update acc :approved-count inc)

          :else acc)))
    {:action-items [] :approved-count 0}
    entries))

(defn approved-with-feedback?
  "True when every required field was approved (nothing fixed after a
   change request) and the auditor still left a comment on at least one
   of them. The listings give this its own symbol: the text passed, but
   there is a remark worth reading."
  [audit fields]
  (let [states (audit-field-states audit fields)]
    (and (seq states)
         (every? #{:approved :stale} (vals states))
         (boolean (some #(not (str/blank? (get-in audit [% :feedback])))
                        (keys states))))))

(defn audit-display-status
  "Audit indicator for the municipality-facing listings. Unlike
   audit-bucket (whose move is it, DVV's tabs) this answers \"which
   verdict holds for the text as it stands now\":

   :changes-requested      — some field has an open change request, even
                             if an approved field was edited meanwhile
   :approved               — every required field is approved (edited
                             since or not) or fixed in response to a
                             change request: DVV accepts both as such
   :approved-with-feedback — approved, and the auditor left a comment
   :none                   — not in the audit sample, or audit unfinished
                             (some field without a verdict: DVV's work
                             in progress)"
  [audit fields]
  (let [states (when (:timestamp audit)
                 (vals (audit-field-states audit fields)))]
    (cond
      (empty? states) :none
      (some #{:pending} states) :none
      (some #{:changes-requested} states) :changes-requested
      :else (if (approved-with-feedback? audit fields)
              :approved-with-feedback
              :approved))))

(defn determine-audit-status
  "audit-display-status of a sports site row in the manager-facing listing."
  [site]
  (audit-display-status (get-in site [:ptv :audit]) (site-audit-fields site)))

(defn ptv-descriptions->texts
  "Extract :summary, :description, :user-instruction maps from PTV descriptions array.
   Works for both serviceDescriptions and serviceChannelDescriptions."
  [descriptions]
  (reduce (fn [acc {:keys [language value type]}]
            (let [k (case type
                      "Summary" :summary
                      "Description" :description
                      "UserInstruction" :user-instruction
                      nil)
                  locale (lang->locale language)]
              ;; A language LIPAS doesn't model (e.g. Sámi) has no locale.
              (if (and k locale)
                (update acc k assoc locale value)
                acc)))
          {}
          descriptions))

(defn ->service-document
  "Extracts the LIPAS-managed subset of a PTV Service entity (camelCase
   API shape) into the normalized kebab-case document persisted in the
   ptv_service table. Mirrors lipas.backend.ptv.core/lipas-managed-service-fields —
   PTV remains authoritative for everything else (ontologyTerms,
   serviceClasses, targetGroups, ...). Callers add :audit and :last-sync."
  [ptv-org-id service]
  (let [source-id (:sourceId service)]
    (merge
      {:source-id source-id
       :service-id (:id service)
       :ptv-org-id ptv-org-id
       :name (reduce (fn [acc {:keys [language value type]}]
                       (if (= "Name" type)
                         (assoc acc (lang->locale language) value)
                         acc))
                     {}
                     (:serviceNames service))
      ;; PTV language codes ("sv") -> LIPAS locale codes ("se"), unsupported dropped
       :languages (into [] (comp (keep lang->locale) (map name)) (:languages service))
       :publishing-status (:publishingStatus service)
      ;; Derivable only for sub-category-mapped source-ids; adopted ones
      ;; (lipas-<org>-ptv-<uuid>) carry no sub-category.
       :sub-category-id (when-not (adopted-service-source-id? source-id)
                          (parse-service-source-id source-id))}
      (ptv-descriptions->texts (:serviceDescriptions service)))))

;;; Services made in PTV ;;;
;;
;; LIPAS is the master of the services it creates and writes them whole. A
;; service made directly in PTV ("native") is only patched: LIPAS writes its
;; name and the Summary/Description/UserInstruction texts in the languages it
;; models (fi, sv, en), and everything else passes through untouched. Its
;; first patch gives it the sourceId lipas-{org}-ptv-{uuid}.
;;
;; PTV facts these rest on (verified in the PTV test env 2026-10-14):
;; - A service has a language version in every language that has any name or
;;   description entry, and each version then requires a Name, a Summary and
;;   a Description. A leftover ChargeTypeAdditionalInfo in a language alone
;;   makes PTV demand the rest.
;; - The service's :languages is the languages the service is provided in,
;;   not its language versions: PTV accepts a language there with no texts.
;; - A language version requires the organisation to be described in it.
;; - A PUT replaces :serviceNames, :serviceDescriptions and :requirements
;;   whole (requirements even when the body leaves them out), but keeps every
;;   other field it isn't sent: classifications, areas, producers and other
;;   responsible organisations, channel connections, legislation, keywords,
;;   vouchers, the charge type (verified 2026-10-16/17). See
;;   carry-over-service-entries, and core/native-service-put-body for what a
;;   patch sends.
;; - A PUT requires :publishingStatus, and without a :generalDescriptionId
;;   also :targetGroups, :ontologyTerms, :serviceClasses. (A PUT that leaves
;;   the id out is validated as if there were none, but the attached
;;   description stays; only :deleteGeneralDescriptionId, with :type,
;;   detaches it.) PTV checks its target group rules against the target
;;   groups sent, so they always go along (put-classifications).
;; - A service in Draft is invisible to the API: GET 404, left out of the
;;   org listing, yet a PUT by id updates it. Published can't be set back to
;;   Draft by a PUT, nor Draft to Deleted (publish first).
;; - GET and PUT shapes differ: code-list items ({:uri :code ...}) vs URIs,
;;   never mixing :uri and :newUri forms; areas {:type :code :municipalities}
;;   vs {:type :areaCodes}; producers and other responsible organisations
;;   only in GET's :organizations; a general description's classes, ontology
;;   terms and target groups listed as the service's own, yet counted
;;   against its limits on a PUT ("Only 4 items allowed!").
;; - A requirement alone in a language makes it a language version too;
;;   keywords, service vouchers and law names don't (a version can be
;;   removed with them left in its language).
;; - At most one AlternativeName per language; industrial classes need
;;   target group KR2 (or a sub group), life events KR1; KR2 from a general
;;   description needs a KR2 sub group of the service's own.

(defn service-management
  "Who maintains the service with `source-id`:
   :lipas (created by LIPAS, its master), :native (made in PTV; LIPAS may
   patch its texts) or :other-system (another system's sourceId; read-only)."
  [source-id]
  (cond
    (str/blank? source-id) :native
    (adopted-service-source-id? source-id) :native
    (str/starts-with? source-id "lipas-") :lipas
    :else :other-system))

(def patched-description-types #{"Summary" "Description" "UserInstruction"})

(def ^:private version-required-description-types ["Summary" "Description"])

(defn- blank-text? [v]
  (or (not (string? v)) (str/blank? v)))

(defn service-language-versions
  "PTV language codes `service` has a language version in: every language
   with a name, description or requirement entry with text (a requirement
   alone makes PTV demand the language's summary and description). PTV's
   GET can carry blank per-language slots, which no PUT sends."
  [service]
  (into (sorted-set)
        (comp cat (remove (comp blank-text? :value)) (keep :language))
        [(:serviceNames service) (:serviceDescriptions service) (:requirements service)]))

(defn service-names
  "{locale name} of the service's main (\"Name\") names in the languages
   LIPAS models."
  [service]
  (into {}
        (keep (fn [{:keys [type language value]}]
                (when-let [locale (and (= "Name" type) (lang->locale language))]
                  [locale value])))
        (:serviceNames service)))

(defn patch-service-texts
  "`service` (PTV shape) with LIPAS's `texts` written in, `texts` being
   {:name {locale s} :summary {..} :description {..} :user-instruction {..}}.

   Only the languages LIPAS has some text in are written, and in those only
   the Name entry and the patched description types are replaced (a blank
   UserInstruction removes it). A language LIPAS has nothing in, one it
   doesn't model, every other entry type and every other field stay as they
   are, so a sync never drops a language version implicitly.

   `remove-langs` (PTV codes) are the explicit exception: every name,
   description and requirement entry in them is dropped, PTV-only types
   included, since any entry left behind would keep the version alive in
   PTV."
  [service texts remove-langs]
  (let [remove-langs (set remove-langs)
        by-type {"Name" (:name texts)
                 "Summary" (:summary texts)
                 "Description" (:description texts)
                 "UserInstruction" (:user-instruction texts)}
        written-langs (into #{}
                            (comp (filter (fn [lang]
                                            (let [locale (lang->locale lang)]
                                              (some #(not (blank-text? (get % locale)))
                                                    (vals by-type)))))
                                  (remove remove-langs))
                            (vals locale->lang))
        keep-entry? (fn [owned-types {:keys [type language]}]
                      (not (or (contains? remove-langs language)
                               (and (contains? written-langs language)
                                    (contains? owned-types type)))))
        entries (fn [types]
                  (for [lang (sort written-langs)
                        type types
                        :let [v (get-in by-type [type (lang->locale lang)])]
                        :when (not (blank-text? v))]
                    {:type type :language lang :value v}))]
    (cond-> (-> service
                (update :serviceNames
                        (fn [xs] (into (filterv #(keep-entry? #{"Name"} %) xs)
                                       (entries ["Name"]))))
                (update :serviceDescriptions
                        (fn [xs] (into (filterv #(keep-entry? patched-description-types %) xs)
                                       (entries (sort patched-description-types))))))
      (seq (:requirements service))
      (update :requirements (fn [xs] (filterv #(not (contains? remove-langs (:language %))) xs))))))

(defn language-version-problems
  "{ptv-lang [missing-type ...]} for every language version of `service` that
   PTV would reject for lacking a Name, a Summary or a Description; empty
   when there are none."
  [service]
  (into (sorted-map)
        (keep (fn [lang]
                (let [has? (fn [entries type]
                             (some #(and (= type (:type %))
                                         (= lang (:language %))
                                         (not (blank-text? (:value %))))
                                   entries))
                      missing (cond-> []
                                (not (has? (:serviceNames service) "Name")) (conj "Name")
                                :always (into (remove #(has? (:serviceDescriptions service) %))
                                              version-required-description-types))]
                  (when (seq missing)
                    [lang missing]))))
        (service-language-versions service)))

(defn- localized-list?
  "Localized lists are `[{:value <str> :language <str> ...} ...]`. Used by
   PTV for descriptions, names, requirements, etc."
  [v]
  (and (sequential? v)
       (seq v)
       (every? map? v)
       (every? #(contains? % :value) v)))

(defn strip-blank-localized-entries
  "PTV's PUT endpoint rejects localized list entries with blank :value
   (400 'The Value field is required.'). The GET response can include
   such entries (e.g. :requirements with empty per-language slots), so
   strip them before writing a list back."
  [m]
  (reduce-kv (fn [acc k v]
               (cond-> acc
                 (localized-list? v)
                 (assoc k (filterv #(not (str/blank? (:value %))) v))))
             m
             m))

(defn put-classifications
  "The classifications of `service` (a Service GET) as a PUT that writes
   them back takes them: URIs, the GET's own :uri form, never mixed with
   :newUri (\"The urls do not have the same base url.\").

   The target groups always: PTV requires them without a general
   description, and checks its rules against the ones sent (KR1 for life
   events, KR2 for industrial classes, a KR2 sub group for a KR2 general
   description). With a general description, its id instead of the ontology
   terms and service classes, which the GET lists merged with the
   description's but a PUT counts against the service's own limits
   (\"ServiceClasses: Only 4 items allowed!\")."
  [service]
  (let [uris (fn [k] (mapv :uri (get service k)))]
    (merge {:targetGroups (uris :targetGroups)}
           (if-let [gd (:generalDescriptionId service)]
             {:generalDescriptionId gd}
             {:ontologyTerms (uris :ontologyTerms)
              :serviceClasses (uris :serviceClasses)}))))

(defn native-patch-body
  "The PUT body for patching `texts` into `service`, made in PTV (a GET
   response), removing the language versions `remove-langs` (PTV codes).

   It carries only what LIPAS writes and what PTV needs alongside it, never
   a GET-shaped field written back: a PUT leaves every field it isn't sent
   as it is (areas, organisations and producers, channel connections,
   legislation, vouchers, life events, ...), while GET and PUT shapes differ
   in ways that break a write-back. Verified in the PTV test env
   2026-10-17/18. Sent:
   - names and descriptions, patched: PTV replaces these lists whole
   - requirements, as PTV has them: a PUT without them clears them
   - publishingStatus, as PTV has it: required
   - the classifications PTV needs (put-classifications)
   - LIPAS's sourceId"
  [org-id service-id service texts remove-langs]
  (-> (merge {:sourceId (->adopted-service-source-id org-id service-id)
              :publishingStatus (:publishingStatus service)}
             (select-keys service [:serviceNames :serviceDescriptions :requirements])
             (put-classifications service))
      strip-blank-localized-entries
      (patch-service-texts texts remove-langs)))

(defn short-service-texts
  "The texts of the PTV Service `body` that PTV rejects as too short (see
   min-text-length), as [{:type t :language l}] in body order. A requirement
   has the :type \"Requirement\"."
  [body]
  (into []
        (comp cat
              (filter (comp text-too-short? :value))
              (map #(select-keys % [:type :language])))
        [(:serviceDescriptions body)
         (map #(assoc % :type "Requirement") (:requirements body))]))

(defn carry-over-service-entries
  "`body`, the full PUT body of a service LIPAS created, plus what its live
   version `live` (PTV shape) has that LIPAS never writes: names other than
   the main one (AlternativeName), description types other than Summary,
   Description and UserInstruction, and requirements. A PUT replaces those
   lists whole, so without this every sync would wipe what the org added to
   them in PTV. PTV keeps the fields LIPAS doesn't send at all by itself.

   LIPAS's own entries are never carried over: it is the service's master.
   Neither is anything in a language `body` has no version in, since that
   would keep the version alive in PTV after LIPAS stopped publishing it."
  [body live]
  (let [langs (service-language-versions body)
        carried (fn [owned-types entries ks]
                  (into []
                        (comp (filter (fn [{:keys [type language value]}]
                                        (and (contains? langs language)
                                             (not (contains? owned-types type))
                                             (not (blank-text? value)))))
                              (map #(select-keys % ks)))
                        entries))
        requirements (carried #{} (:requirements live) [:language :value])]
    (cond-> (-> body
                (update :serviceNames
                        #(into (vec %) (carried #{"Name"} (:serviceNames live)
                                                [:type :language :value])))
                (update :serviceDescriptions
                        #(into (vec %) (carried patched-description-types (:serviceDescriptions live)
                                                [:type :language :value]))))
      (seq requirements) (assoc :requirements requirements))))

(def create-only-service-fields
  "Fields LIPAS sets only when it creates a service: defaults it hardcodes
   and has no UI for (self-produced by the org, the org's municipalities,
   publicly funded). Afterwards they're PTV's to maintain: an update leaves
   them out, and PTV keeps what a PUT isn't sent."
  [:serviceProducers :areaType :areas :fundingType])

(defn lipas-service-update-body
  "The PUT body updating a service LIPAS created, from `body`, the full body
   it would be created with (->ptv-service), and `live`, its PTV version, or
   nil when LIPAS can't read it:
   - create-only-service-fields left out
   - the target groups PTV has: like the create-only fields LIPAS only
     defaults them, but PTV requires them in a PUT without a general
     description. LIPAS's default only when `live` is unknown.
   - what PTV has that LIPAS never writes carried over
     (carry-over-service-entries)

   A service in Draft is invisible to LIPAS: PTV's GET answers 404 and its
   org listing leaves it out (verified 2026-10-18), so `live` is nil and an
   update publishes it again. Only the wizard can do that, exporting a
   sub-category whose service LIPAS doesn't see."
  [body live]
  (let [body (apply dissoc body create-only-service-fields)]
    (if live
      (cond-> (carry-over-service-entries body live)
        (seq (:targetGroups live)) (assoc :targetGroups (mapv :uri (:targetGroups live))))
      body)))

(def ^:private text-max-length
  {:summary max-summary-length
   :description max-description-length
   :user-instruction max-user-instruction-length})

(def ^:private ptv-type->text-field
  "The PTV name and description types LIPAS writes, as the panel's text fields."
  {"Name" :name "Summary" :summary "Description" :description "UserInstruction" :user-instruction})

(defn service-text-errors
  "What stops a service's texts from syncing to PTV, as a vector of error
   maps, empty when nothing does. `texts` is {field {locale text}} as the
   panel would send it, `body` the PUT body the sync would send.

   - :missing   a service created in LIPAS lacks its Finnish summary or
                description (the other languages need no texts: one
                without them is simply not published); a service made in
                PTV has a language version without a name, summary or
                description (`version-problems`, see
                language-version-problems)
   - :too-short a text LIPAS writes is under PTV's minimum (:min); found in
                `body`, so a text only the panel holds but the sync won't
                publish isn't one
   - :too-short-in-ptv  likewise for a text the sync sends on as PTV has
                it, which only PTV can fix: {:ptv-type :language :error :min}
                with PTV's type and language code
   - :too-long  a text over PTV's limit (:max)

   The others are {:field :locale :error}."
  [{:keys [management texts version-problems body]}]
  (let [missing (case management
                  :lipas (for [field [:summary :description]
                               :when (blank-text? (get-in texts [field :fi]))]
                           {:field field :locale :fi :error :missing})
                  :native (for [[lang types] version-problems
                                :let [locale (lang->locale lang)]
                                :when locale
                                type types]
                            {:field (ptv-type->text-field type) :locale locale :error :missing})
                  [])
        too-short (for [{:keys [type language]} (short-service-texts body)
                        :let [field (ptv-type->text-field type)
                              locale (lang->locale language)]]
                    (if (and field locale)
                      {:field field :locale locale :error :too-short :min min-text-length}
                      {:ptv-type type :language language :error :too-short-in-ptv
                       :min min-text-length}))
        too-long (for [[field max-length] text-max-length
                       [locale v] (sort-by key (get texts field))
                       :when (and (string? v) (> (count v) max-length))]
                   {:field field :locale locale :error :too-long :max max-length})]
    (vec (concat missing too-short too-long))))

(defn lipas-service-text-errors
  "service-text-errors of a service LIPAS creates or writes whole, `m` being
   the save-ptv-service request for it (texts, :languages, :source-id, ...).
   Checks the body as the backend builds it: ->ptv-service when the service
   is new, the update body (lipas-service-update-body) when `live`, its PTV
   version, exists."
  [m live]
  (service-text-errors {:management :lipas
                        :texts (select-keys m [:summary :description :user-instruction])
                        :body (cond-> (->ptv-service m)
                                live (lipas-service-update-body live))}))

(defn service-versions-to-drop
  "LIPAS locales of the language versions a service created in LIPAS has in
   PTV that its next sync won't publish, and so removes: LIPAS is its master
   and publishes only the org's languages that have both a summary and a
   description (languages-with-texts). `versions` are LIPAS locale keywords."
  [versions texts org-langs]
  (let [published (into #{} (map keyword) (languages-with-texts texts org-langs))]
    (vec (remove published versions))))
(def persisted-ptv-keys
  "The editable :ptv meta keys persisted on a sync. Both the sync path
   (upsert-ptv-service-location!*) and the no-sync meta path
   (save-ptv-integration-definitions / ::save-ptv-meta) select exactly these
   from the incoming payload, so the two stay in lock-step. Lifecycle keys the
   server owns (:source-id, :publishing-status, :previous-type-code) are NOT
   here — the sync path derives them from the PTV response, the meta path
   preserves them from the existing record."
  [:languages
   :summary
   :description
   :user-instruction
   :last-sync
   :org-id
   :sync-enabled
   :service-integration
   :descriptions-integration
   :service-channel-integration
   :service-ids
   :service-channel-ids])

(defn normalize-ws
  "Normalize whitespace for *comparing* PTV/LIPAS free-text values — never for
   storing or pushing them. PTV collapses runs of horizontal whitespace in
   fields on save (e.g. a double space in a Name becomes a single space), which
   would otherwise read as a change immediately after a sync. Collapses runs of
   spaces/tabs to a single space, normalizes line endings and trims, but keeps
   newlines: paragraph structure is meaningful and PTV preserves it. Returns nil
   for nil/blank input (so a missing value and an all-whitespace value compare
   equal)."
  [s]
  (some-> s
          (str/replace #"\r\n?" "\n")    ; CRLF / CR -> LF
          (str/replace #"[^\S\n]+" " ")  ; collapse horizontal whitespace runs, keep \n
          (str/replace #" *\n *" "\n")   ; drop spaces hugging newlines (line-edge ws)
          str/trim
          not-empty))

(defn texts-match?
  "Compare LIPAS-side texts with PTV-side texts. Returns true if all
   non-nil fields match. Whitespace is normalized for comparison
   (see `normalize-ws`). `fields` defaults to all three lipas text fields.
   `locales` limits the comparison to the
   languages that get published (default: all three) — a draft text in a
   language the org doesn't publish never reaches PTV, so it can't match."
  ([lipas-texts ptv-texts]
   (texts-match? lipas-texts ptv-texts [:summary :description :user-instruction]))
  ([lipas-texts ptv-texts fields]
   (texts-match? lipas-texts ptv-texts fields [:fi :se :en]))
  ([lipas-texts ptv-texts fields locales]
   (let [trim normalize-ws]
     (every? (fn [field]
               (let [lipas-vals (get lipas-texts field)
                     ptv-vals (get ptv-texts field)]
                 (every? (fn [lang]
                           (let [l (trim (get lipas-vals lang))
                                 p (trim (get ptv-vals lang))]
                             (or (and (nil? l) (nil? p))
                                 (= l p))))
                         locales)))
             fields))))

(defn channel-service-ids
  "Ids of the services the PTV service location `channel` (GET shape) is
   connected to."
  [channel]
  (into [] (comp (keep (comp :id :service)) (distinct)) (:services channel)))

(defn first-link?
  "Whether syncing a site with `site-ptv` (its :ptv) to the service location
   `channel` would be LIPAS's first sync of it for the site. LIPAS gives a
   location the site's sourceId when it syncs it, so one without it was made
   in PTV, by another system or for another site."
  [site-ptv channel]
  (let [source-id (:sourceId channel)]
    (or (str/blank? source-id)
        (not= source-id (:source-id site-ptv)))))

(defn link-service-ids
  "The services a sync of a site with `site-ptv` (its :ptv) connects the
   service location `channel` to. LIPAS's list is the master of a location
   it has synced: a sync connects what the list has and disconnects the
   rest. On the first sync of a location (first-link?) the services the
   location is already connected to in PTV are added to the list instead,
   so linking a site to a location made in PTV never disconnects them."
  [site-ptv channel]
  (let [ids (vec (:service-ids site-ptv))]
    (if (and channel (first-link? site-ptv channel))
      (into ids (remove (set ids)) (channel-service-ids channel))
      ids)))

(defn- language-items
  "`items` ({:value :language}) in `languages` (PTV codes), blank ones
   dropped."
  [items languages]
  (vec (for [{:keys [value language]} items
             :when (and (contains? languages language) (not (str/blank? value)))]
         {:value value :language language})))

(defn- address-municipality
  "A GET address's municipality (an object) as a PUT takes it (the code)."
  [m]
  (if (map? m) (:code m) m))

(defn postal-address->input
  "PTV's postal address `address` (GET shape) as a sync sends it back, kept
   to the payload's `languages` (PTV codes): a localized entry in a
   language the location doesn't have makes PTV reject the whole write. nil
   for an address PTV's API rejects, so it can't be sent back: a street
   address with neither a street number nor coordinates, a PO box without a
   box or a postal code, or another sub-type."
  [languages {:keys [subType streetAddress postOfficeBoxAddress]}]
  (case subType
    "PostOfficeBox"
    (let [{:keys [postOfficeBox postalCode municipality additionalInformation]} postOfficeBoxAddress
          box (language-items postOfficeBox languages)
          info (language-items additionalInformation languages)
          municipality (address-municipality municipality)]
      (when (and (seq box) (not (str/blank? postalCode)))
        {:type "Postal"
         :subType "PostOfficeBox"
         :country "FI"
         :postOfficeBoxAddress (cond-> {:postOfficeBox box :postalCode postalCode}
                                 (not (str/blank? municipality)) (assoc :municipality municipality)
                                 (seq info) (assoc :additionalInformation info))}))

    ("Street" "Single")
    (let [{:keys [street streetNumber postalCode municipality latitude longitude
                  additionalInformation]} streetAddress
          streets (language-items street languages)
          info (language-items additionalInformation languages)
          number (when-not (str/blank? streetNumber) streetNumber)
          coords? (every? #(and (some? %) (not (str/blank? (str %)))) [latitude longitude])
          municipality (address-municipality municipality)]
      (when (and (seq streets) (not (str/blank? postalCode)) (or number coords?))
        {:type "Postal"
         :subType "Street"
         :country "FI"
         :streetAddress (cond-> {:street streets :postalCode postalCode}
                          number (assoc :streetNumber number)
                          coords? (assoc :latitude (str latitude) :longitude (str longitude))
                          (not (str/blank? municipality)) (assoc :municipality municipality)
                          (seq info) (assoc :additionalInformation info))}))

    nil))

(defn ptv-postal-addresses
  "The postal addresses of the PTV location `channel` (GET shape), which
   Lipas has no field for, split by what a sync to `languages` (PTV codes,
   the payload's) does with them: :kept are sent back as the PUT takes them
   (postal-address->input), so the location keeps them; :dropped are those
   PTV's API rejects, which the sync therefore removes."
  [channel languages]
  (let [languages (set languages)
        postal (filter #(= "Postal" (:type %)) (:addresses channel))
        converted (map (fn [a] [a (postal-address->input languages a)]) postal)]
    {:kept (into [] (keep second) converted)
     :dropped (into [] (keep (fn [[a input]] (when-not input a))) converted)}))

(defn sports-site->service-ids [types source-id->service sports-site]
  (let [sub-cat-id (-> sports-site :type :type-code types :sub-category)
        org-id (-> sports-site :ptv :org-id)
        source-id (str "lipas-" org-id "-" sub-cat-id)]
    (when-let [service (get source-id->service source-id)]
      #{(:id service)})))

(defn rebound-service-ids
  "The services a sync connects `site` to after its type changed since the
   last sync ([:ptv :previous-type-code]): the LIPAS service of its old
   sub-category swapped for that of the new one. `source-id->service`
   indexes the org's services by sourceId. Connections made otherwise, in
   Lipas or in PTV, stay. The site's own list while the type is unchanged."
  [source-id->service site]
  (let [ptv (:ptv site)
        ids (vec (:service-ids ptv))]
    (if (= (-> site :type :type-code) (:previous-type-code ptv))
      ids
      (let [old-ids (set (sports-site->service-ids types/all source-id->service
                                                   (assoc-in site [:type :type-code]
                                                             (:previous-type-code ptv))))
            new-ids (sports-site->service-ids types/all source-id->service site)]
        (vec (distinct (concat (remove old-ids ids) new-ids)))))))

(def synced-content-keys
  "The :ptv keys of a site the PTV tool edits and a sync sends. The tool
   snapshots them as :ptv-saved whenever it loads or saves a site, to tell an
   edit that isn't saved yet (see unsaved-ptv-edits?)."
  [:sync-enabled :service-ids :service-channel-ids :summary :description])

(defn unsaved-ptv-edits?
  "Whether the PTV tool's edit of `site` (its :ptv) differs from what was
   saved (its :ptv-saved snapshot) in what a sync sends. Comparing with PTV
   can't tell: an edit that makes the site match a change made in PTV, such
   as connecting the service someone connected there, leaves no row, yet
   only a sync saves it. Neither can turning sync back on for a site saved
   with sync off. False when there's no snapshot."
  [site]
  (if-let [saved (:ptv-saved site)]
    (let [edit (:ptv site)
          ids (fn [ptv k] (into #{} (remove str/blank?) (get ptv k)))]
      (or (not= (boolean (:sync-enabled edit)) (boolean (:sync-enabled saved)))
          (not= (ids edit :service-ids) (ids saved :service-ids))
          (not= (ids edit :service-channel-ids) (ids saved :service-channel-ids))
          (not (texts-match? edit saved [:summary :description]))))
    false))

(defn is-sent-to-ptv?
  "Check if the :ptv data shows that the site has been sent to PTV previously"
  [site]
  (let [{:keys [ptv]} site]
    (and (-> ptv :service-channel-ids first)
         (:source-id ptv)
         (= "Published" (:publishing-status ptv)))))

(defn ptv-candidate?
  "Does the site look like it should be sent to the ptv?"
  [site]
  (let [{:keys [status owner]} site
        type-code (-> site :type :type-code)]
    (boolean (and (not (contains? #{"incorrect-data" "out-of-service-permanently"} status))
                  (#{"city" "city-main-owner"} owner)
                  ;; Huoltorakennus
                  ;; Opastuspiste
                  (not (#{7000 207} type-code))))))

(defn save-syncs?
  "Whether saving `site` (a saved revision, not a draft) also writes to
   PTV: sync is on, or an archive is asked for, and the site belongs there
   or is there already. lipas.backend.core/save-sports-site! syncs by it;
   the UI tells the user a save will take PTV's time."
  [site]
  (boolean (and (or (-> site :ptv :sync-enabled)
                    (-> site :ptv :delete-existing))
                (or (ptv-candidate? site)
                    (is-sent-to-ptv? site)))))

(defn ptv-ready?
  [site]
  (let [{:keys [ptv]} site
        {:keys [summary description]} ptv]
    (boolean (and (some-> description :fi count (> 5))
                  (some-> summary :fi count (> 5))))))

(def attention-reasons
  "Why a site's PTV integration needs an admin's attention, most severe first.
  `:error` = it isn't (correctly) in PTV although it should be; `:warning` = it
  is in PTV, but in a state someone should look at."
  [[:ptv/no-org :error]
   [:ptv/missing-texts :error]
   [:ptv/sync-error :error]
   [:ptv/never-sent :error]
   [:ptv/published-not-candidate :warning]
   [:ptv/no-service :warning]
   [:ptv/published-sync-off :warning]
   [:ptv/archived-sync-on :warning]])

(defn attention
  "The `attention-reasons` that apply to `site` (current revision), in that
  order, as `[{:reason kw :severity kw}]`; empty when its PTV state is fine.
  Shared by the admin adoption view's tiles, its attention list and the
  municipality table, so they can't disagree.

  A sync error is only stored by a failed sync and cleared by the next
  successful one (it isn't a persisted PTV key), so it's always current."
  [site]
  (let [{:keys [sync-enabled org-id error publishing-status
                service-ids service-channel-ids]} (:ptv site)
        published? (= "Published" publishing-status)
        candidate? (ptv-candidate? site)
        hits (set [(when (and sync-enabled (str/blank? org-id))
                     :ptv/no-org)
                   (when (and sync-enabled candidate? (not (ptv-ready? site)))
                     :ptv/missing-texts)
                   (when (and sync-enabled error)
                     :ptv/sync-error)
                   (when (and sync-enabled candidate? (not error)
                              (not (str/blank? org-id)) (ptv-ready? site)
                              (empty? service-channel-ids) (nil? publishing-status))
                     :ptv/never-sent)
                   (when (and published? (not candidate?))
                     :ptv/published-not-candidate)
                   (when (and published? candidate? (empty? service-ids))
                     :ptv/no-service)
                   (when (and published? (not sync-enabled))
                     :ptv/published-sync-off)
                   (when (and sync-enabled candidate? (= "Deleted" publishing-status))
                     :ptv/archived-sync-on)])]
    (vec (for [[reason severity] attention-reasons
               :when (hits reason)]
           {:reason reason :severity severity}))))

(defn service-location-short-texts
  "[{:field :locale}] for the site's PTV summaries and descriptions that PTV
  rejects as too short (see min-text-length), in the languages a sync
  publishes: the org's `org-langs` the site has both texts in
  (effective-languages). A text in any other language never reaches PTV,
  and its tab isn't even shown."
  [site org-langs]
  (vec (for [lang (effective-languages site org-langs)
             field [:summary :description]
             :let [locale (keyword lang)]
             :when (text-too-short? (get-in site [:ptv field locale]))]
         {:field field :locale locale})))

(defn sync-blockers
  "What stops `site` from syncing to PTV, as a set of keywords (empty when
  nothing does). Only meaningful while `[:ptv :sync-enabled]` is on and the
  site isn't being archived — otherwise there is nothing to sync.

  - `:ptv/no-org`        no PTV org-id on the site
  - `:ptv/missing-texts` the site should be in PTV but lacks the Finnish
                         summary/description (see `ptv-ready?`)

  Shared by the site form (save gate + messages) and `save-sports-site!`
  (hard gate), so both reject exactly the same states.

  A text under PTV's minimum length isn't a blocker here: which languages a
  sync publishes depends on the org's live config, which the site doesn't
  carry. The sync refuses it instead (`:ptv/texts-too-short`, see
  service-location-short-texts), and the text fields warn about it."
  [site]
  (let [{:keys [sync-enabled delete-existing org-id]} (:ptv site)]
    (if (or (not sync-enabled) delete-existing)
      #{}
      (cond-> #{}
        (str/blank? org-id)
        (conj :ptv/no-org)

        (and (ptv-candidate? site) (not (ptv-ready? site)))
        (conj :ptv/missing-texts)))))

(def server-owned-ptv-keys
  "`:ptv` keys only the PTV sync writes: what PTV returned and when. A user save
  never sets them — `core/check-ptv-save!` keeps them from the previous
  revision — so a browser holding an outdated copy of the site can't roll them
  back. (Seen in prod: a site archived by one save was marked incorrect-data
  seconds later with its pre-archive :ptv, leaving LIPAS saying Published.)"
  [:publishing-status :last-sync :ptv-modified :source-id :service-channel-ids
   :previous-type-code :error])

(def user-controlled-ptv-keys
  "`:ptv` keys an editor sets through the site form. A save that changes any of
  them is a PTV action and needs :ptv/manage; the rest of `:ptv` is written by
  the sync itself."
  [:sync-enabled :org-id :delete-existing :service-ids
   :summary :description :user-instruction])

(defn ptv-edited?
  "Did the editor change PTV settings between `prev` and `site`?"
  [prev site]
  (let [settings (fn [s]
                   ;; Absent, nil, false-y defaults and empty colls/strings all
                   ;; mean "not set" — the edit buffer may materialize them.
                   (into {}
                         (remove (fn [[_ v]]
                                   (or (nil? v) (false? v)
                                       (and (coll? v) (empty? (remove str/blank? (if (map? v) (vals v) v)))))))
                         (select-keys (:ptv s) user-controlled-ptv-keys)))]
    (not= (settings prev) (settings site))))

(defn ptv-service-channel->texts
  "Take PTV ServiceChannel response and build Lipas :summary, :description, :user-instruction"
  [data]
  (ptv-descriptions->texts (:serviceChannelDescriptions data)))

(defn get-all-pages [f]
  (loop [page 1
         results []]
    (let [resp (f page)
          results-pages (:pageCount resp)]
      (if (>= page results-pages)
        {:pageCount (:pageCount resp)
         :itemList (reduce (fn [acc x]
                             (into acc (:itemList x)))
                           []
                           (conj results resp))}
        (recur (inc page)
               (conj results resp))))))