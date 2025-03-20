#!/bin/sh
set -x

EXIT=0

# GIVEN
PORT="12345"
INPUT="Hello, Zilla!"
EXPECTED="Hello, Zilla!"
echo \# Testing tcp.echo/
echo PORT="$PORT"
echo INPUT="$INPUT"
echo EXPECTED="$EXPECTED"
echo

# WHEN
OUTPUT=$(echo "$INPUT" | nc -w 1 localhost $PORT)
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
