#!/bin/sh
set -x

. "$(CDPATH= cd -- "$(dirname -- "$0")/../../.github" && pwd)/test-lib.sh"

EXIT=0

# GIVEN
PORT="7114"
INPUT='{"id": 1, "name": "Spike"}'
EXPECTED='[{"id": 1, "name": "Spike"}]'
echo \# Testing openapi.asyncapi.kakfa.proxy/
echo PORT="$PORT"
echo INPUT="$INPUT"
echo EXPECTED="$EXPECTED"
echo

# WHEN
# the GET races the POST->produce->fetch round-trip, so a single read can return
# [] before the record is fetchable; retry until the produced pet is listed. The
# POST carries an Idempotency-Key and the topic is compacted by id, so re-posting
# across attempts collapses to a single entry and is safe to repeat.
post_then_get() {
  curl "http://localhost:$PORT/pets" --header 'Content-Type: application/json' --header 'Idempotency-Key: 1' --data "$INPUT"
  OUTPUT=$(curl "http://localhost:$PORT/pets")
  [ "$OUTPUT" = "$EXPECTED" ]
}
retry_until 10 3 post_then_get
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
