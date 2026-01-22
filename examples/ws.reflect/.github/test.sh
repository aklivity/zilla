#!/bin/sh
set -x

EXIT=0

# GIVEN
PORT="7114"
INPUT1="Hello from client 1"
INPUT2="Hello from client 2"

echo \# Testing ws.reflect
echo PORT="$PORT"
echo INPUT1="$INPUT1"
echo INPUT2="$INPUT2"

# WHEN

{
  (echo "$INPUT1"; sleep 4) | timeout 5 docker compose -p zilla-ws-reflect exec -T websocat websocat --protocol echo ws://zilla.examples.dev:$PORT/ &
  PID1=$!
  (echo "$INPUT2"; sleep 4) | timeout 5 docker compose -p zilla-ws-reflect exec -T websocat websocat --protocol echo ws://zilla.examples.dev:$PORT/ &
  PID2=$!

  wait $PID1 $PID2
} > output.out 2>&1

RESULT1=$?
RESULT2=$?
OUTPUT=$(cat output.out)

# THEN
COUNT1=$(echo "$OUTPUT" | grep -Fx "$INPUT1" | wc -l)
COUNT2=$(echo "$OUTPUT" | grep -Fx "$INPUT2" | wc -l)

rm -f output.out

if [ "$RESULT1" -eq 0 ] && [ "$RESULT2" -eq 0 ] && [ "$COUNT1" -eq 2 ] && [ "$COUNT2" -eq 2 ]; then
  echo ✅
else
  echo ❌
  EXIT=1
fi

exit $EXIT
