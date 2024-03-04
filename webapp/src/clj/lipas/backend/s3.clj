(ns lipas.backend.s3
  (:import
   (java.time Duration);
   (software.amazon.awssdk.regions Region)
   (software.amazon.awssdk.services.s3.model PutObjectRequest)
   #_(software.amazon.awssdk.services.s3.model S3Exception);
   #_(software.amazon.awssdk.services.s3.presigner.model PresignedPutObjectRequest);
   (software.amazon.awssdk.services.s3.presigner S3Presigner);
   (software.amazon.awssdk.services.s3.presigner.model PutObjectPresignRequest)))

(defn presign-put
  [{:keys [bucket region object-key content-type credentials-provider meta]
    :or   {meta {}}}]
  (let [meta      (-> meta
                      (update-keys name)
                      (update-vals str))
        presigner (-> (S3Presigner/builder)
                      (doto
                          (.region (Region/of region))
                        (.credentialsProvider credentials-provider))
                      (.build))

        put-req (-> (PutObjectRequest/builder)
                    (doto
                        (.bucket bucket)
                      (.key object-key)
                      (.contentType content-type)
                      (.metadata meta))
                    (.build))

        presign-req (-> (PutObjectPresignRequest/builder)
                        (doto
                            (.signatureDuration (Duration/ofMinutes 30))
                          (.putObjectRequest put-req))
                        (.build))]

    {:presigned-url (-> presigner
                        (.presignPutObject presign-req)
                        (.url)
                        (.toString))
     :meta          (update-keys meta #(str "x-amz-meta-" %))}))

(comment
  (import '(software.amazon.awssdk.auth.credentials DefaultCredentialsProvider))
  (presign-put {:region               "eu-north-1"
                :bucket               "lipas-data"
                :object-key           "peruna.gif"
                :content-type         "image/gif"
                :meta                 {:kissa "koira" :kana "heppa"}
                :credentials-provider (DefaultCredentialsProvider/create)}))
