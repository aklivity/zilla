#!/bin/sh
# Runs inside the compose stack as the `verify` service; see .github/test.sh.
set -x

FAIL_FAST=${FAIL_FAST:-1}

. /test-lib.sh

EXIT=0
MCP_ENDPOINT="http://zilla:7114/mcp"
URLELICIT_ENDPOINT="http://urlelicit:3003"
CLIENTS=/work/clients
RUN_TIMEOUT_S=60

INITIALIZE='{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-11-25","capabilities":{"elicitation":{"url":{}}},"clientInfo":{"name":"zilla-mcp-proxy-jwt-test","version":"0.0.1"}}}'

echo "# Testing mcp.proxy.jwt"

# Runs in a subshell so exported variables never leak into the next call, with
# xtrace off because callers capture 2>&1 and grep the client's output
mcp()
{
  (
    { set +x; } 2>/dev/null
    export MCP_URL="$MCP_ENDPOINT"
    while [ "$1" = "-e" ]
    do
      export "$2"
      shift 2
    done
    _script=$1
    shift
    exec timeout "$RUN_TIMEOUT_S" node "$CLIENTS/$_script.mjs" "$@"
  ) 2>&1
}

mint()
{
  node "$CLIENTS/mint.mjs" "$1"
}

list_tools()
{
  mcp -e JWT_TOKEN="$1" list
}

call_tool()
{
  mcp -e JWT_TOKEN="$1" -e CALL_TOOL="$2" -e CALL_ARGS="$3" -e CALL_TIMEOUT_MS="$4" list
}

JWT_NONE=""
JWT_NO_SCOPE=$(mint "profile")
JWT_SCOPED=$(mint "urlelicit:authorize")

# WHEN: a client advertising elicitation.url initializes against the gateway
# THEN: the gateway negotiates protocol version 2025-11-25
initialize_mcp()
{
  INIT_BODY=$(curl -sS -N --max-time 10 \
      -X POST "$MCP_ENDPOINT" \
      -H "Content-Type: application/json" \
      -H "Accept: application/json, text/event-stream" \
      -d "$INITIALIZE")
  echo "$INIT_BODY" | grep -q '"protocolVersion":"2025-11-25"'
}
retry_until 10 3 initialize_mcp
echo INIT_BODY="$INIT_BODY"
if echo "$INIT_BODY" | grep -q '"protocolVersion":"2025-11-25"'; then
  echo "✅ initialize negotiated 2025-11-25"
else
  fail "initialize did not negotiate 2025-11-25"
fi

# WHEN: a caller presents no JWT
# THEN: the ungated everything toolkit is listed, the guarded urlelicit toolkit is not
TOOLS_NONE=$(list_tools "$JWT_NONE")
echo "TOOLS_NONE=$TOOLS_NONE"
if echo "$TOOLS_NONE" | grep -q '^everything__echo$' &&
    ! echo "$TOOLS_NONE" | grep -q '^urlelicit__'; then
  echo "✅ no token: only the ungated everything toolkit is listed"
else
  fail "no token: tools/list did not filter to the everything toolkit"
fi

# WHEN: a caller presents a valid JWT whose scope lacks urlelicit:authorize
# THEN: it sees the same toolkits as an anonymous caller -- authentication
#       alone does not grant access to a guarded toolkit
TOOLS_NO_SCOPE=$(list_tools "$JWT_NO_SCOPE")
echo "TOOLS_NO_SCOPE=$TOOLS_NO_SCOPE"
if echo "$TOOLS_NO_SCOPE" | grep -q '^everything__echo$' &&
    ! echo "$TOOLS_NO_SCOPE" | grep -q '^urlelicit__'; then
  echo "✅ valid token without urlelicit:authorize: urlelicit is not listed"
else
  fail "a valid token without urlelicit:authorize still saw the urlelicit toolkit"
fi

# WHEN: a caller presents a JWT scoped urlelicit:authorize
# THEN: both toolkits are listed
TOOLS_SCOPED=$(list_tools "$JWT_SCOPED")
echo "TOOLS_SCOPED=$TOOLS_SCOPED"
if echo "$TOOLS_SCOPED" | grep -q '^everything__echo$' &&
    echo "$TOOLS_SCOPED" | grep -q '^urlelicit__authorize$'; then
  echo "✅ urlelicit:authorize: both toolkits are listed"
else
  fail "urlelicit:authorize did not unlock the urlelicit toolkit"
fi

# WHEN: a caller with no JWT calls the ungated everything__echo
# THEN: the call succeeds
ECHO_OUT=$(call_tool "$JWT_NONE" everything__echo '{"message":"hello"}' 60000)
echo "ECHO_OUT=$ECHO_OUT"
if echo "$ECHO_OUT" | grep -q 'Echo: hello'; then
  echo "✅ no token: everything__echo succeeded"
else
  fail "no token: everything__echo did not succeed"
fi

# WHEN: a caller without urlelicit:authorize calls urlelicit__authorize by name
# THEN: it gets no result
DENIED_OUT=$(call_tool "$JWT_NO_SCOPE" urlelicit__authorize '{"resource":"demo"}' 5000)
echo "DENIED_OUT=$DENIED_OUT"
if echo "$DENIED_OUT" | grep -q '^FAIL' && ! echo "$DENIED_OUT" | grep -q 'authorization accept'; then
  echo "✅ urlelicit__authorize returned no result without urlelicit:authorize"
else
  fail "urlelicit__authorize was callable without urlelicit:authorize"
fi

# WHEN: a url-elicitation-capable client calls urlelicit__authorize with the scoped JWT
# THEN: the gateway relays the mode:url elicitation/create request and the
#       later notifications/elicitation/complete back to the client
relay_url_elicitation()
{
  URL_OUT=$(mcp -e JWT_TOKEN="$JWT_SCOPED" elicit)
  echo "$URL_OUT" | grep -q 'OK url-mode elicitation relayed end-to-end'
}
retry_until 5 3 relay_url_elicitation
echo "URL_OUT=$URL_OUT"
if echo "$URL_OUT" | grep -q 'OK url-mode elicitation relayed end-to-end'; then
  echo "✅ url-mode elicitation relayed end-to-end"
else
  fail "url-mode elicitation not relayed end-to-end"
fi

# WHEN: the calls above have reached urlelicit through south_mcp_client_urlelicit
# THEN: the mock received the scoped caller's tools/call carrying that caller's
#       own JWT as its Authorization header, and no tools/call from the denied caller
REQUESTS=$(curl -sS --max-time 10 "$URLELICIT_ENDPOINT/requests")
echo "REQUESTS=$REQUESTS"
if echo "$REQUESTS" | grep -q "^tools/call Bearer $JWT_SCOPED$" &&
    ! echo "$REQUESTS" | grep -q "^tools/call Bearer $JWT_NO_SCOPE$"; then
  echo "✅ the scoped caller's own JWT was forwarded on tools/call, the denied caller's call never arrived"
else
  fail "urlelicit did not observe exactly the scoped caller's tools/call with its own JWT"
fi

report_failures
exit $EXIT
