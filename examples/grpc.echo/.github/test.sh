#!/bin/sh
set -x

EXIT=0

# GIVEN
PORT="7151"
INPUT='{"message":"Hello World"}'
EXPECTED='{
  "message": "Hello World"
}'
echo \# Testing grpc.echo/example.EchoService.EchoUnary
echo PORT="$PORT"
echo INPUT="$INPUT"
echo EXPECTED="$EXPECTED"
echo

# WHEN
OUTPUT=$(docker compose run --rm grpcurl -plaintext -proto echo.proto  -d "$INPUT" zilla.examples.dev:$PORT example.EchoService.EchoUnary)
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
PORT="7151"
INPUT='{"message":"Hello World"}'
EXPECTED='{
  "message": "Hello World"
}'
echo \# Testing grpc.echo/example.EchoService.EchoBidiStream
echo PORT="$PORT"
echo INPUT="$INPUT"
echo EXPECTED="$EXPECTED"
echo

# WHEN
OUTPUT=$(docker compose run --rm grpcurl -plaintext -proto echo.proto  -d "$INPUT" zilla.examples.dev:$PORT example.EchoService.EchoBidiStream)
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


# Test EchoService health (should be SERVING)
INPUT='{"service": "example.EchoService"}'
EXPECTED='{
  "status": "SERVING"
}'
echo "# Testing EchoService health"
OUTPUT=$(grpcurl -plaintext -d "$INPUT" localhost:$PORT grpc.health.v1.Health/Check)
if [ "$OUTPUT" = "$EXPECTED" ]; then
  echo ✅ "EchoService is SERVING"
else
  echo ❌ "Unexpected EchoService health status: $OUTPUT"
  EXIT=1
fi

exit $EXIT

