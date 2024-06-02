(ns lipas.integration.ptv.core
  (:require
   [lipas.backend.search :as search]
   [cheshire.core :as json]
   [clj-http.client :as client]))

(def test-config
  {:api-url   "https://palvelutietovaranto.trn.suomi.fi/api"
   :token-url "https://palvelutietovaranto.trn.suomi.fi/api/auth/api-login"
   :creds
   {:org-id "92374b0f-7d3c-4017-858e-666ee3ca2761"
    :main-user
    {:username "paakayttaja35.testi@testi.fi"
     :password "Paatestaaja35-1035*"}
    :maintainer
    {:username "yllapitaja35.testi@testi.fi"
     :password "Yllapitajatestaaja35-1035*"}
    :api
    {:username "API14@testi.fi"
     :password "APIinterfaceUser14-1014*"}}})

(defn authenticate
  "If API account is connected to multiple organisations, user should
  define Palveluhallinta organisation ID by using apiUserOrganisation parameter.

  If parameter is not given, then token return authentication (token)
  for active organization (can be check from Palveluhallinta UI)."
  [{:keys [token-url username password org-id]}]
  (let [params {:headers {:Content-Type "application/json"}
                :body    (merge {:username username :password password}
                                (when org-id
                                  {:apiUserOrganisation org-id}))}]
    (-> (client/post token-url params)
        :body
        (json/decode keyword))))

(defn get-eligible-sites
  [{:keys [indices client] :as _search}
   {:keys [city-codes owners] :as _criteria}]
  (let [idx-name (get-in indices [:sports-site :search])
        params   {:size             5000
                  :track_total_hits 50000
                  :_source          {:excludes ["location.geometries.*"
                                                "search-meta.location.geometries.*"
                                                "search-meta.location.simple-geoms.*"]}
                  :query
                  {:bool
                   {:must
                    [{:terms {:status.keyword ["active" "out-of-service-temporarily"]}}
                     (when city-codes
                       {:terms {:location.city.city-code city-codes}})
                     (when owners
                       {:terms {:owner owners}})]}}}]
    (-> (search/search client idx-name params)
        :body
        :hits
        :hits
        (->> (map :_source)))))
