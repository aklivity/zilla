#!/bin/sh
set -x

EXIT=0

# GIVEN
PORT="7114"
INPUT='{ "message": "hello world", "count": 10 }'
EXPECTED="204"
echo \# Testing http.kafka.proto.json/valid
echo PORT="$PORT"
echo INPUT="$INPUT"
echo EXPECTED="$EXPECTED"
echo

# WHEN
OUTPUT=$(curl -w "%{http_code}" http://localhost:$PORT/requests -H "Content-Type: application/json" -d "$INPUT")
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

# GIVEN
PORT="7114"
INPUT='{ "message": "hello world", "count": 10, "invalid": "field" }'
EXPECTED="400"
echo \# Testing http.kafka.proto.json/invalid
echo PORT="$PORT"
echo INPUT="$INPUT"
echo EXPECTED="$EXPECTED"
echo

# WHEN
OUTPUT=$(curl -w "%{http_code}" http://localhost:$PORT/requests -H "Content-Type: application/json" -d "$INPUT")
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
