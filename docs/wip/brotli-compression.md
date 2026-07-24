# Brotli compression (static precompression + in-flight)

Branch: `feat/brotli-compression`. Follow-up to the bundle-splitting work
(PR #215): switch transfer compression from gzip-only to brotli-first with
gzip fallback.

## Measured impact

Baseline was nginx on-the-fly `gzip_comp_level 6` for everything (including
recompressing the immutable hashed bundles on every request; no
`gzip_static`).

Static JS, latest release build (13 modules):

| payload                          | gzip -6 (old) | brotli -q11 | delta  |
|----------------------------------|---------------|-------------|--------|
| initial load (app + map + geo)   | 1 244 KB      | 987 KB      | −21 %  |
| all 13 modules                   | 1 784 KB      | 1 424 KB    | −20 %  |

Dynamic JSON (real `/api/actions/search` response, 500 sites, 3.1 MB raw):

| codec        | size    | CPU/request |
|--------------|---------|-------------|
| gzip -6 (old)| 713 KB  | 66 ms       |
| brotli -q5   | 397 KB  | 32 ms       |
| brotli -q11  | 279 KB  | 1.9 s (precompression only, never in-flight) |

The JSON/transit win (−44 %) is bigger than the JS win because the search
responses are full of repeated keys and brotli's window (up to 4 MB) sees all
of it, unlike gzip's 32 KB. In-flight brotli q5 also costs *half* the CPU of
the gzip -6 it replaces. Precompressing the whole module set at q11 takes
~8 s at build time.

## How it works

- **`nginx/Dockerfile`** (new): two-stage build compiling google/ngx_brotli
  as dynamic modules against the exact `nginx:stable` of the base image.
  `proxy-base` in docker-compose now uses `build: ./nginx` /
  `image: lipas/proxy`; the image is built on each host, never pulled.
- **`nginx/nginx.conf`**: `load_module` for the two brotli modules. This
  config no longer starts on a stock nginx image — image and config must
  move together.
- **`nginx/proxy*.conf`**: `brotli on; brotli_comp_level 5;` +
  `brotli_types` mirroring the gzip block in every server block, and
  `brotli_static on; gzip_static on;` in the SPA-serving blocks. Clients
  without `Accept-Encoding: br` keep getting gzip; nothing breaks for old
  API consumers.
- **`webapp/scripts/precompress.mjs`** (new): after a release build, writes
  `.br` (q11) and `.gz` (-9) siblings next to every content-hashed bundle
  listed in manifest.edn. Node built-ins only. Dev builds are exempt by
  design: unhashed `app.js` never gets a sibling, because `brotli_static`
  prefers the sibling without mtime comparison and a stale `app.js.br`
  would shadow fresh dev code.

## Deploy paths (both covered)

1. **Manual (`webapp/bb.edn`)**: `npm run build` now runs precompress;
   `-do-deploy` uploads *all* module bundles + `.br`/`.gz` siblings (this
   also fixes a gap where only the `:app` bundle was scp'd — lazy modules
   were missing from manual frontend deploys after code splitting). The
   remote install/restart now runs as a script over ssh stdin, and the
   proxy is recreated with `up -d --no-deps --build --force-recreate`
   against whichever proxy variant the host is running.
2. **Automated (deploy-dev.yml)**: new `frontend-precompress` compose run
   after `frontend-build`; proxy restart replaced with the same
   detect + `up --build --force-recreate` logic.

`--force-recreate` preserves the old `restart proxy` semantics: nginx caches
the backend container IP, so the proxy must bounce whenever backend/worker
are recreated.

## Rollout notes

- First deploy per host compiles ngx_brotli (~1–3 min, then cached). Both
  deploy paths run `docker compose up -d --build` for the proxy, so no
  manual host prep is needed — but the *first* proxy restart after merging
  must go through a deploy (or a manual `docker compose build proxy` +
  `up -d proxy`), never a bare `docker compose restart proxy` with the old
  image, which would fail on the new `load_module` lines.
- Local dev: `docker compose build proxy-local` once, then
  `docker compose up -d --force-recreate --no-deps proxy-local`.
- Rollback = revert the branch and `docker compose up -d --no-deps --build
  --force-recreate <proxy-svc>` — compose rebuilds from the reverted config
  (plain nginx:stable, no load_module).

## Verification (local, release build + real backend)

- `app.<hash>.js` with `Accept-Encoding: br` → `Content-Encoding: br`,
  byte size identical to the on-disk `.br` (brotli_static serving, not
  recompression); with `gzip` → `.gz` file served; correct
  `Content-Type`, `Cache-Control: immutable`, `Vary: Accept-Encoding`.
- `POST /api/actions/search` with `br` → in-flight brotli (~q5 size);
  with `gzip` → gzip as before.
