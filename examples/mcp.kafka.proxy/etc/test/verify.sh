#!/bin/sh
# Runs inside the compose stack as the `verify` service, driven by .github/test.sh.
# Every assertion talks to the gateway through a real MCP SDK client, with JWTs
# minted here from private.pem, one per scope combination under test.
FAIL_FAST=${FAIL_FAST:-1}

. /test-lib.sh

EXIT=0
MCP_ENDPOINT="http://zilla:7114/mcp"
CLIENT=/work/tools-list-client/client.mjs

echo "# Testing mcp.kafka.proxy"

# retry_until bounds attempts; this bounds time, so a slow failing probe cannot
# stretch the gate past what it means ("wait this long for the toolkits")
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

mint() {
  node /work/mint-jwt.mjs "$1"
}

JWT_FULL=$(mint "kafka:tools kafka:write kafka:admin kafka:acls kafka_sr:tools kafka_sr:write")
JWT_READ=$(mint "kafka:tools kafka_sr:tools")

# mcp_call <token> <tool> <json-args>
mcp_call() {
  OUT=$(JWT_TOKEN="$1" MCP_URL="$MCP_ENDPOINT" CALL_TOOL="$2" CALL_ARGS="$3" timeout "${MCP_CALL_TIMEOUT:-60}" node "$CLIENT" 2>&1)
}

mcp_list() {
  OUT=$(JWT_TOKEN="$1" MCP_URL="$MCP_ENDPOINT" timeout 60 node "$CLIENT" 2>&1)
}

# expect_call <token> <tool> <json-args> <pattern>
expect_call() {
  mcp_call "$1" "$2" "$3"
  echo "$OUT" | grep -q -- "$4"
}

# WHEN: a fully scoped caller lists tools
# THEN: both toolkits are served; this also waits out the south bindings' startup
full_toolset_present() {
  mcp_list "$JWT_FULL" &&
    echo "$OUT" | grep -q '^kafka__produce_message$' &&
    echo "$OUT" | grep -q '^kafka__create_acls$' &&
    echo "$OUT" | grep -q '^kafka__list_brokers$' &&
    echo "$OUT" | grep -q '^kafka_sr__register_schema$' &&
    echo "$OUT" | grep -q '^kafka_sr__list_subjects$'
}
retry_deadline 90 3 full_toolset_present
echo "TOOLS_FULL=$OUT"
if full_toolset_present
then
  echo "✅ tools/list serves the kafka and kafka_sr toolkits to a fully scoped caller"
else
  fail "tools/list did not serve both toolkits to a fully scoped caller"
fi

# WHEN: a caller holds only the toolkit-level scopes
# THEN: read-only tools are listed; the mutating tools need their own tool-level scope
scoped_toolsets() {
  mcp_list "$JWT_READ" &&
    echo "$OUT" | grep -q '^kafka__consume_messages$' &&
    echo "$OUT" | grep -q '^kafka__list_acls$' &&
    echo "$OUT" | grep -q '^kafka_sr__list_subjects$' &&
    ! echo "$OUT" | grep -q '^kafka__produce_message$' &&
    ! echo "$OUT" | grep -q '^kafka__create_topics$' &&
    ! echo "$OUT" | grep -q '^kafka__create_acls$' &&
    ! echo "$OUT" | grep -q '^kafka_sr__register_schema$'
}
if retry_until 5 3 scoped_toolsets
then
  echo "✅ tools/list is filtered by the caller's scopes"
else
  echo "OUT=$OUT"
  fail "tools/list was not filtered by the caller's scopes"
fi

# WHEN: a kafka:write caller produces to orders, then a kafka:tools caller consumes it
# THEN: the exact value round-trips through the real broker
VALUE="hello from mcp-kafka $(date +%s)"
produce_and_consume() {
  expect_call "$JWT_FULL" kafka__produce_message \
      "{\"topic\":\"orders\",\"key\":\"order-1\",\"value\":\"$VALUE\"}" 'Produced record to orders topic' &&
    expect_call "$JWT_READ" kafka__consume_messages '{"topic":"orders","limit":100}' "$VALUE"
}
if retry_until 10 3 produce_and_consume
then
  echo "✅ kafka__produce_message and kafka__consume_messages round-tripped a record through the broker"
else
  echo "OUT=$OUT"
  fail "produce/consume round trip through the broker failed"
fi

# WHEN: a caller targets a topic other than orders, or produces without kafka:write
# THEN: no record is written -- the topics allow-list and the tool-level scope leave
#       such a call with no matching route, so it never reaches the broker. Short
#       client timeout because an unrouted call is not answered, just never served;
#       the proof is that the topic a successful produce would auto-create is absent
rejected_calls() {
  export MCP_CALL_TIMEOUT=10
  ! expect_call "$JWT_FULL" kafka__produce_message '{"topic":"other","value":"nope"}' 'Produced record' &&
    ! expect_call "$JWT_FULL" kafka__consume_messages '{"topic":"other","limit":1}' 'Consumed' &&
    ! expect_call "$JWT_READ" kafka__produce_message '{"topic":"orders","value":"nope"}' 'Produced record' &&
    unset MCP_CALL_TIMEOUT &&
    expect_call "$JWT_READ" kafka__list_topics '{}' '"topic":"orders"' &&
    ! echo "$OUT" | grep -q '"topic":"other"'
}
if rejected_calls
then
  echo "✅ produce/consume outside the orders allow-list, and produce without kafka:write, wrote nothing"
else
  unset MCP_CALL_TIMEOUT
  echo "OUT=$OUT"
  fail "calls outside the allow-list or scope were not rejected"
