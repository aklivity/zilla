#!/bin/sh
set -x

EXIT=0

# GIVEN
PORT="7183"
MESSAGE="Hello, world"
echo \# Testing mqtt.proxy.jwt
echo PORT="$PORT"
echo MESSAGE="$MESSAGE"
echo

# Generate JWT token without mqtt:stream scope
JWT_TOKEN_NO_SCOPE=$(docker compose run --rm \
    jwt-cli encode \
    --alg "RS256" \
    --kid "example" \
    --iss "https://auth.example.com" \
    --aud "https://api.example.com" \
    --exp=+1d \
    --no-iat \
    --secret @/private.pem | tr -d '\r\n')

# WHEN
OUTPUT=$(
  docker compose -p zilla-mqtt-proxy-jwt exec -T mosquitto-cli \
    timeout 5s mosquitto_sub --url mqtt://zilla.examples.dev:"$PORT"/zilla -u "Bearer $JWT_TOKEN_NO_SCOPE" || echo "Unauthorized"
)

RESULT=$?
echo RESULT="$RESULT"
echo OUTPUT="$OUTPUT"
echo EXPECTED="Unauthorized"
echo

# THEN
if [ "$RESULT" -ne 0 ] || [ "$OUTPUT" = "Unauthorized" ]; then
  echo ✅
else
  echo ❌
  EXIT=1
fi

# Generate JWT token with mqtt:stream scope
JWT_TOKEN_WITH_SCOPE=$(docker compose run --rm \
    jwt-cli encode \
    --alg "RS256" \
    --kid "example" \
    --iss "https://auth.example.com" \
    --aud "https://api.example.com" \
    --exp=+1d \
    --no-iat \
    --payload "scope=mqtt:stream" \
    --secret @/private.pem | tr -d '\r\n')

# WHEN
OUTPUT=$(
  docker compose -p zilla-mqtt-proxy-jwt exec -T mosquitto-cli \
    timeout 5s mosquitto_sub --url mqtt://zilla.examples.dev:"$PORT"/zilla -u "Bearer $JWT_TOKEN_WITH_SCOPE" &

  SUB_PID=$!

  sleep 1

  # Publish a message
  docker compose -p zilla-mqtt-proxy-jwt exec -T mosquitto-cli \
    mosquitto_pub --url mqtt://zilla.examples.dev:"$PORT"/zilla --message "$MESSAGE" -u "Bearer $JWT_TOKEN_WITH_SCOPE"

  wait $SUB_PID
)

RESULT=$?
echo RESULT="$RESULT"
echo OUTPUT="$OUTPUT"
echo EXPECTED="$MESSAGE"
echo

# THEN
if [ "$RESULT" -eq 0 ] && echo "$OUTPUT" | grep -q "$MESSAGE"; then
  echo ✅
else
  echo ❌
  EXIT=1
fi

exit $EXIT
