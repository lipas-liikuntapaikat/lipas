#!/usr/bin/env bash
#
# Claude Code PreToolUse hook: delimiter repair for Clojure edits.
#
# Thin guard around clj-paren-repair-claude-hook from clojure-mcp-light, which
# is installed per-developer via bbin and so may be absent. Without the guard a
# missing binary makes every Write/Edit emit a hook failure.
#
#   bbin install https://github.com/bhauman/clojure-mcp-light.git --tag v0.2.2 \
#     --as clj-paren-repair-claude-hook --main-opts '["-m" "clojure-mcp-light.hook"]'
#
# Note the deliberate absence of --cljfmt. That flag decides whether a file
# needs formatting by calling cljfmt.core/reformat-string with NO config
# argument — i.e. cljfmt's defaults, not this project's .cljfmt.edn — and only
# then shells out to cljfmt.main, which does read the config. A file already
# clean under default :community style is therefore judged "already formatted"
# and never converted to this project's :cursive style. Still true as of v0.2.2.
# Formatting is handled by format-and-lint.sh on PostToolUse instead, which runs
# cljfmt from webapp/ where the config actually resolves.

set -uo pipefail

command -v clj-paren-repair-claude-hook >/dev/null 2>&1 || exit 0

# exec preserves stdin, stdout and the exit code, all of which the hook
# protocol depends on.
exec clj-paren-repair-claude-hook "$@"
