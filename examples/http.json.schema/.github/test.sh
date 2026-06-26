#!/bin/sh
set -x

. "$(CDPATH= cd -- "$(dirname -- "$0")/../../.github" && pwd)/test-lib.sh"

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
get_valid() {
  OUTPUT=$(curl http://localhost:$PORT/valid.json)
  [ $? -eq 0 ] && [ "$OUTPUT" = "$EXPECTED" ]
}
retry_until 5 2 get_valid
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
get_invalid() {
  OUTPUT=$(curl http://localhost:$PORT/invalid.json)
  RESULT=$?
  [ "$RESULT" -eq 18 ]
}
retry_until 5 2 get_invalid
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
