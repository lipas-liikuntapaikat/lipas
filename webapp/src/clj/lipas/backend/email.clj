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
     :text    (safe-slurp "email_templates/reminder_fi.txt")}
    :ptv-audit-complete
    {:subject "PTV-auditointi valmistunut"
     :html    (safe-slurp "email_templates/ptv_audit_complete_fi.html")
     :text    (safe-slurp "email_templates/ptv_audit_complete_fi.txt")}}})

(defn send*!
  "Thin wrapper for postal."
  [{:keys [host port user pass from]}
   {:keys [to subject plain html]}]
  (postal/send-message
    (merge
      {:host host}
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

(defn send-ptv-audit-complete-email!
  [emailer to {:keys [org-name site-count summary-approved summary-changes desc-approved desc-changes]}]
  (.send! emailer {:subject (-> templates :fi :ptv-audit-complete :subject)
                   :to      to
                   :plain   (-> templates
                                :fi
                                :ptv-audit-complete
                                :text
                                (str/replace "{{org-name}}" org-name)
                                (str/replace "{{site-count}}" site-count)
                                (str/replace "{{summary-approved}}" summary-approved)
                                (str/replace "{{summary-changes}}" summary-changes)
                                (str/replace "{{desc-approved}}" desc-approved)
                                (str/replace "{{desc-changes}}" desc-changes))
                   :html    (-> templates
                                :fi
                                :ptv-audit-complete
                                :html
                                (str/replace "{{org-name}}" org-name)
                                (str/replace "{{site-count}}" site-count)
                                (str/replace "{{summary-approved}}" summary-approved)
                                (str/replace "{{summary-changes}}" summary-changes)
                                (str/replace "{{desc-approved}}" desc-approved)
                                (str/replace "{{desc-changes}}" desc-changes))}))

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
