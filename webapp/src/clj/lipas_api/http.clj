(ns lipas-api.http
  (:require [ring.util.codec :refer [form-encode]]))

(defn linked-partial-content
  [body {:keys [first last next prev total]}]
  {:status  206
   :headers {"Link" (str "<" next ">; rel=\"next\", "
                         "<" last ">; rel=\"last\", "
                         "<" first ">; rel=\"first\", "
                         "<" prev ">; rel=\"prev\"")
             "X-total-count" (str total)}
   :body    body})

(defn last-page
  [total page-size]
  (int (Math/ceil (/ total page-size))))

(defn create-page-links
  [path query-params page page-size total]
  {:first (str path "/?" (form-encode (assoc query-params "page" 1)))
   :next  (str path "/?" (form-encode (assoc query-params "page" (inc page))))
   :prev  (str path "/?" (form-encode (assoc query-params "page"
                                             (max (dec page) 1))))
   :last  (str path "/?" (form-encode (assoc query-params "page"
                                             (last-page total page-size))))
   :total total})
