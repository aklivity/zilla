#!/bin/sh
set -x

EXIT=0

# GIVEN
INPUT="Hello, Zilla!"
EXPECTED="Hello, Zilla!"
RECEIVER_LOG="output.out"

echo "# Testing amqp.reflect/"
echo "INPUT=$INPUT"
echo "EXPECTED=$EXPECTED"
echo

docker compose -p zilla-amqp-reflect exec cli-rhea cli-rhea-receiver \
  --address 'zilla' \
  --count 1 \
  --log-msgs 'body' \
  --broker zilla.examples.dev:7172 >"$RECEIVER_LOG" 2>&1 &

RECEIVER_PID=$!

sleep 2

timeout 2 docker compose -p zilla-amqp-reflect exec cli-rhea cli-rhea-sender \
  --address 'zilla' \
  --msg-content "$INPUT" \
  --count 1 \
  --broker zilla.examples.dev:7172
RESULT=$?

wait "$RECEIVER_PID"

OUTPUT=$(grep -o "$INPUT" "$RECEIVER_LOG" | head -n 1)

echo "OUTPUT=\"$OUTPUT\""
echo "EXPECTED=\"$EXPECTED\""
echo

if [ "$OUTPUT" = "$EXPECTED" ]; then
  echo "✅"
else
  echo "❌"
  EXIT=1
fi

exit $EXIT
