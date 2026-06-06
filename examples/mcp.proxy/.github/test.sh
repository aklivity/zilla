#!/bin/sh
# Drives the build-workflow verification for examples/mcp.proxy.
#
# Two assertions, both at the Zilla layer:
#   1. a url-elicitation-capable client initializes and negotiates 2025-11-25
#   2. a real MCP SDK client drives a url-mode elicitation round-trip end-to-end
#      through the gateway (elicitation/create mode:url + completion notification)
#
# Streamable HTTP responses arrive as Server-Sent Events; checks grep the
# streamed body / client output rather than asserting exact-string equality.
set -x

EXIT=0
PORT="7114"
INITIALIZE='{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-11-25","capabilities":{"elicitation":{"url":{}}},"clientInfo":{"name":"zilla-mcp-proxy-test","version":"0.0.1"}}}'

echo "# Testing mcp.proxy"
echo "PORT=$PORT"

# WHEN: a url-elicitation-capable client initializes against the gateway
# THEN: the gateway negotiates protocol version 2025-11-25 in the response
INIT_BODY=$(curl -sS -N --max-time 10 \
    -X POST "http://localhost:$PORT/mcp" \
    -H "Content-Type: application/json" \
    -H "Accept: application/json, text/event-stream" \
    -d "$INITIALIZE")
echo INIT_BODY="$INIT_BODY"
if echo "$INIT_BODY" | grep -q '"protocolVersion":"2025-11-25"'; then
  echo ✅ initialize negotiated 2025-11-25
else
  echo ❌ initialize did not negotiate 2025-11-25
  EXIT=1
fi

# WHEN: a real MCP SDK client (method-first envelopes, elicitation.url capability)
#       calls the urlelicit toolkit's authorize tool through the gateway
# THEN: Zilla relays the mode:url elicitation/create request and the subsequent
#       notifications/elicitation/complete back to the client
ELICIT_OUT=$(docker compose run --rm --no-deps \
    -e MCP_URL="http://zilla:$PORT/mcp" \
    --entrypoint node urlelicit /app/client.mjs 2>&1)
echo "$ELICIT_OUT"
if echo "$ELICIT_OUT" | grep -q 'OK url-mode elicitation relayed end-to-end'; then
  echo ✅ url-mode elicitation relayed end-to-end
else
  echo ❌ url-mode elicitation not relayed end-to-end
  EXIT=1
fi

exit $EXIT
