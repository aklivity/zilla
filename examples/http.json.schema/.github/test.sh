#!/bin/sh
set -x

EXIT=0

# GIVEN
PORT="7114"
EXPECTED='{
    "id": 42,
    "status": "Active"
}'
echo \# Testing http.json.schema/valid.json
echo PORT="$PORT"
echo EXPECTED="$EXPECTED"
echo

# WHEN
OUTPUT=$(curl http://localhost:$PORT/valid.json)
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
echo \# Testing http.json.schema/invalid.json
echo PORT="$PORT"

echo

# WHEN
OUTPUT=$(curl http://localhost:$PORT/invalid.json)
RESULT=$?
echo RESULT="$RESULT"

# THEN
echo
if [ "$RESULT" -eq 18 ]; then
  echo ✅
else
  echo ❌
  EXIT=1
fi

exit $EXIT
