(ns lipas.backend.address.pelias
  "Digitransit's Pelias geocoder, reverse direction: a WGS84 point in, the
  nearest *known* addresses out.

  Pelias answers the half of reverse geocoding our own tables cannot: which
  buildings are near this point and how far away they are. What it says about
  postal codes is a hint, not an answer — the code travels with the nearest
  address record (NLS/OpenAddresses/OSM), so near a postal boundary or out in
  the woods it belongs to a house a kilometre away. `lipas.backend.address.core`
  treats it accordingly.

  This namespace is the HTTP call and nothing else. It throws on any failure —
  timeout, 4xx, 5xx, malformed body — and the caller decides what a missing
  answer means; degrading here would hide an expired subscription key behind a
  permanently empty address list."
  (:require
    [clj-http.client :as client]))

(def reverse-url "https://api.digitransit.fi/geocoding/v1/reverse")

(def ^:private timeout-ms
  "The endpoint sits in a user-facing map click, so a slow Pelias must lose
  quickly and let the Paavo/Posti half of the answer through."
  5000)

(def ^:private query-defaults
  "`sources` and `layers` restrict the result to address points — venues and
  streets have no building number and cannot be checked against BAF. `size`
  is trimmed to five in the response; the extra candidates give the summary
  rules a chance to find a BAF-known address behind a phantom one. The radius
  is in kilometres."
  {"sources" "nlsfi,oa,osm"
   "layers" "address"
   "size" "10"
   "boundary.circle.radius" "5"})

(defn nearest-addresses
  "The GeoJSON `:properties` of the address features Pelias finds around
  (`lat`, `lon`), nearest first — `:street`, `:housenumber`, `:label`,
  `:localadmin`, `:postalcode` and `:distance` (in kilometres) are what the
  caller reads.

  `config` is the `:pelias` app config: `:subscription-key` (required by
  api.digitransit.fi since 2023) and an optional `:url` override.

  Throws on any HTTP or connection failure."
  [{:keys [subscription-key url]} lat lon]
  (-> (client/get (or url reverse-url)
                  {:as :json
                   :socket-timeout timeout-ms
                   :connection-timeout timeout-ms
                   :headers {"digitransit-subscription-key" subscription-key}
                   :query-params (assoc query-defaults
                                        "point.lat" (str lat)
                                        "point.lon" (str lon))})
      :body
      :features
      (->> (mapv :properties))))
