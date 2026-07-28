# clj-kondo + cljfmt enforcement

As-built record. Everything below is implemented on `chore/lint-format-gates`,
stacked on the reformat in `chore/cljfmt-reformat`.

Measurements taken 2026-07-28 against `master` @ `93c5ac66`.

## Why formatting drifted

The repository had a formatter hook, a linter config and a formatter config,
and none of them were reaching the code. 183 of 409 Clojure files (45%) did not
match `webapp/.cljfmt.edn`, and 104 of those had been touched in the preceding
two months — active drift, not legacy debt.

Two independent causes, both verified rather than inferred.

### 1. The hook was not loaded at all (the actual cause)

Hooks were configured in `webapp/.claude/settings.local.json`. Claude Code loads
project settings from the directory the session was started in, so that file is
only read when a session is rooted at `webapp/`. This project is normally opened
at the repository root, where nothing configured a formatter.

Two pieces of evidence:

- The hook's own stats log, `~/.clojure-mcp-light/stats.log`, records its last
  event on **2026-04-01**. Nothing since.
- A `Write` of a deliberately mis-indented `.clj` file in a root-rooted session
  came back byte-identical, and produced no new stats entry.

The drift histogram matches: 88 of the 183 files were last touched in 2026-07
and 16 in 2026-06, all after the hook went quiet.

### 2. Config discovery is cwd-dependent (latent, still real)

Both cljfmt and clj-kondo resolve config by walking **up** from the process
working directory, and this project's config lives in `webapp/` rather than at
the repository root:

```
$ cd webapp && cljfmt fix accessibility.clj              # finds .cljfmt.edn -> :cursive
$ cd .       && cljfmt fix webapp/…/accessibility.clj    # finds nothing      -> :community
70 differing lines on one 100-line file

$ clj-kondo --lint webapp/src/cljs/lipas/ui/stats/common.cljs   # from root
linting took 55ms, errors: 0, warnings: 0
$ cd webapp && clj-kondo --lint src/cljs/lipas/ui/stats/common.cljs
… warning: Unsorted namespace … errors: 0, warnings: 1
```

This did not cause the drift — nothing was running — but it would have caused it
the moment a hook was added at the root, and it silently weakened every manual
`clj-kondo` invocation from the repository root.

### A third, upstream

`clojure-mcp-light`'s `--cljfmt` flag decides whether a file needs formatting by
calling `cljfmt.core/reformat-string` with **no config argument**, then formats
via `cljfmt.main`, which does read config. A file already clean under default
`:community` style is judged "already formatted" and never converted to
`:cursive`. Still present in v0.2.2, so the hooks here do not use that flag;
formatting runs from `webapp/` where config resolves.

## Toolchain

Everything was months behind. Updated first, because the reformat target depends
on it:

| Tool | Was | Now |
|---|---|---|
| clj-kondo | 2026.04.15 | 2026.07.24 |
| cljfmt | 0.13.1 | 0.16.5 |
| clojure-lsp | 2026.02.20 | 2026.07.06 |
| clojure-mcp-light | `4babfb57` | `d341c239` (v0.2.2+) |

`brew outdated` reported nothing because `borkdude/brew` had become an untrusted
tap and its formulae could not be loaded; `brew trust borkdude/brew` fixed that.
The old `cljfmt` 0.13.1 binary still sits at `/usr/local/bin/cljfmt`, shadowed by
`/opt/homebrew/bin` earlier in `PATH`. Removing it needs sudo.

Two consequences worth recording:

- **clj-kondo 2026.07.24 reports `errors: 0`.** The three
  `clojure.test.check.clojure-test/defspec` "Unresolved symbol" errors in
  `gis_test.clj` were an upstream gap, not a code defect. No fix was needed.
- **The reformat target is version-stable.** cljfmt 0.16.5 flags the identical
  179-file set as 0.13.1, and produces byte-identical output to both 0.13.1 and
  clojure-lsp 2026.07.06. The reformat was therefore safe to apply.

## Speed, and why per-edit enforcement is practical

| | |
|---|---|
| `cljfmt check` one file | 31 ms |
| `cljfmt fix` one file | 98 ms |
| `clj-kondo --lint` one file | 16–56 ms |
| `clj-kondo --lint src test dev` | 2.9 s |
| `cljfmt check src test` | 10 s |
| `clojure-lsp format` one file | **4.9 s** |

Both are GraalVM binaries, so a per-edit hook is essentially free. The last row
is why `bb cljfmt` was replaced: it shelled out to clojure-lsp, which
re-analyses the whole project on every call, for byte-identical output — 50×
slower.

`bb clean-ns` stays on clojure-lsp, because removing unused requires genuinely
needs whole-project analysis.

## What was built

