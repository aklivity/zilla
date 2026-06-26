#!/bin/sh
set -x

. "$(CDPATH= cd -- "$(dirname -- "$0")/../../.github" && pwd)/test-lib.sh"

EXIT=0

# GIVEN
PORT="7114"
EXPECTED='[{"id":1,"name":"string","tag":"string"}]'
echo \# Testing openapi.proxy/
echo PORT="$PORT"
echo EXPECTED="$EXPECTED"
echo

# WHEN
get_pets() {
  OUTPUT=$(curl --silent --location "http://localhost:$PORT/pets" --header 'Accept: application/json')
  [ $? -eq 0 ] && [ "$OUTPUT" = "$EXPECTED" ]
}
retry_until 5 2 get_pets
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
