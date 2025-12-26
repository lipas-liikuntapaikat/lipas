# LIPAS — Finnish Sports Facility Registry

**LIPAS** (Liikuntapaikat.fi) is Finland's national database of sports and recreational facilities. The system provides comprehensive, up-to-date geographic information about sports venues, outdoor recreation areas, and related infrastructure across Finland.

[lipas.fi](https://www.lipas.fi) · [API](https://api.lipas.fi) · [GeoServer](http://lipas.cc.jyu.fi/geoserver)

## Open Data

LIPAS data is **freely available** to everyone. The database contains:

- **48,000+** sports facilities
- **Location data** with precise coordinates
- **Facility details** — types, properties, accessibility information
- **Administrative data** — ownership, operators, contact information
- **Recreational routes** — trails, tracks, skiing routes

All data is available through multiple access methods with no registration required.

### Data License

LIPAS data is licensed under **[CC BY 4.0](https://creativecommons.org/licenses/by/4.0/)** (Creative Commons Attribution 4.0 International). You are free to use, copy, distribute, and modify the data for any purpose, including commercial use, provided you give appropriate attribution:

> Sports facilities: Lipas.fi, University of Jyväskylä, [sampling date]

## Data Access

### REST API

Programmatic access to all LIPAS data.

| Version | Base URL | Documentation |
|---------|----------|---------------|
| **V1 API** | `api.lipas.fi/v1/` | [api-v1.md](docs/api-v1.md) |
| **V2 API** | `api.lipas.fi/v2/` | [api-v2.md](docs/api-v2.md) |

```bash
# Example: Get all swimming halls in Helsinki
curl "https://api.lipas.fi/v2/sports-sites?type-codes=3110&city-codes=91"
```

### GeoServer (WFS/WMS)

Standard OGC web services for GIS applications.

- **WFS**: Vector data for analysis and integration
- **WMS**: Map tiles for visualization

Available at [lipas.cc.jyu.fi/geoserver](http://lipas.cc.jyu.fi/geoserver)

### Web Application

Interactive map interface at [lipas.fi](https://www.lipas.fi) for browsing, searching, and analyzing facilities. Registered users can manage facility data and generate reports.

## Technical Overview

LIPAS is built as a modern web application with spatial data capabilities:

- **Backend**: Clojure
- **Frontend**: ClojureScript (Re-frame)
- **Database**: PostgreSQL with PostGIS
- **Search**: Elasticsearch
- **Spatial Services**: GeoServer, Mapproxy

The entire system is **open source** under the MIT license.

## Integrations

LIPAS integrates with Finnish national infrastructure:

- **PTV** (Palvelutietovaranto) — Service registry synchronization
- **YTI** (Yhteentoimivuusalusta) — Terminology services

## For Developers

See [DEV-README.md](DEV-README.md) for development environment setup.

Additional documentation:
- [docs/](docs/) — API documentation and operations
- [webapp/docs/](webapp/docs/) — Technical architecture and guides
- [webapp/CLAUDE.md](webapp/CLAUDE.md) — LLM development context

## Contributing

Contributions are welcome. Please open an issue to discuss proposed changes before submitting a pull request.

## Contact

LIPAS is developed and maintained by the **University of Jyväskylä**, Faculty of Sport and Health Sciences.

- Website: [jyu.fi/lipas](https://www.jyu.fi/fi/lipas-liikunnan-paikkatietojarjestelma)
- Issues: [GitHub Issues](https://github.com/lipas-liikuntapaikat/lipas/issues)

## License

**Source code**: [MIT License](LICENSE)

**Data**: [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/) — Attribution required
