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
bb lint-ratchet  fail only where a changed file GAINED warnings
bb fmt           cljfmt fix
bb fmt-check     cljfmt check
bb check         fmt-check + lint — what CI and pre-commit run
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
| PostToolUse `Write\|Edit` | `format-and-lint.sh` | `cljfmt fix` + `clj-kondo`; errors exit 2 |
| Stop | `lint-session.sh` | whole-project error check |

`format-and-lint.sh` raises **errors only**. 177 of 409 files carry pre-existing
warnings; repeating them on every edit to a legacy file would be noise. Exit 2 is
how a PostToolUse hook feeds text back to the model.

`lint-session.sh` exists because the per-file hook cannot see cross-file
breakage — rename a var and the dangling reference is in a namespace nothing
re-lints. ~3 s, but only when a Clojure file actually changed. Honours
`stop_hook_active` so it cannot loop.

Checked against the incident that made `Edit` unsafe on conflicted files: with
git conflict markers present, cljfmt refuses to parse and leaves the file
byte-identical while clj-kondo reports the unmatched bracket. This hook does not
rebalance parens, so it cannot corrupt a conflicted file.

### Layer 2 — pre-commit (`.githooks/pre-commit`)

`cljfmt check` + `clj-kondo --fail-level error` on staged Clojure files only.
Opt-in per clone via `git config core.hooksPath .githooks`, which `setup-dev.sh`
now does. Missing tools degrade to a warning. Bypass with `--no-verify`.

Limitation, noted in the hook: it reads the working tree, not the staged blobs,
so a partially staged file is checked as it sits on disk.

### Layer 3 — CI (`.github/workflows/ci.yaml`)

New `lint` job: `bb fmt-check`, `bb lint`, and on PRs `bb lint-ratchet`. No
services, no JVM, no dependency resolution — verified that a cold checkout with
no `.clj-kondo/.cache` gives the same `errors: 0, warnings: 484` as a warm local
one. Versions pinned to match the local toolchain. Needs `fetch-depth: 0` for
the merge base.

### The ratchet, and why not `--fail-level warning`

484 warnings live across 177 of 409 files. Gating changed files at
`--fail-level warning` would fail roughly two PRs in five over warnings nobody
in that PR wrote, and the gate would be switched off within a week.

`bb lint-ratchet` (`webapp/scripts/lint_ratchet.bb`) instead materialises the
merge base in a throwaway git worktree, counts clj-kondo warnings per file on
both sides, and fails only where a file got worse:

```
❌ New clj-kondo warnings introduced:

  src/cljs/lipas/ui/stats/common.cljs  (1 → 2)
      …:3:14: warning: Unsorted namespace: @mui/material/Button$default
      …:43:9: warning: unused binding unused-here
```

New files have a base count of zero, so they must be warning-clean. Legacy
warnings are tolerated but can only go down.

## Current warning inventory (484)

```
190  unused binding / unused default
176  unused require + Unsorted namespace   <- largely `bb clean-ns`-able
118  everything else (unresolved, redundant let, inconsistent alias, …)
```

The 85 `@mui/material/*` "Unsorted namespace" warnings come from the project's
own `:unsorted-required-namespaces {:sort :case-sensitive}` interacting with JS
module strings. Worth deciding whether to normalise the order or exempt JS
modules — currently pure noise.

## Left undone

- **Warning burn-down.** The ratchet stops growth; it does not reduce the 484.
  `bb clean-ns` over the affected files would clear ~176 mechanically.
- **The `@mui/material/*` sort question** above.
- **`/usr/local/bin/cljfmt` 0.13.1** is shadowed but still installed; removing it
  needs sudo.
- **babashka is one minor behind** (1.12.217 local and pinned in CI, 1.13.219
  available). Deliberately not bumped — bb runs the deploy tasks too, so that is
  a separate change.
- **`bb lint-strict` is unused.** It exists for the day the warning count
  reaches zero and the repo-wide gate can replace the ratchet.
