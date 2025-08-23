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

# ---------------------------------------
# Test Health Check
# ---------------------------------------
# Note: service = "" means check overall server health.
HEALTH_INPUT='{"service":"grpc.health.v1.Health"}'
HEALTH_EXPECTED='{
  "status": "SERVING"
}'
echo \# Testing grpc.health.v1.Health.Check
echo PORT="$PORT"
echo INPUT="$HEALTH_INPUT"
echo EXPECTED="$HEALTH_EXPECTED"
echo

OUTPUT=$(docker compose run --rm grpcurl -plaintext -proto health.proto -d "$HEALTH_INPUT" zilla.examples.dev:$PORT grpc.health.v1.Health/Check)
RESULT=$?
echo RESULT="$RESULT"
echo OUTPUT="$OUTPUT"
echo EXPECTED="$HEALTH_EXPECTED"
echo

if [ "$RESULT" -eq 0 ] && [ "$OUTPUT" = "$HEALTH_EXPECTED" ]; then
  echo ✅
else
  echo ❌
  EXIT=1
fi

# ------------------------------
# Test Health Check - UNKNOWN
# ------------------------------
HEALTH_INPUT='{"service":"example.EchoService"}'
HEALTH_EXPECTED='{
  "status": "UNKNOWN"
}'
echo \# Testing grpc.health.v1.Health.Check UNKNOWN
# Before running this, make sure your server sets the service to UNKNOWN
OUTPUT=$(docker compose run --rm grpcurl -plaintext -proto health.proto -d "$HEALTH_INPUT" zilla.examples.dev:$PORT grpc.health.v1.Health/Check)
RESULT=$?
if [ "$RESULT" -eq 0 ] && [ "$OUTPUT" = "$HEALTH_EXPECTED" ]; then
  echo ✅
else
  echo ❌
  EXIT=1
fi

exit $EXIT
