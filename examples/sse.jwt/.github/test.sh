#!/bin/sh
set -x

EXIT=0
PORT="7143"
STREAM_URL="https://localhost:$PORT/events"
NC_PORT="7001"
MESSAGE="Hello, world"

echo \# Testing sse.jwt
echo "PORT=$PORT"
echo "STREAM_URL=$STREAM_URL"
echo "NC_PORT=$NC_PORT"
echo "MESSAGE=$MESSAGE"

JWT_TOKEN=$(docker compose run --rm \
    jwt-cli encode \
    --alg "RS256" \
    --kid "example" \
    --iss "https://auth.example.com" \
    --aud "https://api.example.com" \
    --exp=+1d \
    --no-iat \
    --payload "scope=proxy:stream" \
    --secret @/private.pem | tr -d '\r\n')

OUTPUT=$(curl --cacert test-ca.crt --no-buffer -N --max-time 5 "$STREAM_URL?access_token=${JWT_TOKEN}" &

  sleep 1

  echo '{ "data": "Hello, world" }' | timeout 5s nc localhost $NC_PORT
)

if echo "$OUTPUT" | grep -q "$MESSAGE"; then
  echo ✅
else
  echo ❌
  EXIT=1
fi

exit $EXIT
