# Operations Manual

## Infrastructure

### Environments

| Environment | Hostname | URL | Purpose |
|-------------|----------|-----|---------|
| Production | `lipas-prod.cc.jyu.fi` | https://lipas.fi | Live service |
| Development | `lipas-dev.cc.jyu.fi` | https://lipas-dev.cc.jyu.fi | Staging/testing |

Both environments are identical in configuration.

### Server Specifications (Production)

- **OS:** RHEL 9.7 (Plow)
- **CPU:** 4× Intel Xeon Gold 6240 @ 2.60GHz
- **RAM:** 30 GB
- **Storage:** ~300 GB on `/var` (primary application data)
- **Network:** Ports 80/443 open, SSH via VPN

### Docker Services

```
lipas-proxy-1           Nginx reverse proxy (80/443)
lipas-backend-1         Clojure web server (8091, nREPL 7888)
lipas-worker-1          Background job processor
lipas-postgres-1        PostGIS database (5432)
lipas-elasticsearch-1   Search engine (9200)
lipas-geoserver-1       WFS/WMS publishing (8888)
lipas-mapproxy-1        Tile caching
lipas-osrm-car-1        Car routing (5001)
lipas-osrm-bicycle-1    Bicycle routing (5002)
lipas-osrm-foot-1       Pedestrian routing (5003)
lipas-logstash-1        Log aggregation
lipas-kibana-1          Log visualization (5601)
```

### Responsibility Split

**JYU IT Services (digipalvelut)** - Infrastructure:
- Server infrastructure and virtualization
- Network and firewall configuration
- DNS and domain management
- SSL certificate management
- OS-level security updates
- VM-level backups
- Infrastructure monitoring and alerts
- Application-level backups (databases)
- Contact: https://help.jyu.fi/

**Norppandalotti Software Ky** - Application:
- LIPAS application development and updates
- Docker container management
- Database maintenance (PostGIS, Elasticsearch)
- External integrations (PTV, UTP CMS)
- Container and application level Deployments
- Application monitoring and troubleshooting

### Backup Strategy

**Infrastructure (JYU IT):**
- Daily incremental VM snapshots
- Daily PostgreSQL dumps (30-day retention)
- Monthly PostgreSQL dumps (180-day retention)
- Backup location: NFS mount at `/autodbbackup/backup`

**Application (Norppandalotti):**
- Source code in public GitHub repository
- Elasticsearch indices backed up as needed

### Access Requirements

1. **VPN:** JYU VPN with `appdevel` or `affiliate` profile
2. **SSH:** Key-based authentication to `lipas-dev` / `lipas-prod` hosts
3. Configure SSH host aliases in `~/.ssh/config`:
   ```
   Host lipas-dev
       HostName lipas-dev.cc.jyu.fi
       User your_username

   Host lipas-prod
       HostName lipas-prod.cc.jyu.fi
       User your_username
   ```

---

## Deployment

Deployment is **run from artifacts**: the backend uberjar and the frontend
bundle are built once on GitHub-hosted CI and installed on the servers by
`scripts/deploy/install.sh` — servers never compile from source. The git
checkout at `/var/lipas` provides configuration and orchestration only
(docker-compose.yml, nginx/mapproxy/geoserver config) and is pinned to the
same commit as the artifacts on every deploy. Design and rationale:
`docs/wip/deployment-consolidation.md`.

### The three deploy doors

| Door | Trigger | Builds run on |
|------|---------|---------------|
| lipas-dev | `lipas-dev` label on a PR | GitHub CI |
| lipas-prod | Manual "Deploy to lipas-prod" workflow + approval | GitHub CI |
| Break-glass | `bb deploy <dev\|prod>` from a laptop | The laptop |

**lipas-dev (label):** add the `lipas-dev` label to a PR — dev tracks that
PR's head (new pushes redeploy). Removing the label or closing the PR
redeploys `master`. The label is exclusive: applying it strips it from other
PRs. Every dev deploy runs migrations and a full search reindex.

**lipas-prod (dispatch):** GitHub → Actions → "Deploy to lipas-prod" → Run
workflow (ref, optional reindex) → approve the run when the `lipas-prod`
environment asks. Each successful deploy creates a `prod-YYYYMMDD-HHMM`
release with the shipped artifacts attached — the audit trail and rollback
store. **Rollback:** dispatch again with the previous release's commit.

**Break-glass (laptop):** for when GitHub or a runner is down. Connect VPN,
then `cd webapp && bb deploy prod`. Ships the **local working tree** via
scp + the same installer; the server's `/var/lipas` checkout stays untouched.

