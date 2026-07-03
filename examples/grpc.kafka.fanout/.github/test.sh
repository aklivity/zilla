#!/bin/sh
set -x

. "$(CDPATH= cd -- "$(dirname -- "$0")/../../.github" && pwd)/test-lib.sh"

EXIT=0

# GIVEN
PORT="7151"

EXPECTED='{
  "message": "test"
}'
echo \# Testing grpc.kafka.fanout/example.FanoutService.FanoutServerStream
echo PORT="$PORT"
echo INPUT="$INPUT"
echo EXPECTED="$EXPECTED"
echo

# WHEN

# produce the record once; the fanout stream replays it from the topic on every
# connection, so re-reading below is safe and must not re-produce.
# the topic can exist (kafka-init's create call succeeded) before its metadata
# has propagated to the point where a produce is accepted, so retry on failure
# rather than leaving the topic empty and failing every read below
produce_fanout() {
  docker compose -p zilla-grpc-kafka-fanout exec kafkacat kafkacat -P -b kafka.examples.dev:29092 -t messages -k -e /tmp/binary.data
}
retry_until 10 3 produce_fanout

# the grpc-kafka route warms up after the TCP healthcheck passes; retry the
# streamed read until it returns the produced record or attempts are exhausted
read_fanout() {
  OUTPUT=$(echo "EXIT" | timeout 3s docker compose run --rm grpcurl -plaintext -proto fanout.proto  -d '' zilla.examples.dev:$PORT example.FanoutService.FanoutServerStream)
  [ "$OUTPUT" = "$EXPECTED" ]
}
retry_until 10 3 read_fanout
RESULT=$?
echo RESULT="$RESULT"
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
