#!/bin/sh
set -x

. "$(CDPATH= cd -- "$(dirname -- "$0")/../../.github" && pwd)/test-lib.sh"

EXIT=0

# GIVEN
PORT="7114"
INPUT_GOOD='{ "name": "event name", "data": { "id": 1, "name": "Hello World!" } }'
INPUT_BAD='{ "name": "event name", "data": { "id": -1, "name": "Hello bad World!" } }'
EXPECTED='data:{"id":1,"name":"Hello World!"}'
echo \# Testing asyncapi.sse.proxy/
echo PORT="$PORT"
echo INPUT_GOOD="$INPUT_GOOD"
echo INPUT_BAD="$INPUT_BAD"
echo EXPECTED="$EXPECTED"
echo

# WHEN
# the sse-server test double broadcasts live with no buffering, so a
# subscriber must already be connected through Zilla when the event is
# published or it is lost for good; wait for the subscription to establish
# before publishing, and retry the whole cycle in case it is still slow.
subscribe_and_publish() {
  rm -f .testoutput
  timeout 3s curl -N --http2 -H "Accept:text/event-stream" "http://localhost:$PORT/events/1" | tee .testoutput &
  CURL_PID=$!

  sleep 1

  echo "$INPUT_GOOD" | nc -w 1 localhost 7001
  echo "$INPUT_BAD" | nc -w 1 localhost 7001

  wait "$CURL_PID"
  OUTPUT=$(cat .testoutput | grep "^data:")
  [ -n "$OUTPUT" ]
}
retry_until 5 2 subscribe_and_publish
RESULT=$?
echo RESULT="$RESULT"
rm -f .testoutput

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
