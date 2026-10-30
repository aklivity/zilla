#!/bin/sh
set -e
[ -d node_modules ] || npm install --no-audit --no-fund --silent >&2
script=$1
shift
exec node "$script.mjs" "$@"
