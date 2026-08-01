# Deployment consolidation — run from artifacts

Status: design proposal, 2026-08-01 — draft implementation on branch
`infra/deployment-consolidation` (installer, workflows, runner infra,
compose profiles, bb break-glass task, stale-script deletions)

## The decision

**Run from artifacts.** Compiled code (backend uberjar + frontend bundle) is
built exactly once, on GitHub-hosted CI, and shipped to hosts. Nothing compiles
on lipas-dev, lipas-prod, or (outside break-glass) the laptop.

The git checkout at `/var/lipas` does **not** go away — it keeps a single,
narrower role: **configuration and orchestration**. It provides
`docker-compose.yml`, nginx/mapproxy/geoserver/logstash config, and ops
scripts. It stops being a build input. The rule that resolves the current
ambiguity:

> **The repo on a host is config. Artifacts are code. A deploy pins both to
> the same commit SHA.**

This is less of a leap than it sounds — the runtime layout is already
artifact-shaped today (`webapp/backend.jar` + `resources/public/`, all
gitignored, `java -jar` commands in compose). What's mixed is *where builds
happen* (dev host builds from source, laptop builds locally, prod receives
scp'd files) and three divergent copies of the install/restart logic.

Why not full container images (GHCR)? It doesn't remove the repo checkout —
nginx/mapproxy/geoserver config, certs and compose itself would still come
from git — so it just moves the code/config seam without eliminating it, at
the cost of registry auth, image GC and a compose rework. Can be revisited
later; nothing below blocks it.

## What exists today (inventory)

| Path | What it does | Verdict |
|---|---|---|
| `.github/workflows/deploy-dev.yml` | Label-driven dev deploy; **builds on the dev host runner** | Keep flow, move build to cloud CI |
| `.github/workflows/ci.yaml` | Lint, tests, uberjar+frontend build, bundle budget | Becomes the artifact producer |
| `webapp/bb.edn` deploy tasks (9 of them) | Laptop build + scp + embedded remote heredoc; **no migrate step** | Collapse to one break-glass task calling the shared installer |
| `scripts/deploy.sh` | On-host build (references removed `frontend-npm-bundle` service — broken) | Delete |
| `scripts/deploy-backend.sh` | On-host backend build, stale restart pattern | Delete |
| `scripts/deploy-frontend.sh` | Broken (`/cd /var/lipas` typo), stale service refs | Delete |
| `docker-compose.yml.backup` | Snapshot noise | Delete |
| `docker-compose.yml` one-offs (`backend-migrate`, `backend-index-search`, `backend-maintenance`, `backend-refresh-wfs`) | Run **from source** via `clojure -M`, need mvn_cache + dep downloads on prod | Switch commands to the jar |
| `docs/ops.md` deployment section | Documents bb tasks as the primary path | Rewrite around the three doors |

Verified enablers:

- The uberjar's `Main-Class` is `clojure.main` (`build.clj`), so
  `java -jar backend.jar -m lipas.migrate-db migrate`,
  `-m lipas.search-indexer`, `-m lipas.maintenance`, `-m lipas.wfs.core` all
  work with **zero code changes** — one-offs become artifact-pure by editing
  compose commands only.
- `webapp/backend.jar`, `resources/public/index.html`, and
  `resources/public/js/compiled/` are all gitignored, so installing artifacts
  into the checkout never trips the dirty-tree guard.

## Target architecture: three doors, one installer

### The shared installer — `scripts/deploy/install.sh`

One script, lives in the repo, runs **on the host** as root. All three doors
end here; the restart/health logic exists in exactly one place (today it's
triplicated across deploy-dev.yml, the bb.edn heredoc, and scripts/deploy.sh,
and they have already drifted).

```
install.sh --artifacts <dir> --sha <sha> [--skip-frontend] [--skip-backend] [--reindex]
```

1. Guard: `/var/lipas` tree clean (tracked files), else abort.
2. `git fetch && checkout <sha>` — config now matches the code being installed.
3. Install: `cp backend.jar webapp/backend.jar`; untar
   `frontend-public.tar.gz` (module bundles + `.br`/`.gz` siblings first,
   `index.html` last — users never see an index pointing at missing bundles).
4. Migrate: `docker compose run --rm backend-migrate` (now jar-based).
5. Restart: `rm -sf backend worker && up -d backend worker`; recreate the
   detected `proxy(-dev|-local)` with `--build` (brotli image logic, as today).
6. Health check `/api/health`; on failure exit non-zero (workflow goes red).
7. Optional `--reindex`: `docker compose run --rm backend-index-search`.
8. Write `/var/lipas/DEPLOYED`: sha, timestamp, door used.

### Door 1 — lipas-dev, label-driven (exists; rework build)

`deploy-dev.yml` keeps its trigger/plan/exclusive-label machinery unchanged.
Changes to the deploy side:

- New cloud `build` job (ubuntu-latest): checkout the resolved ref, build
  uberjar + frontend release + precompress, upload
  `backend.jar` / `frontend-public.tar.gz` as workflow artifacts.
  Reuses the same steps (and caches) as `ci.yaml`'s build job — extract a
  composite action or reusable workflow so they cannot drift.
- The self-hosted deploy job stops building: download artifacts, run
  `scripts/deploy/install.sh --artifacts ... --sha ... --reindex`.

Net effect: dev host no longer burns CPU/RAM on shadow-cljs releases next to
the running services, and dev deploys exercise the exact installer prod uses.

### Door 2 — lipas-prod, manually triggered (new)

New `deploy-prod.yml`:

- **Trigger:** `workflow_dispatch` only, input `ref` (default `master`),
  boolean input `reindex` (default false). No PR triggers at all.
- **Gate:** GitHub Environment `lipas-prod` with *required reviewers* — the
  run pauses until approved in the GitHub UI. Keep the `ALLOWED_USERS` check
  as belt-and-braces.
- **Build job** on ubuntu-latest (same shared build steps). Building in the
  dispatch workflow — rather than fishing artifacts out of an old CI run —
  guarantees the artifact matches the requested SHA with no retention issues.
- **Release record:** create/overwrite tag `prod-YYYYMMDD-HHmm` (or a GitHub
  Release) with the two artifacts attached → durable rollback store + audit
  trail of what ran in prod when.
- **Deploy job** on a new self-hosted runner on lipas-prod
  (`runs-on: [self-hosted, lipas-prod]`): download artifacts →
  `install.sh --artifacts ... --sha <sha>`.

**Prod runner:** containerized, but in its **own compose project** — see
"Prod runner design" below. Outbound-only connection to GitHub — no inbound
firewall holes, no VPN involvement.

**Rollback:** re-dispatch with the previous SHA (artifacts rebuild
deterministically, or attach from the release tag). No bespoke rollback
mechanism on the host.

### Door 3 — laptop break-glass (exists; tighten)

For when GitHub or the runner is down. `bb deploy <env>` (collapse the current
nine tasks into this one, or keep `deploy-prod` as an alias):

- Build locally (as today: `bb uberjar`, `npm run build`), tar the frontend
  the same way CI does.
- scp artifacts + `ssh <host> sudo scripts/deploy/install.sh ...` — the
  embedded heredoc in bb.edn is deleted; the laptop path runs the *same
  installer*, which also fixes the currently missing migrate step in
  `bb deploy-prod`.
- Document loudly in ops.md: this door is for emergencies; it deploys
  whatever your working tree holds, not a reviewed master SHA.

## Prod runner design

Note: the dev runner's setup is currently undocumented — it exists only as
state on the dev host (container, runs as root, `/var/lipas` owned by
`lipas-ci`). The prod runner below is defined as committed code; retrofit dev
to the same layout afterwards.

### Placement: a container, but NOT in the app compose stack

The deploy job restarts services in the app stack. If the runner were a
service in that same compose project, a deploy that touches
`docker-compose.yml` and runs `up -d` could recreate the runner container
*while it is executing that very job* — a self-terminating deploy. Routine
maintenance (`docker compose down`) would also silently take the deploy path
with it, and CI infra would pollute `docker compose ps` on the host.

So: own compose project at `/opt/lipas-runner/`, **outside** `/var/lipas`
(a repo checkout must never be able to modify runner infrastructure). The
compose file is committed to the repo (e.g. `infra/runner/docker-compose.yml`)
and copied to the host on provisioning; secrets stay host-side.

```yaml
# /opt/lipas-runner/docker-compose.yml
services:
  runner:
    image: myoung34/github-runner:<pinned>   # bump deliberately, not :latest
    restart: unless-stopped
    environment:
      REPO_URL: https://github.com/lipas-liikuntapaikat/lipas
      RUNNER_SCOPE: repo
      RUNNER_NAME: lipas-prod-1
      LABELS: lipas-prod
      # One-time registration token at first boot; persisted config below
      # means no long-lived PAT ever lives on the host.
      RUNNER_TOKEN: ${RUNNER_TOKEN}
      CONFIGURED_ACTIONS_RUNNER_FILES_DIR: /runner-state
      DISABLE_AUTO_UPDATE: "true"            # updates come as image bumps
    volumes:
      - runner-state:/runner-state
      - runner-work:/actions-runner/_work
      - /var/run/docker.sock:/var/run/docker.sock
      - /var/lipas:/var/lipas
volumes:
  runner-state:
  runner-work:
```

### Permissions: name the truth, then gate upstream

The runner needs the docker socket (to run `docker compose`) and write access
to `/var/lipas`. **The docker socket is root-equivalent on the host — no user
mapping or container hardening changes that.** A runner whose job is to
administer the host cannot be meaningfully sandboxed *from* the host; the real
security boundary is **what code can ever reach the runner**. Layers:

1. **Workflow triggers:** only `deploy-prod.yml` targets
   `[self-hosted, lipas-prod]`; it is `workflow_dispatch`-only. No PR-triggered
   workflow ever names this label.
2. **Environment gate:** `lipas-prod` environment with required reviewers —
   the job does not start until a human approves — plus a deployment branch
   policy (master + `prod-*` tags only).
3. **Runner group** (org-level): put the runner in a group restricted to the
   `lipas` repository, and — if the plan supports it — restricted to the
   selected workflow `deploy-prod.yml`. This is the control that stops a
   workflow file on a rogue branch from targeting the label at all.
   (Verify availability on the org's GitHub plan; public-repo defaults
   already block fork PRs from self-hosted runners — keep that setting on.)
4. **No secrets on GitHub's side:** `install.sh` sources `/var/lipas/.env.sh`
   on the host. GitHub holds no prod credentials; the runner registration
   state can only *receive jobs*, not administer the repo.
5. **No long-lived tokens on the host:** register once with a short-lived
   registration token and persist the runner state volume. (The alternative —
   ephemeral runners re-registering per job — requires a repo-admin PAT or
   GitHub App key on the host, a *worse* secret to lose. Ephemeral's benefit
   is hygiene for untrusted jobs; every job here is human-approved.)

### Robustness

- `restart: unless-stopped` + docker daemon enabled at boot → survives
  reboots and crashes; persisted state volume → survives recreation without
  re-registration.
- **Offline runner ≠ lost deploy:** dispatched jobs queue (up to 24 h)
  waiting for the runner and are visible in the Actions UI. Add
  `timeout-minutes` to the deploy job so a wedged run fails loudly.
- **Heartbeat:** a weekly scheduled workflow runs a trivial job on the runner
  (echo + disk-space check). Runner rot is then discovered on a calm Tuesday,
  not during an urgent deploy. The break-glass laptop door covers the gap
  while repairing.
- **Updates:** pin the image; GitHub eventually refuses old runner versions,
  so bump the tag on a calendar cadence (the heartbeat workflow can warn when
  the runner version trails the latest release).
- **Disk:** `_work` is a bounded named volume; deploys' `--build` on the
  nginx image slowly accumulates dangling layers — an occasional
  `docker system prune` (existing host cron) covers it.

### Rejected hardening (for now): socket-less runner

A stricter design removes the docker socket entirely: the runner only drops
artifacts + a manifest into a watched directory, and a root systemd path unit
on the host validates and runs `install.sh`. This removes root-equivalence
from the runner but makes the workflow blind to install results (async
status-file polling), doubles the moving parts, and still ends in root running
repo-supplied code. With dispatch-only + required reviewers + runner groups,
the marginal security is not worth the operational opacity. Revisit if the
runner ever has to serve untrusted workloads.

## docker-compose.yml changes

- `backend-migrate` → `java -jar backend.jar -m lipas.migrate-db migrate`
- `backend-index-search` → `java -jar backend.jar -m lipas.search-indexer`
- `backend-maintenance` → `java -jar backend.jar -m lipas.maintenance`
- `backend-refresh-wfs` → `java -jar backend.jar -m lipas.wfs.core`
- `scripts/reindex.sh` (cron) keeps working unchanged — same service name.
- Move dev/build-only services (`backend-dev`, `backend-build`,
  `backend-tests`, `backend-seed`, `worker-local`, `frontend-*`, `node-base`,
  `mailhog`, `proxy-local`) behind a compose **profile** (e.g.
  `profiles: [dev]` / `[build]`) so `docker compose ps/up` on servers only
  ever sees runtime services, and nobody re-grows an on-host build habit.
- Later (optional): runtime `backend-base` to a plain JRE image; narrow the
  `./webapp` mount to `backend.jar` + `resources/public`; align node-base
  (node:16!) with CI's node 20; then the `mvn_cache` volume can be dropped on
  servers.

## Cleanup list

Tracked gunk (delete in the same PR as the rework):

- `scripts/deploy.sh`, `scripts/deploy-backend.sh`, `scripts/deploy-frontend.sh`
- `docker-compose.yml.backup`
- bb.edn: `-do-deploy` + 8 of the 9 deploy task wrappers
- `docs/ops.md` deployment section → rewrite: the three doors, when to use
  which, runner locations, rollback procedure

Host/local gunk (manual sweep, not git):

- Stray local artifacts in checkout roots: `legacy-api.jar`,
  `legacy-api-worker.jar`, `*.backup` DB dumps (gitignored, but investigate
  whether the legacy-api jars are still what's actually running anywhere
  before deleting).
- `/tmp/backend.jar`, `/tmp/*.js*` leftovers from scp deploys on both hosts.
- After compose one-offs go jar-based: `mvn_cache` docker volume on prod.
- Dev host: build caches accumulated by the old on-host builds.

## Rollout order (each step independently shippable)

1. **Installer + jar-based one-offs.** Add `scripts/deploy/install.sh`, switch
   the four compose one-off commands to the jar. No flow changes yet.
   Verify via a label deploy to dev (migrate + reindex prove the jar paths).
2. **Rework deploy-dev.yml** to cloud-build + artifact install via the
   installer. Extract shared build steps into a composite action used by both
   ci.yaml and deploy-dev.yml. Verify label flow end-to-end.
3. **Prod runner + deploy-prod.yml.** Provision the runner container on
   lipas-prod (mirror dev), create the `lipas-prod` GitHub Environment with
   required reviewers, add the workflow. First run at a quiet moment,
   `reindex=false`, with the laptop door warmed up as fallback.
4. **Tighten door 3 + delete gunk.** Re-point bb to the installer, collapse
   tasks, delete stale scripts + compose backup, rewrite ops.md.
5. **Optional later:** compose profiles polish, JRE runtime image, mount
   narrowing, node alignment, GHCR images if ever desired.

## Deliberate non-goals

- No GHCR / container-image delivery (revisit only if hosts multiply).
- No blue-green / zero-downtime — restart blips are accepted today; the
  ordered bundle-then-index install already avoids broken frontends.
- No change to what deploys *contain* (jar + public bundle + migrations +
  optional reindex) — only where they're built and how they travel.
