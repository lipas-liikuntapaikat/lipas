#!/usr/bin/env bash
#
# Claude Code PostToolUse hook: format and lint a single edited Clojure file.
#
# Wired up in .claude/settings.json (repo root) and webapp/.claude/settings.json,
# so it runs whichever directory the session was started in. It locates the
# repository from its own path rather than from the process cwd, because cwd is
# exactly what cannot be trusted here: cljfmt and clj-kondo discover their
# config by walking UP from it, and this project keeps that config in webapp/.
#
# Runs in ~90 ms (both tools are GraalVM binaries; a single file is 16-56 ms for
# clj-kondo and ~31 ms for cljfmt), so it is cheap enough to run on every edit.
#
# Formatting is applied silently. clj-kondo ERRORS exit 2, which is how a
# PostToolUse hook feeds text back to the model — so unresolved symbols, arity
# mistakes and unbalanced delimiters surface immediately instead of at CI.
# Warnings are deliberately NOT raised here: 177 of 409 files carry pre-existing
# ones, and re-reporting them on every edit to a legacy file would be pure noise.
# Warnings are handled by `bb lint-ratchet` in CI, which only fails on files that
# got worse.

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
WEBAPP="$REPO_ROOT/webapp"

payload="$(cat)"

file_path="$(printf '%s' "$payload" | jq -r '.tool_input.file_path // empty' 2>/dev/null)"
[ -n "$file_path" ] || exit 0
[ -f "$file_path" ] || exit 0

# Only Clojure sources inside webapp/. Files elsewhere in the repo have no
# .cljfmt.edn above them, so formatting them here would apply the wrong style.
case "$file_path" in
    "$WEBAPP"/*) ;;
    *) exit 0 ;;
esac
case "$file_path" in
    *.clj|*.cljs|*.cljc|*.cljd|*.cljx|*.bb|*.edn) ;;
    *) exit 0 ;;
esac

command -v cljfmt   >/dev/null 2>&1 || exit 0
command -v clj-kondo >/dev/null 2>&1 || exit 0

# Path relative to webapp/, so both tools resolve config from there.
rel="${file_path#"$WEBAPP"/}"

cd "$WEBAPP" || exit 0

cljfmt fix "$rel" >/dev/null 2>&1

kondo_out="$(clj-kondo --lint "$rel" 2>&1)"
errors="$(printf '%s' "$kondo_out" | grep ': error: ' || true)"

if [ -n "$errors" ]; then
    {
        echo "clj-kondo found errors in $rel:"
        echo
        printf '%s\n' "$errors"
        echo
        echo "Fix these before moving on. (Warnings are not reported here; run"
        echo "\`cd webapp && bb lint $rel\` to see them.)"
    } >&2
    exit 2
fi

exit 0
