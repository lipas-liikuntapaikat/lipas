# Lipas Development Environment

A streamlined development setup for the Lipas sports facility management system.

## Architecture Overview

This project uses a **simplified two-level structure**:

- **Root Level** (`/deps.edn` + `/dev/user.clj`): Development tooling and infrastructure
- **Webapp Level** (`/webapp/deps.edn`): Clean production dependencies

The root-level REPL provides development utilities that dynamically load webapp dependencies when needed, keeping production dependencies clean while providing rich development tooling.

## Quick Start

1. **Start the REPL from project root**:
   ```bash
   clj -M:nrepl
   ```

2. **Launch the webapp**:
   ```clojure
   (dev-webapp!)
   ```

That's it! The system will:
- Load all necessary dependencies
- Start the Jetty server
- Connect to PostgreSQL and Elasticsearch
- Initialize all integrations (AWS, email, PTV, etc.)

## Available Development Functions

The `user` namespace provides these utilities:

### System Management
- `(dev-webapp!)` - **One-command startup**: loads dependencies and starts the complete system
- `(load-webapp-dev-deps!)` - Loads only the :dev and :test dependencies from webapp/deps.edn
- `(read-webapp-deps)` - Inspects webapp dependency structure
- `(extract-dev-deps webapp-deps)` - Extracts development dependencies from webapp deps.edn

### System Access
Once the system is running, you can access components via:
```clojure
integrant.repl.state/system  ; Full system map with all components
```

The system includes:
- `:lipas/server` - Jetty web server
- `:lipas/db` - PostgreSQL database connection
- `:lipas/search` - Elasticsearch client with configured indices
- `:lipas/emailer` - Email configuration
- `:lipas/aws` - S3 integration
- `:lipas/ptv` - Finnish public service integration
- `:lipas/mailchimp` - Newsletter integration

## Development Workflow

### Starting Development
```clojure
;; Single command to get everything running
(dev-webapp!)

;; System is now ready for development
;; All namespaces loaded, server running, databases connected
```

### Exploring the System
```clojure
;; List all available namespaces
(clj-mcp.repl-tools/list-ns)

;; Explore specific namespaces
(clj-mcp.repl-tools/list-vars 'lipas.backend.core)

;; Check system status
integrant.repl.state/system
```

### Running Tests
```clojure
;; After dev-webapp! has loaded everything
(require '[cognitect.test-runner.api :as tr])
(tr/test {:dirs ["webapp/test/clj"]})
```

## Project Structure

```
├── deps.edn              # Root dependencies + development tooling
├── dev/user.clj          # Development utilities (simplified)
└── webapp/
    ├── deps.edn          # Clean production dependencies
    ├── src/              # Application source code
    ├── test/             # Tests
    └── dev/              # Webapp-specific development helpers
```

## Environment Setup

The system requires environment variables for external services:

1. **Copy the sample environment**:
   ```bash
   cp .env.sample.sh .env.sh
   ```

2. **Edit `.env.sh`** with your configuration

3. **Load environment before starting REPL**:
   ```bash
   source .env.sh
   clj -M:nrepl
   ```

## Key Features

- **Ultra-fast startup** - Single function call to get complete system running
- **Clean separation** - Production dependencies stay in webapp, development tooling at root
- **Rich development environment** - Full access to all system components
- **LLM-friendly** - Simple, predictable structure that's easy to understand and navigate
- **Minimal configuration** - Most complexity handled automatically

## Troubleshooting

**Issue**: Functions not available after REPL start  
**Solution**: Run `(dev-webapp!)` to load everything

**Issue**: System components not accessible  
**Solution**: Check `integrant.repl.state/system` to see loaded components

**Issue**: Environment variables not set  
**Solution**: Ensure you've run `source .env.sh` before starting the REPL

**Issue**: Tests not found  
**Solution**: Run `(dev-webapp!)` first to ensure all paths are loaded

## For LLM Developers

This setup is designed to be LLM-friendly:

- **Single entry point**: `(dev-webapp!)` gets everything running
- **Predictable structure**: Two-level architecture with clear separation
- **Rich introspection**: Use `clj-mcp.repl-tools/*` functions to explore
- **Full system access**: Everything available via `integrant.repl.state/system`
- **Simple commands**: Minimal complexity, maximum capability

The system automatically handles dependency loading, path configuration, and service initialization, allowing you to focus on understanding and modifying the application logic.
