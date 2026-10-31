#!/bin/sh
# Runs inside the compose network (see the `verify` service in compose.yaml).
#
#  1. a kafka_connect:tools token lists the read tools but not the admin tools
#  2. a kafka_connect:admin token also lists the admin tools
#  3. list_connector_plugins reports the FileStream connectors on plugin.path
#  4. create_connector succeeds for an admin token
#  5. list_connectors and describe_connector read back the created connector
#  6. describe_connector_status reports RUNNING
#  7. pause_connector moves it to PAUSED
#  8. resume_connector moves it back to RUNNING
#  9. restart_connector leaves it RUNNING
# 10. delete_connector removes it
set -x

FAIL_FAST=${FAIL_FAST:-1}

. /test-lib.sh

EXIT=0
MCP_URL="http://zilla:7114/mcp"
CONNECTOR="file-source-demo"
CLIENT_TIMEOUT_S=60
STATUS_DEADLINE_S=90

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

JWT_TOOLS=$(node /work/client/mint.mjs "kafka_connect:tools")
JWT_ADMIN=$(node /work/client/mint.mjs "kafka_connect:tools kafka_connect:admin")

# call <token> [tool [args-json]]: list tools, or call one, as the token's caller
call() {
  _token=$1
  _tool=$2
  _args=${3:-}
  if [ -z "$_args" ]
  then
    _args="{}"
  fi
  (
    { set +x; } 2>/dev/null
    export MCP_URL JWT_TOKEN="$_token" CALL_TOOL="$_tool" CALL_ARGS="$_args"
    exec timeout "$CLIENT_TIMEOUT_S" node /work/client/client.mjs
  ) 2>&1
}

connector_args() {
  echo "{\"connector\":\"$CONNECTOR\"}"
}

echo "# Testing mcp.kafka.connect.proxy"

list_tools_as_tools_scope() {
  TOOLS_OUT=$(call "$JWT_TOOLS")
  echo "$TOOLS_OUT" | grep -q '^kafka_connect__list_connectors$'
}
retry_until 10 3 list_tools_as_tools_scope
echo "TOOLS_OUT=$TOOLS_OUT"
if echo "$TOOLS_OUT" | grep -q '^kafka_connect__list_connectors$' &&
    echo "$TOOLS_OUT" | grep -q '^kafka_connect__list_connector_plugins$' &&
    ! echo "$TOOLS_OUT" | grep -q '^kafka_connect__create_connector$' &&
    ! echo "$TOOLS_OUT" | grep -q '^kafka_connect__pause_connector$'; then
  echo "✅ kafka_connect:tools token lists read tools only"
else
  fail "kafka_connect:tools token did not list exactly the read tools"
fi

ADMIN_TOOLS_OUT=$(call "$JWT_ADMIN")
echo "ADMIN_TOOLS_OUT=$ADMIN_TOOLS_OUT"
if echo "$ADMIN_TOOLS_OUT" | grep -q '^kafka_connect__list_connectors$' &&
    echo "$ADMIN_TOOLS_OUT" | grep -q '^kafka_connect__create_connector$' &&
    echo "$ADMIN_TOOLS_OUT" | grep -q '^kafka_connect__pause_connector$' &&
    echo "$ADMIN_TOOLS_OUT" | grep -q '^kafka_connect__delete_connector$'; then
  echo "✅ kafka_connect:admin token also lists the admin tools"
else
  fail "kafka_connect:admin token did not list the admin tools"
fi

list_plugins() {
  PLUGINS_OUT=$(call "$JWT_TOOLS" kafka_connect__list_connector_plugins)
  echo "$PLUGINS_OUT" | grep -q 'FileStreamSourceConnector'
}
retry_until 10 3 list_plugins
echo "PLUGINS_OUT=$PLUGINS_OUT"
if echo "$PLUGINS_OUT" | grep -q 'FileStreamSourceConnector' &&
    echo "$PLUGINS_OUT" | grep -q 'FileStreamSinkConnector'; then
  echo "✅ list_connector_plugins reported the FileStream connectors"
else
  fail "list_connector_plugins did not report the FileStream connectors"
fi

CREATE_ARGS="{\"name\":\"$CONNECTOR\",\"config\":{\"connector.class\":\"org.apache.kafka.connect.file.FileStreamSourceConnector\",\"tasks.max\":\"1\",\"file\":\"/tmp/kc-source.txt\",\"topic\":\"connect-demo\"}}"

create_connector() {
  CREATE_OUT=$(call "$JWT_ADMIN" kafka_connect__create_connector "$CREATE_ARGS")
  echo "$CREATE_OUT" | grep -q "$CONNECTOR"
}
retry_until 10 3 create_connector
echo "CREATE_OUT=$CREATE_OUT"
if echo "$CREATE_OUT" | grep -q "$CONNECTOR"; then
  echo "✅ create_connector created the connector on the worker"
