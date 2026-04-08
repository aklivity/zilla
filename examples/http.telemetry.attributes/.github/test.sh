#!/bin/sh
set -x

EXIT=0

# GIVEN
PORT="7114"
METRICS_PORT="7190"
INPUT="Hello, Zilla!"
EXPECTED="Hello, Zilla!"
echo \# Testing http.telemetry.attributes/
echo PORT="$PORT"
echo INPUT="$INPUT"
echo EXPECTED="$EXPECTED"
echo

# WHEN
OUTPUT=$(curl -sf -d "$INPUT" http://localhost:$PORT/items)
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

# Verify metrics endpoint responds
METRICS=$(curl -sf http://localhost:$METRICS_PORT/metrics)
RESULT=$?
echo METRICS_RESULT="$RESULT"
if [ "$RESULT" -ne 0 ]; then
  echo ❌ Metrics endpoint failed
  EXIT=1
else
  echo ✅ Metrics endpoint OK
fi

exit $EXIT