### Layer 0 — determinism (`webapp/bb.edn`)

Every task pins its working directory to the `bb.edn` directory, so the verdict
never depends on where it was invoked from. Paths may be passed either
webapp-relative or repo-root-relative, so `bb fmt $(git diff --name-only)`
works.

```
bb lint          clj-kondo, errors fail        (all sources or given files)
bb lint-strict   as above, warnings fail too
bb lint-changed  clj-kondo on files changed vs LINT_BASE
bb fmt           cljfmt fix
bb fmt-check     cljfmt check
bb check         fmt-check + lint-strict — what CI runs
```

Scope went from `src` only to `src test dev build.clj scripts`. `bb lint`
covering only `src` is why the three errors in `test/` were invisible.

The vendored `.clj-kondo/<lib>/` configs are now committed. Previously 5 of 13
directories were tracked while `.gitignore` excluded the rest, so CI would have
linted against a different config set than any developer machine.

### Layer 1 — Claude Code hooks (~90 ms/edit)

Committed at **both** `.claude/settings.json` and `webapp/.claude/settings.json`,
so the session root no longer decides whether formatting happens. Both point at
`scripts/claude-hooks/`, which locate the repository from their own path.

| Event | Script | Does |
|---|---|---|
| PreToolUse `Write\|Edit` | `paren-repair.sh` | delimiter repair, no-op if not installed |
| PostToolUse `Write\|Edit` | `format-and-lint.sh` | `cljfmt fix` + `clj-kondo`; findings exit 2 |
| Stop | `lint-session.sh` | whole-project check, warnings included |

`format-and-lint.sh` reports **warnings as well as errors**. That only became
safe once the tree reached zero warnings — while 177 of 409 files still carried
pre-existing ones, repeating them on every edit to a legacy file would have been
noise, so the hook was errors-only until the cleanup landed. Exit 2 is how a
PostToolUse hook feeds text back to the model.

`lint-session.sh` exists because the per-file hook cannot see cross-file
breakage — rename a var and the dangling reference is in a namespace nothing
re-lints. ~3 s, but only when a Clojure file actually changed. Honours
`stop_hook_active` so it cannot loop.

Checked against the incident that made `Edit` unsafe on conflicted files: with
git conflict markers present, cljfmt refuses to parse and leaves the file
byte-identical while clj-kondo reports the unmatched bracket. This hook does not
rebalance parens, so it cannot corrupt a conflicted file.

### Layer 2 — pre-commit (`.githooks/pre-commit`)

`cljfmt check` + `clj-kondo --fail-level warning` on staged Clojure files only.
Opt-in per clone via `git config core.hooksPath .githooks`, which `setup-dev.sh`
now does. Missing tools degrade to a warning. Bypass with `--no-verify`.

Limitation, noted in the hook: it reads the working tree, not the staged blobs,
so a partially staged file is checked as it sits on disk.

### Layer 3 — CI (`.github/workflows/ci.yaml`)

New `lint` job: `bb fmt-check` and `bb lint-strict`. No services, no JVM, no
dependency resolution — verified that a cold checkout with no
`.clj-kondo/.cache` gives the same result as a warm local one, so `bb init-lint`
is not needed. Tool versions are pinned to match the local toolchain.

## The warning cleanup

The repository went from **484 clj-kondo warnings to 0**, which is why the gates
above fail on warnings rather than only errors, and why the merge-base warning
ratchet that existed briefly has been deleted — `--fail-level warning` applied
to the whole tree is strictly stronger and much less machinery.

38 of the 484 needed no code change at all, only `.clj-kondo/config.edn`:
`:unresolved-var` exclusions for the eight `hugsql/def-db-fns` namespaces and
the three `deftranslations` i18n namespaces, and `:config-in-ns` turning off
`:unresolved-namespace` for `lipas.ui.lazy`, whose entire job is naming
namespaces it must not require.

The rest was split across six parallel agents by directory. What it surfaced:

- **Namespaces used fully qualified but never required** — the dominant real
  bug, 25 instances. `clojure.set` in six test namespaces, `clojure.string` in
  eight files across clj/cljc/cljs, plus `clj-http.client`, `next.jdbc`,
  `honey.sql`, `integrant.repl.state` and four `with-redefs` targets in
  `dispatcher_test.clj`. Each worked only because some other namespace happened
  to load the dependency first.
- **A duplicate `def`** in `api/v1/sports_place.clj`, the second silently
  shadowing the first.
- **A namespace required twice under two aliases** in `analysis/heatmap.clj`,
  both in use.
- **An eager static require defeating intentional lazy loading** —
  `lipas.backend.system` statically required `lipas.jobs.system` while `-main`
  separately does `(require 'lipas.jobs.system)` + `resolve` in worker mode
  specifically to keep worker-only deps out of server boot.
