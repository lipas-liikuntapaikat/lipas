# LIPAS self-hosted GitHub Actions runner

Runs deploy jobs on lipas-dev / lipas-prod. Design rationale (why a separate
compose project, why the docker socket, why no PAT on the host):
`docs/wip/deployment-consolidation.md` → "Prod runner design".

## Provision (per host)

```bash
sudo mkdir -p /opt/lipas-runner
cd /opt/lipas-runner
# copy docker-compose.yml and .env.sample from this directory (e.g. scp)
sudo cp .env.sample .env && sudo chmod 600 .env
sudo $EDITOR .env        # pin image tag, set name/labels, paste a fresh
                         # registration token from repo Settings → Actions →
                         # Runners → New self-hosted runner
sudo docker compose up -d
```

Verify: repo → Settings → Actions → Runners shows the runner as **Idle**.
Then optionally blank `RUNNER_TOKEN` in `.env` — registration lives in the
`runner-state` volume from now on.

> The env var names above are for the `myoung34/github-runner` image. When
> bumping the pinned version, skim its changelog for renamed variables.

## GitHub-side configuration (once)

1. **Runner group** (org → Settings → Actions → Runner groups): put the
   runners in a group restricted to the `lipas` repository, and — if the
   plan offers it — restricted to selected workflows
   (`deploy-dev.yml`, `deploy-prod.yml`, `runner-heartbeat.yml`). This stops
   workflow files on arbitrary branches from targeting the runner labels.
2. **Environment `lipas-prod`** (repo → Settings → Environments): required
   reviewers = the maintainers allowed to approve prod deploys; deployment
   branch policy = `master` only. The deploy job pauses until approved.
3. Keep the default **fork PR protections** for self-hosted runners enabled.

## Operate

- **Health:** the weekly `Runner heartbeat` workflow runs a trivial job on
  every runner and fails on low disk. A heartbeat stuck in "queued" means the
  runner is down.
- **Restart / recreate:** `docker compose restart runner` or
  `docker compose up -d --force-recreate runner` — registration survives in
  the `runner-state` volume.
- **Upgrade:** bump `RUNNER_IMAGE_TAG` in `.env`, `docker compose pull &&
  docker compose up -d`. GitHub refuses ancient runner versions, so do this
  on a calendar cadence, not only when forced.
- **Remove:** repo → Settings → Actions → Runners → remove; then
  `docker compose down -v` (the `-v` drops the registration state).
- **While the runner is down:** deploys queue for up to 24 h and run when it
  returns; for urgent fixes use the break-glass door
  (`cd webapp && bb deploy prod` over VPN).

## Migrating the hand-provisioned lipas-dev runner

lipas-dev runs a trial-and-error-era runner (`gha-runner-lipas-ci`, compose
project under `/home/lipas-ci/runner`) whose compose file carries an inline
PAT (`ACCESS_TOKEN`) and registration token **world-readable on a
shared-admin host**. Replace it:

1. Provision the new runner per the section above
   (`RUNNER_NAME=lipas-dev-1`, `RUNNER_LABELS=lipas-dev`); verify it shows
   **Idle** on GitHub alongside the old `lipas-ci` runner.
2. Retire the old one — from `/home/lipas-ci/runner`:
   `docker compose down -v` (also drops its state volume), then delete the
   directory (this is what removes the PAT from disk).
3. Remove the old `lipas-ci` runner entry in repo → Settings → Actions →
   Runners if it lingers.
4. **Revoke the PAT** the old setup used (github.com → Settings → Developer
   settings → tokens) — treat it as exposed; it has been readable by every
   local user for months.

Also on lipas-dev: two abandoned *native* runner installs predate the
container era — `/opt/actions-runner` (with a still-enabled
`actions.runner.…lipas-dev2.service` systemd unit) and
`/home/lipas/actions-runner`. Neither is registered on GitHub anymore:
`systemctl disable` the unit, remove its file, `rm -rf` both directories.
