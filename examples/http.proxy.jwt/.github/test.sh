#!/bin/sh
set -x

EXIT=0
PORT="7114"
MESSAGE="Hello, world"

echo "# Testing http.proxy.jwt"
echo "PORT=$PORT"

# Generate JWT token without echo:stream scope
JWT_TOKEN_NO_SCOPE=$(docker compose run --rm \
    jwt-cli encode \
    --alg "RS256" \
    --kid "example" \
    --iss "https://auth.example.com" \
    --aud "https://api.example.com" \
    --exp=+1d \
    --no-iat \
    --secret @/private.pem | tr -d '\r\n')

UNAUTHORIZED_RESPONSE=$(curl -w "%{http_code}" http://localhost:$PORT/ \
    -H "Authorization: Bearer $JWT_TOKEN_NO_SCOPE" \
    -H "Content-Type: text/plain" \
    -d "$MESSAGE")

if [ "$UNAUTHORIZED_RESPONSE" = "404" ]; then
  echo ✅
else
  echo ❌
  EXIT=1
fi

# Generate JWT token with echo:stream scope
JWT_TOKEN_WITH_SCOPE=$(docker compose run --rm \
    jwt-cli encode \
    --alg "RS256" \
    --kid "example" \
    --iss "https://auth.example.com" \
    --aud "https://api.example.com" \
    --exp=+1d \
    --no-iat \
    --payload "scope=echo:stream" \
    --secret @/private.pem | tr -d '\r\n')

AUTHORIZED_RESPONSE=$(curl "http://localhost:$PORT/" \
    -H "Authorization: Bearer $JWT_TOKEN_WITH_SCOPE" \
    -H "Content-Type: text/plain" \
    -d "$MESSAGE")

if [ "$AUTHORIZED_RESPONSE" = "$MESSAGE" ]; then
  echo ✅
else
  echo ❌
  EXIT=1
fi

exit $EXIT
