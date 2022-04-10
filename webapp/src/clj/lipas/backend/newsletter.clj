(ns lipas.backend.newsletter
  (:require [cheshire.core :as json]
            [clj-http.client :as client]
            [lipas.backend.config :as config]
            [taoensso.timbre :as log]))

(defn retrieve
  [{:keys [api-url api-key campaign-folder-id]}]
  (let [url  (str api-url "/campaigns")
        opts {:query-params {:folder_id campaign-folder-id}
              :basic-auth   (str ":" api-key)}]
    (-> (client/get url opts)
        :body
        (json/decode keyword)
        :campaigns
        (->>
         (filter (comp #{"sent"} :status))
         (map
          (fn [m]
            {:send-time    (:send_time m)
             :url          (:long_archive_url m)
             :title        (-> m :settings :title)
             :preview-text (-> m :settings :preview_text)
             :subject-line (-> m :settings :subject_line)
             :from-name    (-> m :settings :from_name)}))))))

(defn subscribe
  [{:keys [api-url api-key list-id newsletter-interest-id]}
   {:keys [email]}]
  (let [url  (str api-url "/lists/" list-id)
        opts {:basic-auth   (str ":" api-key)
              :content-type :json
              :accept       :json
              :body         (json/encode
                             {:update_existing true ;; Don't care if already on list
                              :members
                              [{:status        "subscribed"
                                :email_address email
                                :interests     {newsletter-interest-id true}}]})}
        resp (-> (client/post url opts)
                 :body
                 (json/decode keyword))]
    
    (when-let [error (first (:errors resp))]
      (let [ex (ex-info "Newsletter subscription failed" error)]
        (log/error ex)
        (throw ex)))

    {:status "ok"}))
