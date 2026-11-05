#!/bin/sh
# Runs inside the compose stack as the `verify` service; .github/test.sh only
# starts it. Assertions, all at the Zilla layer:
#   1. a caller with inventory:tools lists get_item and list_items
#   2. a caller without inventory:tools gets no result from the inventory toolkit
#   3. get_item reads an item through the generated grpc client, so the JSON
#      arguments became a protobuf request and the protobuf reply became JSON
#   4. list_items filters by category, proving a repeated message field converts
#   5. the gRPC service only answers calls carrying the credential Zilla forwards
#      from the caller's session via options.authorization
#   6. a gRPC NOT_FOUND status comes back as an MCP tool error, not a failure
#   7. MCP metrics count the tool calls, a gRPC error status included, since
#      it is a tool result and not a failed call
set -x

FAIL_FAST=${FAIL_FAST:-1}

. /test-lib.sh

# retry_until bounds attempts, not time; this bounds how long a gate may take
# whatever the probe's own duration is. The first retry is immediate.
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

EXIT=0
MCP_ENDPOINT="http://zilla:7114/mcp"
METRICS_ENDPOINT="http://zilla:7190/metrics"
SCOPE_TOOLS="inventory:tools"
SCOPE_OTHER="unrelated:tools"
DENIED_TIMEOUT=8

echo "# Testing mcp.grpc.proxy"

# mcp_run [-e VAR=VALUE]... : the client, bounded so a reply that never comes
# is an ordinary failure. Tracing is off inside so the captured output is only
# the client's own.
mcp_run() {
  (
    { set +x; } 2>/dev/null
    while [ "$1" = "-e" ]
    do
      export "$2"
      shift 2
    done
    export MCP_URL="$MCP_ENDPOINT"
    exec timeout "${MCP_TIMEOUT:-60}" node /work/client/client.mjs 2>&1
  )
}

list_tools() {
  mcp_run -e JWT_SCOPE="$1"
}

call_tool() {
  mcp_run -e JWT_SCOPE="$1" -e CALL_TOOL="$2" -e CALL_ARGS="$3"
}

# WHEN: an inventory:tools caller lists tools
# THEN: get_item and list_items are listed, generated from the .proto
list_inventory() {
  TOOLS_INVENTORY=$(list_tools "$SCOPE_TOOLS")
  echo "$TOOLS_INVENTORY" | grep -q '^inventory__get_item$'
}
retry_deadline 60 2 list_inventory
echo "TOOLS_INVENTORY=$TOOLS_INVENTORY"
if echo "$TOOLS_INVENTORY" | grep -q '^inventory__get_item$' &&
    echo "$TOOLS_INVENTORY" | grep -q '^inventory__list_items$'; then
  echo "✅ inventory:tools lists get_item and list_items"
else
  fail "inventory:tools did not list get_item and list_items"
fi

# WHEN: a caller without inventory:tools asks for an item
# THEN: it gets no result, because the toolkit route is closed to it
DENIED_OUT=$(mcp_run -e MCP_TIMEOUT="$DENIED_TIMEOUT" -e JWT_SCOPE="$SCOPE_OTHER" \
    -e CALL_TOOL=inventory__get_item -e CALL_ARGS='{"sku":"sku-1"}')
echo "DENIED_OUT=$DENIED_OUT"
if ! echo "$DENIED_OUT" | grep -q 'Widget'; then
  echo "✅ a caller without inventory:tools got no inventory result"
else
  fail "a caller without inventory:tools reached the inventory toolkit"
fi

# WHEN: an inventory:tools caller calls get_item for a known sku
# THEN: the item comes back as JSON, converted from the gRPC protobuf reply
GET_ITEM_OUT=$(call_tool "$SCOPE_TOOLS" inventory__get_item '{"sku":"sku-1"}')
echo "GET_ITEM_OUT=$GET_ITEM_OUT"
if echo "$GET_ITEM_OUT" | grep -q '"name":"Widget"'; then
  echo "✅ inventory__get_item called a real gRPC method end-to-end"
else
  fail "inventory__get_item did not return the inventory item"
fi

# WHEN: the caller lists the items in one category
# THEN: only that category's items come back, in a repeated protobuf field
LIST_ITEMS_OUT=$(call_tool "$SCOPE_TOOLS" inventory__list_items '{"category":"toys"}')
echo "LIST_ITEMS_OUT=$LIST_ITEMS_OUT"
if echo "$LIST_ITEMS_OUT" | grep -q 'Gizmo' && ! echo "$LIST_ITEMS_OUT" | grep -q 'Widget'; then
  echo "✅ inventory__list_items filtered by category and converted a repeated field"
else
  fail "inventory__list_items did not filter by category"
fi

# WHEN: the successful calls above reached the gRPC service
# THEN: the service only answers calls carrying a credential, so they prove
#       options.authorization forwarded the session credential as gRPC metadata
if echo "$GET_ITEM_OUT$LIST_ITEMS_OUT" | grep -q 'UNAUTHENTICATED'; then
  fail "the gRPC service did not receive the forwarded credential"
else
  echo "✅ the gRPC service received the credential forwarded by options.authorization"
fi

# WHEN: the caller asks for a sku the service does not have
# THEN: the gRPC NOT_FOUND status is returned as an MCP tool error carrying the
#       status name and message, not as a transport failure
MISSING_ITEM_OUT=$(call_tool "$SCOPE_TOOLS" inventory__get_item '{"sku":"nope"}')
echo "MISSING_ITEM_OUT=$MISSING_ITEM_OUT"
if echo "$MISSING_ITEM_OUT" | grep -q 'NOT_FOUND: unknown sku nope'; then
  echo "✅ inventory__get_item reported a gRPC NOT_FOUND status as an MCP tool error"
else
  fail "inventory__get_item did not report the gRPC status as a tool error"
fi

# WHEN: the Prometheus endpoint is scraped after the calls above
# THEN: both tools are counted as ok, and so is the NOT_FOUND call, which came
#       back as a tool result carrying isError rather than as a failed call
METRICS=$(wget -qO- -T 10 "$METRICS_ENDPOINT" | grep '^mcp_tools_call_total')
echo "METRICS=$METRICS"
if echo "$METRICS" | grep -q 'outcome="ok",tool="inventory__get_item"} [2-9]' &&
    echo "$METRICS" | grep -q 'outcome="ok",tool="inventory__list_items"} [1-9]'; then
  echo "✅ mcp_tools_call_total counts inventory tool calls, a gRPC error status included"
else
  fail "mcp_tools_call_total did not count the inventory tool calls"
fi

report_failures
exit $EXIT
