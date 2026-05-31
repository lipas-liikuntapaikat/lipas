(ns lipas.data.ptv
  (:require [clojure.set :as set]
            [clojure.string :as str]
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
   lipas.data.ptv/sports-site->ptv-input shape).

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

(defn resolve-org-id
  "Returns the effective PTV org-id for a site.
   Resolution order:
   1. Persisted :ptv :org-id on the site (authoritative once integrated).
   2. User belongs to exactly one org → that org.
   3. Exactly one of the user's orgs covers the site's city-code → that org.
   4. Nil (ambiguous; UI must prompt)."
  [site user-orgs]
  (or (get-in site [:ptv :org-id])
      (when (= 1 (count user-orgs))
        (get-in (first user-orgs) [:ptv-data :org-id]))
      (let [city-code (get-in site [:location :city :city-code])
            matches (filter (fn [org]
                              (contains? (set (get-in org [:ptv-data :city-codes]))
                                         city-code))
                            user-orgs)]
        (when (= 1 (count matches))
          (get-in (first matches) [:ptv-data :org-id])))))

(def lang->locale
  "PTV language code -> LIPAS locale keyword"
  {"fi" :fi, "sv" :se, "en" :en})

(def lipas-lang->ptv-lang
  "LIPAS language code -> PTV language code"
  {"fi" "fi", "se" "sv", "en" "en"})

(def locale->lang
  "LIPAS locale keyword -> PTV language code"
  {:fi "fi", :se "sv", :en "en"})

(defn- resolve-lang-pairs
  "Given a set/seq of LIPAS language codes (\"fi\" \"se\" \"en\"),
   returns seq of [ptv-lang lipas-locale] pairs for iteration."
  [languages]
  (for [lipas-lang languages
        :let [ptv-lang (get lipas-lang->ptv-lang lipas-lang)]
        :when ptv-lang]
    [ptv-lang (keyword lipas-lang)]))

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
  (let [lipas-languages (set languages)
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

     :serviceNames (let [custom-name (:service-name m)]
                     (for [[lang locale] (resolve-lang-pairs lipas-languages)]
                       {:type "Name" ; Name | AlternativeName
                        :language lang
                        :value (or (when (and custom-name (= locale :fi))
                                     custom-name)
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

     :serviceDescriptions (for [[k v] {:summary "Summary"
                                       :description "Description"
                                       :user-instruction "UserInstruction"}
                                [lang locale] (resolve-lang-pairs lipas-languages)]
                            {:type v
                             :language lang
                             :value (get-in m [k locale] placeholder)})

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
                 (<= (count extracted) 500))
        extracted))))

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

(defn ->ptv-service-location
  [org-id
   coord-transform-fn
   now
   {:keys [status ptv lipas-id location search-meta] :as sports-site}]
  (let [lipas-languages (get ptv :languages default-langs)
        ;; PTV language codes for the :languages field on the payload
        languages (->> lipas-languages (map lipas-lang->ptv-lang) (remove nil?) set)
        type (get types/all (get-in sports-site [:type :type-code]))
        _sub-cat (get types/sub-categories (:sub-category type))
        _main-cat (get types/main-categories (:main-category type))

        email (parse-email (:email sports-site))
        www (parse-www (:www sports-site))
        phone-number (parse-phone-number (:phone-number sports-site))]

    #?(:clj (println "PTV data"))
    #?(:clj (prn ptv))

    ; (println "Languages resolved" languages)
    ; (prn location)

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
             ;; serviceChannelDescriptions. For sv/en, use the LIPAS-entered
             ;; localized value if non-blank, otherwise fall back to the
             ;; Finnish value. PTV-side edits to these fields are not
             ;; preserved — drift is surfaced separately in the LIPAS UI.
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
                          (let [[lon lat] (-> search-meta :location :wgs84-point coord-transform-fn)
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

      www (assoc :webPages [{:url www
                             :language "fi"}])
      (not www) (assoc :deleteAllWebPages true)

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

(defn index-services [services])

(defn resolve-missing-services
  "Infer services (sub-categories) that need to be created in PTV and
  attached to sports-sites."
  [org-id services sports-sites]
  (let [source-ids (->> services
                        vals
                        (keep :sourceId)
                        set)]
    (->> sports-sites
         (filter (fn [{:keys [ptv]}] (empty? (:service-ids ptv))))
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

(defn determine-audit-status
  "Determines the audit status for a site based on its audit data.
   Returns one of: :approved, :changes-requested, :partial, :none"
  [site]
  (let [audit-data (get-in site [:ptv :audit])
        summary-status (get-in audit-data [:summary :status])
        desc-status (get-in audit-data [:description :status])]
    (cond
      ;; Both fields audited
      (and summary-status desc-status)
      (cond
        ;; Both approved
        (and (= "approved" summary-status) (= "approved" desc-status))
        :approved

        ;; Any changes requested
        (or (= "changes-requested" summary-status) (= "changes-requested" desc-status))
        :changes-requested

        ;; Mixed or other statuses
        :else :partial)

      ;; Only one field audited
      (or summary-status desc-status)
      :partial

      ;; No audit data
      :else :none)))

(defn ptv-descriptions->texts
  "Extract :summary, :description, :user-instruction maps from PTV descriptions array.
   Works for both serviceDescriptions and serviceChannelDescriptions."
  [descriptions]
  (reduce (fn [acc {:keys [language value type]}]
            (if-let [k (case type
                         "Summary" :summary
                         "Description" :description
                         "UserInstruction" :user-instruction
                         nil)]
              (update acc k assoc (lang->locale language) value)
              acc))
          {}
          descriptions))

(def service-channel-compare-fields
  "Fields to compare when checking drift against a PTV ServiceChannel.
   PTV ServiceChannels don't carry :user-instruction — that's a Service-level
   concept — so leave it out of the comparison."
  [:summary :description])

(defn normalize-ws
  "Normalize whitespace for *comparing* PTV/LIPAS free-text values — never for
   storing or pushing them. PTV collapses runs of horizontal whitespace in
   fields on save (e.g. a double space in a Name becomes a single space), which
   would otherwise read as drift immediately after a sync. Collapses runs of
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
   (see `normalize-ws`). `fields` defaults to all three lipas text fields;
   callers comparing against a PTV ServiceChannel should pass
   `service-channel-compare-fields`."
  ([lipas-texts ptv-texts]
   (texts-match? lipas-texts ptv-texts [:summary :description :user-instruction]))
  ([lipas-texts ptv-texts fields]
   (let [trim normalize-ws]
     (every? (fn [field]
               (let [lipas-vals (get lipas-texts field)
                     ptv-vals (get ptv-texts field)]
                 (every? (fn [lang]
                           (let [l (trim (get lipas-vals lang))
                                 p (trim (get ptv-vals lang))]
                             (or (and (nil? l) (nil? p))
                                 (= l p))))
                         [:fi :se :en])))
             fields))))

(defn- effective-lipas-name
  "What LIPAS will push for [type, locale] under the strict 1-way model.
   Mirrors the per-language fallback rule in `->ptv-service-location`:
   sv/en use the localized name when entered, else the Finnish name.
   Returns nil when LIPAS has nothing to push (e.g., AlternativeName
   without a marketing-name)."
  [site type locale]
  (case type
    "Name" (let [v (get-in site [:name-localized locale])]
             (if (str/blank? v) (:name site) v))
    "AlternativeName" (when-not (str/blank? (:marketing-name site))
                        (:marketing-name site))))

(defn- effective-lipas-text
  "Mirrors the description fallback in `->ptv-service-location`: use the
   localized value if entered, else the Finnish value, else nil."
  [site field locale]
  (let [m (get-in site [:ptv field])
        v (get m locale)]
    (if (str/blank? v) (get m :fi) v)))

(defn- resolve-service-name
  "Resolve a service ID to a human-readable Finnish name from the
   services-by-id map. Falls back to the ID when the service isn't
   in the cache (e.g. external services or stale cache)."
  [services service-id]
  (or (some-> (get services service-id)
              :serviceNames
              select-service-name)
      service-id))

(defn compute-service-channel-drift
  "Build a structured per-field drift report comparing what LIPAS would
   push on next sync against what's currently stored in PTV. Returns a
   vector of entries; empty vector means no drift. Each entry has
   `:field` (:name, :marketing-name, :summary, :description, :services).
   For value fields, the entry has localized `:lipas`/`:ptv` strings
   (with `:language`, `:type`, `:locale`). For service link drift, the
   entry's `:lipas`/`:ptv`/`:added`/`:removed` are vectors of
   `{:id :name}` maps so the UI can show readable service names rather
   than raw UUIDs.

   `site` is the LIPAS sports-site map; `ptv-channel` is the cached PTV
   ServiceChannel response (or nil if unavailable); `services` is the
   org's services-by-id map used for resolving service names; and
   `lipas-languages` is the list of LIPAS-side language codes
   (e.g. [\"fi\" \"se\" \"en\"]) to consider. Returns nil when the
   channel hasn't been fetched yet."
  [site ptv-channel services lipas-languages]
  (when ptv-channel
    (let [ptv-names (into {} (map (juxt (juxt :type :language) :value))
                          (:serviceChannelNames ptv-channel))
          ptv-descs (into {} (map (juxt (juxt :type :language) :value))
                          (:serviceChannelDescriptions ptv-channel))
          lang-pairs (resolve-lang-pairs lipas-languages)
          ;; Normalize whitespace so PTV's server-side collapsing (e.g. a
          ;; double space in a name) isn't reported as drift right after a sync.
          trim normalize-ws
          mk-text-comparison (fn [field type-v]
                               (for [[ptv-lang locale] lang-pairs]
                                 {:field field
                                  :type type-v
                                  :language ptv-lang
                                  :locale locale
                                  :lipas (effective-lipas-text site (case field
                                                                      :summary :summary
                                                                      :description :description) locale)
                                  :ptv (get ptv-descs [type-v ptv-lang])}))
          comparisons (concat
                        (for [[ptv-lang locale] lang-pairs]
                          {:field :name
                           :type "Name"
                           :language ptv-lang
                           :locale locale
                           :lipas (effective-lipas-name site "Name" locale)
                           :ptv (get ptv-names ["Name" ptv-lang])})
                        [{:field :marketing-name
                          :type "AlternativeName"
                          :language "fi"
                          :locale :fi
                          :lipas (effective-lipas-name site "AlternativeName" :fi)
                          :ptv (get ptv-names ["AlternativeName" "fi"])}]
                        (mk-text-comparison :summary "Summary")
                        (mk-text-comparison :description "Description"))
          drifted (->> comparisons
                       (keep (fn [c]
                               (let [l (trim (:lipas c))
                                     p (trim (:ptv c))]
                                 (when (not= l p)
                                   (assoc c :lipas l :ptv p))))))
          lipas-service-ids (set (-> site :ptv :service-ids))
          ptv-service-ids (->> (:services ptv-channel)
                               (map (comp :id :service))
                               (remove nil?)
                               set)
          mk-entry (fn [id] {:id id :name (resolve-service-name services id)})
          mk-entries (fn [id-set] (->> id-set (map mk-entry) (sort-by :name) vec))
          link-drift (when (not= lipas-service-ids ptv-service-ids)
                       [{:field :services
                         :lipas (mk-entries lipas-service-ids)
                         :ptv (mk-entries ptv-service-ids)
                         :added (mk-entries (set/difference ptv-service-ids lipas-service-ids))
                         :removed (mk-entries (set/difference lipas-service-ids ptv-service-ids))}])]
      (vec (concat drifted link-drift)))))

(defn sports-site->ptv-input [{:keys [types org-id org-defaults org-langs]} service-channels services site]
  (let [service-id (-> site :ptv :service-ids first)
        service-channel-id (-> site :ptv :service-channel-ids first)

        summary (-> site :ptv :summary)
        description (-> site :ptv :description)
        user-instruction (-> site :ptv :user-instruction)

        last-sync (-> site :ptv :last-sync)

        ;; Drift detection: build a structured per-field diff comparing what
        ;; LIPAS would push on next sync vs what's currently stored in PTV.
        ;; Covers Name (per language), AlternativeName/marketing-name, Summary
        ;; and Description (per language), and service-link membership.
        ptv-channel (get service-channels service-channel-id)
        ptv-texts (when ptv-channel (ptv-descriptions->texts (:serviceChannelDescriptions ptv-channel)))
        drift-langs (or (-> site :ptv :languages) org-langs)
        drift-fields (when (and last-sync ptv-channel)
                       (compute-service-channel-drift site ptv-channel services drift-langs))
        has-drift? (boolean (seq drift-fields))

        ;; A site that was synced before (last-sync present) but now has no
        ;; service-channel-id was deliberately unlinked by the user — a
        ;; successful sync always writes service-channel-ids back, so this
        ;; combination can't arise any other way. (Use str/blank? rather than
        ;; empty?: clearing the autocomplete can leave service-channel-ids as
        ;; [nil]/[""], not [].) Treat it as out-of-date so the sync button
        ;; re-activates and the backend create-path (channel id nil) builds a
        ;; fresh PTV service-location. Without this, has-drift? is false (no
        ;; channel left to diff) and event-date still equals last-sync, so
        ;; sync-status would wrongly read :ok and the button would stay
        ;; disabled ("up to date").
        link-removed? (and last-sync (str/blank? service-channel-id))]

    {:valid (boolean (and (some-> description :fi count (> 5))
                          (some-> summary :fi count (> 5))))
     :lipas-id (:lipas-id site)
     :name (:name site)
     :event-date (:event-date site)
     :name-conflict (detect-name-conflict site (vals service-channels))
     :marketing-name (:marketing-name site)
     :type (-> site :search-meta :type :name :fi)
     :type-code (-> site :type :type-code)
     :sub-category (-> site :search-meta :type :sub-category :name :fi)
     :sub-category-id (-> site :type :type-code types :sub-category)
     :org-id org-id
     :admin (-> site :search-meta :admin :name :fi)
     :owner (-> site :search-meta :owner :name :fi)
     :summary summary
     :description description
     :user-instruction user-instruction
     :languages (or (-> site :ptv :languages) org-langs)

     :sync-enabled (get-in site [:ptv :sync-enabled] false)
     :last-sync last-sync

     :sync-status (cond
                    (not last-sync) :not-synced
                    link-removed? :out-of-date
                    has-drift? :content-drift
                    (= (:event-date site) last-sync) :ok
                    :else :out-of-date)

     :drift-fields drift-fields

     :ptv-texts ptv-texts

     :service-ids (-> site :ptv :service-ids)
     :service-name (-> services
                       (get service-id)
                       :serviceNames
                       (select-service-name))
     :service-channel-id service-channel-id
     :service-channel-ids (-> site :ptv :service-channel-ids)
     :service-channel-name (-> (get service-channels service-channel-id)
                               (resolve-service-channel-name))
     :service-channel-publishing-status (:publishingStatus ptv-channel)

     :audit-status (determine-audit-status site)}))

(defn sports-site->service-ids [types source-id->service sports-site]
  (let [sub-cat-id (-> sports-site :type :type-code types :sub-category)
        org-id (-> sports-site :ptv :org-id)
        source-id (str "lipas-" org-id "-" sub-cat-id)]
    (when-let [service (get source-id->service source-id)]
      #{(:id service)})))

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

(defn ptv-ready?
  [site]
  (let [{:keys [ptv]} site
        {:keys [summary description]} ptv]
    (boolean (and (some-> description :fi count (> 5))
                  (some-> summary :fi count (> 5))))))

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
