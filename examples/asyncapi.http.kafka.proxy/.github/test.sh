#!/bin/sh
set -x

EXIT=0

# GIVEN
PORT="7114"
INPUT='{"name": "Rocky","id": 1}'
EXPECTED="204"
echo \# Testing asyncapi.http.kafka.proxy/POST
echo PORT="$PORT"
echo INPUT="$INPUT"
echo EXPECTED="$EXPECTED"
echo

# WHEN
OUTPUT=$(curl -w "%{http_code}" --location "http://localhost:$PORT/pets" \
  --header 'Content-Type: application/json' \
  --data "$INPUT")
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
INPUT=''
EXPECTED='{"name": "Rocky","id": 1}'
echo \# Testing asyncapi.http.kafka.proxy/GET
echo PORT="$PORT"
echo INPUT="$INPUT"
echo EXPECTED="$EXPECTED"
echo

# WHEN
OUTPUT=$(curl "http://localhost:$PORT/pets" \
  --header 'Content-Type: application/json')
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
