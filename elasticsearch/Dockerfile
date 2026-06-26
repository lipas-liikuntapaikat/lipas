# LIPAS Elasticsearch image.
#
# Extends the stock Elasticsearch image with the analysis-icu plugin, which
# provides:
#   - icu_collation_keyword: locale-correct (Finnish/Swedish/English) sort order
#     for facility names, so accented letters (ä, ö, á, č, ...) sort in the
#     order users expect rather than by raw Unicode code point.
#
# Diacritic-insensitive *search* (e.g. "Njalla" matching "Njálla") is handled
# by the built-in asciifolding token filter and does not require this plugin.
#
# Keep the base version in sync with the `elasticsearch` service in
# docker-compose.yml. After bumping it, rebuild the image:
#   docker compose build elasticsearch
FROM docker.elastic.co/elasticsearch/elasticsearch:8.19.9

RUN bin/elasticsearch-plugin install --batch analysis-icu
