#!/bin/sh
# Runs inside the compose stack as the `verify` service; .github/test.sh starts it.
#
# Assertions, all at the Zilla layer:
#   1. initialize negotiates 2025-11-25 and advertises resources.subscribe
#   2. tools/list aggregates the everything toolkit's tools under the everything__ prefix
#   3. tools/call everything__echo reaches the upstream server and returns its result
#   4. tools/call everything__get-sum computes from its arguments
#   5. resources/list aggregates the toolkit's resources under the everything+ prefix
#   6. resources/read relays a listed resource's contents
#   7. resources/subscribe, notifications/resources/updated and resources/unsubscribe
#      are relayed end-to-end
#   8. the Prometheus endpoint counts the tools/call requests above
#   9. the Prometheus endpoint counts tools/list requests
set -x

. /test-lib.sh

EXIT=0
MCP_ENDPOINT="http://zilla:7114/mcp"
METRICS_ENDPOINT="http://zilla:7190/metrics"
INITIALIZE='{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-11-25","capabilities":{},"clientInfo":{"name":"zilla-mcp-proxy-test","version":"0.0.1"}}}'

# bounds each client so a lost reply is an ordinary failure instead of a hung job
MCP_RUN_TIMEOUT_S=${MCP_RUN_TIMEOUT_S:-60}

# mcp_run [-e VAR=VALUE]... <client>
# xtrace is off inside so tracing does not land in the output callers grep
mcp_run() {
  (
    { set +x; } 2>/dev/null
    while [ "$1" = "-e" ]
    do
      export "$2"
      shift 2
    done

    case "$1" in
    tools-list-client)         _client=/work/tools-list-client/client.mjs ;;
    resource-subscribe-client) _client=/work/resource-subscribe-client/client.mjs ;;
    *) echo "mcp_run: unknown client \"$1\"" >&2; exit 2 ;;
    esac

    export MCP_URL="$MCP_ENDPOINT"
    exec timeout "$MCP_RUN_TIMEOUT_S" node "$_client"
  )
}

# WHEN: a client initializes against the gateway
# THEN: the gateway negotiates 2025-11-25 and advertises resources.subscribe, learned
#       from the everything server's own initialize handshake
initialize_mcp() {
  INIT_BODY=$(curl -sS -N --max-time 10 \
      -X POST "$MCP_ENDPOINT" \
      -H "Content-Type: application/json" \
      -H "Accept: application/json, text/event-stream" \
      -d "$INITIALIZE")
  echo "$INIT_BODY" | grep -q '"protocolVersion":"2025-11-25"' &&
    echo "$INIT_BODY" | grep -q '"subscribe":true'
}
retry_until 10 3 initialize_mcp
echo INIT_BODY="$INIT_BODY"
if echo "$INIT_BODY" | grep -q '"protocolVersion":"2025-11-25"' &&
    echo "$INIT_BODY" | grep -q '"subscribe":true'; then
  echo ✅ initialize negotiated 2025-11-25 and advertised resources.subscribe
else
  fail "initialize did not negotiate 2025-11-25 with resources.subscribe"
fi

# WHEN: a client lists tools, resources and resource templates
# THEN: the everything toolkit's tools are all present, each carrying the toolkit prefix
list_all() {
  LISTING=$(mcp_run tools-list-client 2>&1)
  echo "$LISTING" | grep -q '^everything__echo$'
}
retry_until 10 3 list_all
echo "LISTING=$LISTING"
TOOL_NAMES=$(echo "$LISTING" | grep -v '^resource:\|^template:\|^\[client\]')
if echo "$LISTING" | grep -q '^everything__echo$' &&
    echo "$LISTING" | grep -q '^everything__get-sum$' &&
    [ -n "$TOOL_NAMES" ] &&
    ! echo "$TOOL_NAMES" | grep -qv '^everything__'; then
  echo "✅ tools/list aggregated the everything toolkit's tools under the everything__ prefix"
else
  fail "tools/list did not list the everything toolkit's tools under the everything__ prefix"
