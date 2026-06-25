#!/bin/sh
set -x

. "$(CDPATH= cd -- "$(dirname -- "$0")/../../.github" && pwd)/test-lib.sh"

EXIT=0

# create schema
for i in $(seq 1 5); do
  RESPONSE=$(curl -s --header "Content-Type: application/json" --data '{
    "schema":
      "{\"fields\":[{\"name\":\"id\",\"type\":\"string\"},{\"name\":\"status\",\"type\":\"string\"}],\"name\":\"Event\",\"namespace\":\"io.aklivity.example\",\"type\":\"record\"}",
    "schemaType": "AVRO"
  }' "http://localhost:8081/subjects/items-snapshots-value/versions")

  if [ "$RESPONSE" = '{"id":1}' ]; then
    break
  fi

  sleep 2
done

# GIVEN
PORT="7114"
INPUT='{"id": "123", "status": "OK"}'
EXPECTED='{"id":"123","status":"OK"}'
echo \# Testing http.kafka.avro.json/valid
echo PORT="$PORT"
echo INPUT="$INPUT"
echo EXPECTED="$EXPECTED"
echo

# WHEN
# the producing POST goes through Zilla and can race a cold route; retry the
# send+read together. The Idempotency-Key makes re-posting collapse to a single
# record.
send_then_fetch_valid() {
  curl -k http://localhost:7114/items -H 'Idempotency-Key: 1'  -H 'Content-Type: application/json' -d "$INPUT"
  OUTPUT=$(curl -k http://localhost:$PORT/items/1)
  [ "$OUTPUT" = "$EXPECTED" ]
}
retry_until 10 3 send_then_fetch_valid
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
INPUT='{"id": 123,"status": "OK"}'
EXPECTED='404'
echo \# Testing http.kafka.avro.json/invalid
echo PORT="$PORT"
echo INPUT="$INPUT"
echo EXPECTED="$EXPECTED"
echo

# send message
curl -k http://localhost:7114/items -H 'Idempotency-Key: 2'  -H 'Content-Type: application/json' -d "$INPUT"

# WHEN
fetch_invalid() {
  OUTPUT=$(curl -w "%{http_code}" http://localhost:$PORT/items/2)
  [ $? -eq 0 ] && [ "$OUTPUT" = "$EXPECTED" ]
}
retry_until 10 3 fetch_invalid
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
