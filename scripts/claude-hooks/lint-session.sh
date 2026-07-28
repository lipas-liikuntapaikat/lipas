#!/usr/bin/env bash
#
# Claude Code Stop hook: whole-project clj-kondo error check at end of turn.
#
# The PostToolUse hook only ever sees the one file that was just edited, so it
# cannot notice cross-file breakage — rename a var and the dangling reference
# lives in some *other* namespace, which nothing re-lints. This catches that
# once the edits have settled.
#
# Errors exit 2, which makes Claude Code keep working instead of ending the turn
# on broken code. Warnings are left to `bb lint-ratchet` in CI.
#
# Costs ~3 s over the whole project, but only when Clojure files actually
# changed in the working tree; otherwise it exits in a few milliseconds.

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
WEBAPP="$REPO_ROOT/webapp"

payload="$(cat)"

# Claude Code sets this when the turn is already being continued because of a
# Stop hook. Blocking again from here would loop.
already_active="$(printf '%s' "$payload" | jq -r '.stop_hook_active // false' 2>/dev/null)"
[ "$already_active" = "true" ] && exit 0

command -v clj-kondo >/dev/null 2>&1 || exit 0
[ -d "$WEBAPP" ] || exit 0

cd "$REPO_ROOT" || exit 0

# Cheap gate: nothing to do unless a Clojure source actually changed.
changed="$(git status --porcelain -- 'webapp/src' 'webapp/test' 'webapp/dev' 2>/dev/null \
    | grep -E '\.(clj|cljs|cljc|cljd|cljx|bb|edn)$' || true)"
[ -n "$changed" ] || exit 0

cd "$WEBAPP" || exit 0

kondo_out="$(clj-kondo --lint src test dev 2>&1)"
findings="$(printf '%s' "$kondo_out" | grep -E ': (error|warning): ' || true)"

if [ -n "$findings" ]; then
    {
        echo "clj-kondo findings across the project:"
        echo
        printf '%s\n' "$findings"
        echo
        echo "Some may be in files you did not edit directly — a rename or a"
        echo "removed var can break a reference elsewhere, and removing a require"
        echo "can leave another namespace short. The tree is kept at zero"
        echo "warnings, so fix these before finishing."
    } >&2
    exit 2
fi

exit 0
