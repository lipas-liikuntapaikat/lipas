# LLM Instructions for PTV Integration in LIPAS

This document provides instructions for LLMs to understand and work with the PTV (Palvelutietovaranto) integration in LIPAS. It summarizes key concepts, components, and patterns to help future interactions be more productive.

## Overview of PTV Integration

PTV (Palvelutietovaranto) is Finland's centralized service information repository. The LIPAS-PTV integration allows sports facility data from LIPAS to be synchronized with the PTV system, ensuring sports facilities are accurately represented and available for public service discovery.

### Key Concepts

1. **Data Structure Mapping**:
   - **Services**: In PTV, represent types of sports facilities (based on LIPAS type categorization)
   - **ServiceChannels**: In PTV, represent specific sports facility locations
   - **Organization**: Represents municipalities that own the sports facilities

2. **Component Hierarchy**:
   - Organization → Services → ServiceChannels
   - Each municipality can have multiple Services (facility types)
   - Each Service can have multiple ServiceChannels (specific facilities)

3. **LIPAS-PTV Data Flow**:
   - Sports facility types in LIPAS → Services in PTV
   - Individual sports facilities in LIPAS → ServiceChannels in PTV
   - The connection is managed via metadata stored in the `:ptv` field of LIPAS data

## Code Organization

The PTV integration codebase is organized as follows:

1. **Data Transformation** (`lipas.data.ptv`):
   - Contains functions for transforming LIPAS data to PTV format
   - Defines organization mappings and configurations
   - Contains utility functions for working with PTV data

2. **UI Components** (`lipas.ui.ptv.*`):
   - `views.cljs`: Main UI components for the PTV integration
   - `controls.cljs`: Reusable control components
   - `events.cljs`: Re-frame event handlers for PTV operations
   - `subs.cljs`: Re-frame subscriptions for PTV data
   - `audit.cljs`: Components for the PTV audit functionality

3. **Backend Integration** (`lipas.backend.ptv`):
   - API integration with PTV's external services
   - Authentication and request handling

## Key UI Patterns

When working with the PTV UI, keep in mind these patterns:

1. **Data Organization**:
   - PTV data is stored under the `:ptv` key in app-db
   - Organization-specific data is under `:ptv :org org-id :data`
   - Site-specific data follows the path `:ptv :org org-id :data :sports-sites lipas-id :ptv`

2. **Re-frame Events and Subscriptions**:
   - Always include `lipas-id` when working with site-specific data
   - Use canonical paths rather than temporary state
   - Pass explicit data to events rather than relying on implicit app-db lookups

3. **Wizard-based UI**:
   - The PTV integration uses a step-by-step wizard for initial setup
   - Each step is organized into its own component
   - Navigation between steps is managed through the `:selected-step` state

## PTV Audit Functionality

The PTV Audit functionality allows specially privileged users to review and provide feedback on PTV data. Key aspects include:

1. **Role-based Access**:
   - Users with `:ptv/audit` role can provide feedback
   - Users with `:ptv/manage` role can see and act on feedback

2. **Site-specific Audit Data**:
   - Audit data is stored at `:ptv :org org-id :data :sports-sites lipas-id :ptv :audit`
   - Each field (`:summary` and `:description`) has its own audit data

3. **Audit Data Structure**:
   ```clojure
   {:summary {:status "approved" :feedback "Looks good" :timestamp "2023-01-01T12:00:00Z"}
    :description {:status "changes-requested" :feedback "Needs more detail" :timestamp "2023-01-01T12:00:00Z"}}
   ```

4. **UI Components**:
   - Main audit view shows a list of sites that need review
   - Site form shows the content to be reviewed and audit controls
   - Use a single save button for both fields to improve UX

## Best Practices for PTV Development

1. **Handling Site-specific Data**:
   - Always pass `lipas-id` to events and subscriptions
   - Use the canonical data path rather than temporary state
   - Make sure data is saved to the backend with explicit function calls

2. **UI Component Design**:
   - Group related functionality in dedicated namespaces
   - Use a single save button for related fields
   - Prefer explicit passing of data rather than implicit lookups

3. **Re-frame Patterns**:
   - Use `site-specific` subscriptions for data that varies by site
   - Avoid global state for site-specific data
   - Store timestamps using `utils/timestamp` for consistency

4. **Error Handling**:
   - PTV integration involves external API calls - handle errors gracefully
   - Show meaningful error messages to users
   - Ensure local state is consistent even if external calls fail

## Common Tasks and Solutions

1. **Adding new site-specific functionality**:
   - Add event handlers in `events.cljs` that take `lipas-id` parameter
   - Add subscriptions in `subs.cljs` that extract data from site-specific path
   - Create UI components that use these events and subscriptions

2. **Implementing audit workflow**:
   - Use the site-specific pattern for storing audit data
   - Store all related audit fields together (feedback, status, timestamp)
   - Provide clear UI indicators for audit status

3. **Adding new fields to PTV data**:
   - Update data structures in both frontend and backend
   - Ensure backend API receives the new fields
   - Add UI components for the new fields

## Tips for LLM Assistance

1. When asked about PTV integration:
   - Check the current implementation in `lipas.ui.ptv.*` namespaces
   - Reference the patterns described in this document
   - Maintain consistency with existing code

2. When implementing new features:
   - Follow the site-specific data pattern for per-facility data
   - Use the same event/subscription patterns as existing code
   - Keep UI consistent with the rest of the application

3. When debugging PTV issues:
   - Check the data path to ensure data is stored/retrieved correctly
   - Verify that `lipas-id` is correctly passed to all events and subscriptions
   - Check that UI components receive and display the correct data