#!/bin/sh
set -x

EXIT=0

# GIVEN
PORT="7183"
INPUT="Hello, Zilla!"
EXPECTED=""
echo \# Testing asyncapi.sse.kafka.proxy/
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

# TODO remove once fixed
echo '❌ Tested on main. and does not work with described instructions'
echo 'Refer: https://github.com/aklivity/zilla/issues/1416'
EXIT=1

exit $EXIT
