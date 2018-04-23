(ns auth-service.auth-resources.token-auth-backend
  (:require [environ.core :refer [env]]
            [buddy.auth.backends :refer [jws]]))

(def token-backend
  "Use the `jws` from the buddy library as our token backend. Tokens
  are valid for fifteen minutes after creation. If token is valid the decoded
  contents of the token will be added to the request with the keyword of
  `:identity`"
  (jws {:secret (env :auth-key) :options {:alg :hs512}}))
