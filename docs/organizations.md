# LIPAS Organizations Documentation

## Overview

The LIPAS organization functionality provides a system for managing organizations that own or operate sports facilities. This feature enables centralized management of contact information, user permissions, and bulk operations across multiple sports sites belonging to the same organization.

### Key Benefits
- **Centralized Contact Management**: Organizations can maintain contact information in one place rather than updating each sports site individually
- **User Access Control**: Organizations can manage which users have permissions to edit their sports sites
- **Bulk Operations**: Update contact information across multiple sports sites simultaneously
- **PTV Integration**: Lipas admins can manage organizations PTV integration settings

## Architecture

### Backend Architecture

The organization functionality is implemented in the backend with the following components:

#### Database Schema
Organizations are stored in the `org` table with the following structure:

```sql
CREATE TABLE IF NOT EXISTS public.org (
  id                uuid NOT NULL DEFAULT uuid_generate_v4(),
  name              text COLLATE pg_catalog."default" NOT NULL,
  data              jsonb,
  ptv_data          jsonb,

  CONSTRAINT org_pkey PRIMARY KEY (id),
  CONSTRAINT org_mail_key UNIQUE (name)
)
```

- **id**: Unique identifier for the organization
- **name**: Organization name (must be unique)
- **data**: JSONB field storing organization details like contact information
- **ptv_data**: JSONB field storing PTV integration data

#### Core Backend Module (`lipas.backend.org`)

The main backend module provides the following functions:

- **`all-orgs [db]`**: Retrieves all organizations from the database
- **`create-org [db org]`**: Creates a new organization
- **`update-org! [db org-id org]`**: Updates an existing organization
- **`user-orgs [db user-id]`**: Gets all organizations a user belongs to
- **`get-org-users [db org-id]`**: Gets all users belonging to an organization
- **`update-org-users! [db org-id changes]`**: Adds or removes users from an organization
- **`add-org-user-by-email! [db org-id email role]`**: Adds a user by email address

### Frontend Architecture

The frontend organization functionality is organized into several ClojureScript namespaces:

#### Routes (`lipas.ui.org.routes`)
- `/organisaatiot` - Lists all organizations the user belongs to
- `/organisaatio/:org-id` - Shows details and management interface for a specific organization
- `/organisaatio/:org-id/massa-paivitys` - Bulk operations interface for updating multiple sports sites

#### Views (`lipas.ui.org.views`)
- **`orgs-list-view`**: Displays a list of organizations the user belongs to
- **`org-view`**: Main organization management interface
- **`bulk-operations-view`**: Interface for bulk updating sports site contact information
- **`admin-user-management`**: User management component for LIPAS admins
- **`org-admin-user-management`**: User management component for organization admins

#### Events (`lipas.ui.org.events`)
Handles all organization-related actions including:
- Fetching organization data
- Saving organization updates
- Managing organization users
- Initializing bulk operations

#### Subscriptions (`lipas.ui.org.subs`)
Provides reactive data queries for:
- User's organizations
- Organization details
- Organization users
- Form validation state
- User permissions

## Data Model

### Organization Structure

```clojure
{:id #uuid "..."
 :name "Organization Name"
 :data {:primary-contact {:phone "+358501234567"
                         :email "contact@organization.fi"
                         :website "https://organization.fi"
                         :reservations-link "https://varaukset.organization.fi"}}
 :ptv_data {:org-id "PTV organization identifier"
           ;; Additional PTV integration data
           }}
```

### Contact Information Fields
- **phone**: Primary contact phone number
- **email**: Primary contact email address
- **website**: Organization's website URL
- **reservations-link**: Link to the organization's reservation system

## Roles and Permissions

The organization system integrates with LIPAS's role-based access control:

### Organization-Specific Roles

#### org-admin
- Can manage organization details and contact information
- Can add and remove users from the organization
- Can perform bulk operations on organization's sports sites
- Has all permissions of org-user

#### org-user
- Can view organization details
- Listed as a member of the organization
- Can be granted additional permissions for specific sports sites

### Permission Checks

The system uses the `lipas.roles` namespace to check permissions:

```clojure
;; Check if user is an organization admin
(roles/check-privilege user {:org-id org-id} :org/manage)

;; Check if user is an organization member
(roles/check-privilege user {:org-id org-id} :org/member)
```

## User Management

### Adding Users to Organizations

There are two methods for adding users:

1. **For LIPAS Admins**: Can select from all registered users via autocomplete
2. **For Organization Admins**: Can add users by email address
   - User must already have a LIPAS account
   - System will show an error if the email is not found

### Removing Users

Users can be removed by clicking the delete button next to their role in the users table. This removes the organization role from the user's permissions.

## Bulk Operations

The bulk operations feature allows organizations to update contact information across multiple sports sites simultaneously.

### Workflow
1. Navigate to the organization page
2. Click "Bulk Operations" button
3. Select sports sites to update using filters or individual checkboxes
4. Enter new contact information (leaving fields empty will clear them)
5. Preview and confirm changes
6. System updates all selected sports sites

### Available Fields for Bulk Update
- Phone number
- Email address
- Website
- Reservation system link

## PTV Integration

Organizations can integrate with PTV (Palvelutietovaranto) - Finland's national service registry.

### PTV Data Structure
- **org-id**: The organization's identifier in PTV
- Organizations can manage which sports sites are synchronized with PTV
- Contact information from the organization can be used for PTV services

### Integration Points
- Organization details can be mapped to PTV organization data
- Sports sites inherit organization's PTV settings
- Bulk operations can update PTV-synchronized sites

## API Endpoints

While specific REST endpoints are not explicitly defined in the code, the organization functionality is accessed through LIPAS's general API structure:

- **GET** `/api/user/orgs` - Get user's organizations
- **GET** `/api/org/:id/users` - Get organization users
- **POST** `/api/org` - Create organization (admin only)
- **PUT** `/api/org/:id` - Update organization
- **POST** `/api/org/:id/users` - Add user to organization
- **DELETE** `/api/org/:id/users/:user-id` - Remove user from organization

## Usage Examples

### Creating an Organization (Admin Only)
```clojure
(create-org db
  {:name "Helsingin Liikuntavirasto"
   :data {:primary-contact {:phone "+358901234567"
                           :email "liikunta@hel.fi"
                           :website "https://liikunta.hel.fi"}}})
```

### Adding a User by Email
```clojure
(add-org-user-by-email! db org-id "user@example.fi" "org-admin")
```

### Updating Organization Contact Info
```clojure
(update-org! db org-id
  {:data {:primary-contact {:phone "+358901234568"
                           :email "uusi@hel.fi"}}})
```

## Future Enhancements

Based on the existing documentation (`llm-org-begin-state.md`), planned enhancements include:

1. **Organization Dashboard**:
   - Statistics about organization's sports sites
   - Activity reports and usage analytics

2. **Advanced Permissions**:
   - Organization-level permissions

## Error Handling

The system includes error handling for common scenarios:

- **User Not Found**: When adding a user by email that doesn't exist in LIPAS
- **Permission Denied**: When non-admin users try to access admin functions
- **Validation Errors**: Form validation for contact information fields
- **Database Errors**: Transaction rollback for failed operations

## Best Practices

1. **Contact Information**: Keep organization contact information up-to-date as it may be inherited by sports sites
2. **User Management**: Regularly review and update user permissions
3. **Bulk Operations**: Always preview changes before applying bulk updates
4. **PTV Integration**: Ensure PTV organization ID is correctly set before enabling integration
5. **Email Addresses**: Verify email addresses before adding users to avoid errors
