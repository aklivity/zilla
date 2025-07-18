#!/bin/sh
set -x

EXIT=0

# GIVEN
PORT="7183"
INPUT='{"id":"1","status":"on"}'
EXPECTED='{"id":"1","status":"on"}'
echo \# Testing asyncapi.mqtt.proxy
echo PORT="$PORT"
echo INPUT="$INPUT"
echo EXPECTED="$EXPECTED"
echo

# WHEN

for i in $(seq 1 5); do
  docker compose -p zilla-asyncapi-mqtt-proxy exec -T mosquitto-cli \
      mosquitto_pub --url mqtt://zilla.examples.dev:"$PORT"/zilla --message "Test"

  if [ $? -eq 0 ]; then
    echo "✅ Zilla is reachable."
    break
  fi

  sleep 5
done

OUTPUT=$(
  docker compose -p zilla-asyncapi-mqtt-proxy exec -T mosquitto-cli \
    timeout 5s mosquitto_sub --url mqtt://zilla.examples.dev:"$PORT"/smartylighting/streetlights/1/0/event/+/lighting/measured &

  SUB_PID=$!

  sleep 1

  docker compose -p zilla-asyncapi-mqtt-proxy exec -T mosquitto-cli \
    mosquitto_pub --url mqtt://zilla.examples.dev:"$PORT"/smartylighting/streetlights/1/0/event/1/lighting/measured --message "$INPUT"

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

exit $EXIT
