# PTV Integration Documentation

## Overview
The PTV (Palvelutietovaranto) integration allows synchronization of sports facility data from Lipas to the PTV service. This integration ensures that sports facilities are accurately represented in the PTV system, enabling better service discovery and management.

## Key Components
1. **PTV API Endpoints**:
   - **Service**: Represents a type of service provided by a facility.
   - **ServiceChannel**: Represents a specific location or channel through which a service is provided.
   - **Organization**: Represents the organization responsible for the service.

2. **Data Transformation**:
   - **`->ptv-service`**: Converts Lipas sports site categorization data into a PTV Service format.
   - **`->ptv-service-location`**: Converts Lipas sports site data into a PTV ServiceChannel format.

3. **Integration Workflow**:
   - **Fetching Data**: Retrieve existing services, service channels, and organizations from PTV.
   - **Creating/Updating Data**: Create or update services and service channels in PTV based on Lipas data.
   - **Synchronization**: Ensure that Lipas and PTV data are in sync, handling updates and deletions.

## PTV Endpoints
1. **Authentication**:
   - **`/v11/Service/token`**: POST - Obtain an authentication token for API requests.

2. **Service Management**:
   - **`/v11/Service/list/organization`**: GET - List services for an organization.
   - **`/v11/Service`**: POST - Create a service.
   - **`/v11/Service/SourceId/{sourceId}`**: PUT - Update a service by its source ID.

3. **Service Channel Management**:
   - **`/v11/ServiceChannel/organization/{orgId}`**: GET - List service channels for an organization.
   - **`/v11/ServiceChannel/{serviceChannelId}`**: GET - Get details of a specific service channel.
   - **`/v11/ServiceChannel/ServiceLocation`**: POST|PUT - Create or update a service location (service channel).

4. **Organization Management**:
   - **`/v11/Organization/{orgId}`**: GET - Get details of a specific organization.

## Service Mapping and Sports Site Attachment

1. **Service Mapping**:
   - Services in PTV are created based on LIPAS type categorization.
   - Each sports facility type belongs to a main category and sub-category, defined in `types_new.cljc`.
   - These categories contain PTV-specific metadata (`:ptv` key) including:
     - `:ontology-urls`: Links to YSO ontology terms
     - `:service-classes`: PTV service classification codes
   - The `->ptv-service` function in `ptv.cljc` converts this categorization into PTV Service format.

2. **Sports Site Attachment**:
   - Sports sites act as service locations in PTV.
   - Each site's type code determines which PTV service(s) it should be attached to.
   - The `->ptv-service-location` function creates PTV ServiceChannel entries for each sports site.
   - Services and service locations are linked through `:service-ids` and `service-channel-id` in the sports site's `:ptv` metadata.

3. **Synchronization**:
   - The `sync-ptv!` function handles updates when a sports site's type changes.
   - It ensures PTV services are updated to reflect the new type.
   - Missing services are identified and created using `resolve-missing-services`.

This mapping allows sports facilities to be accurately represented in PTV while maintaining the connection between facility types and the services they provide.

## Use Cases
1. **Initial Sync**:
   - Fetch existing services and service channels from PTV.
   - Enable integration for desired sports sites.
   - Create new services and service channels in PTV for Lipas sports sites that are not yet in PTV.

2. **Data Updates**:
   - Update existing services and service channels in PTV when corresponding Lipas data changes.
   - Handle changes in facility type, status, or other attributes.

3. **Data Archival**:
   - Mark services and service channels as "Deleted" in PTV when the corresponding Lipas sports site is no longer active or eligible for PTV integration.

4. **Error Handling**:
   - Log and handle errors during synchronization, ensuring that Lipas data remains consistent even if PTV updates fail.

## Example Workflow
1. **Fetch Existing Data**:
   - Retrieve all services and service channels for the organization from PTV.
   - This enables attaching existing services and service channels to sports sites.

2. **Identify Missing Services**:
   - Compare Lipas sports sites with existing PTV services.
   - Identify and create any missing services in PTV.

3. **Update Service Channels**:
   - For each Lipas sports site, create or update the corresponding service channel in PTV.
   - Ensure that service connections (links between services and service channels) are correctly updated.

4. **Handle Archival**:
   - If a sports site is no longer eligible for PTV integration, mark the corresponding service channel as "Deleted" in PTV.

### When is the PTV Integration Run?

The PTV integration is triggered in the following scenarios:

1. **Getting Started Wizard**:
   - A separate **"getting started" wizard** is available to guide administrators in enabling the PTV integration for a desired set of an organization's facilities. This wizard helps configure the integration for new or existing sports sites.

2. **Sports Site Updates**:
   - The integration runs each time a sports site is saved in Lipas if integration has been enabled for the site. This includes:
     - **Updates**: When a sports site's data is modified (e.g., changes to type, status, or other attributes).
     - **Archival**: When a sports site is marked as inactive or no longer eligible for PTV integration.
     - If a sports site's type code changes, the integration ensures that the corresponding PTV services and service channels are updated to reflect the new type. Missing services are identified and created as needed.
