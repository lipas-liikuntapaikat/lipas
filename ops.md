# Operations manual

## MML Population Grid Processing / Data Update ##

Population grids are updated once every 1-2 years. This affects analysis tools (reachability, diversity).

### Common Initial Steps ###

- Data from MML
  - 1km grid is open data available via Tilastokeskus Geoserver
  - 250m grid is paid data, purchased from MML
    - They'll provide an ESRI shapefile via a download link
- Load data in QGIS
- Calculate centroids (polygon -> point)
- Export as CSV, geometry in WKT, properties as plain strings, CRS WGS84 (important) !!!

### 1km Population Grid ###

- Run `lipas.backend.analysis.common/seed-population-1km-grid-from-csv!`
- Copy using elasticdump to a new index in production
  - Open tunnel `lipas-prod-tunnel-elastic`

```bash
export TMP_INDEX_NAME=population-1km-xxxx-xx-xxtxx-xx-xx-xxxxxx
elasticdump --input=http://elasticsearch:9200/$TMP_INDEX_NAME --output=http://localhost:9209/$TMP_INDEX_NAME --type=mapping
elasticdump --input=http://elasticsearch:9200/$TMP_INDEX_NAME --output=http://localhost:9209/$TMP_INDEX_NAME --type=data --limit 1000

# Remove existing indices from the alias
curl -X POST "localhost:9209/_aliases?pretty" -H 'Content-Type: application/json' -d'
  {
    "actions": [
      {
        "remove": {
          "index": "*",
          "alias": "vaestoruutu_1km"
        }
      }
    ]
  }
  '

# Create alias to point to the new index
curl -X POST "localhost:9209/_aliases?pretty" -H 'Content-Type: application/json' -d'
  {
    "actions": [
      {
        "add": {
          "index": "'$TMP_INDEX_NAME'",
          "alias": "vaestoruutu_1km"
        }
      }
    ]
  }
  '
```

### 250m Population Grid ###
- Run `lipas.backend.analysis.common/seed-population-250m-grid-from-csv!`
- Copy using elasticdump to a new index in production
- Open tunnel `lipas-prod-tunnel-elastic`

```bash
export TMP_INDEX_NAME=population-1km-xxxx-xx-xxtxx-xx-xx-xxxxxx
elasticdump --input=http://elasticsearch:9200/$TMP_INDEX_NAME --output=http://localhost:9209/$TMP_INDEX_NAME --type=mapping
elasticdump --input=http://elasticsearch:9200/$TMP_INDEX_NAME --output=http://localhost:9209/$TMP_INDEX_NAME --type=data --limit 1000

# Remove existing indices from the alias
curl -X POST "localhost:9209/_aliases?pretty" -H 'Content-Type: application/json' -d'
{
  "actions": [
    {
      "remove": {
        "index": "*",
        "alias": "vaestoruutu_250m"
      }
    }
  ]
}
'

# Create alias to point to the new index
curl -X POST "localhost:9209/_aliases?pretty" -H 'Content-Type: application/json' -d'
{
  "actions": [
    {
      "add": {
        "index": "'$TMP_INDEX_NAME'",
        "alias": "vaestoruutu_250m"
      }
    }
  ]
}
'
```

### Diversity (pre-calculated diversity grid) ###

Diversity grid is calculated from the 250m population grid by  amending each grid item with distances and travel times to each sports facility within 2km radius. OSRM is used for calculating the distances and travel times.

NOTE: Calculation takes about 8 hours

- Run lipas.backend.analysis.diversity/seed-new-grid-from-csv!
  - use 250m population grid as the input
    - sports facilities are queried from Elasticsearch, so make sure it contains up-to-date data from production
  - Takes about 8 HOURS
- Copy the reuslts using elasticdump to a new index in production
  - Open tunnel lipas-prod-tunnel-elastic

```bash
export TMP_INDEX_NAME=diversity-xxxx-xx-xxtxx-xx-xx-xxxxxx
elasticdump --input=http://elasticsearch:9200/$TMP_INDEX_NAME --output=http://localhost:9209/$TMP_INDEX_NAME --type=mapping
elasticdump --input=http://elasticsearch:9200/$TMP_INDEX_NAME --output=http://localhost:9209/$TMP_INDEX_NAME --type=data --limit 1000
```

Transfer takes a few minutes, depending mostly on VPN bandwidth

- Point 'diversity' alias to the new index

```bash
# Remove existing indices from the alias
curl -X POST "localhost:9209/_aliases?pretty" -H 'Content-Type: application/json' -d'
  {
    "actions": [
      {
        "remove": {
          "index": "*",
          "alias": "diversity"
        }
      }
    ]
  }
  '

# Create alias to point to the new index
curl -X POST "localhost:9209/_aliases?pretty" -H 'Content-Type: application/json' -d'
  {
    "actions": [
      {
        "add": {
          "index": "'$TMP_INDEX_NAME'",
          "alias": "diversity"
        }
      }
    ]
  }
  '
```
