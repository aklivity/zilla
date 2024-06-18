#!/bin/bash

# GIVEN
PORT="12345"
INPUT="Hello, Zilla!"
EXPECTED="Hello, Zilla!"
echo \# Testing tcp.echo
echo PORT=$PORT
echo INPUT=$INPUT
echo EXPECTED=$EXPECTED
echo

# WHEN
OUTPUT=$(echo $INPUT | nc -w 1 localhost $PORT)
RESULT=$?
echo OUTPUT=$OUTPUT
echo RESULT=$RESULT

# THEN
if [[ $RESULT -eq 0 && "$OUTPUT" == "$EXPECTED" ]]; then
  echo ✅
else
  echo ❌
  exit 1
fi
