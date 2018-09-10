(ns lipas.backend.email
  (:require [clojure.string :as str]
            [postal.core :as postal]))

(defprotocol Emailer
  (send! [config message]))

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
              {:type "text/html" :content html}]}))

(defn send-reset-password-email! [emailer to reset-link]
  (.send! emailer {:subject "Salasanan vaihtolinkki"
                   :to      to
                   :plain   (str reset-link)
                   :html    (str "<html><body>" reset-link "</body></html>")}))

(defn send-magic-login-email! [emailer to link]
  (.send! emailer {:subject "Taikalinkki Lipakseen"
                   :to      to
                   :plain   (str link)
                   :html    (str "<html><body>" link "</body></html>")}))

(defrecord SMTPEmailer [config]
  Emailer
  (send! [_ message] (send*! config message)))

(defrecord TestEmailer []
  Emailer
  (send! [_ message] {:status "OK"}))
