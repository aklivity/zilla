#!/bin/sh
set -x

EXIT=0

# GIVEN
PORT="23456"
INPUT="Hello!"
INPUT1="Hello, Zilla!"
INPUT2="Bye, Zilla!"
EXPECTED="Hello, Zilla!
Bye, Zilla!"

echo \# Testing tls.reflect/
echo PORT="$PORT"
echo INPUT="$INPUT"
echo EXPECTED="$EXPECTED"
echo

# WHEN

for i in $(seq 1 5); do
  echo "$INPUT" | openssl s_client -connect localhost:$PORT -CAfile test-ca.crt -quiet -alpn echo -no_ign_eof

  if [ $? -eq 0 ]; then
    echo "✅ Zilla is reachable."
    break
  fi

  sleep 2
done

OUTPUT=$(
echo "$INPUT1"; sleep 2 | openssl s_client -connect localhost:$PORT -CAfile test-ca.crt -quiet -alpn echo -no_ign_eof
echo "$INPUT2"; sleep 2 | openssl s_client -connect localhost:$PORT -CAfile test-ca.crt -quiet -alpn echo -no_ign_eof)
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
