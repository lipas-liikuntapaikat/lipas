# Lipas-API V1

> **⚠️ Important Notice**
>
> This documentation describes LIPAS API V1, which was created in 2017. For the latest API versions (V2 and newer) and up-to-date documentation, please visit:
>
> 🔗 [https://api.lipas.fi](https://api.lipas.fi)
>
> While V1 continues to be maintained for backwards compatibility, it is in feature freeze and no new functionality will be added. We recommend using the latest API version for new implementations.
>
> The V1 documentation below remains available for existing integrations.

## README

REST-service for Open data of Finnish sports-places

This repository contains currently only documentation and issue tracking. Source code *may* be available some day in the future.

[Swagger](http://lipas.cc.jyu.fi/api/index.html "Link to Swagger docs") | [Lipas info](https://www.jyu.fi/sport/laitokset/liikunta/liikuntapaikat "Lipas info") | [Lipas web-app](http://lipas.cc.jyu.fi/lipas/ "Link to Lipas web-application")

Lipas is a nationwide, public GIS service for Finnish sport sites. Lipas is maintained by the Faculty of Sport and Health Sciences of the University of Jyväskylä. Our finance comes from the ministry of Culture and education. Lipas system has information on sport sites, routes and recreational areas and economy.

## Endpoints

[Swagger](http://lipas.cc.jyu.fi/api/index.html "Link to Swagger docs") UI contains descriptions of all endpoints. Models in Lipas Swagger spec are not perfect because Swagger 2.0 lacks support for oneOf which makes it near impossible to describe polymorphic GeoJSON entries. Error messages provide more detailed information, which might be helpful when integrating.

Optional `lang` parameter provides Swedish and English localization for certain fields (i.e. sports-place name, type and administration fields). Please note that not all data is localized. Default locale is `fi`. In `POST` and `PUT` endpoints `lang` parameter doesn't have significance to input data.

### GET /sports-places

Returns list of sports-places. This is *the endpoint* for most use-cases. List can be filtered in several ways in order to get relevant results:

* fields | determines which data fields will be available in the response. By default only sportsPlaceId is returned.
* typeCodes | get only certain types of sports-places (e.g. tennis halls, ski jump hills...). See endpint GET /spors-place-types for available type-codes.
* cityCodes | get sports-places located in certain cities (e.g. Helsinki, Äänekoski...). See [official list of city codes in Finland](https://fi.wikipedia.org/wiki/Luettelo_Suomen_kuntanumeroista "Link to wikipedia article about official city codes in Finland")
* closeTo* | get sports-places within given radius from given point. **Lat/Lon point must be given as WGS84**
* searchString | [ElasticSearch query string](https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl-query-string-query.html#query-string-syntax "Link to ElasticSearch query string syntax documentation"), matches all fields by default.

#### Pagination

Results are paged by `pageSize` and `page` parameters. Endpoint returns `HTTP206` when results are paged and `HTTP200` when result-set is complete. Max page size is 100, default is 50. Links to first, last, next and previous page are provided in response `link` header ([GitHub style](https://developer.github.com/v3/guides/traversing-with-pagination/ "Link to GitHub pagination specification")). `X-total-count` header provides the total count of available results.

### GET /sports-places/<sports-place-id>

Returns single sports-place with all fields.

### GET /sports-place-types

Returns list of available sports place types. Also available as [PDF](https://www.jyu.fi/sport/laitokset/liikunta/liikuntapaikat/kayttoohjeet/kayttoohjeet-lipas-2013/LipasTypes_inenglish18012017.pdf "Link to pdf about sports place types").

### GET /sports-place-types/{type-code}

Returns details about single sports place type. Each type contains a set of additional `properties` which vary between different sports place types.

### GET /categories

Returns higher level categorization of sports place types.

## Geometries

Lipas provides geometries in two available forms:

* Simple lat/lon points (WGS84 and TM35FIN)
* GeoJSON FeatureCollections

Internally geometries are presented in Lipas as TM35FIN since it's the Finnish national standard. However latest [GeoJSON specification](https://tools.ietf.org/html/rfc7946#section-4 "Link to GeoJSON specification") mandates that all geometries must be represented as WGS84. Therefore Lipas REST-API returns all GeoJSON responses in WGS84.

**Note:** Currently there are ~300 sports places which don't have geometries.

**Note:** Some precision is lost when geometries are converted to and back between TM35FIN and WGS84. In practice difference is very small, but as side-effect some inputted geometries might be slightly different when they come out from Lipas. E.g. inputted double `25.0` might become `24.999999998`.

### Simple points

Simple points are available as [TM35FIN](https://epsg.io/3067 "Link to TM35FIN specification") and [WGS84](https://epsg.io/4326 "Link to WGS84 specification") coordinates. In case of more complex geometries (LineStrings, Polygons), starting point is returned.

```
"location": {
      "coordinates": {
        "tm35fin": {
          "lon": 394188.910987268,
          "lat": 7381487.70351918
        },
        "wgs84": {
          "lon": 24.6182741658716,
          "lat": 66.534329249287
        }
      }
    }
```

### GeoJSON FeatureCollections

Lipas geometries are subset of [GeoJSON](http://geojson.org/ "Link to GeoJSON web page") Geometries. Geometries are always wrapped into Features and Features are always wrapped into a FeatureCollection. Supported geometry types are `Point`, `LineString` and `Polygon`. One sports-place type can contain only features of one geometry type, e.g. "Ski track" geometries must always be LineStrings.

It should be possible to drop any FeatureCollection outputted from Lipas into e.g. [geojson.io](http://geojson.io/ "Link to geojson.io").

#### Routes (LineStrings)

Routes are hierachial in Lipas: RouteCollection -1-n-> Route -m-n-> RouteSegment.

Each Feature in route FeatureCollection represents a RouteSegment. Feature `properties` provides data to construct Routes and RouteCollections from segments.

```
{
  "type": "Feature",
  "geometry": {
    "coordinates": [
      [
        29.182666099758,
        65.9716556598375
      ],
      [
        29.1837907832021,
        65.9716756001474
      ],
      ...
    ],
    "type": "LineString"
  },
  "properties": {
    "routeCollectionId": 500146,
    "routeCollectionName": null,
    "routeId": 500192,
    "routeName": "Ympäryslatu",
    "routeSegmentId": 500469,
    "routeSegmentName": "Ympäryslatu_osa_1"
  }
}
```

#### Areas (Polygons)

Areas are simpler than Routes: Area -1-*-> AreaSegment. Feature `properties` provide data to construct Areas from segments.

```
{
  "type": "Feature",
  "geometry": {
    "type": "Polygon",
    "coordinates": [
      [
        [
          25.1871101980935,
          61.9253176922796
        ],
        [
          25.1872336669947,
          61.9253238070079
        ],
        ...
      ]
    ]
  },
  "properties": {
    "areaId": 500022,
    "areaName": null,
    "areaSegmentId": 500020,
    "areaSegmentName": "Osa_1"
  }
}
```

#### Points

Points are normal GeoJSON points with one property `pointId`. Property is redundant but GeoJSON spec states there must be something.

```
{
  "type": "Feature",
  "geometry": {
    "coordinates": [
      24.9262778233189,
      60.1831081931234
    ],
    "type": "Point"
  },
  "properties": {
    "pointId": 70001
  }
}
```

## Contact

* lipasinfo@jyu.fi

## License

Copyright © Jyväskylän Yliopisto