### What a deploy does (install.sh)

1. Pins `/var/lipas` to the built commit (config = code; skipped break-glass)
2. Installs `backend.jar` + frontend bundle (bundles before `index.html`)
3. Runs migrations from the jar, restarts backend + worker, recreates proxy
4. Verifies `https://localhost/api/health` end-to-end; stamps `/var/lipas/DEPLOYED`
5. Optionally reindexes Elasticsearch

### Self-hosted runners

Both hosts run a GitHub Actions runner under `/opt/lipas-runner` (own compose
project — see `infra/runner/README.md` for provisioning, upgrades and the
GitHub-side gates). The weekly "Runner heartbeat" workflow checks liveness
and disk on both runners.

### Config-only changes

Configuration served from the git checkout (nginx/mapproxy/geoserver conf,
static files under `webapp/resources/public/`) normally rides along with the
next deploy, since deploys pin the checkout. To push a config change alone:

```bash
ssh lipas-prod
cd /var/lipas
sudo git pull
sudo docker compose restart proxy  # or relevant service
```

Note `certs/` is also volume-mounted from the checkout (untracked content).

### Monitoring

- **Infrastructure:** JYU IT monitors server health and availability
- **Application:** External healthcheck service (custom) monitors all key service availability from the Internet

---

## Database

### Dump

```
docker exec -i lipas_postgres_1 pg_dump -U lipas -Fc lipas > $(date --iso-8601).$(hostname).db.backup
```

### Restore

```
docker exec -i lipas_postgres_1 pg_restore -U lipas -d lipas < 2024-06-17.lipas-prod2.db.backup > restore.log
```

## Updating OSM data

OSM data is used by OSRM to calculate distances and travel times by walking, car or bicycle. This is used in analysis tools (diversity, reachability).

The data is updated yearly.

Operation takes ~30 minutes to execute in production.

See [osrm/README.md](../osrm/README.md).

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

**IMPORTANT:** Tilastokeskus column names may vary between data releases. Normalize field names to match existing indices:
- `grid_id` → `grd_id`
- `euref_x` → `xkoord`, `euref_y` → `ykoord`
- `he_vakiy` → `vaesto`
- For 1km grid: `id` → `gid`

If field names differ, either rename columns in QGIS/CSV before import, or use Elasticsearch reindex API with Painless script to transform field names after seeding.

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

NOTE: The July 2026 batching rewrite (tiled multi-source OSRM table
requests, ~3000x fewer HTTP round-trips) replaced the implementation
that took about 8 hours for a full-country seed. Expect the seed to be
dominated by dense urban tiles' foot-profile tables; re-measure on the
first full run and update this note.

- Run lipas.backend.analysis.diversity/seed-new-grid-from-csv!
  - use 250m population grid as the input
    - sports facilities are queried from Elasticsearch, so make sure it contains up-to-date data from production
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

### Subsidies

OKM and AVI are the two main sports facility related subsidy issuers in Finland. The data from both sources is combined manually to an Excel-file by the Lipas-team.

The data is updated yearly to Lipas. The updated data contains subsidies considering the current year.

The data is stored to the db and indexed from db to elasticsearch.

- Acquire the Excel file from the team
- Save Excel as CSV
  - The CSV should contain only "new data" (no historical data)
- Upload the csv to prod server
- Run `lipas.maintenance/add-subsidies-from-csv!` from the REPL
- Enable current year in the stats -> subsidies UI
  - `lipas.ui.stats.subsidies.views` (year selector valid range)
  - `lipas.ui.stats.subsidies.db` (selected year default value)

### City finance data

We have traditionally collected and distributed information on the finances of municipalities' sports and youth activities. The data source changed in 2021, and the format also changed slightly. Therefore, the information before and after 2021 is not entirely comparable.

The Lipas team receives data in Excel format from the Association of Finnish Local and Regional Authorities. The data is updated annually. Typically, information for the previous year is obtained only in the following year; for example, data for the year 2022 is usually received by the end of 2023.

The data is stored to the db and indexed from db to elasticsearch.

- Acquire the Excel file from the team
- Save Excel as CSV
  - The CSV should contain only "new data" (no historical data)
- Upload the csv to prod server
- Run `lipas.maintenance/add-city-finance-stats-from-csv!` from the REPL
- Enable current year in the stats -> city finance UI
  - `lipas.ui.stats.finance.views` (year selector valid range)
  - `lipas.ui.stats.finance.db` (selected year default value)

## SSL Certificate Management

See [certs/README.md](../certs/README.md)
