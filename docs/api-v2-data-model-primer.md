# LIPAS Data Model Primer for API V2 Users

Welcome to the LIPAS API V2! This guide is designed to give you a clear understanding of the core concepts behind the LIPAS data, helping you use the API effectively.

This guide complements the documentation available in [Swagger UI](https://api.lipas.fi/v2/swagger-ui/index.html).

## 1. What is LIPAS? ##

At its heart, LIPAS is a comprehensive database of sports and recreational facilities in Finland. It's a structured system that categorizes places, describes their specific features, and maps them geographically.

You will primarily interact with two main types of data:

* **Sports Sites**: The traditional core of LIPAS. These are physical structures for sports and exercise, from football stadiums to ski tracks.
* **Locations of Interest (LOI)**: Complementary entities introduced to enrich the data, especially for outdoor recreation (e.g., campfire sites, historical buildings).

Due to the system's history where everything was modeled as a "Sports Site," you might find some overlap (e.g., a "Information Point" existing as a Sports Site). To clarify the distinction for all new and future data, please use the following rule of thumb:

> **Going forward, structural facilities intended for a specific sport are 'Sports Sites', while non-structural points of interest like lean-tos, info boards, or natural attractions should be 'LOIs'.**

## 2. The Type System ##

Every **Sports Site** in LIPAS has a `type.type-code` (e.g., `1180` for a Disc golf course). This code is fundamental. It tells you:

1.  What the sports site *is*.
2.  What kind of geometry to expect (a point, a line, or a polygon).
3.  What specific properties (the `properties` object) it will have.

You can and should use the `/v2/sports-site-categories` endpoints to get a full, human-readable list of all possible type codes and their associated properties. This classification system is relatively stable.

## 3. Understanding a Sports Site Object ##

When you fetch a sports site, you'll get a rich object with several key parts.

You can explore the complete schemas in [Swagger UI](https://api.lipas.fi/v2/swagger-ui/index.html).

### Key Identifiers and Metadata ###

* `lipas-id`: The unique and permanent integer ID for a sports site.
* `name`: The official name of the site.
* `status`: The operational status (e.g., 'active', 'out-of-service-permanently').
* `owner` / `admin`: Who owns and manages the site.
* `type`: Contains the all-important `type-code` that defines the site.

### Location and Geometry ###

The `location` object in a sports site contains its address and, crucially, its complete geometry.

* **`location.geometries`**: This GeoJSON FeatureCollection contains **all the raw geometric parts** that make up the physical site.
    * **Why?** A single "Sports Site" can represent a whole network of trails (e.g., a ski resort). To simplify data entry, these networks are digitized as smaller segments split at intersections. This field contains all of those "raw material" segments.

### Activities: The Enriched Content Layer ###

While `location.geometries` represents the raw physical structure, the `activities` object provides a richer, "productized" layer of content. This enriched data layer was added in 2024 to support user-centric services like Luontoon.fi, and the data collection is ongoing. We expect the data to grow over the years and to include more activity types.

The enriched data can take two forms:

* **1. Site-Level Enrichment (for Points and Polygons)**
    For many sports sites represented by a Point or Polygon (like a `Fishing area/spot` or `Leisure park`), the `activities` object contains a set of descriptive properties that apply to the *entire site*. For example, an `activities.fishing` object contains details about fish populations and permits for the whole area.

* **2. Curated "Virtual Routes" (for LineStrings)**
    For route-based sports sites, the activities object contains a routes array. Each item in this list is a "virtual route"—a flexible model for presenting curated experiences to the end-user. This model handles both simple and complex scenarios:
    - Simple Case (1:1): It is not uncommon for a linear A-to-B trail to have a single geometry in location.geometries and a single corresponding virtual route in activities. In this case, the virtual route's main purpose is to add richer metadata (like a detailed description, difficulty, photos) to the physical route.
    - Complex Case (Network): The model's real power is for complex sites like ski resorts. Here, location.geometries contains the entire network of raw track segments. Each logical trail (e.g., "5km Blue Trail", "10km Red Trail") is then defined as a separate virtual route, whose geometry is assembled from a specific subset of those raw segments.
In essence, the virtual route is a generalization that allows a single sports site lipas-id to contain one or many distinct, productized routes. The full geometry for each logical route is provided directly within its route object for convenience.



Currently supported activity types include:
* **Outdoor Recreation**: This is a group of activities related to general outdoor pursuits. They are separated in the data model based on the geometry type of the sports site:
    * `outdoor-recreation-areas`: For polygon-based sites like leisure parks.
    * `outdoor-recreation-facilities`: For point-based sites like cooking facilities or huts.
    * `outdoor-recreation-routes`: For line-based sites like nature trails or hiking routes.
* **`cycling`**: For biking routes.
* **`paddling`**: For canoe routes and other water routes.
* **`fishing`**: For fishing spots and areas.

## 4. A Recommended Workflow for New Users ##

1.  **Understand the Categories**: Start by calling `GET /v2/sports-site-categories` to see what kinds of sports sites exist.
2.  **Fetch Sports Sites**: Use `GET /v2/sports-sites`. We highly recommend using the `statuses` filter, as most applications are only interested in facilities that are currently in use. The default and most common filter is `?statuses=active,out-of-service-temporarily`.
3.  **Interpret the Site**: For each site you retrieve:
    * First, check `type.type-code` to understand its basic nature and geometry type.
    * If the `activities` key exists, you have found enriched content.
        * If the activity object contains a `routes` array (e.g., `activities.cycling.routes`), use the geometry and properties found within each route object to display curated trails.
        * If no `routes` array exists, the properties in the `activities` object apply to the entire site's geometry as defined in `location.geometries`.
    * If the `activities` key is not present, rely solely on the raw `location.geometries` and the main `properties` object for the site's data.
4.  **Explore LOIs (Optional)**: Use the `GET /v2/lois` endpoint to find complementary points of interest like campfire sites or info boards that might be located near your sports sites.
