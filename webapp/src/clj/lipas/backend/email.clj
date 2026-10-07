(ns lipas.backend.email
  (:require [clojure.java.io :as io]
            [clojure.pprint :as pprint]
            [clojure.string :as str]
            [postal.core :as postal]))

(defprotocol Emailer
  (send! [config message]))

(defn safe-slurp [path]
  (some-> (io/resource path) slurp))

(defn escape-html
  "Escapes `s` for HTML text AND quoted attribute values. Every value that
  reaches an HTML body goes through this — user-typed text (names, feedback,
  reminder messages, org names) and links alike."
  [s]
  (-> (str s)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")
      (str/replace "\"" "&quot;")
      (str/replace "'" "&#39;")))

(def templates
  {:fi
   {:permissions-updated
    {:subject "Käyttöikeutesi on päivitetty"
     :html    (safe-slurp "email_templates/permissions_updated_fi.html")
     :text    (safe-slurp "email_templates/permissions_updated_fi.txt")}
    :magic-link
    {:portal
     {:subject "Jää- ja uimahalliportaalit ovat nyt Lipaksessa"
      :html    (safe-slurp "email_templates/magic_link_portal_fi.html")
      :text    (safe-slurp "email_templates/magic_link_portal_fi.txt")}
     :lipas
     {:subject "LIPAS sisäänkirjautumislinkki"
      :html    (safe-slurp "email_templates/magic_link_lipas_fi.html")
      :text    (safe-slurp "email_templates/magic_link_lipas_fi.txt")}}
    :reminder
    {:subject "LIPAS-muistutus"
     :html    (safe-slurp "email_templates/reminder_fi.html")
     :text    (safe-slurp "email_templates/reminder_fi.txt")}}})

(defn postal-message
  "The postal message map for `msg`. Both parts declare their charset: a bare
  \"text/plain\" made JavaMail write Latin-1 bytes under a charset=UTF-8 label,
  garbling ä/ö/å for every client that shows the text part."
  [from {:keys [to subject plain html]}]
  {:from    from
   :to      to
   :subject subject
   :body    (cond-> [:alternative]
              (not-empty plain) (conj {:type "text/plain;charset=utf-8" :content plain})
              (not-empty html)  (conj {:type "text/html;charset=utf-8" :content html})
              ;; Fallback if both are empty to avoid NPE
              (and (empty? plain) (empty? html)) (conj {:type "text/plain;charset=utf-8" :content ""}))})

(defn send*!
  "Thin wrapper for postal."
  [{:keys [host port user pass from]} msg]
  (postal/send-message
    (merge
      {:host host
     ;; Socket timeouts (ms, javax.mail wants strings). Blocking socket IO
     ;; ignores thread interrupts, so without these a stuck SMTP server
     ;; would leak a job-worker thread past the watchdog timeout.
       :connectiontimeout "30000"
       :timeout "60000"
       :writetimeout "60000"}
      (when port {:port port})
      (when (and (not-empty user) (not-empty pass))
        {:user user :pass pass :ssl true}))
    (postal-message from msg)))

(defn send-reset-password-email!
  [emailer to {:keys [link]}]
  (.send! emailer {:subject "Salasanan vaihtolinkki"
                   :to      to
                   :plain   (str link)
                   :html    (str "<html><body>" (escape-html link) "</body></html>")}))

(defn send-magic-login-email!
  [emailer to variant {:keys [link valid-days]}]
  (.send! emailer {:subject (-> templates :fi :magic-link variant :subject)
                   :to      to
                   :plain   (-> templates
                                :fi
                                :magic-link
                                variant
                                :text
                                (str/replace "{{link}}" link)
                                (str/replace "{{valid-days}}" (str valid-days)))
                   :html    (-> templates
                                :fi
                                :magic-link
                                variant
                                :html
                                (str/replace "{{link}}" (escape-html link))
                                (str/replace "{{valid-days}}" (str valid-days)))}))