fi

# WHEN: a client calls everything__echo
# THEN: the upstream server's echo comes back through the gateway
ECHO_OUT=$(mcp_run -e CALL_TOOL=everything__echo -e CALL_ARGS='{"message":"hello from zilla"}' tools-list-client 2>&1)
echo "ECHO_OUT=$ECHO_OUT"
if echo "$ECHO_OUT" | grep -q 'Echo: hello from zilla'; then
  echo "✅ everything__echo returned the upstream echo"
else
  fail "everything__echo did not return 'Echo: hello from zilla'"
fi

# WHEN: a client calls everything__get-sum with a=2, b=3
# THEN: the upstream server computes the sum
SUM_OUT=$(mcp_run -e CALL_TOOL=everything__get-sum -e CALL_ARGS='{"a":2,"b":3}' tools-list-client 2>&1)
echo "SUM_OUT=$SUM_OUT"
if echo "$SUM_OUT" | grep -q 'The sum of 2 and 3 is 5'; then
  echo "✅ everything__get-sum computed 2 + 3 = 5"
else
  fail "everything__get-sum did not return 'The sum of 2 and 3 is 5'"
fi

# WHEN: a client lists resources
# THEN: the everything toolkit's resources are present, prefixed everything+
RESOURCE_URI=$(echo "$LISTING" | grep '^resource:everything+' | head -n 1 | sed 's/^resource://')
if [ -n "$RESOURCE_URI" ]; then
  echo "✅ resources/list aggregated the everything toolkit's resources under the everything+ prefix"
else
  fail "resources/list did not include an everything+ resource"
fi

# WHEN: a client reads a listed resource
# THEN: the contents are relayed from the upstream server
READ_OUT=$(mcp_run -e READ_RESOURCE="$RESOURCE_URI" tools-list-client 2>&1)
echo "READ_OUT=$READ_OUT"
if [ -n "$RESOURCE_URI" ] && [ -n "$READ_OUT" ] && ! echo "$READ_OUT" | grep -q 'FAIL'; then
  echo "✅ resources/read relayed the contents of $RESOURCE_URI"
else
  fail "resources/read did not relay the contents of $RESOURCE_URI"
fi

# WHEN: a client subscribes to a resource and the upstream server starts pushing updates
# THEN: the notifications/resources/updated is relayed back with the prefixed URI
SUBSCRIBE_OUT=$(mcp_run resource-subscribe-client 2>&1)
echo "SUBSCRIBE_OUT=$SUBSCRIBE_OUT"
if echo "$SUBSCRIBE_OUT" | grep -q 'OK resource subscription relayed end-to-end'; then
  echo "✅ resources/subscribe, notifications/resources/updated and resources/unsubscribe relayed end-to-end"
else
  fail "resource subscription was not relayed end-to-end"
fi

# WHEN: the requests above have completed
# THEN: the Prometheus endpoint counts them, attributed by method, tool and outcome
metric_counted() {
  METRICS=$(curl -sS --max-time 5 "$METRICS_ENDPOINT")
  echo "$METRICS" | grep -q '^mcp_tools_call_total{.*tool="everything__echo"'
}
retry_until 10 3 metric_counted
echo "$METRICS" | grep '^mcp_'
if echo "$METRICS" | grep '^mcp_tools_call_total{' | grep 'tool="everything__echo"' | grep -q 'outcome="ok"' &&
    echo "$METRICS" | grep '^mcp_tools_call_total{' | grep 'tool="everything__get-sum"' | grep -q 'outcome="ok"'; then
  echo "✅ mcp_tools_call_total counted the everything__echo and everything__get-sum calls"
else
  fail "mcp_tools_call_total did not count the tool calls"
fi

if echo "$METRICS" | grep -q '^mcp_tools_list_total'; then
  echo "✅ mcp_tools_list_total counted the tools/list requests"
else
  fail "mcp_tools_list_total was not reported"
fi

report_failures
exit $EXIT
