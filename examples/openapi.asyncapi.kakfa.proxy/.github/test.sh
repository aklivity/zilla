#!/bin/sh
set -x

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

curl "http://localhost:$PORT/pets" --header 'Content-Type: application/json' --header 'Idempotency-Key: 1' --data "$INPUT"

# WHEN
OUTPUT=$(curl "http://localhost:$PORT/pets")
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
