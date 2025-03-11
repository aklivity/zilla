#!/bin/sh
set -x

EXIT=0

# GIVEN
PORT="7114"
INPUT="Hello, world"
EXPECTED="Hello, world"
echo \# Testing ws.echo
echo PORT="$PORT"
echo INPUT="$INPUT"
echo EXPECTED="$EXPECTED"

# WHEN
OUTPUT=$(echo "$INPUT" | docker compose -p zilla-ws-echo exec -T websocat websocat --protocol echo ws://zilla.examples.dev:7114/)
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
