#!/bin/sh
set -x

EXIT=0

# GIVEN
PORT="7114"
INPUT='{"greeting":"Hello, world"}'
EXPECTED="204"
echo \# Testing http.kafka.oneway/
echo PORT="$PORT"
echo INPUT="$INPUT"
echo EXPECTED="$EXPECTED"
echo

# WHEN
OUTPUT=$(curl -w "%{http_code}" http://localhost:$PORT/events -H "Content-Type: application/json" -d "$INPUT")
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
