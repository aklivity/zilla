#!/bin/sh
# Runs inside the compose stack as the `verify` service; .github/test.sh only starts it.
set -x

FAIL_FAST=${FAIL_FAST:-1}

. /test-lib.sh

EXIT=0
PORT="7114"
MCP_ENDPOINT="http://zilla:$PORT/mcp"
METRICS_ENDPOINT="http://zilla:7190/metrics"
INITIALIZE='{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-11-25","capabilities":{},"clientInfo":{"name":"zilla-mcp-proxy-search-test","version":"0.0.1"}}}'

CACHE_HYDRATE_TIMEOUT_S=${CACHE_HYDRATE_TIMEOUT_S:-90}
CACHE_REFRESH_TIMEOUT_S=${CACHE_REFRESH_TIMEOUT_S:-90}

echo "# Testing mcp.proxy.search"

retry_deadline() {
  _deadline=$(( $(date +%s) + $1 ))
  _delay=$2
  shift 2

  _retried=0
  until "$@"
  do
    _status=$?
    if [ "$(date +%s)" -ge "$_deadline" ]
    then
      return "$_status"
    fi
    if [ "$_retried" -eq 1 ]
    then
      sleep "$_delay"
    fi
    _retried=1
  done
}

mcp_run() {
  (
    { set +x; } 2>/dev/null
    while [ "$1" = "-e" ]
    do
      export "$2"
      shift 2
    done
    export MCP_URL="$MCP_ENDPOINT"
    exec timeout 60 node /work/tools-list-client/client.mjs
  )
}

list_tools()
{
  mcp_run 2>&1
}

call_tool()
{
  mcp_run -e CALL_TOOL="$1" -e CALL_ARGS="$2" 2>&1
}

# a counter of mcp_tools_list requests seen by one binding, summed across its series
tools_list_count()
{
  curl -sS --max-time 5 "$METRICS_ENDPOINT" |
    grep '^mcp_tools_list_total' |
    grep "binding=\"$1\"" |
    awk '{ total += $NF } END { print total + 0 }'
}

# WHEN: a client initializes against the gateway
# THEN: the gateway negotiates protocol version 2025-11-25
initialize_mcp() {
  INIT_BODY=$(curl -sS -N --max-time 10 \
      -X POST "$MCP_ENDPOINT" \
      -H "Content-Type: application/json" \
      -H "Accept: application/json, text/event-stream" \
      -d "$INITIALIZE")
  echo "$INIT_BODY" | grep -q '"protocolVersion":"2025-11-25"'
}
retry_until 10 3 initialize_mcp
echo "INIT_BODY=$INIT_BODY"
if echo "$INIT_BODY" | grep -q '"protocolVersion":"2025-11-25"'; then
  echo "✅ initialize negotiated 2025-11-25"
else
  fail "initialize did not negotiate 2025-11-25"
fi

# WHEN: the cache finishes its first hydration from the everything backend
# THEN: tools/list serves the eager tools and the synthesized search tools
cache_hydrated() {
  TOOLS=$(list_tools) && echo "$TOOLS" | grep -q '^everything__echo$'
}
retry_deadline "$CACHE_HYDRATE_TIMEOUT_S" 3 cache_hydrated
echo "TOOLS=$TOOLS"
if echo "$TOOLS" | grep -q '^everything__echo$'; then
  echo "✅ cache hydrated from the everything backend"
else
  fail "cache never finished hydrating from the everything backend"
fi

# THEN: only the eager tools and the search family are listed
if echo "$TOOLS" | grep -q '^everything__echo$' &&
    echo "$TOOLS" | grep -q '^everything__get-env$' &&
    echo "$TOOLS" | grep -q '^zilla__search_tools$' &&
    echo "$TOOLS" | grep -q '^zilla__describe_tool$' &&
    echo "$TOOLS" | grep -q '^zilla__execute_tool$'; then
  echo "✅ tools/list lists the eager tools plus search_tools, describe_tool and execute_tool"
else
  fail "tools/list did not list the eager tools plus the search family"
fi

# THEN: cold tools are omitted from tools/list
if ! echo "$TOOLS" | grep -q '^everything__get-sum$'; then
  echo "✅ the cold everything__get-sum tool is not listed"
else
  fail "everything__get-sum was listed despite not matching cache.tools.eager.match"
fi

