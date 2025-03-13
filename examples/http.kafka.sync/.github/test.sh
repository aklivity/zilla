#!/bin/sh
set -x

EXIT=0

# GIVEN
PORT="7114"
KAFKA_BOOTSTRAP_SERVER="kafka.examples.dev:29092"
ITEM_ID="$(date +%s)"
GREETING="Hello, World! $ITEM_ID"
GREETING_DATE="Hello, World! $(date)"
EXPECTED="{\"greeting\":\"$GREETING_DATE\"}"

echo \# Testing http.kafka.sync/
echo PORT="$PORT"
echo KAFKA_BOOTSTRAP_SERVER="$KAFKA_BOOTSTRAP_SERVER"
echo ITEM_ID="$ITEM_ID"
echo GREETING="$GREETING"
echo GREETING_DATE="$GREETING_DATE"
echo

# WHEN
# send request to zilla
timeout 60s curl \
  -X "PUT" http://localhost:$PORT/items/$ITEM_ID \
  -H "Idempotency-Key: $ITEM_ID" \
  -H "Content-Type: application/json" \
  -d "{\"greeting\":\"$GREETING\"}" | tee .testoutput &

# fetch correlation id from kafka with kafkacat; try 5 times
for i in $(seq 0 5); do
  sleep $i
  CORRELATION_ID=$(docker compose -p zilla-http-kafka-sync exec kafkacat kafkacat -C -c 1 -o-1 -b $KAFKA_BOOTSTRAP_SERVER -t items-requests -J -u | jq -r '.headers | index("zilla:correlation-id") as $index | .[$index + 1]')
  if [ -n "$CORRELATION_ID" ]; then
    break
  fi
done
echo CORRELATION_ID="$CORRELATION_ID"
if [ -z "$CORRELATION_ID" ]; then
  echo ❌
  EXIT=1
fi

# push response to kafka with kafkacat
echo "{\"greeting\":\"$GREETING_DATE\"}" |
  docker compose -p zilla-http-kafka-sync exec -T kafkacat \
    kafkacat -P \
    -b $KAFKA_BOOTSTRAP_SERVER \
    -t items-responses \
    -k "$ITEM_ID" \
    -H ":status=200" \
    -H "zilla:correlation-id=$CORRELATION_ID"

# fetch the output of zilla request; try 5 times
for i in $(seq 0 5); do
  sleep $i
  OUTPUT=$(cat .testoutput)
  if [ -n "$OUTPUT" ]; then
    break
  fi
done
rm .testoutput

# THEN
echo OUTPUT="$OUTPUT"
echo EXPECTED="$EXPECTED"
echo
if [ "$OUTPUT" = "$EXPECTED" ]; then
  echo ✅
else
  echo ❌
  EXIT=1
fi

exit $EXIT
