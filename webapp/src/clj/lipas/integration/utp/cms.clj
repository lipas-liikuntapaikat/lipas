(ns lipas.integration.utp.cms
  (:require [cheshire.core :as json]
            [clj-http.client :as client]
            [cemerick.url :as url]
            [lipas.backend.config :as config]))

(def api-url (get-in config/default-config [:app :utp :cms-api-url]))

(def host (let [{:keys [protocol host]} (url/url api-url)]
            (str protocol "://" host)))

(def basic-auth-creds
  [(get-in config/default-config [:app :utp :cms-api-user])
   (get-in config/default-config [:app :utp :cms-api-pass])])

(def default-headers
  {:Content-Type "application/vnd.api+json"
   :Accept       "application/vnd.api+json"})

(defn resolve-media-directory-id
  []
  (let [url (str api-url
                 "/taxonomy_term/media_directories?"
                 "filter[name]=Lipas&fields[taxonomy_term--media_directories]=id")]
    (-> (client/get url {:basic-auth basic-auth-creds :headers default-headers})
        :body
        (json/decode keyword)
        :data
        first
        :id)))

(defn upload-blob!
  [{:keys [filename data]}]
  (let [url     (str api-url "/media/image/field_media_image")
        headers (merge default-headers
                       {:Content-Type        "application/octet-stream"
                        :Content-Disposition (format "file; filename=\"%s\"" filename)})]
    (-> (client/post url {:basic-auth basic-auth-creds :headers headers :body data})
        :body
        (json/decode keyword))))

(defn create-media-entity!
  [{:keys [filename media-directory-id media-id]}]
  (let [url  (str api-url "/media/image")
        body (json/encode
              {:data
               {:type       "media--image",
                :attributes {:name filename},
                :relationships
                {:field_media_image
                 {:data {:type "file--file", :id media-id}},
                 :directory
                 {:data {:type "taxonomy_term--media_directories", :id media-directory-id}}}}})]
    (-> (client/post url {:basic-auth basic-auth-creds :headers default-headers :body body})
        :body
        (json/decode keyword))))

(defn upload-image!
  [{:keys [filename data user] :as params}]
  (assert filename "Key :filename must have a value")
  (assert data "Key :data must have a value")
  (assert user "Key :user must have a value")
  (let [dir-id (resolve-media-directory-id)
        resp1  (upload-blob! params)
        resp2  (create-media-entity! {:filename           filename
                                      :media-directory-id dir-id
                                      :media-id           (-> resp1 :data :id)})
        url    (str host (-> resp1 :data :attributes :uri :url))]
    {:public-urls        (merge
                          {:original url}
                          (-> resp1 :data :attributes :image_style_uri))
     :media-directory-id dir-id
     :filename           filename
     :file-id            (-> resp1 :data :id)
     :file-href          (-> resp1 :data :links :self :href)
     :filemime           (-> resp1 :data :attributes :filemime)
     :filesize           (-> resp1 :data :attributes :filesize)
     :media-image-id     (-> resp2 :data :id)
     :media-image-href   (-> resp2 :data :links :self :href)
     #_#_:lipas-user-id      (:id user)}))

(comment
  (require '[clojure.java.io :as io])
  (def dir-id (resolve-media-directory-id))
  (def resp1 (upload-blob! {:filename "panda.jpeg" :data (io/input-stream "/Users/tipo/Desktop/panda.jpeg")}))

  (def resp2 (create-media-entity! {:filename           "panda.jpeg"
                                    :media-directory-id dir-id
                                    :media-id           (-> resp1 :data :id)}))
  )