(defn send-register-notification!
  "Tells the ops inbox that someone completed self-registration. By then the
  address is verified — the account can't be created without the emailed link."
  [emailer to {:keys [email username user-data]}]
  (let [{:keys [firstname lastname permissions-request]} user-data
        rows (->> [["Sähköposti" email]
                   ["Käyttäjätunnus" username]
                   ["Etunimi" firstname]
                   ["Sukunimi" lastname]
                   ["Käyttöoikeuspyyntö" permissions-request]]
                  (remove (comp str/blank? second)))
        intro "Uusi käyttäjä on rekisteröitynyt ja vahvistanut sähköpostiosoitteensa."
        outro "Käyttöoikeudet myönnetään LIPAS-ylläpidon käyttäjähallinnassa."]
    (.send! emailer {:subject "Uusi rekisteröitynyt käyttäjä"
                     :to      to
                     :plain   (str intro "\n\n"
                                   (str/join "\n" (map (fn [[k v]] (str k ": " v)) rows))
                                   "\n\n" outro "\n")
                     :html    (str "<html><body>"
                                   "<p>" intro "</p>"
                                   "<table>"
                                   (apply str
                                          (map (fn [[k v]]
                                                 (str "<tr><th align=\"left\">" k "</th>"
                                                      "<td style=\"white-space:pre-wrap\">"
                                                      (escape-html v) "</td></tr>"))
                                               rows))
                                   "</table>"
                                   "<p>" outro "</p>"
                                   "</body></html>")})))

(defn send-permissions-updated-email!
  [emailer to {:keys [link valid-days]}]
  (.send! emailer {:subject (-> templates :fi :permissions-updated :subject)
                   :to      to
                   :plain   (-> templates
                                :fi
                                :permissions-updated
                                :text
                                (str/replace "{{link}}" link)
                                (str/replace "{{valid-days}}" (str valid-days)))
                   :html    (-> templates
                                :fi
                                :permissions-updated
                                :html
                                (str/replace "{{link}}" (escape-html link))
                                (str/replace "{{valid-days}}" (str valid-days)))}))

;; --- "You've been added to an org" emails -----------------------------------
;; One trilingual scaffold (subject + per-language intro), two variants that
;; differ only in the per-language action sentence: :invitation (new account,
;; set a password) and :added (existing account, one-click login).

(def ^:private org-membership-intros
  "Per-language shared intro sentence ({{org-name}} placeholder)."
  {:fi {:plain "Sinut on lisätty organisaatioon \"{{org-name}}\" LIPAS-palvelussa."
        :html  "Sinut on lisätty organisaatioon <b>{{org-name}}</b> LIPAS-palvelussa."}
   :se {:plain "Du har lagts till i organisationen \"{{org-name}}\" i LIPAS."
        :html  "Du har lagts till i organisationen <b>{{org-name}}</b> i LIPAS."}
   :en {:plain "You have been added to the organization \"{{org-name}}\" in LIPAS."
        :html  "You have been added to the organization <b>{{org-name}}</b> in LIPAS."}})

(def ^:private org-membership-actions
  "Per-variant, per-language action sentence ({{link}}/{{valid-days}} placeholders)."
  {:invitation
   {:fi {:plain "Kirjaudu sisään ja aseta salasanasi tästä linkistä (voimassa {{valid-days}} päivää):\n{{link}}"
         :html  "<a href=\"{{link}}\">Kirjaudu sisään ja aseta salasanasi</a> (voimassa {{valid-days}} päivää)."}
    :se {:plain "Logga in och ange ditt lösenord via länken (giltig i {{valid-days}} dagar):\n{{link}}"
         :html  "<a href=\"{{link}}\">Logga in och ange ditt lösenord</a> (giltig i {{valid-days}} dagar)."}
    :en {:plain "Log in and set your password via this link (valid for {{valid-days}} days):\n{{link}}"
         :html  "<a href=\"{{link}}\">Log in and set your password</a> (valid for {{valid-days}} days)."}}
   :added
   {:fi {:plain "Kirjaudu sisään tästä linkistä — oikeutesi päivittyvät heti (linkki voimassa {{valid-days}} päivää):\n{{link}}"
         :html  "<a href=\"{{link}}\">Kirjaudu sisään</a> — oikeutesi päivittyvät heti (linkki voimassa {{valid-days}} päivää)."}
    :se {:plain "Logga in via länken — dina behörigheter uppdateras genast (giltig i {{valid-days}} dagar):\n{{link}}"
         :html  "<a href=\"{{link}}\">Logga in</a> — dina behörigheter uppdateras genast (giltig i {{valid-days}} dagar)."}
    :en {:plain "Log in via this link — your permissions update immediately (valid for {{valid-days}} days):\n{{link}}"
         :html  "<a href=\"{{link}}\">Log in</a> — your permissions update immediately (valid for {{valid-days}} days)."}}})

