#!/bin/bash
# Backup Elasticsearch indexes before ES 7->8 upgrade
# Run from lipas root: ./elasticsearch/backup-indexes.sh

set -e

BACKUP_DIR="./data/es-backup-$(date +%Y%m%d-%H%M%S)"
mkdir -p "$BACKUP_DIR"

echo "Backing up to: $BACKUP_DIR"

# Indexes to backup (excluding logstash, sports_sites, loi, legacy, search)
INDEXES=(
  "city_stats"
  "diversity-2025-11-01-harmonized"
  "kunnat_2021_wgs84"
  "kunnat_2021_wgs84_fixed"
  "population-1km-2025-11-01-harmonized"
  "population-250m-2025-11-01-harmonized"
  "schools"
  "subsidies"
  "vaestoruutu_1km_2019_kp"
  "vaestoruutu_250m_2020_kp"
)

backup_index() {
  local index=$1
  local backup_subdir="/data/es-backup-$(date +%Y%m%d-%H%M%S)"

  echo "=== Backing up: $index ==="

  # Backup mapping
  docker compose run --rm elasticdump \
    --input=http://elasticsearch:9200/$index \
    --output=/data/$index.mapping.json \
    --type=mapping

  # Backup data
  docker compose run --rm elasticdump \
    --input=http://elasticsearch:9200/$index \
    --output=/data/$index.data.json \
    --type=data \
    --limit=10000

  echo "  Done: $index"
}

for index in "${INDEXES[@]}"; do
  backup_index "$index"
done

echo ""
echo "=== Backup complete ==="
echo "Files saved to: ./data/"
ls -lh ./data/*.json 2>/dev/null | tail -20
