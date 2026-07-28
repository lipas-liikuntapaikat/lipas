#!/usr/bin/env bash
#
# Claude Code PostToolUse hook: repair delimiters, format, and lint one file.
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
# Formatting is applied silently. clj-kondo findings exit 2, which is how a
# PostToolUse hook feeds text back to the model — so unresolved symbols, arity
# mistakes, unused bindings and unbalanced delimiters surface immediately
# instead of at CI.
#
# This reports warnings as well as errors. That was not always safe: while the
# repository still carried ~480 legacy warnings, re-reporting them on every edit
# to an old file would have been pure noise. The tree is now at zero warnings
# and CI gates on that, so anything reported here is genuinely new.

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
WEBAPP="$REPO_ROOT/webapp"

payload="$(cat)"

# Delimiter repair runs FIRST, chained here rather than registered as a second
# PostToolUse hook, so there is exactly one writer to the file and the order is
# guaranteed: repair, then format, then lint.
#
# This half is not optional. clj-paren-repair's PreToolUse:Edit handler only
# takes a backup — the actual repair, and deletion of that backup, happen in its
# PostToolUse:Edit handler. Registering the Pre half without the Post half
# creates a backup per edit that nothing ever consumes: 133 orphaned files,
# ~2.9 MB, in a single session before this was caught.
#
# If repair reports a problem it emits hook JSON (decision: block) and possibly
# restores the file; forward that verbatim and stop, since linting a
# just-restored file would only add noise.
if command -v clj-paren-repair-claude-hook >/dev/null 2>&1; then
    repair_out="$(printf '%s' "$payload" | clj-paren-repair-claude-hook 2>/dev/null)"
    repair_rc=$?
    if [ -n "$repair_out" ]; then
        printf '%s\n' "$repair_out"
        exit "$repair_rc"
    fi
    [ "$repair_rc" -ne 0 ] && exit "$repair_rc"
fi

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
findings="$(printf '%s' "$kondo_out" | grep -E ': (error|warning): ' || true)"

if [ -n "$findings" ]; then
    {
        echo "clj-kondo findings in $rel:"
        echo
        printf '%s\n' "$findings"
        echo
        echo "The tree is at zero warnings and CI gates on that, so these are new."
        echo "Fix them before moving on. If one is genuinely a false positive,"
        echo "silence it narrowly with #_{:clj-kondo/ignore [:linter-name]} and a"
        echo "comment saying why the code is correct — not as a shortcut."
    } >&2
    exit 2
fi

exit 0