# WHEN: tools/list is requested repeatedly
# THEN: the north binding counts every request while the backend sees none, as
#       they are all served from the cache -- retried when a scheduled refresh
#       lands inside the observation window
repeat_tools_list_from_cache() {
  SOUTH_BEFORE=$(tools_list_count south_mcp_client_everything)
  NORTH_BEFORE=$(tools_list_count north_mcp_server)
  list_tools > /dev/null
  list_tools > /dev/null
  list_tools > /dev/null
  SOUTH_AFTER=$(tools_list_count south_mcp_client_everything)
  NORTH_AFTER=$(tools_list_count north_mcp_server)
  [ "$SOUTH_AFTER" -eq "$SOUTH_BEFORE" ]
}
retry_until 3 1 repeat_tools_list_from_cache
echo "north $NORTH_BEFORE -> $NORTH_AFTER, south $SOUTH_BEFORE -> $SOUTH_AFTER"
if [ "$NORTH_AFTER" -ge $(( NORTH_BEFORE + 3 )) ] && [ "$SOUTH_AFTER" -eq "$SOUTH_BEFORE" ]; then
  echo "✅ repeated tools/list is served from the cache without reaching the backend"
else
  fail "repeated tools/list reached the backend (north $NORTH_BEFORE -> $NORTH_AFTER, south $SOUTH_BEFORE -> $SOUTH_AFTER)"
fi

# WHEN: search_tools is called for "sum"
# THEN: the cold everything__get-sum tool is found by keyword
SEARCH_OUT=$(call_tool zilla__search_tools '{"query":"sum"}')
echo "SEARCH_OUT=$SEARCH_OUT"
if echo "$SEARCH_OUT" | grep -q 'everything__get-sum'; then
  echo "✅ search_tools found the cold everything__get-sum tool"
else
  fail "search_tools did not find everything__get-sum for query \"sum\""
fi

# WHEN: describe_tool is called for the cold tool
# THEN: its full definition, input schema included, is returned
DESCRIBE_OUT=$(call_tool zilla__describe_tool '{"name":"everything__get-sum"}')
echo "DESCRIBE_OUT=$DESCRIBE_OUT"
if echo "$DESCRIBE_OUT" | grep -q 'inputSchema' && echo "$DESCRIBE_OUT" | grep -q 'everything__get-sum'; then
  echo "✅ describe_tool resolved the cold tool's full definition"
else
  fail "describe_tool did not resolve everything__get-sum"
fi

# WHEN: execute_tool is called for the cold tool
# THEN: the tool's own result comes back
EXECUTE_OUT=$(call_tool zilla__execute_tool '{"name":"everything__get-sum","arguments":{"a":2,"b":3}}')
echo "EXECUTE_OUT=$EXECUTE_OUT"
if echo "$EXECUTE_OUT" | grep -q 'The sum of 2 and 3 is 5'; then
  echo "✅ execute_tool invoked the cold tool"
else
  fail "execute_tool did not invoke everything__get-sum"
fi

# WHEN: the cold tool is called directly by name
# THEN: it succeeds as an eager tool would
COLD_CALL_OUT=$(call_tool everything__get-sum '{"a":2,"b":3}')
echo "COLD_CALL_OUT=$COLD_CALL_OUT"
if echo "$COLD_CALL_OUT" | grep -q 'The sum of 2 and 3 is 5'; then
  echo "✅ the cold tool is directly callable by name"
else
  fail "everything__get-sum was not callable directly"
fi

# WHEN: cache.ttl (PT30S) elapses
# THEN: the cache re-hydrates from the backend, observed as a new tools/list
#       request on the south binding, while tools/list keeps answering the same set
cache_refreshed() {
  [ "$(tools_list_count south_mcp_client_everything)" -gt "$SOUTH_AFTER" ]
}
retry_deadline "$CACHE_REFRESH_TIMEOUT_S" 3 cache_refreshed
SOUTH_REFRESHED=$(tools_list_count south_mcp_client_everything)
echo "south $SOUTH_AFTER -> $SOUTH_REFRESHED"
if [ "$SOUTH_REFRESHED" -gt "$SOUTH_AFTER" ]; then
  echo "✅ the cache refreshed from the backend after cache.ttl"
else
  fail "the cache did not refresh from the backend within ${CACHE_REFRESH_TIMEOUT_S}s"
fi

TOOLS_AFTER_REFRESH=$(list_tools)
if [ "$TOOLS_AFTER_REFRESH" = "$TOOLS" ]; then
  echo "✅ tools/list is unchanged after the refresh"
else
  fail "tools/list changed after the refresh"
fi

# THEN: the calls above were counted by the mcp metrics
METRICS=$(curl -sS --max-time 5 "$METRICS_ENDPOINT")
echo "$METRICS" | grep '^mcp_'
if echo "$METRICS" | grep '^mcp_tools_call_total' | grep -q 'tool="everything__get-sum"'; then
  echo "✅ mcp_tools_call_total counted the everything__get-sum calls"
else
  fail "mcp_tools_call_total has no everything__get-sum series"
fi

report_failures

exit $EXIT
