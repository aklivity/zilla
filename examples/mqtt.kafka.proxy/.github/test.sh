#!/bin/sh
set -x

EXIT=0

# GIVEN
PORT="7183"
INPUT='Hello Zilla!'
EXPECTED='Hello Zilla!'
echo \# Testing mqtt.kafka.proxy
echo PORT="$PORT"
echo INPUT="$INPUT"
echo EXPECTED="$EXPECTED"
echo

# WHEN

for i in $(seq 1 5); do
  docker compose -p zilla-mqtt-kafka-proxy exec -T mosquitto-cli \
      mosquitto_pub --url mqtt://zilla.examples.dev:"$PORT"/zilla --message "Test"

  if [ $? -eq 0 ]; then
    echo "✅ Zilla is reachable."
    break
  fi

  sleep 2
done

OUTPUT=$(
  docker compose -p zilla-mqtt-kafka-proxy exec -T mosquitto-cli \
    timeout 5s mosquitto_sub --url mqtt://zilla.examples.dev:"$PORT"/zilla &

  SUB_PID=$!

  sleep 1

  docker compose -p zilla-mqtt-kafka-proxy exec -T mosquitto-cli \
    mosquitto_pub --url mqtt://zilla.examples.dev:"$PORT"/zilla --message "$INPUT"

  wait $SUB_PID
)

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

# GIVEN shared subscribers
COUNT=20
SHARE_FILTER='$share/group/sensor/#'
WORK=$(mktemp -d)
echo \# Testing shared subscriptions
echo SHARE_FILTER="$SHARE_FILTER"
echo COUNT="$COUNT"
echo

# WHEN two subscribers share one subscription and messages are published
for SUBSCRIBER in 1 2; do
  docker compose -p zilla-mqtt-kafka-proxy exec -T mosquitto-cli \
    timeout 40s mosquitto_sub -V 5 -q 1 -i "shared-$SUBSCRIBER-$$" -d \
      -h zilla.examples.dev -p "$PORT" -t "$SHARE_FILTER" -t ready \
    > "$WORK/subscriber-$SUBSCRIBER.log" 2>&1 &
  eval "SUB_PID_$SUBSCRIBER=$!"

  # mosquitto_sub only flushes its debug log when it prints a message,
  # so publish to the plain topic until the subscription is acknowledged
  for i in $(seq 1 30); do
    docker compose -p zilla-mqtt-kafka-proxy exec -T mosquitto-cli \
      mosquitto_pub -V 5 -h zilla.examples.dev -p "$PORT" -t ready --message ready
    grep -q "received SUBACK" "$WORK/subscriber-$SUBSCRIBER.log" && break
    sleep 1
  done
done

for i in $(seq 1 "$COUNT"); do
  docker compose -p zilla-mqtt-kafka-proxy exec -T mosquitto-cli \
    mosquitto_pub -V 5 -q 1 -h zilla.examples.dev -p "$PORT" -t sensor/temperature --message "$i"
done

for i in $(seq 1 15); do
  RECEIVED=$(cat "$WORK"/subscriber-1.log "$WORK"/subscriber-2.log | grep -cE '^[0-9]+$')
  [ "$RECEIVED" -ge "$COUNT" ] && break
  sleep 1
done

docker compose -p zilla-mqtt-kafka-proxy exec -T mosquitto-cli pkill mosquitto_sub
wait "$SUB_PID_1"
wait "$SUB_PID_2"

# THEN each message is received exactly once across both subscribers
for SUBSCRIBER in 1 2; do
  grep -E '^[0-9]+$' "$WORK/subscriber-$SUBSCRIBER.log" > "$WORK/subscriber-$SUBSCRIBER.out"
done
cat "$WORK/subscriber-1.out" "$WORK/subscriber-2.out" | sort -n > "$WORK/received"
seq 1 "$COUNT" > "$WORK/expected"
echo SUBSCRIBER_1="$(wc -l < "$WORK/subscriber-1.out")"
echo SUBSCRIBER_2="$(wc -l < "$WORK/subscriber-2.out")"
echo RECEIVED="$(tr '\n' ' ' < "$WORK/received")"
echo
if cmp -s "$WORK/received" "$WORK/expected"; then
  echo ✅
else
  echo ❌
  cat "$WORK"/subscriber-1.log "$WORK"/subscriber-2.log
  EXIT=1
fi

rm -rf "$WORK"

exit $EXIT
