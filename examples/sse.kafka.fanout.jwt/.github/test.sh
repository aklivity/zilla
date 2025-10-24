#!/bin/sh
set -x

EXIT=0

# GIVEN
PORT="7114"
KAFKA_BOOTSTRAP_SERVER="kafka.examples.dev:29092"
INPUT='{"id":1,"name":"Hello World!"}'
EXPECTED='data:{"id":1,"name":"Hello World!"}'
echo \# Testing sse.kafka.fanout
echo PORT="$PORT"
echo KAFKA_BOOTSTRAP_SERVER="$KAFKA_BOOTSTRAP_SERVER"
echo INPUT="$INPUT"
echo EXPECTED="$EXPECTED"
echo

# WHEN

# Ensure topic exists
docker compose -p zilla-sse-kafka-fanout exec -T kafka \
  kafka-topics.sh --describe --topic events --bootstrap-server $KAFKA_BOOTSTRAP_SERVER

# push messages to sse server
echo "$INPUT" |
  docker compose -p zilla-sse-kafka-fanout exec -T kafkacat \
    kafkacat -P \
    -b $KAFKA_BOOTSTRAP_SERVER \
    -t events \
    -k "1"

sleep 5
# send request to zilla
OUTPUT=$(timeout 3s curl -N --http2 -H "Accept:text/event-stream" "http://localhost:$PORT/events" | grep "^data:")

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
