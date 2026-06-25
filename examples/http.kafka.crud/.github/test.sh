#!/bin/sh
set -x

. "$(CDPATH= cd -- "$(dirname -- "$0")/../../.github" && pwd)/test-lib.sh"

EXIT=0

# GIVEN
PORT="7114"
INPUT='{"greeting":"Hello, world1"}'
EXPECTED='{"greeting":"Hello, world1"}'
echo \# Testing http.kafka.crud/
echo PORT="$PORT"
echo INPUT="$INPUT"
echo EXPECTED="$EXPECTED"
echo

# WHEN
# the POST goes through Zilla and can race a cold route; retry the create+read
# together. The Idempotency-Key makes re-posting collapse to a single record.
create_then_fetch() {
  curl -k -v -X POST http://localhost:$PORT/items -H 'Idempotency-Key: 1'  -H 'Content-Type: application/json' -d "$INPUT"
  OUTPUT=$(curl -k http://localhost:$PORT/items/1)
  [ "$OUTPUT" = "$EXPECTED" ]
}
retry_until 10 3 create_then_fetch
RESULT=$?
echo RESULT="$RESULT"

# THEN
echo OUTPUT="$OUTPUT"
echo EXPECTED="$EXPECTED"
echo
if [ "$RESULT" -eq 0 ] && [ "$OUTPUT" = "$EXPECTED" ]; then
  echo ✅
else
  echo ❌
  EXIT=1
fi

exit $EXIT
