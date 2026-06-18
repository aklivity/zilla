#!/bin/sh
set -x

EXIT=0

PORT="7114"
MESSAGE='{"greeting":"Hello, world"}'

echo "# Testing http.kafka.oneway.oauthbearer"
echo "PORT=$PORT"

# Test POST without Authorization header - expect 204 (HTTP is open, Kafka uses service account)
RESULT=$(curl -s -o /dev/null -w "%{http_code}" \
    http://localhost:$PORT/events \
    -H "Content-Type: application/json" \
    -d "$MESSAGE")
if [ "$RESULT" = "204" ]; then
  echo ✅
else
  echo ❌
  EXIT=1
fi

exit $EXIT
