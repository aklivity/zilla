#!/bin/sh
set -x

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
# send request to zilla
timeout 3s curl -N --http2 -H "Accept:text/event-stream" "http://localhost:$PORT/events/1" | tee .testoutput &

# push events to sse server
echo "$INPUT_GOOD" | nc -w 1 localhost 7001
echo "$INPUT_BAD" | nc -w 1 localhost 7001

# fetch the output of zilla request; try 5 times
for i in $(seq 0 2); do
  sleep 5
  OUTPUT=$(cat .testoutput | grep "^data:")
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
