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

for i in $(seq 1 5); do
  echo "$INPUT1" | timeout 3 docker compose -p zilla-ws-reflect exec -T websocat websocat --protocol echo ws://zilla.examples.dev:7114/

  if [ $? -eq 0 ]; then
    echo "✅ Zilla is reachable."
    break
  fi

  sleep 2
done

{
  (echo "$INPUT1"; sleep 2) | timeout 3 docker compose -p zilla-ws-reflect exec -T websocat websocat --protocol echo ws://zilla.examples.dev:$PORT/ &
  PID1=$!
  (echo "$INPUT2"; sleep 2) | timeout 3 docker compose -p zilla-ws-reflect exec -T websocat websocat --protocol echo ws://zilla.examples.dev:$PORT/ &
  PID2=$!

  wait $PID1 $PID2
} > output.out 2>&1

RESULT1=$?
RESULT2=$?
OUTPUT=$(cat output.out)

# THEN
COUNT1=$(echo "$OUTPUT" | grep -Fx "$INPUT1" | wc -l)
COUNT2=$(echo "$OUTPUT" | grep -Fx "$INPUT2" | wc -l)

if [ "$RESULT1" -eq 0 ] && [ "$RESULT2" -eq 0 ] && [ "$COUNT1" -eq 2 ] && [ "$COUNT2" -eq 2 ]; then
  echo ✅
else
  echo ❌
  EXIT=1
fi

exit $EXIT
