#!/bin/sh
set -x

EXIT=0

PORT="23456"
INPUT="Hello, Zilla!"
EXPECTED="Hello, Zilla!"

# GIVEN
echo \# Testing tls.echo/
echo PORT="$PORT"
echo INPUT="$INPUT"
echo EXPECTED="$EXPECTED"
echo

# WHEN
OUTPUT=$({ echo "$INPUT"; sleep 2; } | openssl s_client -connect localhost:$PORT -CAfile test-ca.crt -quiet -alpn echo -no_ign_eof)
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

# GIVEN
echo \# Testing tls.echo/ guarded route without a client certificate
echo PORT="$PORT"
echo INPUT="$INPUT"
echo EXPECTED=""
echo

# WHEN
OUTPUT=$({ echo "$INPUT"; sleep 2; } | openssl s_client -connect localhost:$PORT -CAfile test-ca.crt -quiet -alpn secure-echo -no_ign_eof)
echo RESULT="$?"

# THEN
echo OUTPUT="$OUTPUT"
echo EXPECTED=""
echo

if [ -z "$OUTPUT" ]; then
  echo ✅
else
  echo ❌
  EXIT=1
fi

# GIVEN
echo \# Testing tls.echo/ guarded route with a client certificate from an unnamed issuer
echo PORT="$PORT"
echo INPUT="$INPUT"
echo EXPECTED=""
echo

# WHEN
OUTPUT=$({ echo "$INPUT"; sleep 2; } | openssl s_client -connect localhost:$PORT -CAfile test-ca.crt -cert other.crt -key other.key -quiet -alpn secure-echo -no_ign_eof)
echo RESULT="$?"

# THEN
echo OUTPUT="$OUTPUT"
echo EXPECTED=""
echo

if [ -z "$OUTPUT" ]; then
  echo ✅
else
  echo ❌
  EXIT=1
fi

# GIVEN
echo \# Testing tls.echo/ guarded route with a client certificate from the named issuer
echo PORT="$PORT"
echo INPUT="$INPUT"
echo EXPECTED="$EXPECTED"
echo

# WHEN
OUTPUT=$({ echo "$INPUT"; sleep 2; } | openssl s_client -connect localhost:$PORT -CAfile test-ca.crt -cert client.crt -key client.key -quiet -alpn secure-echo -no_ign_eof)
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
