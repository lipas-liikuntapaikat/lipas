(ns lipas.backend.email
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [postal.core :as postal]))

(defprotocol Emailer
  (send! [config message]))

(def templates
  {:fi
   {:magic-link
    {:html (slurp (io/resource "email_templates/magic_link_fi.html"))
     :text (slurp (io/resource "email_templates/magic_link_fi.txt"))}}})

(defn send*!
  "Thin wrapper for postal."
  [{:keys [host user pass from]}
   {:keys [to subject plain html]}]
  (postal/send-message
   {:host host
    :user user
    :pass pass
    :ssl  true}
   {:from    from
    :to      to
    :subject subject
    :body    [:alternative
              {:type "text/plain" :content plain}
              {:type "text/html;charset=utf-8" :content html}]}))

(defn send-reset-password-email! [emailer to reset-link]
  (.send! emailer {:subject "Salasanan vaihtolinkki"
                   :to      to
                   :plain   (str reset-link)
                   :html    (str "<html><body>" reset-link "</body></html>")}))

(defn send-magic-login-email! [emailer to link]
  (.send! emailer {:subject "Jää- ja uimahalliportaalit ovat nyt Lipaksessa"
                   :to      to
                   :plain   (-> templates
                                :fi
                                :magic-link
                                :text
                                (str/replace "{{link}}" link))
                   :html    (-> templates
                                :fi
                                :magic-link
                                :html
                                (str/replace "{{link}}" link))}))

(defrecord SMTPEmailer [config]
  Emailer
  (send! [_ message] (send*! config message)))

(defrecord TestEmailer []
  Emailer
  (send! [_ message] {:status "OK"}))