- **Four `deftest` forms with a string in docstring position.** `deftest` takes
  no docstring, so each was evaluated and discarded.
- **A MUI/recharts `Tooltip` name collision** where two modules bound the same
  bare name and require order decided which won.
- **Two tests asserting less than their siblings** — LineString and Polygon
  sub-tests in `wfs/core_test.clj` destructured `status` and never checked it,
  while the Point sub-test did.

## The trap that makes `clean-ns` unsafe here

clojure-lsp's `clean-ns` removes any require clj-kondo calls unused, and
clj-kondo is wrong in two ways that both fail silently.

**Aliases colliding with `cljs.core`.** With
`["@mui/material/Box$default" :as Box]` and bare `[:> Box ...]`, clj-kondo
resolves the bare symbol to the real `cljs.core/Box` deftype and never records a
use. Delete the require and clj-kondo *still* reports zero warnings, and the
ClojureScript release build *still* succeeds — `cljs.core/Box` genuinely exists.
The component is simply wrong in the browser. Colliding names, determined
empirically: `Box`, `List`, `Symbol`, `Keyword`, `Delay`, `Atom`, `Var`,
`Range`, `Cons`, `Empty`, `Reduced`, `Volatile`, `MultiFn`, `Namespace`,
`Repeat`, `Iterate`, `Cycle`. `Set` and `Map` do not collide. 18 requires were
restored across the frontend for this reason.

**Requires kept for load-time side effects.** re-frame `reg-event-*`/`reg-sub`,
`defmethod` implementations, integrant `init-key` methods, proj4 projection
registration. `test_utils.clj` requiring `lipas.backend.system` is the clearest
case: nothing references it, but deleting it would have broken `ig/init` for the
entire test suite.

Both are restored with `#_{:clj-kondo/ignore [:unused-namespace]}` and a comment.

**How to tell a false positive from genuinely dead code:** check whether the
only usage sits inside a `#_` discard or `(comment ...)` block. clj-kondo skips
those (`:skip-comments true`), so such a require really is unused. This matters
— a first attempt at an automated checker did not model discards and reported
three files as broken whose usages were all inside `#_(defn ...)`. Restoring
those requires would have been wrong.

## Verification performed

Neither the test suite nor a release build can catch a wrongly removed
side-effecting require, so the cleanup was checked by:

- **Require-graph diff** against the pre-cleanup commit, inspecting every
  removed edge for top-level side effects in the target namespace. 69 removed,
  68 with no side effects, 1 flagged and confirmed correct by hand. The detector
  was itself validated by confirming it fires on known re-frame namespaces and
  stays quiet on pure ones.
- **Component-binding check** over all 194 `src/cljs` files: every symbol used
  in live `[:> ...]` position is bound by its ns form. Verified clean on the
  pre-cleanup tree first, so any hit would have been newly introduced.
- **ClojureScript release build**: 810 files compiled, 0 shadow-cljs warnings
  (an unresolved symbol would appear here as "Use of undeclared Var").
- **`bb bundle-size-check`**: `:app` 795 KB gzip against the 815 KB budget,
  confirming none of the 25 added requires hoisted a lazy module into the base
  bundle.
- **Backend test suite** compared against a baseline captured at the
  pre-cleanup commit in a separate worktree, because the local test database
  carries schema drift that fails a couple of search tests regardless.

## Left undone

- **`utils_test.cljc`** binds `invalid-data {:name "John" :age -5}` and never
  asserts on it. The intended check could not be inferred, so it is marked
  `_invalid-data` with a TODO rather than given an invented assertion.
- **`backend/ptv_test.clj`** lost its `lipas.data.types` require, whose only use
  is inside a fully `#_`-discarded integration test. Correct today; that test
  would need the require back if reinstated.
- **`sync-model!`** in `integration/yti/tietomallit.clj` documented a
  `:skip-existing` option that was destructured but never used — the behaviour
  it promised does not exist. The dead option was removed rather than left
  misleading. If it was meant to work, that is a separate change.
- **Two possible gaps in `map/map.cljs`**, surfaced while removing dead
  bindings and deliberately not "fixed", since either fix would invent
  behaviour: `map-inner`'s `component-did-mount` never calls `set-overlays!`
  (only `component-did-update` does), and `update-diversity-mode!` ignores
  `lipas-id`/`fit-nonce`/`sub-mode` that its sibling `update-reachability-mode!`
  uses.
- **babashka is one minor behind** (1.12.217 local and pinned in CI, 1.13.219
  available). Not bumped here — bb also runs the deploy tasks.
- **`/usr/local/bin/cljfmt` 0.13.1** is shadowed by the 0.16.5 install but still
  present; removing it needs sudo.
