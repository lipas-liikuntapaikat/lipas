# LIPAS webapp

Webapp is written in Clojure(script).

[Architecture diagram](https://drive.google.com/file/d/18JercdBIV_QO8HOXz4uBjAMPRvhy2CUW/view?usp=sharing)

## Backend

* [reitit](https://github.com/metosin/reitit) HTTP request handler
* [PostGIS](https://postgis.net/) spatial database
* [Elasticsearch](https://www.elastic.co/) search engine (under construction)
* [Mapproxy](https://mapproxy.org/) basemap proxy and cache
* [Geoserver](http://geoserver.org/) publishing spatial data (under construction)
* [Nginx](https://www.nginx.com/) reverse proxy, ssl-termination

### Handler

Handler is a simple HTTP(s) service with endpoints for different
tasks. Some endpoints require authentication. Authentication is
implemented using HTTP-basic authentication and signed JWT-tokens.

## Frontend

* [re-frame](https://github.com/Day8/re-frame)
* [material-ui](https://material-ui.com/)

Frontend is a Single Page Application (SPA)

### Development

#### Start Cider from Emacs:

Put this in your Emacs config file:

```
(setq cider-cljs-lein-repl
	"(do (require 'figwheel-sidecar.repl-api)
         (figwheel-sidecar.repl-api/start-figwheel!)
         (figwheel-sidecar.repl-api/cljs-repl))")
```

Navigate to a clojurescript file and start a figwheel REPL with `cider-jack-in-clojurescript` or (`C-c M-J`)

### Run application:

```
lein clean
lein figwheel dev
```

Figwheel will automatically push cljs changes to the browser.

Wait a bit, then browse to [http://localhost:3449](http://localhost:3449).

## Production Build

To compile clojurescript to javascript:

Using `lein`:

```
lein clean
lein cljsbuild once min
```

Using Docker:

`docker-compose run frontend-build`
