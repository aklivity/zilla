#!/bin/sh
set -x

. "$(CDPATH= cd -- "$(dirname -- "$0")/../../.github" && pwd)/test-lib.sh"

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
post_valid() {
  OUTPUT=$(curl -w "%{http_code}" http://localhost:$PORT/requests -H "Content-Type: application/json" -d "$INPUT")
  RESULT=$?
  [ "$RESULT" -eq 0 ] && [ "$OUTPUT" = "$EXPECTED" ]
}
retry_until 5 2 post_valid
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
post_invalid() {
  OUTPUT=$(curl -w "%{http_code}" http://localhost:$PORT/requests -H "Content-Type: application/json" -d "$INPUT")
  RESULT=$?
  [ "$RESULT" -eq 0 ] && [ "$OUTPUT" = "$EXPECTED" ]
}
retry_until 5 2 post_invalid
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
