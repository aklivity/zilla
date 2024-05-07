#!/bin/bash

# GIVEN
ZILLA_PORT="7114"
KAFKA_PORT="9092"
ITEM_ID="5cf7a1d5-3772-49ef-86e7-ba6f2c7d7d07"
GREETING="Hello, World!"
GREETING_DATE="Hello, World! $(date)"
EXPECTED="{\"greeting\":\"$GREETING_DATE\"}"

echo \# Testing http.kafka.sync
echo ZILLA_PORT=$ZILLA_PORT
echo KAFKA_PORT=$KAFKA_PORT
echo ITEM_ID=$ITEM_ID
echo GREETING=$GREETING
echo GREETING_DATE=$GREETING_DATE
echo EXPECTED=$EXPECTED
echo

# WHEN
# send request to zilla
timeout 300 curl -vs \
       -X "PUT" http://localhost:$ZILLA_PORT/items/$ITEM_ID \
       -H "Idempotency-Key: 1" \
       -H "Content-Type: application/json" \
       -d "{\"greeting\":\"$GREETING\"}" | tee .testoutput &

# fetch correlation id from kafka with kcat; retry until ready
for i in $(seq 1 20); do
  CORRELATION_ID=$(timeout 10 kcat -C -b localhost:$KAFKA_PORT -t items-requests -J -u | jq -r '.headers | index("zilla:correlation-id") as $index | .[$index + 1]')
  if [[ ! -z "$CORRELATION_ID" ]]; then
    break
  fi
done
echo CORRELATION_ID=$CORRELATION_ID
if [[ -z "$CORRELATION_ID" ]]; then
  echo ❌
  exit 1
fi

# push response to kafka with kcat
echo "{\"greeting\":\"$GREETING_DATE\"}" | \
    kcat -P \
         -b localhost:$KAFKA_PORT \
         -t items-responses \
         -k "$ITEM_ID" \
         -H ":status=200" \
         -H "zilla:correlation-id=$CORRELATION_ID"

# fetch the output of zilla request; retry until ready
for i in $(seq 1 20); do
  OUTPUT=$(cat .testoutput)
  if [[ ! -z "$OUTPUT" ]]; then
    break
  fi
  sleep 10
done
echo
echo OUTPUT=$OUTPUT


# THEN
rm .testoutput
if [[ "$OUTPUT" == "$EXPECTED" ]]; then
  echo ✅
else
  echo ❌
  exit 1
fi
