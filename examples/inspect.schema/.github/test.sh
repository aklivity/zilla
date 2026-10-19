#!/bin/sh
set -x

EXIT=0
SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
ACTUAL_FILE=$(mktemp)

echo \# Testing inspect.schema/
echo

# WHEN
docker compose exec zilla zilla inspect schema > "$ACTUAL_FILE"
RESULT=$?
echo RESULT="$RESULT"

# THEN
# Byte-for-byte against a checked-in golden file, not a handful of spot
# checks -- the schema is deterministic (same source, same merge order), so
# this catches ANY change anywhere in the repo that shifts the merged
# zilla.yaml JSON Schema, not just the ones this script happened to assert on.
#
# Regenerate after an intentional schema change with:
#   docker compose exec zilla zilla inspect schema > .github/schema.expected.json
if diff -u "$SCRIPT_DIR/schema.expected.json" "$ACTUAL_FILE"; then
  echo ✅ output matches schema.expected.json
else
  echo ❌ output does not match schema.expected.json
  EXIT=1
fi

rm -f "$ACTUAL_FILE"

exit $EXIT
