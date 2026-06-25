#!/bin/sh
set -x

. "$(CDPATH= cd -- "$(dirname -- "$0")/../../.github" && pwd)/test-lib.sh"

EXIT=0

# GIVEN
PORT="7114"
EXPECTED="[]"
echo \# Testing http.kafka.cache/
echo PORT="$PORT"
echo INPUT="$INPUT"
echo EXPECTED="$EXPECTED"
echo

# WHEN
fetch_empty() {
  OUTPUT=$(curl http://localhost:$PORT/items)
  [ $? -eq 0 ] && [ "$OUTPUT" = "$EXPECTED" ]
}
retry_until 10 3 fetch_empty
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
INPUT='{"message":"Hello World"}'
EXPECTED='[{"message":"Hello World"}]'
echo \# Testing http.kafka.cache/
echo PORT="$PORT"
echo INPUT="$INPUT"
echo EXPECTED="$EXPECTED"
echo

echo "$INPUT" | docker compose -p zilla-http-kafka-cache exec -T kafkacat \
  kafkacat -P \
    -b kafka.examples.dev:29092 \
    -t items-snapshots \
    -k "5cf7a1d5-3772-49ef-86e7-ba6f2c7d7d07" \
    -H "content-type=application/json"

# WHEN
fetch_item() {
  OUTPUT=$(curl http://localhost:$PORT/items)
  [ $? -eq 0 ] && [ "$OUTPUT" = "$EXPECTED" ]
}
retry_until 10 3 fetch_item
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
