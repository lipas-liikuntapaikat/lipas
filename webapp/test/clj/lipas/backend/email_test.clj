(ns lipas.backend.email-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [lipas.backend.email :as email]
            [postal.message :as message]))

(defn- raw-mime
  "The message exactly as it would go on the wire (7-bit: QP-encoded parts)."
  [msg]
  (message/message->str msg))

(deftest both-parts-are-utf8-test
  ;; The plain-text part used to be declared bare "text/plain": JavaMail then
  ;; wrote Latin-1 bytes (ä => =E4) under a charset=UTF-8 label, so every
  ;; client that shows the text part garbled ä/ö/å in every LIPAS mail. Only
  ;; the HTML part, declared with its charset, was right.
  (let [raw (raw-mime (email/postal-message "dev@lipas.fi"
                                            {:to      "u@example.com"
                                             :subject "Hei"
                                             :plain   "pyytänyt"
                                             :html    "<p>pyytänyt</p>"}))]
    (is (not (str/includes? raw "pyyt=E4nyt")) "Latin-1 bytes under a UTF-8 label")
    (is (= 2 (count (re-seq #"pyyt=C3=A4nyt" raw))) "both parts UTF-8 encoded")))
