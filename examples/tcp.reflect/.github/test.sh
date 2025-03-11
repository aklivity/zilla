#!/bin/sh
set -x

EXIT=0

# GIVEN
PORT="12345"
INPUT="Hello!"
INPUT1="Hello from client 1"
INPUT2="Hello from client 2"

echo \# Testing tcp.reflect
echo PORT="$PORT"
echo INPUT1="$INPUT1"
echo INPUT2="$INPUT2"

# WHEN

echo "$INPUT" | nc -w 1 localhost $PORT

{
  (echo "$INPUT1"; sleep 2) | nc -w 1 localhost $PORT &
  PID1=$!
  (echo "$INPUT2"; sleep 2) | nc -w 1 localhost $PORT &
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