fi

# WHEN: a kafka:tools caller describes the orders topic's configs
# THEN: the broker's effective config comes back
if retry_until 10 3 expect_call "$JWT_READ" kafka__describe_topic_configs '{"topic":"orders"}' '"name":"cleanup.policy"'
then
  echo "✅ kafka__describe_topic_configs read the topic config from the broker"
else
  echo "OUT=$OUT"
  fail "kafka__describe_topic_configs did not return the topic config"
fi

# WHEN: a kafka:acls caller grants, lists and revokes an ACL on a dedicated topic
# THEN: the broker accepts each; a dedicated topic because once an ACL exists for a
#       resource the authorizer stops implicitly allowing every other principal on it
ACL_PRINCIPAL="User:acl-test-user"
ACL_CREATE="{\"acls\":[{\"resource_type\":\"topic\",\"resource_name\":\"acl-test-topic\",\"pattern_type\":\"literal\",\"principal\":\"$ACL_PRINCIPAL\",\"host\":\"*\",\"operation\":\"read\",\"permission_type\":\"allow\"}]}"
ACL_DELETE="{\"acls\":[{\"resource_type\":\"topic\",\"resource_name\":\"acl-test-topic\",\"pattern_type\":\"literal\",\"principal\":\"$ACL_PRINCIPAL\",\"operation\":\"read\",\"permission_type\":\"allow\"}]}"
acl_lifecycle() {
  expect_call "$JWT_FULL" kafka__create_acls "$ACL_CREATE" 'Created 1 ACL(s)' &&
    expect_call "$JWT_READ" kafka__list_acls "{\"principal\":\"$ACL_PRINCIPAL\"}" '"resource_name":"acl-test-topic"' &&
    expect_call "$JWT_FULL" kafka__delete_acls "$ACL_DELETE" 'Deleted 1 ACL(s)'
}
if retry_until 5 3 acl_lifecycle
then
  echo "✅ kafka__create_acls, kafka__list_acls and kafka__delete_acls managed a real ACL"
else
  echo "OUT=$OUT"
  fail "ACL create/list/delete did not succeed against the broker"
fi

# WHEN: a kafka:tools caller lists brokers and describes the cluster
# THEN: the single KRaft node is reported as broker 1 and as the controller
cluster_introspection() {
  expect_call "$JWT_READ" kafka__list_brokers '{}' 'Brokers: 1@kafka.examples.dev:29092' &&
    expect_call "$JWT_READ" kafka__describe_cluster '{}' 'Described cluster .*, controller 1'
}
if retry_until 10 3 cluster_introspection
then
  echo "✅ kafka__list_brokers and kafka__describe_cluster reported the real broker"
else
  echo "OUT=$OUT"
  fail "broker/cluster introspection did not report the real broker"
fi

# WHEN: a kafka:tools caller describes a consumer group that has never committed an offset
# THEN: the broker reports it Dead, which is what Kafka returns for a group that does not exist
if retry_until 10 3 expect_call "$JWT_READ" kafka__describe_consumer_group '{"group_id":"orders-analytics"}' \
    'Consumer group orders-analytics is Dead'
then
  echo "✅ kafka__describe_consumer_group reported state Dead for a never-used group"
else
  echo "OUT=$OUT"
  fail "kafka__describe_consumer_group did not report the group's state"
fi

SR_SUBJECT="orders-value"
SR_SCHEMA='{\"type\":\"record\",\"name\":\"Order\",\"fields\":[{\"name\":\"id\",\"type\":\"string\"}]}'
SR_ARGS="{\"subject\":\"$SR_SUBJECT\",\"schemaType\":\"AVRO\",\"schema\":\"$SR_SCHEMA\"}"

# WHEN: a kafka_sr:write caller registers a schema, then lists subjects and reads it back
# THEN: Karapace assigns an id, and the subject and schema are real, persisted state
schema_lifecycle() {
  expect_call "$JWT_FULL" kafka_sr__register_schema "$SR_ARGS" 'Registered schema with id' &&
    expect_call "$JWT_READ" kafka_sr__list_subjects '{}' "$SR_SUBJECT" &&
    expect_call "$JWT_READ" kafka_sr__get_schema "{\"subject\":\"$SR_SUBJECT\",\"version\":\"latest\"}" \
        'Retrieved schema id 1, version 1'
}
if retry_until 10 3 schema_lifecycle
then
  echo "✅ kafka_sr__register_schema, list_subjects and get_schema worked against a real Karapace"
else
  echo "OUT=$OUT"
  fail "schema register/list/get did not succeed against Karapace"
fi

# WHEN: a caller sets a compatibility level, then checks the same schema against it
# THEN: the level round-trips and the identical schema is reported compatible
compatibility() {
  expect_call "$JWT_FULL" kafka_sr__set_compatibility "{\"subject\":\"$SR_SUBJECT\",\"compatibility\":\"FULL\"}" \
      'Compatibility level set to FULL' &&
    expect_call "$JWT_READ" kafka_sr__check_compatibility \
        "{\"subject\":\"$SR_SUBJECT\",\"version\":\"1\",\"schemaType\":\"AVRO\",\"schema\":\"$SR_SCHEMA\"}" \
        'Compatibility check result: true'
}
if retry_until 10 3 compatibility
then
  echo "✅ kafka_sr__set_compatibility and kafka_sr__check_compatibility worked against a real Karapace"
else
  echo "OUT=$OUT"
  fail "schema compatibility set/check did not succeed against Karapace"
fi

report_failures

exit $EXIT
