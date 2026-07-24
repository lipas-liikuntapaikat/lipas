# Frontend code splitting (bundle size)

Branch: `feat/bundle-splitting`. Splits the single 6.7 MB (1.73 MB gzip)
release bundle into shadow-cljs `:modules` loaded on demand.

## Results (release build report)

| module | JS | gzip | loaded when |
|---|---|---|---|
| `:app` (base) | 3.2 MB | **755 KB** | always |
| `:map` | 1.6 MB | 413 KB | map routes (OpenLayers, turf, shpjs, sports-site forms, search, loi, reports) |
| `:geo` | 254 KB | 76 KB | with `:map` or `:ptv` (proj4 + EPSG:3067 + ol/proj) |
| `:charts` | 627 KB | 171 KB | first chart render (recharts; stats/admin/analysis dep, elevation profile) |
| `:analysis` | 539 KB | 122 KB | analysis tools opened (@turf/buffer + jsts, reachability/diversity/heatmap) |
| `:ptv` | 450 KB | 85 KB | PTV dialog / site PTV tab / admin |
| `:admin` | 219 KB | 40 KB | /admin |
| `:org` | 158 KB | 28 KB | org routes + site editing-rights panel |
| `:stats` | 118 KB | 18 KB | /tilastot |
| `:xlsx` | 100 KB | 31 KB | first excel export (zipcelx+jszip) |
| `:help-manage` | 60 KB | 11 KB | help CMS editor (admin) |
| `:i18n-en` / `:i18n-se` | 50 KB | 17 KB | language switched (fi is baked in) |

- Front-page/stats/login entry: 1732 KB → **755 KB gzip (−56 %)**
- Map-first entry (liikuntapaikat.lipas.fi): 1732 KB → **1243 KB gzip (−28 %)**,
  and `index.html` emits `<link rel=prefetch>` for `geo`/`map`.
- Total across modules grew ~11 % raw (~3 % gzip): cross-module references
  block some Closure property collapsing. Expected cost of splitting.

## Architecture

- **`lipas.ui.lazy`** — the one registry of `shadow.lazy` loadables +
  `lazy-view` component wrapper + `::load-fx` / `::load-then` re-frame fx.
- **Routes** carry a loadable in `:view`; `lipas.ui.routes/on-navigate`
  loads the module *before* dispatching `::navigated`, so route
  controllers always dispatch into registered handlers. Feature
  `routes.cljs` files stay in `:app` but require no views/events —
  controllers use literal namespaced keywords.
- **`lipas.ui.map.hooks`** — registry the `:analysis` module installs
  into on load (`lipas.ui.analysis.map-integration`): reachability
  buffer drawing (`lipas.ui.analysis.buffer`, owns @turf/buffer/jsts)
  and the `:heatmap` `popup-body` defmethod. Analysis map modes can only
  activate through analysis UI, so the registry is always populated in
  time; accessors no-op otherwise.
- **Assistant** (always mounted) reaches map/PTV only via literal
  keywords wrapped in `[:lipas.ui.lazy/load-then :map …]`, plus light
  extractions: `lipas.ui.geom` (length/area/elevation, small turf pkgs),
  `lipas.ui.ptv.context` (pure db-reader for dialog context),
  `:lipas.ui.subs/ptv-dialog-open?` (cross-module root sub, see below).
- **Cross-module root subs** — when always-loaded UI needs data from a
  lazy feature's db region, the root (extractor) sub is registered in
  the *shallowest module that needs it* and the feature's own subs layer
  on it with `:<-`; the db path is written exactly once. Base-resident
  roots live in the "Cross-module root subs" section of `lipas.ui.subs`
  (ptv dialog, admin users/orgs); `::analysis-mode-inputs` in
  `lipas.ui.map.subs` is the same pattern one level down (`:map` ←
  `:analysis`). Signal chains may only point toward `:app` along module
  `:depends-on` edges — that direction is registration-race-free because
  the module loader loads dependencies before dependents. The reverse
  direction (base subscribing to a lazy module's sub) is exactly the
  unregistered-sub error below.
- **i18n**: cljs bundles only `:fi`; `lipas.i18n.register-en/-se`
  (module entries) call `i18n/register-dict!` on load.
  `::set-translator` switches immediately (tongue falls back to fi) and
  re-sets after the dictionary module lands. JVM side unchanged (all
  dicts static via reader conditionals).
- **Excel**: `:lipas.ui.effects/download-excel!` fx lazy-loads
  `lipas.ui.excel`; callers unchanged.
- **form-table** (drag-sortable, @hello-pangea/dnd) split out of
  `components.tables` into `components.form-table` — all users are in
  `:map`, so dnd left the base bundle.
- **cache-bust hook** renders the base module script tag + prefetch
  links from `manifest.edn`.

## Gotchas for future work

- Never statically require a lazy-module ns from `:app` (or from a
  module that doesn't depend on it) — shadow silently *hoists* the
  shared code up to the common dominator module (usually `:app`) instead
  of erroring. Check `npm run build-report` per-module sizes after
  adding cross-feature requires. CI guards the headline win:
  `bb bundle-size-check` (run after the release build) fails if `:app`
  exceeds its gzip budget (`app-budget-kb` in `bb.edn`) and prints all
  module sizes. If it trips, find what hoisted via the build report;
  bump the budget only for intentional base growth. The reverse
  direction is always safe: lazy modules may require base namespaces
  with normal aliases.
- Dispatching an event whose handler lives in an unloaded module drops
  the event with a console error — wrap with
  `[:lipas.ui.lazy/load-then <module> <event>…]`.
- Subscribing to an unregistered sub returns nil + console error — for
  always-mounted UI, register a cross-module root sub in the shallower
  module and layer the feature's subs on it (see Architecture above).
  Don't duplicate the db path in two independent root subs.
- In dev all modules load eagerly at page load; real lazy loading only
  happens in release builds. Verify with a release build served locally.
- If a module fetch fails (network drop), goog's module manager retries
  a few times and then gives up on that module *permanently* — a later
  `load!` won't refetch. The sticky failure notification therefore
  advises reloading the page. Navigations whose module never loaded also
  never dispatch `::navigated`, so the previous view stays rendered
  (guarded by a nav token against stale loads completing late).
- `lipas.data.*` (~550 KB raw) is still baked into `:app` —
  data-from-server was deliberately left out of scope.

## Simplification candidates

- **i18n split** (~34 KB gzip for en+se) is the weakest trade here: a
  reader-conditional `i18n/core`, dictionary-atom rebuild machinery and
  two race guards (`::set-translator`/`::dict-loaded`), plus a Finnish
  flash for en/se users on first visit. If the translator machinery
  misbehaves, fold the dictionaries back into `:app` first — it is
  self-contained: delete the `:i18n-en`/`:i18n-se` modules and
  `lipas.i18n.register-*`, restore static dicts in `lipas.i18n.core`,
  drop the `::dict-loaded` chain.
