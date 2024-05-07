#!/bin/bash

# GIVEN
PORT="23456"
INPUT="Hello, Zilla!"
EXPECTED="Hello, Zilla!"
echo \# Testing tls.echo
echo PORT=$PORT
echo INPUT=$INPUT
echo EXPECTED=$EXPECTED
echo

# WHEN
OUTPUT=$(echo $INPUT | timeout 2 openssl s_client -connect localhost:$PORT -CAfile test-ca.crt -quiet -alpn echo)
RESULT=$?
echo OUTPUT=$OUTPUT
echo RESULT=$RESULT

# THEN
if [[ $RESULT -eq 124 && "$OUTPUT" == "$EXPECTED" ]]; then
  echo ✅
else
  echo ❌
  exit 1
fi
