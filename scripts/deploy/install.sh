#!/usr/bin/env bash
#
# LIPAS release installer — the single install path for every deploy door:
#
#   1. lipas-dev:  GitHub Actions, `lipas-dev` PR label   (deploy-dev.yml)
#   2. lipas-prod: GitHub Actions, manual dispatch        (deploy-prod.yml)
#   3. break-glass: `bb deploy <dev|prod>` from a laptop  (webapp/bb.edn)
#
# Runs ON the target host as root. Artifacts are built elsewhere (GitHub CI
# or, break-glass only, a laptop); this script installs, migrates, restarts
# and verifies. Run it from a copy OUTSIDE /var/lipas (workflow workspace or
# /tmp) — it may check out a different ref under /var/lipas and bash reads
# scripts incrementally, so the running copy must not change underneath.
#
# Usage:
#   install.sh --artifacts DIR [--sha SHA] [--skip-backend] [--skip-frontend]
#              [--reindex] [--door NAME] [--label TEXT]
#
#   --artifacts DIR   Directory containing backend.jar and/or
#                     frontend-public.tar.gz
#   --sha SHA         Pin the /var/lipas checkout to this commit before
#                     installing, so config matches the artifacts. Omit
#                     (break-glass) to leave the checkout untouched.
#   --skip-backend    Install only the frontend bundle (no restarts needed —
#                     nginx serves the mounted files directly)
#   --skip-frontend   Install only the backend jar
#   --reindex         Run the Elasticsearch sports-site reindex after deploy
#   --door NAME       Which deploy door invoked this (for the DEPLOYED stamp)
#   --label TEXT      Extra text for the DEPLOYED stamp (e.g. git describe
#                     of a break-glass working tree)

set -euo pipefail

LIPAS_DIR=/var/lipas
ARTIFACTS=""
SHA=""
DOOR="manual"
LABEL=""
INSTALL_BACKEND=true
INSTALL_FRONTEND=true
REINDEX=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    --artifacts)     ARTIFACTS="$2"; shift 2 ;;
    --sha)           SHA="$2"; shift 2 ;;
    --door)          DOOR="$2"; shift 2 ;;
    --label)         LABEL="$2"; shift 2 ;;
    --skip-backend)  INSTALL_BACKEND=false; shift ;;
    --skip-frontend) INSTALL_FRONTEND=false; shift ;;
    --reindex)       REINDEX=true; shift ;;
    *) echo "ERROR: unknown argument: $1" >&2; exit 2 ;;
  esac
done

if [[ -z "$ARTIFACTS" ]]; then
  echo "ERROR: --artifacts is required" >&2
  exit 2
fi

JAR="$ARTIFACTS/backend.jar"
FRONTEND_TAR="$ARTIFACTS/frontend-public.tar.gz"

if $INSTALL_BACKEND && [[ ! -f "$JAR" ]]; then
  echo "ERROR: $JAR not found (use --skip-backend for a frontend-only install)" >&2
  exit 2
fi
if $INSTALL_FRONTEND && [[ ! -f "$FRONTEND_TAR" ]]; then
  echo "ERROR: $FRONTEND_TAR not found (use --skip-frontend for a backend-only install)" >&2
  exit 2
fi

cd "$LIPAS_DIR"

# Git must operate on /var/lipas regardless of who owns it (runner container
# root vs lipas-ci on the host). Scoped to this process — no persistent
# --global config edits.
export GIT_CONFIG_COUNT=1
export GIT_CONFIG_KEY_0=safe.directory
export GIT_CONFIG_VALUE_0="$LIPAS_DIR"

if [[ -n "$SHA" ]]; then
  if ! git diff --quiet HEAD; then
    echo "ERROR: $LIPAS_DIR has uncommitted changes to tracked files; refusing to switch refs" >&2
    git status --short >&2
    exit 1
  fi
  git fetch origin --prune --tags
  if git rev-parse --verify "origin/${SHA}" >/dev/null 2>&1; then
    TARGET="origin/${SHA}"
  else
    TARGET="${SHA}"
  fi
  git -c advice.detachedHead=false checkout "$TARGET"
  echo "Config checkout at $(git rev-parse --short HEAD) — $(git log -1 --pretty='%s')"
else
  echo "No --sha given: leaving the $LIPAS_DIR checkout untouched (break-glass mode)"
fi

# Compose interpolates credentials and host-specific settings from here.
# shellcheck disable=SC1091
source "$LIPAS_DIR/.env.sh"

if $INSTALL_FRONTEND; then
  echo "Installing frontend bundle..."
  # The archive is ordered bundles-first, index.html last, so the live
  # index.html never references a bundle that is not yet on disk. Old
  # content-addressed bundles are left in place on purpose: sessions loaded
  # before the deploy keep lazy-loading their modules.
  tar -xzf "$FRONTEND_TAR" -C webapp/resources/public/
fi

if $INSTALL_BACKEND; then
  echo "Installing backend.jar..."
  install -m 0644 "$JAR" webapp/backend.jar

  echo "Running database migrations..."
  docker compose run --rm backend-migrate

  echo "Restarting backend and worker..."
  docker compose rm -sf backend worker
  if ! docker compose up -d --wait backend worker; then
    echo "ERROR: backend did not become healthy" >&2
    docker compose logs --tail 100 backend >&2
    exit 1
  fi

  # The proxy must be recreated after the backend so nginx re-resolves the
  # new backend container IP. --build picks up nginx/Dockerfile changes
  # (brotli modules ship in the image) and is a cached no-op otherwise.
  # Hosts run different proxy variants, so detect the running one.
  PROXY_SVC=$(docker compose ps --services | grep -E '^proxy(-dev|-local)?$' || true)
  if [[ -n "$PROXY_SVC" ]]; then
    docker compose up -d --no-deps --build --force-recreate "$PROXY_SVC"
    echo "Verifying end-to-end health through the proxy..."
    HEALTHY=false
    for _ in $(seq 1 24); do
      if curl -skf https://localhost/api/health >/dev/null; then
        HEALTHY=true
        break
      fi
      sleep 5
    done
    if ! $HEALTHY; then
      echo "ERROR: https://localhost/api/health not responding within 120s" >&2
      docker compose logs --tail 50 "$PROXY_SVC" >&2
      exit 1
    fi
  else
    echo "No running proxy service found; skipping proxy restart"
  fi
fi

if $REINDEX; then
  echo "Reindexing Elasticsearch..."
  docker compose run --rm backend-index-search
fi

{
  echo "deployed_at: $(date -u +%FT%TZ)"
  echo "door: $DOOR"
  if [[ -n "$SHA" ]]; then
    echo "sha: $(git rev-parse HEAD)"
  else
    echo "sha: (checkout untouched — artifacts only)"
  fi
  if [[ -n "$LABEL" ]]; then echo "label: $LABEL"; fi
  echo "backend: $INSTALL_BACKEND frontend: $INSTALL_FRONTEND reindex: $REINDEX"
} > "$LIPAS_DIR/DEPLOYED"

echo "Deploy complete."
cat "$LIPAS_DIR/DEPLOYED"
