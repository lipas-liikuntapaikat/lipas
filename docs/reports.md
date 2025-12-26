# LIPAS Reports & Statistics

This document provides an overview of the reporting and statistics features available in LIPAS.

## Overview

LIPAS provides comprehensive reporting capabilities that enable users to analyze and export data about Finnish sports facilities, outdoor recreation areas, and related statistics. The reporting features are organized into two main categories:

1. **Statistics (Tilastot)** - Predefined statistical reports with interactive visualizations
2. **Custom Reports** - User-configurable data exports from search results

## Statistics Section (Tilastot)

The Statistics section is accessible from the main navigation menu and provides five tabs of predefined statistical reports:

### 1. Sports Facility Statistics (Liikuntapaikat)

Provides aggregate statistics about sports facilities across Finland:

- **Metrics available:**
  - Facility count
  - Facility count per 1,000 inhabitants
  - Total sports surface area (m²)
  - Sports surface area per capita
  - Route length (km)
  - Route length per capita

- **Grouping options:**
  - By city/municipality
  - By facility type

- **Filters:**
  - Region/city selection
  - Facility type categories

### 2. Construction Year Statistics (Rakennusvuodet)

Analyzes the age structure of sports facilities:

- Shows distribution of facilities by construction year
- Configurable time intervals (1, 5, or 10 years)
- Grouping by owner or administrator type
- Filters by region and facility type

### 3. City Statistics (Kuntatilastot)

Financial statistics at the municipal level:

- Focuses on individual city comparison
- Shows sports and youth services finances over time
- Metrics include investments, operating expenses, income, and net costs
- Data available in absolute amounts (€1,000) or per capita

### 4. Finance Statistics (Taloustiedot)

Broader financial analysis across regions:

- **Metrics:**
  - Investments
  - Operating expenses/income
  - Subsidies
  - Net costs
  - Surplus/Deficit

- **Grouping:**
  - By AVI region
  - By province
  - By municipality

- **Features:**
  - Comparison charts
  - Ranking charts
  - Multi-city comparison

### 5. Subsidies (Avustukset)

Tracks government subsidies for sports facilities:

- Subsidy amounts by region, type, and year
- Filtering by issuer (AVI, OKM)
- Comparison and ranking views
- Historical data from 2002 onwards

### Common Features

All statistics tabs share:
- **Chart/Table toggle** - View data as visual charts or tabular format
- **Excel export** - Download current view as Excel file
- **Data disclaimers** - Clear attribution to data sources (Statistics Finland, etc.)

## Custom Reports from Search

When browsing sports facilities on the map or in list view, users can generate custom Excel/CSV/GeoJSON reports:

### Accessing the Report Dialog

The "Create Report" button appears when viewing search results in list mode. It opens a dialog where users can:

1. **Select fields to include:**
   - Basic info (name, ID, type, city, address)
   - Properties (surface material, area, dimensions)
   - Metadata (coordinates, categories, audit dates)
   - All 100+ available fields

2. **Quick field presets:**
   - Common field combinations for fast selection
   - Saved report templates (for logged-in users)

3. **Export format:**
   - **Excel (.xlsx)** - Full formatting, limited for very large exports
   - **CSV** - Plain text, no practical limit
   - **GeoJSON** - Geographic format with geometries

### Saved Report Templates

Logged-in users can save their field selections as named templates for reuse. Templates are stored in the user's profile and persist across sessions.

## Analysis Tool Reports

The Analysis tools provide specialized reporting for in-depth spatial analysis:

### Reachability Analysis Report

When analyzing a sports facility's reachability, users can download an Excel report containing:

- **Population sheet** - Demographics by distance/travel-time zones
- **Schools sheet** - Educational facilities within reach
- **Sports facilities sheet** - Other facilities in the area

The report covers:
- Multiple travel profiles (car, bicycle, walking)
- Distance-based and travel-time-based zones
- Age group breakdowns (0-14, 15-64, 65+)

### Diversity Analysis Reports

The diversity analysis tool offers multiple export formats:

- **Areas report** - Diversity index by analysis area (Excel/GeoJSON)
- **Grid report** - 250m grid level diversity data
- **Categories report** - Custom classification definitions
- **Parameters report** - Analysis settings for reproducibility

## Energy Consumption Statistics

For ice stadiums and swimming pools, LIPAS tracks energy consumption data:

- Electricity usage (MWh)
- Heat consumption (MWh)
- Water usage (m³)

These facilities have dedicated statistics pages showing:
- Aggregate consumption data across facilities
- Facility comparisons and rankings
- "Hall of Fame" for complete data reporters

## Data Model Export

A special report documents the complete LIPAS data model:

- Sports facility fields and their types
- Facility type definitions
- Property classifications
- Owner and administrator categories
- Activity definitions
- WFS layer mappings
- API field mappings

This is primarily useful for developers and data integrators.

## Data Sources & Disclaimers

LIPAS reports incorporate data from multiple sources:

- **Sports facilities:** Self-reported by facility owners, verified by JYU
- **Financial data:** Statistics Finland (Tilastokeskus)
- **Population data:** Statistics Finland 250m and 1km grid data
- **School locations:** Statistics Finland and LIKES research center
- **Travel times:** OpenStreetMap and OSRM

All reports include appropriate source attributions and data quality disclaimers. Users should note that data completeness and accuracy vary as municipalities are responsible for maintaining their own information.

## License

LIPAS data is licensed under CC BY 4.0 (Creative Commons Attribution 4.0 International). When using LIPAS data in publications or other works, proper attribution is required:

> Sports facilities: Lipas.fi, University of Jyväskylä, [sampling date]
