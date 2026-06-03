#!/bin/sh
# Drives the build-workflow verification for examples/mcp.proxy.
#
# Assertion is limited to the Zilla layer (MCP initialize handshake) so
# the test is independent of upstream MCP server availability. End-to-end
# tool aggregation and url-mode elicitation are documented as manual checks
# in README.md.
#
# Streamable HTTP responses arrive as Server-Sent Events; the check uses
# grep against the streamed body rather than asserting exact-string equality.
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

exit $EXIT
