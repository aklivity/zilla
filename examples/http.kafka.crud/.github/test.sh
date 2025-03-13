#!/bin/sh
set -x

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

curl -k -v -X POST http://localhost:$PORT/items -H 'Idempotency-Key: 1'  -H 'Content-Type: application/json' -d "$INPUT"

# WHEN
OUTPUT=$(curl -k http://localhost:$PORT/items/1)
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