else
  fail "create_connector did not create the connector"
fi

read_back_connector() {
  LIST_OUT=$(call "$JWT_TOOLS" kafka_connect__list_connectors)
  DESCRIBE_OUT=$(call "$JWT_TOOLS" kafka_connect__describe_connector "$(connector_args)")
  echo "$LIST_OUT" | grep -q "$CONNECTOR" && echo "$DESCRIBE_OUT" | grep -q 'FileStreamSourceConnector'
}
retry_until 10 3 read_back_connector
echo "LIST_OUT=$LIST_OUT"
echo "DESCRIBE_OUT=$DESCRIBE_OUT"
if echo "$LIST_OUT" | grep -q "$CONNECTOR" && echo "$DESCRIBE_OUT" | grep -q 'FileStreamSourceConnector'; then
  echo "✅ list_connectors and describe_connector read back the connector"
else
  fail "list_connectors and describe_connector did not read back the connector"
fi

status_running() {
  STATUS_OUT=$(call "$JWT_TOOLS" kafka_connect__describe_connector_status "$(connector_args)")
  echo "$STATUS_OUT" | grep -q 'RUNNING'
}
retry_deadline "$STATUS_DEADLINE_S" 3 status_running
echo "STATUS_OUT=$STATUS_OUT"
if echo "$STATUS_OUT" | grep -q 'RUNNING'; then
  echo "✅ describe_connector_status reported the connector RUNNING"
else
  fail "describe_connector_status did not report RUNNING within ${STATUS_DEADLINE_S}s"
fi

pause_connector() {
  PAUSE_OUT=$(call "$JWT_ADMIN" kafka_connect__pause_connector "$(connector_args)")
  PAUSED_OUT=$(call "$JWT_TOOLS" kafka_connect__describe_connector_status "$(connector_args)")
  echo "$PAUSED_OUT" | grep -q 'PAUSED'
}
retry_until 10 3 pause_connector
echo "PAUSE_OUT=$PAUSE_OUT"
echo "PAUSED_OUT=$PAUSED_OUT"
if echo "$PAUSED_OUT" | grep -q 'PAUSED'; then
  echo "✅ pause_connector moved the connector to PAUSED"
else
  fail "pause_connector did not move the connector to PAUSED"
fi

resume_connector() {
  RESUME_OUT=$(call "$JWT_ADMIN" kafka_connect__resume_connector "$(connector_args)")
  RESUMED_OUT=$(call "$JWT_TOOLS" kafka_connect__describe_connector_status "$(connector_args)")
  echo "$RESUMED_OUT" | grep -q 'RUNNING'
}
retry_until 10 3 resume_connector
echo "RESUME_OUT=$RESUME_OUT"
echo "RESUMED_OUT=$RESUMED_OUT"
if echo "$RESUMED_OUT" | grep -q 'RUNNING'; then
  echo "✅ resume_connector moved the connector back to RUNNING"
else
  fail "resume_connector did not move the connector back to RUNNING"
fi

restart_connector() {
  RESTART_OUT=$(call "$JWT_ADMIN" kafka_connect__restart_connector "$(connector_args)")
  RESTARTED_OUT=$(call "$JWT_TOOLS" kafka_connect__describe_connector_status "$(connector_args)")
  echo "$RESTARTED_OUT" | grep -q 'RUNNING'
}
retry_until 10 3 restart_connector
echo "RESTART_OUT=$RESTART_OUT"
echo "RESTARTED_OUT=$RESTARTED_OUT"
if echo "$RESTARTED_OUT" | grep -q 'RUNNING'; then
  echo "✅ restart_connector left the connector RUNNING"
else
  fail "restart_connector did not leave the connector RUNNING"
fi

delete_connector() {
  DELETE_OUT=$(call "$JWT_ADMIN" kafka_connect__delete_connector "$(connector_args)")
  LIST_AFTER_DELETE_OUT=$(call "$JWT_TOOLS" kafka_connect__list_connectors)
  ! echo "$LIST_AFTER_DELETE_OUT" | grep -q "$CONNECTOR"
}
retry_until 10 3 delete_connector
echo "DELETE_OUT=$DELETE_OUT"
echo "LIST_AFTER_DELETE_OUT=$LIST_AFTER_DELETE_OUT"
if ! echo "$LIST_AFTER_DELETE_OUT" | grep -q "$CONNECTOR"; then
  echo "✅ delete_connector removed the connector"
else
  fail "delete_connector did not remove the connector"
fi

report_failures

exit $EXIT