(defn- send-org-membership-email!
  "Build and send one of the trilingual (fi/se/en — the recipient's locale is
  unknown) org-membership emails; `variant` selects the action sentence."
  [emailer to variant {:keys [org-name link valid-days]}]
  (let [fill  (fn [kind s]
                (let [v (if (= :html kind) escape-html str)]
                  (-> s
                      (str/replace "{{org-name}}" (v org-name))
                      (str/replace "{{link}}" (v link))
                      (str/replace "{{valid-days}}" (str valid-days)))))
        langs [:fi :se :en]
        block (fn [kind lang]
                (str (fill kind (get-in org-membership-intros [lang kind]))
                     ({:plain "\n" :html " "} kind)
                     (fill kind (get-in org-membership-actions [variant lang kind]))))]
    (.send! emailer
            {:subject "Sinut on lisätty organisaatioon LIPAS-palvelussa / Du har lagts till i en organisation / You've been added to an organization in LIPAS"
             :to      to
             :plain   (str (str/join "\n\n" (map #(block :plain %) langs)) "\n")
             :html    (str "<html><body>"
                           (apply str (map #(str "<p>" (block :html %) "</p>") langs))
                           "</body></html>")})))

(defn send-org-invitation-email!
  "Custom organization-invitation email: notifies a user they've been added to an
  organization and gives them a magic login link (to set a password / access org
  features)."
  [emailer to opts]
  (send-org-membership-email! emailer to :invitation opts))

(defn send-org-added-email!
  "Notify an EXISTING user that they've been added to an organization. Includes a
  magic login link (one-click login that lands them authenticated with the fresh
  token carrying the new org role) — mirrors send-permissions-updated-email!."
  [emailer to opts]
  (send-org-membership-email! emailer to :added opts))

(defn send-reminder-email!
  [emailer to {:keys [link valid-days]} {:keys [message]}]
  (.send! emailer {:subject (-> templates :fi :reminder :subject)
                   :to      to
                   :plain   (-> templates
                                :fi
                                :reminder
                                :text
                                (str/replace "{{message}}" message)
                                (str/replace "{{link}}" link)
                                (str/replace "{{valid-days}}" (str valid-days)))
                   :html    (-> templates
                                :fi
                                :reminder
                                :html
                                (str/replace "{{message}}" (escape-html message))
                                (str/replace "{{link}}" (escape-html link))
                                (str/replace "{{valid-days}}" (str valid-days)))}))

;; --- Self-registration and email-change emails -------------------------------
;; Sent in the language the requester was using (they're on the page when they
;; ask, unlike org invites whose recipient's locale is unknown). Copy is plain
;; text with {{placeholders}}; `render-copy` derives the HTML part, escaping the
;; copy and every value and turning URL placeholders into links.

(def ^:private copy-texts
  {:link
   {:fi {:subject "Viimeistele rekisteröitymisesi LIPAS-palveluun"
         :body    "Hei!\n\nViimeistele rekisteröitymisesi LIPAS-palveluun avaamalla alla oleva linkki. Linkki on voimassa {{valid-hours}} tuntia.\n\n{{link}}\n\nJos et pyytänyt tätä viestiä, voit jättää sen huomiotta. Tunnusta ei luoda, ellei linkkiä avata.\n\nTerveisin,\nLIPAS"}
    :se {:subject "Slutför din registrering i LIPAS"
         :body    "Hej!\n\nSlutför din registrering i LIPAS genom att öppna länken nedan. Länken är giltig i {{valid-hours}} timmar.\n\n{{link}}\n\nOm du inte har begärt detta meddelande kan du ignorera det. Inget konto skapas om länken inte öppnas.\n\nMed vänliga hälsningar,\nLIPAS"}
    :en {:subject "Complete your LIPAS registration"
         :body    "Hello!\n\nComplete your registration to LIPAS by opening the link below. The link is valid for {{valid-hours}} hours.\n\n{{link}}\n\nIf you didn't request this, you can ignore this message. No account is created unless the link is opened.\n\nBest regards,\nLIPAS"}}
   :account-exists
   {:fi {:subject "LIPAS-rekisteröityminen"
         :body    "Hei!\n\nTälle sähköpostiosoitteelle pyydettiin rekisteröitymislinkkiä LIPAS-palveluun, mutta osoitteella on jo tunnus.\n\nKirjaudu sisään: {{login-url}}\nJos olet unohtanut salasanasi, voit vaihtaa sen: {{reset-url}}\n\nJos et pyytänyt tätä viestiä, voit jättää sen huomiotta.\n\nTerveisin,\nLIPAS"}
    :se {:subject "Registrering i LIPAS"
         :body    "Hej!\n\nEn registreringslänk till LIPAS begärdes för den här e-postadressen, men adressen har redan ett konto.\n\nLogga in: {{login-url}}\nOm du har glömt ditt lösenord kan du byta det: {{reset-url}}\n\nOm du inte har begärt detta meddelande kan du ignorera det.\n\nMed vänliga hälsningar,\nLIPAS"}
    :en {:subject "LIPAS registration"
         :body    "Hello!\n\nSomeone asked for a LIPAS registration link for this email address, but the address already has an account.\n\nLog in: {{login-url}}\nForgot your password? Reset it here: {{reset-url}}\n\nIf you didn't request this, you can ignore this message.\n\nBest regards,\nLIPAS"}}
   :email-change-link-self
   {:fi {:subject "Vahvista uusi sähköpostiosoitteesi"
         :body    "Hei!\n\nOlet pyytänyt LIPAS-tunnuksesi sähköpostiosoitteen vaihtamista tähän osoitteeseen. Vahvista muutos avaamalla alla oleva linkki. Linkki on voimassa {{valid-hours}} tuntia.\n\n{{link}}\n\nOsoite vaihtuu vasta, kun linkki avataan. Jos et pyytänyt muutosta, voit jättää viestin huomiotta.\n\nTerveisin,\nLIPAS"}
    :se {:subject "Bekräfta din nya e-postadress"
         :body    "Hej!\n\nDu har begärt att e-postadressen för ditt LIPAS-konto ändras till den här adressen. Bekräfta ändringen genom att öppna länken nedan. Länken är giltig i {{valid-hours}} timmar.\n\n{{link}}\n\nAdressen ändras först när länken öppnas. Om du inte har begärt ändringen kan du ignorera meddelandet.\n\nMed vänliga hälsningar,\nLIPAS"}
    :en {:subject "Confirm your new email address"
         :body    "Hello!\n\nYou asked to change the email address of your LIPAS account to this address. Confirm the change by opening the link below. The link is valid for {{valid-hours}} hours.\n\n{{link}}\n\nThe address changes only once the link is opened. If you didn't ask for this, you can ignore this message.\n\nBest regards,\nLIPAS"}}
   :email-change-link-admin
   {:fi {:subject "Vahvista uusi sähköpostiosoitteesi"
         :body    "Hei!\n\nLIPAS-ylläpitäjä on pyytänyt LIPAS-tunnuksesi sähköpostiosoitteen vaihtamista tähän osoitteeseen. Vahvista muutos avaamalla alla oleva linkki. Linkki on voimassa {{valid-hours}} tuntia.\n\n{{link}}\n\nOsoite vaihtuu vasta, kun linkki avataan. Jos et odottanut tätä viestiä, voit jättää sen huomiotta.\n\nTerveisin,\nLIPAS"}
    :se {:subject "Bekräfta din nya e-postadress"
         :body    "Hej!\n\nEn LIPAS-administratör har begärt att e-postadressen för ditt LIPAS-konto ändras till den här adressen. Bekräfta ändringen genom att öppna länken nedan. Länken är giltig i {{valid-hours}} timmar.\n\n{{link}}\n\nAdressen ändras först när länken öppnas. Om du inte väntade dig detta meddelande kan du ignorera det.\n\nMed vänliga hälsningar,\nLIPAS"}
    :en {:subject "Confirm your new email address"
         :body    "Hello!\n\nA LIPAS administrator asked to change the email address of your LIPAS account to this address. Confirm the change by opening the link below. The link is valid for {{valid-hours}} hours.\n\n{{link}}\n\nThe address changes only once the link is opened. If you weren't expecting this message, you can ignore it.\n\nBest regards,\nLIPAS"}}
   :email-change-in-use
   {:fi {:subject "LIPAS: sähköpostiosoitteen vaihto"
         :body    "Hei!\n\nLIPAS-tunnuksen sähköpostiosoitetta yritettiin vaihtaa tähän osoitteeseen, mutta osoitteella on jo LIPAS-tunnus. Muutosta ei tehty.\n\nJos et pyytänyt tätä, voit jättää viestin huomiotta.\n\nTerveisin,\nLIPAS"}
    :se {:subject "LIPAS: byte av e-postadress"
         :body    "Hej!\n\nNågon försökte ändra e-postadressen för ett LIPAS-konto till den här adressen, men adressen har redan ett LIPAS-konto. Ingen ändring gjordes.\n\nOm du inte har begärt detta kan du ignorera meddelandet.\n\nMed vänliga hälsningar,\nLIPAS"}
    :en {:subject "LIPAS: email address change"
         :body    "Hello!\n\nSomeone tried to change a LIPAS account's email address to this address, but the address already has a LIPAS account. Nothing was changed.\n\nIf you didn't ask for this, you can ignore this message.\n\nBest regards,\nLIPAS"}}
   :email-changed-notice
   {:fi {:subject "LIPAS-tunnuksesi sähköpostiosoite on vaihdettu"
         :body    "Hei!\n\nLIPAS-tunnuksesi sähköpostiosoite on vaihdettu osoitteeseen {{masked-email}}. Tähän osoitteeseen ei enää lähetetä LIPAS-viestejä, eikä sillä voi enää kirjautua sisään.\n\nJos et tehnyt tai pyytänyt muutosta, ota heti yhteyttä: lipasinfo@jyu.fi\n\nTerveisin,\nLIPAS"}
    :se {:subject "E-postadressen för ditt LIPAS-konto har ändrats"
         :body    "Hej!\n\nE-postadressen för ditt LIPAS-konto har ändrats till {{masked-email}}. Inga LIPAS-meddelanden skickas längre till den här adressen, och den kan inte längre användas för att logga in.\n\nOm du inte har gjort eller begärt ändringen, kontakta genast lipasinfo@jyu.fi\n\nMed vänliga hälsningar,\nLIPAS"}
    :en {:subject "Your LIPAS email address was changed"
         :body    "Hello!\n\nThe email address of your LIPAS account was changed to {{masked-email}}. LIPAS no longer sends messages to this address, and it can no longer be used to log in.\n\nIf you didn't make or request this change, contact lipasinfo@jyu.fi immediately.\n\nBest regards,\nLIPAS"}}})

(defn render-copy
  "{:subject :plain :html} from copy `body` and placeholder `values`
  ({:link \"https://…\" :valid-hours 24}). Values under `url-keys` become
  anchors in the HTML part; everything is escaped there."
  [subject body values url-keys]
  (let [placeholder #(str "{{" (name %) "}}")
        fill        (fn [s f] (reduce-kv (fn [acc k v] (str/replace acc (placeholder k) (f k v)))
                                         s values))
        html-value  (fn [k v]
                      (let [v (escape-html v)]
                        (if (contains? url-keys k) (str "<a href=\"" v "\">" v "</a>") v)))]
    {:subject subject
     :plain   (fill body (fn [_ v] (str v)))
     :html    (str "<html><body>"
                   (->> (str/split body #"\n\n")
                        (map #(-> (escape-html %)
                                  (fill html-value)
                                  (str/replace "\n" "<br>")))
                        (map #(str "<p>" % "</p>"))
                        (apply str))
                   "</body></html>")}))

(defn- send-copy!
  [emailer to kind lang values url-keys]
  (let [{:keys [subject body]} (get-in copy-texts [kind lang]
                                       (get-in copy-texts [kind :fi]))]
    (.send! emailer (assoc (render-copy subject body values url-keys) :to to))))

(defn send-registration-link-email!
  "Self-registration step 1: the link that proves the requester reads `to`."
  [emailer to lang {:keys [link valid-hours]}]
  (send-copy! emailer to :link lang
              {:link link :valid-hours valid-hours} #{:link}))

(defn send-registration-account-exists-email!
  "Sent instead of a registration link when `to` already has an account. The
  endpoint answers identically either way, so it can't be used to probe which
  addresses are registered; the address owner learns what to do instead."
  [emailer to lang {:keys [login-url reset-url]}]
  (send-copy! emailer to :account-exists lang
              {:login-url login-url :reset-url reset-url}
              #{:login-url :reset-url}))

(defn send-email-change-link-email!
  "Email change, step 1: the confirmation link, sent to the NEW address.
  `admin?` only changes who the copy says asked for the change."
  [emailer to lang {:keys [link valid-hours admin?]}]
  (send-copy! emailer to (if admin? :email-change-link-admin :email-change-link-self) lang
              {:link link :valid-hours valid-hours} #{:link}))

(defn send-email-change-in-use-email!
  "Sent to the new address instead of a link when it already has an account
  (self-service only: the requester sees the same answer either way)."
  [emailer to lang]
  (send-copy! emailer to :email-change-in-use lang {} #{}))

(defn send-email-changed-notice!
  "Email change, step 2: tells the OLD address the account moved. The new
  address is masked — the old mailbox may have passed to someone else."
  [emailer to lang {:keys [masked-email]}]
  (send-copy! emailer to :email-changed-notice lang {:masked-email masked-email} #{}))

;; --- PTV katselmointi notification -------------------------------------------
;; Sent to a municipality's PTV managers after DVV has reviewed
;; ("katselmoinut") their PTV texts. Contents are derived server-side from
;; the current audit sample (lipas.backend.ptv.core) — the email leads with
;; the items that await the municipality's fixes; when there are none, a
;; short all-approved note goes out instead. Finnish only, like the DVV
;; process itself. Copy lives here in code (org-membership emails set the
;; precedent) because the two variants share no useful template skeleton.

(def ^:private ptv-audit-field-names-fi
  {:summary "Tiivistelmä"
   :description "Kuvaus"
   :user-instruction "Toimintaohje"})

(def ^:private ptv-audit-section-copy
  {:sites {:subject-suffix "(liikuntapaikat)"
           :items-nom "kohteet"
           :items-part "kohdetta"
           :tab "liikuntapaikat"}
   :services {:subject-suffix "(palvelut)"
              :items-nom "palvelut"
              :items-part "palvelua"
              :tab "palvelut"}})

(defn ptv-audit-notification-message
  "Builds the katselmointi notification email as {:subject :plain :html}.
   `section` is :sites or :services; `action-items` ({:name .. :fields [..]})
   and `approved-count` come from lipas.data.ptv/audit-notification-summary."
  [{:keys [org-name section action-items approved-count]}]
  (let [{:keys [subject-suffix items-nom items-part tab]}
        (get ptv-audit-section-copy section)

        fixes? (boolean (seq action-items))
        item-line (fn [{:keys [name fields]}]
                    (str name " — "
                         (str/join ", " (keep ptv-audit-field-names-fi fields))))
        intro (str "DVV (Digi- ja väestötietovirasto) on katselmoinut organisaationne "
                   org-name " PTV-tekstejä " subject-suffix ".")
        fixes-heading (str "Seuraavat " items-nom " vaativat korjauksia ("
                           (count action-items) " kpl):")
        approved-line (when (pos? approved-count)
                        (str "Lisäksi " approved-count " " items-part
                             " on katselmoitu ilman muutospyyntöjä."))
        all-approved (str "Katselmoinnissa ei havaittu korjattavaa: "
                          approved-count " " items-part
                          " on katselmoitu ja hyväksytty.")
        navigate (if fixes?
                   (str "Kirjaudu LIPAS-järjestelmään ja avaa PTV-näkymän " tab
                        "-välilehti nähdäksesi katselmoijan palautteen ja"
                        " tehdäksesi tarvittavat korjaukset:")
                   (str "Voit tarkastella katselmoituja tekstejä LIPAS-järjestelmän"
                        " PTV-näkymän " tab "-välilehdellä:"))]
    {:subject (str (if fixes?
                     "PTV-katselmointi: korjauspyyntöjä "
                     "PTV-katselmointi valmis ")
                   subject-suffix)
     :plain (str/join "\n\n"
                      (remove nil?
                              ["Hyvä LIPAS-käyttäjä!"
                               intro
                               (when fixes?
                                 (str fixes-heading "\n\n"
                                      (str/join "\n" (map #(str "  - " (item-line %))
                                                          action-items))))
                               (if fixes? approved-line all-approved)
                               (str navigate "\nhttps://lipas.fi")
                               "Jos sinulla on kysyttävää, ota yhteyttä."
                               "Terveisin,\nLipas-järjestelmä"
                               (str "Jyväskylän yliopisto:\n"
                                    "Tapani Laakso, LIPAS-projekti, puh. 0400 247 980\n"
                                    "s-posti: lipasinfo@jyu.fi")]))
     :html (str "<html><body>"
                "<p>Hyvä LIPAS-käyttäjä!</p>"
                "<p>" (escape-html intro) "</p>"
                (when fixes?
                  (str "<p><b>" (escape-html fixes-heading) "</b></p>"
                       "<ul>"
                       (apply str (map #(str "<li>" (escape-html (item-line %)) "</li>")
                                       action-items))
                       "</ul>"))
                (if fixes?
                  (some->> approved-line escape-html (format "<p>%s</p>"))
                  (str "<p>" (escape-html all-approved) "</p>"))
                "<p>" (escape-html navigate) "<br>"
                "<a href=\"https://lipas.fi\">https://lipas.fi</a></p>"
                "<p>Jos sinulla on kysyttävää, ota yhteyttä.</p>"
                "<p>Terveisin,<br>Lipas-järjestelmä</p>"
                "<p>Jyväskylän yliopisto:<br>"
                "Tapani Laakso, LIPAS-projekti, puh. 0400 247 980<br>"
                "s-posti: lipasinfo@jyu.fi</p>"
                "</body></html>")}))

(defn send-ptv-audit-notification-email!
  [emailer to params]
  (.send! emailer (assoc (ptv-audit-notification-message params) :to to)))

(defn send-feedback-email!
  [emailer to feedback]
  (.send! emailer {:subject "LIPAS-palaute"
                   :to      to
                   :plain   (with-out-str (pprint/pprint feedback))
                   :html    (str "<html><body><pre>"
                                 (escape-html (with-out-str (pprint/pprint feedback)))
                                 "</pre></body></html>")}))

(defrecord SMTPEmailer [config]
  Emailer
  (send! [_ message] (send*! config message)))

(defrecord TestEmailer []
  Emailer
  (send! [_ _message] {:status "OK"}))

(comment
  (require '[lipas.backend.config :as config])

  (def emailer (SMTPEmailer. (-> config/default-config :emailer)))
  (def emailer2 (SMTPEmailer. (-> config/default-config
                                  :emailer
                                  (assoc :from "lipasinfo@jyu.fi"))))
  emailer2
  (send-permissions-updated-email! emailer2 "valtteri.harmainen@jyu.fi"
                                   {:link "www.kissa.fi" :valid-days 1}))
