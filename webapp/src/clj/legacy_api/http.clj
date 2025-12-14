(ns legacy-api.http
  (:require [clojure.string :as str]
            [ring.util.codec :refer [form-encode]]))

(defn linked-partial-content
  [body {:keys [first last next prev total]}]
  {:status 206
   :headers {"Link" (str "<" next ">; rel=\"next\", "
                         "<" last ">; rel=\"last\", "
                         "<" first ">; rel=\"first\", "
                         "<" prev ">; rel=\"prev\"")
             "X-total-count" (str total)}
   :body body})

(defn last-page
  [total page-size]
  (int (Math/ceil (/ total page-size))))

(defn create-page-links
  [path query-params page page-size total]
  {:first (str path "/?" (form-encode (assoc query-params "page" 1)))
   :next (str path "/?" (form-encode (assoc query-params "page" (inc page))))
   :prev (str path "/?" (form-encode (assoc query-params "page"
                                            (max (dec page) 1))))
   :last (str path "/?" (form-encode (assoc query-params "page"
                                            (last-page total page-size))))
   :total total})

(defn- get-header
  "Get a header value case-insensitively from Ring request headers."
  [headers header-name]
  (let [lower-name (str/lower-case header-name)]
    (some (fn [[k v]]
            (when (= (str/lower-case k) lower-name)
              v))
          headers)))

(defn extract-base-path
  "Extract the base path for link generation from a Ring request.

   The base path is determined by:
   1. X-Forwarded-Prefix header (set by nginx) - preferred
   2. Extracting from request URI - fallback

   Different entry points set different prefixes:
   - api.lipas.fi/v1/...              -> /v1
   - lipas.fi/rest/api/...            -> /rest/api
   - lipas.cc.jyu.fi/api/...          -> /api
   - lipas.fi/legacy-api/...          -> /legacy-api"
  [req]
  (let [headers (:headers req)
        forwarded-prefix (get-header headers "x-forwarded-prefix")]
    (if forwarded-prefix
      forwarded-prefix
      ;; Fallback: extract from URI
      ;; URI looks like: /rest/api/sports-places or /rest/api/sports-places/123
      ;; We want to extract: /rest/api
      (let [uri (:uri req)]
        (when uri
          (let [;; Match known patterns (order matters - more specific first)
                patterns [#"^(/rest/api)" #"^(/legacy-api)" #"^(/api)" #"^(/v1)"]
                match (some #(re-find % uri) patterns)]
            (when match
              (second match))))))))

(defn build-sports-places-path
  "Build the sports-places endpoint path from a base prefix."
  [base-path]
  (str base-path "/sports-places"))
