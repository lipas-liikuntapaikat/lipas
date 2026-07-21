(ns lipas.backend.email
  (:require [clojure.java.io :as io]
            [clojure.pprint :as pprint]
            [clojure.string :as str]
            [postal.core :as postal]))

(defprotocol Emailer
  (send! [config message]))

(defn safe-slurp [path]
  (some-> (io/resource path) slurp))

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

(defn send*!
  "Thin wrapper for postal."
  [{:keys [host port user pass from]}
   {:keys [to subject plain html]}]
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
   {:from    from
    :to      to
    :subject subject
    :body    (cond-> [:alternative]
               (not-empty plain) (conj {:type "text/plain" :content plain})
               (not-empty html)  (conj {:type "text/html;charset=utf-8" :content html})
               ;; Fallback if both are empty to avoid NPE
                (and (empty? plain) (empty? html)) (conj {:type "text/plain" :content ""}))}))

(defn send-reset-password-email!
  [emailer to {:keys [link]}]
  (.send! emailer {:subject "Salasanan vaihtolinkki"
                   :to      to
                   :plain   (str link)
                   :html    (str "<html><body>" link "</body></html>")}))

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
                                (str/replace "{{link}}" link)
                                (str/replace "{{valid-days}}" (str valid-days)))}))

(defn send-register-notification!
  [emailer to user]
  (.send! emailer {:subject "Uusi rekisteröitynyt käyttäjä"
                   :to      to
                   :plain   (with-out-str (pprint/pprint user))
                   :html    (str "<html><body>"
                                 (with-out-str (pprint/pprint user))
                                 "</body></html>")}))

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
                                (str/replace "{{link}}" link)
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
  (let [fill  (fn [s] (-> s
                          (str/replace "{{org-name}}" (str org-name))
                          (str/replace "{{link}}" (str link))
                          (str/replace "{{valid-days}}" (str valid-days))))
        langs [:fi :se :en]
        block (fn [kind lang]
                (str (fill (get-in org-membership-intros [lang kind]))
                     ({:plain "\n" :html " "} kind)
                     (fill (get-in org-membership-actions [variant lang kind]))))]
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
                                (str/replace "{{message}}" message)
                                (str/replace "{{link}}" link)
                                (str/replace "{{valid-days}}" (str valid-days)))}))

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

(defn- escape-html [s]
  (-> (str s)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")))

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
                   :html    (str "<html><body>"
                                 (with-out-str (pprint/pprint feedback))
                                 "</body></html>")}))

(defrecord SMTPEmailer [config]
  Emailer
  (send! [_ message] (send*! config message)))

(defrecord TestEmailer []
  Emailer
  (send! [_ message] {:status "OK"}))

(comment
  (require '[lipas.backend.config :as config])

  (def emailer (SMTPEmailer. (-> config/default-config :emailer)))
  (def emailer2 (SMTPEmailer. (-> config/default-config
                                  :emailer
                                  (assoc :from "lipasinfo@jyu.fi"))))
  emailer2
  (send-permissions-updated-email! emailer2 "valtteri.harmainen@jyu.fi"
                                   {:link "www.kissa.fi" :valid-days 1}))
