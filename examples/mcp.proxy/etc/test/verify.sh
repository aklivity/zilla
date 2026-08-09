#!/bin/sh
# Drives the build-workflow verification for examples/mcp.proxy.
#
# Runs INSIDE the compose stack as the `verify` service -- .github/test.sh is a
# shim that mints the caller JWTs and starts it with `docker compose run --rm
# verify`. It used to run on the host and drive each assertion as its own
# `docker compose run`, ~48 of them, each starting a fresh container that re-ran
# `npm install` over the same bind-mounted client directory before producing any
# output. In here the clients are invoked directly as node processes, so that
# cost is paid once at container start. Depending on zilla's health transitively
# covers the whole topology, since zilla already depends on every component.
#
# Assertions, all at the Zilla layer:
#   1. a url-elicitation-capable client initializes and negotiates 2025-11-25
#   2. a real MCP SDK client drives a url-mode elicitation round-trip end-to-end
#      through the gateway (elicitation/create mode:url + completion notification)
#   3. south_mcp_client_urlelicit forwards the caller's own JWT upstream
#      (options.authorization on a mcp(client) binding)
#   4. tools/list is filtered by the caller's JWT scopes: unauthorized toolkits
#      and tools are absent from the result, layered per binding hop
#      (mcp proxy toolkit routes, mcp-http per-tool route, mcp-openapi
#      OpenAPI-native per-operation security)
#   5. mcp-http's create_pr forwards call arguments as the request body via
#      with.body, scoped to exclude args already consumed by the :path
#   6. mcp-openapi's search_pets renames an argument via with.params before
#      building the request (options.specs.petstore.server also overrides
#      the OpenAPI document's declared server to the local mock)
#   7. mcp-http's pull_by_number resource template ({owner}/{repo}/{number})
#      is read end-to-end, with captured path params surfacing as ${params.x}
#   8. petstore__create_pet actually succeeds for a pets:write-scoped caller,
#      not just listed
#   9. petstore's featured_pets resource (a static, non-templated resource)
#      is read end-to-end
#  10. options.cache.tools.eager keeps tools/list short even for a fully
#      authorized caller: a cold tool (everything__get-sum) never appears
#      alongside the eagerly-matched ones and the synthesized search-family
#      tools (zilla__search_tools, zilla__describe_tool, zilla__execute_tool)
#  11. the cold tool is still discoverable by keyword through the synthesized
#      zilla__search_tools tool
#  12. the cold tool's full definition (schema included) is resolvable by
#      exact name through zilla__describe_tool
#  13. the cold tool is invokable by name through zilla__execute_tool, with
#      the same result as calling it directly
#  14. the cold tool is still directly callable by name -- "cold" only ever
#      changes what tools/list reports, never what tools/call accepts
#  15. kafka__produce_message writes a record to a real, single-node KRaft Kafka
#      broker (mcp-kafka kind:client's own generated cache_client/client/
#      tcp_client pipeline, not the engine's test double)
#  16. kafka__consume_messages reads that same record back, round-tripping the exact
#      value through the real broker
#  17. kafka_sr__register_schema registers a real schema against a
#      real Karapace instance (mcp-schema-registry kind:client's own
#      generated composite, not a mock), with ${result.id} interpolated
#      into the tool's summary
#  18. kafka_sr__list_subjects and describe_subject confirm the
#      registration is real, persisted Karapace state
#  19. kafka_sr__get_schema reads the schema back by version, with
#      two result fields (${result.id}, ${result.version}) interpolated
#      at once
#  20. kafka_sr__set_compatibility then get_compatibility round-trip
#      a compatibility level -- a fresh subject has none configured until
#      set_compatibility is called at least once
#  21. kafka_sr__check_compatibility validates a schema against the
#      configured compatibility level
#  22. mcp-schema-registry's own routes[].guarded layers a tool-specific
#      scope (kafka_sr:write) under the toolkit-level scope
#      (kafka_sr:tools) for register_schema only -- no OpenAPI
#      security scheme is involved, unlike petstore's create_pet
#  23. kafka_connect__list_connector_plugins lists the bundled FileStream
#      source/sink connector plugins from a real Kafka Connect distributed
#      worker (mcp-kafka-connect kind:client's own generated composite
#      -- mcp-openapi -> mcp-http -> http_client -- not a mock)
#  24. kafka_connect__create_connector creates a real FileStreamSourceConnector
#      reading a file inside the worker container, gated by its own
#      tool-specific kafka_connect:admin scope layered under the
#      toolkit-level kafka_connect:tools scope -- the same layering
#      mechanism as register_schema/kafka_sr:write, demonstrated on a
#      third toolkit
#  25. kafka_connect__list_connectors and describe_connector confirm the
#      connector created above is real, running worker state, not just
#      an echoed request
#  26. kafka_connect__describe_connector_status reports the connector and
#      its one task as RUNNING against the real worker
#  27. kafka_connect__pause_connector then describe_connector_status
#      confirm the connector transitions to PAUSED on the real worker
#  28. kafka_connect__resume_connector then describe_connector_status
#      confirm it transitions back to RUNNING
#  29. kafka_connect__restart_connector succeeds against the running
#      connector, sharing pause_connector/resume_connector's
#      kafka_connect:admin-gated route
#  30. kafka_connect__delete_connector removes the connector, confirmed by
#      list_connectors reporting none remaining, sharing create_connector's
#      kafka_connect:admin-gated route
#  31. kafka__create_topics creates a real topic on the same broker
#      (mcp-kafka kind:client's own generated pipeline, not the engine's
#      test double), gated by its own tool-specific kafka:admin scope
#      layered under the toolkit-level kafka:tools scope -- the same
#      layering mechanism as register_schema/kafka_sr:write and
#      kafka_connect's admin-tier tools, demonstrated on a fourth toolkit
#  32. kafka__delete_topics deletes that same topic on the same broker,
#      sharing create_topics' route (one `when` list, two `tool` entries)
#      and its kafka:admin scope -- both are structural, admin-risk
#      mutations, so one route/guard covers both instead of duplicating it
#  33. kafka__describe_topic_configs reads back the real broker's effective
#      config for the orders topic, including a config every topic
#      carries by default -- needs no scope beyond the toolkit-level
#      kafka:tools guard already exercised by consume_messages
#  34. kafka__alter_topic_configs changes the orders topic's cleanup.policy on
#      the same real broker, sharing create_topics/delete_topics' route
#      and kafka:admin scope -- a third structural, admin-risk mutation
#      coalesced onto the same route/guard
#  35. kafka__list_topics lists the real topics on the same broker, gated
#      by only the toolkit-level kafka:tools scope (no admin/write needed,
#      same tier as consume_messages)
#  36. kafka__describe_topic describes the orders topic by name, reporting
#      its partitions/leader/replicas/isr from the same real broker
#  37. kafka__cluster_overview summarizes the same broker's topic/broker
#      counts, sharing list_topics/describe_topic's read-only route
#  38. kafka__list_brokers lists the real, single-node KRaft broker started
#      by this example -- proving KafkaApiDescribeClusterClient's shared
#      DescribeCluster request/response path (not the older, fully-decoded
#      mechanism used elsewhere in binding-kafka) reaches a real broker
#  39. kafka__describe_cluster reports that same broker as its controller --
#      both tools share one route/guard (kafka:tools only, no admin scope),
#      being read-only cluster introspection like consume_messages
#  40. kafka__describe_consumer_group, called against a group id that has
#      never committed an offset, reports real broker state "Dead" -- Kafka's
#      actual behavior for a group that does not yet exist, not an error --
#      needing only the toolkit-level kafka:tools scope
#  41. kafka__describe_consumer_group_lag, called against that same
#      never-used group, sequences a real OffsetFetch then ListOffsets and
#      reports total lag 0 with an empty partitions array -- a group with no
#      committed offsets has no lag to report, not an error -- sharing
#      describe_consumer_group's read-only route/scope
#  42. kafka__list_consumer_groups succeeds against the real broker, needing
#      only the toolkit-level kafka:tools scope
#  43. kafka__create_acls grants a real ACL on a dedicated test resource,
#      gated by its own kafka:acls scope -- deliberately distinct from
#      kafka:admin per KIP-1318's "destructive-mutate" classification of ACL
#      mutation (see the routes[] comment in etc/zilla.yaml)
#  44. kafka__list_acls reads that same ACL back from the real broker,
#      needing only the toolkit-level kafka:tools scope like describe_topic_configs
#  45. kafka__delete_acls revokes the ACL granted above, sharing
#      create_acls' route and kafka:acls scope
#  46. a real MCP SDK client subscribes to an everything resource, triggers
#      the everything server's own toggle-subscriber-updates tool, and
#      receives a relayed notifications/resources/updated -- exercising
#      resources/subscribe, resources/unsubscribe, and the notification
#      pass-through across mcp(server), mcp(proxy), and mcp(client)
#      (aklivity/zilla#2220)
#
# kafka__reset_offsets' own OffsetCommit stage (the third hop of its
# FindCoordinator -> DescribeGroups -> OffsetCommit flow) is a known,
# tracked gap in this example's real-broker coverage -- see the note above
# the kafka__list_consumer_groups check below.
#
# Streamable HTTP responses arrive as Server-Sent Events; checks grep the
# streamed body / client output rather than asserting exact-string equality.
set -x

. /test-lib.sh


# Wall-clock per assertion, accumulated here and reported as one block at the end
# rather than printed where it is measured. The log is read back tail-first under a
# hard line cap, and this script emits enough output that everything before the last
# few assertions falls outside that window -- which is why the subscribe round-trip
# could be measured from CI and the url-elicit one could not. Reporting at the end
# puts every timing in the tail. Whole seconds, because the delays worth naming are
# tens of seconds and `date +%N` is not portable to the busybox date in here.
TIMINGS=""
timed() {
  _label=$1
  shift
  _start=$(date +%s)
  "$@"
  _rc=$?
  TIMINGS="$TIMINGS$(( $(date +%s) - _start )) $_label
"
  return $_rc
}

report_timings() {
  if [ -n "$TIMINGS" ]
  then
    echo "=== elapsed by assertion ==="
    printf '%s' "$TIMINGS" |
      awk '{ total += $1 + 0; printf "%5ds  %s\n", $1 + 0, $2 } END { printf "%5ds  total\n", total }'
  fi
}

EXIT=0
PORT="7114"
INITIALIZE='{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-11-25","capabilities":{"elicitation":{"url":{}}},"clientInfo":{"name":"zilla-mcp-proxy-test","version":"0.0.1"}}}'

echo "# Testing mcp.proxy"
echo "PORT=$PORT"

# In-network hostname, not the published localhost port: this runs on the
# compose network alongside everything else.
MCP_ENDPOINT="http://zilla:$PORT/mcp"

# Every assertion below drives an MCP client, and each one blocks on network I/O
# against the gateway. Bounding each call turns a client that never gets its
# reply into an ordinary non-zero exit -- which retry_until retries and the
# existing comparison block reports as a normal failure -- instead of pinning
# the job until the workflow step cap with no logs to show for it. The bound is
# on the node process itself, so a timeout kills the client outright.
MCP_RUN_TIMEOUT_S=${MCP_RUN_TIMEOUT_S:-60}

# The subscribe client is the one call whose own budget legitimately exceeds the
# default: it waits TIMEOUT_MS (20s) for the update notification and retries that
# internally up to RETRIES (5) times with RETRY_DELAY_MS (3s) between, so ~115s of
# waiting is expected behaviour rather than a hang.
MCP_SUBSCRIBE_TIMEOUT_S=${MCP_SUBSCRIBE_TIMEOUT_S:-180}

# mcp_run [-e VAR=VALUE]... <client> [args...]
#
# Same calling convention the `docker compose run --rm --no-deps` form had, so
# every assertion below is unchanged: leading -e pairs become environment for the
# client, and <client> still names the compose service it used to start. Those
# services remain in compose.yaml as the documented interactive entry points
# (README drives them by hand) -- this maps the name onto the same client script,
# run as a node process in this container instead of a new one.
#
# The -e pairs are exported inside a subshell so a value never leaks into the
# next call, and so word-splitting cannot mangle a JSON argument. xtrace is off
# in here because every caller captures this with 2>&1 to grep the client's
# output, so tracing the parsing would land inside the string being asserted on.
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
    urlelicit-client)          _client=/work/url-elicit/client.mjs ;;
    resource-subscribe-client) _client=/work/resource-subscribe-client/client.mjs ;;
    *) echo "mcp_run: unknown client \"$1\"" >&2; exit 2 ;;
    esac
    shift

    export MCP_URL="${MCP_URL:-$MCP_ENDPOINT}"
    exec timeout "${MCP_TIMEOUT_S:-$MCP_RUN_TIMEOUT_S}" node "$_client" "$@"
  )
}

# service_logs <service>
#
# stdout+stderr of another container in this compose project, standing in for the
# `docker compose logs <service>` the host script used to run. Two assertions need
# it and have no other observation point: the urlelicit mock records the forwarded
# bearer token and the petstore mock records the renamed query argument. Reading
# them is the only reason this service mounts the docker socket.
#
# The project name comes from this container's own compose label rather than a
# guess, so a non-default project name resolves correctly.
service_logs() {
  docker logs "$(docker ps -aq \
      --filter "label=com.docker.compose.project=$COMPOSE_PROJECT" \
      --filter "label=com.docker.compose.service=$1")" 2>&1
}

COMPOSE_PROJECT=$(docker inspect -f '{{ index .Config.Labels "com.docker.compose.project" }}' "$(hostname)")

# retry_deadline <total_seconds> <delay_seconds> <command...>
#
# retry_until bounds attempts, not time, so its real cost is attempts x how long
# the probe takes to fail -- and that is set by the failure path, not by whoever
# wrote the count. `retry_until 60 5` over the cache_hydrated probe reads like a
# short gate but bills ~37s per failed attempt, so it consumed the whole 30m
# Execute Test budget at attempt 50 without ever reaching the assertion it guards.
# A deadline states what the gate actually means -- wait this long for the cache to
# hydrate -- and costs the same whether the probe fails fast or slow.
#
# As in retry_until, the first retry is immediate and the delay applies from the
# second retry onward: the probe's own duration is already the settle time, so a
# gate that needs exactly one retry should not also pay <delay_seconds> to
# re-observe a condition that has since become true.
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

# 7m per hydration gate, so the two of them cannot crowd out the assertions between
# them inside the step cap. Comfortably longer than a healthy hydrate, which lands
# in seconds.
CACHE_HYDRATE_TIMEOUT_S=${CACHE_HYDRATE_TIMEOUT_S:-420}

# The JWTs the authn_jwt guard validates are minted by .github/test.sh before
# this container starts and arrive as environment, one per scope combination
# under test. They are signed by jwt-cli, which is a container of its own with
# private.pem mounted, so it is not something this image can run -- keeping the
# minting in the shim avoids reaching back out to the docker daemon for it.
# `scope` is a jwt guard `roles` claim: a space-separated list, matched against
# the roles each `guarded:` route requires.
JWT_NONE=""

for _var in JWT_URLELICIT JWT_PARTIAL JWT_FULL
do
  if [ -z "$(eval echo \$"$_var")" ]
  then
    echo "$_var must be set -- .github/test.sh mints it before starting this container" >&2
    exit 1
  fi
done

# WHEN: a url-elicitation-capable client initializes against the gateway
# THEN: the gateway negotiates protocol version 2025-11-25 in the response
# retry until the mcp route is live and negotiates the protocol version
initialize_mcp() {
  INIT_BODY=$(curl -sS -N --max-time 10 \
      -X POST "$MCP_ENDPOINT" \
      -H "Content-Type: application/json" \
      -H "Accept: application/json, text/event-stream" \
      -d "$INITIALIZE")
  echo "$INIT_BODY" | grep -q '"protocolVersion":"2025-11-25"'
}
timed initialize_mcp retry_until 10 3 initialize_mcp
echo INIT_BODY="$INIT_BODY"
if echo "$INIT_BODY" | grep -q '"protocolVersion":"2025-11-25"'; then
  echo ✅ initialize negotiated 2025-11-25
else
  fail "initialize did not negotiate 2025-11-25"
fi

list_tools() {
  _token=$1
  mcp_run -e JWT_TOKEN="$_token" -e MCP_URL="http://zilla:$PORT/mcp" \
      tools-list-client 2>&1
}

# north_mcp_proxy aggregates each south binding's real capabilities (learned
# from that binding's own initialize handshake) and hydrates its tools /
# resources / prompts caches asynchronously, both settling only a short time
# after startup -- see "Observe the cache" and the resources.subscribe note
# in the README. Every assertion below that reads tools/list, a cached
# search/describe/execute result, or resources/subscribe's capability is
# racing that same one-time warm-up, not its own independent condition, so
# wait for it here, once, instead of retrying each assertion individually.
full_toolset_present() {
  echo "$TOOLS_FULL" | grep -q '^everything__' &&
    # the everything toolkit's resources have their own hydration lag distinct
    # from its tools -- observed to still be empty for a moment after
    # everything__* tools and every other toolkit are already fully listed
    echo "$TOOLS_FULL" | grep -qE '^resource:everything\+' &&
    echo "$TOOLS_FULL" | grep -q '^urlelicit__authorize$' &&
    echo "$TOOLS_FULL" | grep -q '^github__create_pr$' &&
    echo "$TOOLS_FULL" | grep -q '^petstore__list_pets$' &&
    echo "$TOOLS_FULL" | grep -q '^petstore__search_pets$' &&
    echo "$TOOLS_FULL" | grep -q '^petstore__create_pet$' &&
    echo "$TOOLS_FULL" | grep -q '^resource:petstore+/pets/featured$' &&
    echo "$TOOLS_FULL" | grep -q '^template:petstore+/pets/{petId}$' &&
    echo "$TOOLS_FULL" | grep -q '^template:github+pr://{owner}/{repo}/{number}$' &&
    echo "$TOOLS_FULL" | grep -q '^kafka_sr__list_subjects$' &&
    echo "$TOOLS_FULL" | grep -q '^kafka_sr__register_schema$' &&
    echo "$TOOLS_FULL" | grep -q '^kafka_connect__list_connector_plugins$' &&
    echo "$TOOLS_FULL" | grep -q '^kafka_connect__list_connectors$' &&
    echo "$TOOLS_FULL" | grep -q '^kafka_connect__describe_connector$' &&
    echo "$TOOLS_FULL" | grep -q '^kafka_connect__create_connector$' &&
    echo "$TOOLS_FULL" | grep -q '^kafka_connect__delete_connector$' &&
    echo "$TOOLS_FULL" | grep -q '^kafka__produce_message$' &&
    echo "$TOOLS_FULL" | grep -q '^kafka__consume_messages$' &&
    echo "$TOOLS_FULL" | grep -q '^kafka__create_topics$' &&
    echo "$TOOLS_FULL" | grep -q '^kafka__delete_topics$' &&
    echo "$TOOLS_FULL" | grep -q '^kafka__describe_topic_configs$' &&
    echo "$TOOLS_FULL" | grep -q '^kafka__describe_broker_configs$' &&
    echo "$TOOLS_FULL" | grep -q '^kafka__list_acls$' &&
    echo "$TOOLS_FULL" | grep -q '^kafka__create_acls$' &&
    echo "$TOOLS_FULL" | grep -q '^kafka__delete_acls$' &&
    echo "$TOOLS_FULL" | grep -q '^kafka__alter_topic_configs$' &&
    echo "$TOOLS_FULL" | grep -q '^kafka__alter_broker_configs$' &&
    echo "$TOOLS_FULL" | grep -q '^kafka__list_topics$' &&
    echo "$TOOLS_FULL" | grep -q '^kafka__describe_topic$' &&
    echo "$TOOLS_FULL" | grep -q '^kafka__cluster_overview$' &&
    echo "$TOOLS_FULL" | grep -q '^kafka__list_brokers$' &&
    echo "$TOOLS_FULL" | grep -q '^kafka__describe_cluster$' &&
    echo "$TOOLS_FULL" | grep -q '^kafka__list_consumer_groups$' &&
    echo "$TOOLS_FULL" | grep -q '^kafka__describe_consumer_group$' &&
    echo "$TOOLS_FULL" | grep -q '^kafka__describe_consumer_group_lag$' &&
    echo "$TOOLS_FULL" | grep -q '^kafka__reset_offsets$'
}
cache_hydrated() {
  CACHE_INIT_BODY=$(curl -sS -N --max-time 10 \
      -X POST "$MCP_ENDPOINT" \
      -H "Content-Type: application/json" \
      -H "Accept: application/json, text/event-stream" \
      -d "$INITIALIZE")
  echo "$CACHE_INIT_BODY" | grep -q '"subscribe":true' &&
    TOOLS_FULL=$(list_tools "$JWT_FULL") &&
    full_toolset_present
}
timed cache_hydrated retry_deadline "$CACHE_HYDRATE_TIMEOUT_S" 5 cache_hydrated
if echo "$CACHE_INIT_BODY" | grep -q '"subscribe":true' && full_toolset_present; then
  echo "✅ tools/resources/prompts cache and resources.subscribe capability are hydrated"
else
  fail "cache never finished hydrating -- every cache-backed assertion below would just be racing this"
fi

# WHEN: a real MCP SDK client (method-first envelopes, elicitation.url capability)
#       calls the urlelicit toolkit's authorize tool through the gateway, with
#       a JWT scoped to exactly what the urlelicit toolkit route requires
# THEN: Zilla relays the mode:url elicitation/create request and the subsequent
#       notifications/elicitation/complete back to the client. This also
#       exercises south_mcp_client_urlelicit's own options.authorization,
#       which forwards this same caller JWT on to the urlelicit mock -- see
#       "Forward the caller's own credential upstream" in the README.
relay_elicitation() {
  ELICIT_OUT=$(mcp_run \
      -e MCP_URL="http://zilla:$PORT/mcp" \
      -e JWT_TOKEN="$JWT_URLELICIT" \
      urlelicit-client 2>&1)
  echo "$ELICIT_OUT" | grep -q 'OK url-mode elicitation relayed end-to-end'
}
timed elicitation retry_until 10 3 relay_elicitation
echo "$ELICIT_OUT"
if echo "$ELICIT_OUT" | grep -q 'OK url-mode elicitation relayed end-to-end'; then
  echo ✅ url-mode elicitation relayed end-to-end
else
  fail "url-mode elicitation not relayed end-to-end"
fi

# WHEN: the call above reaches the urlelicit mock through south_mcp_client_urlelicit
# THEN: the mock observes the caller's own JWT_URLELICIT bearer token, not some
#       separate service credential -- proving options.authorization on a
#       mcp(client) binding forwards the session's own credential upstream
URLELICIT_LOGS=$(service_logs urlelicit)
if echo "$URLELICIT_LOGS" | grep -q "authorization: Bearer $JWT_URLELICIT"; then
  echo "✅ south_mcp_client_urlelicit forwarded the caller's own JWT to urlelicit"
else
  fail "urlelicit did not observe the caller's forwarded JWT"
fi

# WHEN: a caller presents no JWT at all
# THEN: the ungated "everything" toolkit is listed, every guarded toolkit --
#       and its resources -- is not
TOOLS_NONE=$(list_tools "$JWT_NONE")
echo "TOOLS_NONE=$TOOLS_NONE"
if echo "$TOOLS_NONE" | grep -q '^everything__' &&
    ! echo "$TOOLS_NONE" | grep -q '^urlelicit__' &&
    ! echo "$TOOLS_NONE" | grep -q '^github__' &&
    ! echo "$TOOLS_NONE" | grep -q '^petstore__' &&
    ! echo "$TOOLS_NONE" | grep -q '^kafka_connect__' &&
    ! echo "$TOOLS_NONE" | grep -q '^kafka__' &&
    ! echo "$TOOLS_NONE" | grep -q 'petstore+' &&
    ! echo "$TOOLS_NONE" | grep -q 'github+'; then
  echo "✅ no token: only the ungated everything toolkit is listed"
else
  fail "no token: tools/list did not filter to only the everything toolkit"
fi

# WHEN: a caller has toolkit-level scopes (github:tools, petstore:tools,
#       kafka_sr:tools, kafka_connect:tools) but none of the finer-grained
#       operation scopes
# THEN: petstore__list_pets, both petstore resources, github's
#       pull_by_number template, kafka_sr__list_subjects, and
#       kafka_connect__list_connector_plugins are listed (none of them
#       declare an extra scope beyond toolkit access) but
#       petstore__create_pet, github__create_pr, kafka_sr__register_schema,
#       and kafka_connect__create_connector are not (they require
#       pets:write / github:pr:write / kafka_sr:write / kafka_connect:admin
#       respectively) -- proof that toolkit access alone does not imply
#       access to every tool/resource in it
TOOLS_PARTIAL=$(list_tools "$JWT_PARTIAL")
echo "TOOLS_PARTIAL=$TOOLS_PARTIAL"
if echo "$TOOLS_PARTIAL" | grep -q '^petstore__list_pets$' &&
    echo "$TOOLS_PARTIAL" | grep -q '^petstore__search_pets$' &&
    echo "$TOOLS_PARTIAL" | grep -q '^resource:petstore+/pets/featured$' &&
    echo "$TOOLS_PARTIAL" | grep -q '^template:petstore+/pets/{petId}$' &&
    echo "$TOOLS_PARTIAL" | grep -q '^template:github+pr://{owner}/{repo}/{number}$' &&
    echo "$TOOLS_PARTIAL" | grep -q '^kafka_sr__list_subjects$' &&
    echo "$TOOLS_PARTIAL" | grep -q '^kafka_connect__list_connector_plugins$' &&
    ! echo "$TOOLS_PARTIAL" | grep -q '^petstore__create_pet$' &&
    ! echo "$TOOLS_PARTIAL" | grep -q '^github__create_pr$' &&
    ! echo "$TOOLS_PARTIAL" | grep -q '^kafka_sr__register_schema$' &&
    ! echo "$TOOLS_PARTIAL" | grep -q '^kafka_connect__create_connector$' &&
    ! echo "$TOOLS_PARTIAL" | grep -q '^urlelicit__' &&
    ! echo "$TOOLS_PARTIAL" | grep -q '^kafka__'; then
  echo "✅ toolkit-only scope: sees list_pets, search_pets, list_subjects, list_connector_plugins, and all three read-only resources, but not create_pet, create_pr, register_schema, or create_connector"
else
  fail "toolkit-only scope did not layer as expected"
fi

# WHEN: a caller has every scope required by every guarded route
# THEN: every tool, resource, and resource template across every toolkit is listed
# $TOOLS_FULL was already populated (and validated) by cache_hydrated above --
# same token, same route, nothing about the response changes by re-fetching it
echo "TOOLS_FULL=$TOOLS_FULL"
if full_toolset_present; then
  echo "✅ full scope: every toolkit's tools and resources are listed"
else
  fail "full scope did not unlock every toolkit"
fi

# WHEN: that same fully authorized caller's tools/list is inspected for a
#       "cold" tool -- one options.cache.tools.eager does not explicitly match
# THEN: everything__get-sum never appears, even though the caller is
#       authorized for the everything toolkit and every other eager tool from
#       it (everything__echo) is listed -- proving eager, not authorization,
#       is what kept it out of this response
if echo "$TOOLS_FULL" | grep -q '^everything__echo$' &&
    ! echo "$TOOLS_FULL" | grep -q '^everything__get-sum$'; then
  echo "✅ options.cache.tools.eager kept the cold everything__get-sum tool out of tools/list"
else
  fail "everything__get-sum was listed despite not matching options.cache.tools.eager.match"
fi

# WHEN: that same caller calls zilla__search_tools for "sum"
# THEN: the cold everything__get-sum tool comes back in structuredContent.tools --
#       proving a tool omitted from tools/list is discoverable by keyword, not gone
SEARCH_OUT=$(mcp_run \
    -e JWT_TOKEN="$JWT_FULL" \
    -e MCP_URL="http://zilla:$PORT/mcp" \
    -e CALL_TOOL="zilla__search_tools" \
    -e CALL_ARGS='{"query":"sum"}' \
    tools-list-client 2>&1)
echo "SEARCH_OUT=$SEARCH_OUT"
if echo "$SEARCH_OUT" | grep -q 'everything__get-sum'; then
  echo "✅ zilla__search_tools surfaced the cold everything__get-sum tool by keyword"
else
  fail "zilla__search_tools did not surface everything__get-sum for query \"sum\""
fi

# WHEN: that same caller calls zilla__describe_tool for the cold tool found above
# THEN: the full cached definition (schema included) comes back -- the same
#       shape tools/list would show were it not cold, resolved by exact name
DESCRIBE_OUT=$(mcp_run \
    -e JWT_TOKEN="$JWT_FULL" \
    -e MCP_URL="http://zilla:$PORT/mcp" \
    -e CALL_TOOL="zilla__describe_tool" \
    -e CALL_ARGS='{"name":"everything__get-sum"}' \
    tools-list-client 2>&1)
echo "DESCRIBE_OUT=$DESCRIBE_OUT"
if echo "$DESCRIBE_OUT" | grep -q 'inputSchema' && echo "$DESCRIBE_OUT" | grep -q 'everything__get-sum'; then
  echo "✅ zilla__describe_tool resolved the cold everything__get-sum tool's full definition"
else
  fail "zilla__describe_tool did not resolve everything__get-sum's full definition"
fi

# WHEN: that same caller calls zilla__execute_tool naming the cold tool found above
# THEN: it actually invokes it -- the same result as calling everything__get-sum
#       directly, proving execute_tool dispatches through the real tools/call path
EXECUTE_OUT=$(mcp_run \
    -e JWT_TOKEN="$JWT_FULL" \
    -e MCP_URL="http://zilla:$PORT/mcp" \
    -e CALL_TOOL="zilla__execute_tool" \
    -e CALL_ARGS='{"name":"everything__get-sum","arguments":{"a":2,"b":3}}' \
    tools-list-client 2>&1)
echo "EXECUTE_OUT=$EXECUTE_OUT"
if echo "$EXECUTE_OUT" | grep -q 'The sum of 2 and 3 is 5'; then
  echo "✅ zilla__execute_tool invoked the cold everything__get-sum tool by name"
else
  fail "zilla__execute_tool did not successfully invoke everything__get-sum by name"
fi

# WHEN: that same caller calls everything__get-sum directly by name, despite
#       it never appearing in tools/list above
# THEN: the call still succeeds -- options.cache.tools.eager only changes what
#       tools/list reports, never what tools/call accepts
COLD_CALL_OUT=$(mcp_run \
    -e JWT_TOKEN="$JWT_FULL" \
    -e MCP_URL="http://zilla:$PORT/mcp" \
    -e CALL_TOOL="everything__get-sum" \
    -e CALL_ARGS='{"a":2,"b":3}' \
    tools-list-client 2>&1)
echo "COLD_CALL_OUT=$COLD_CALL_OUT"
if echo "$COLD_CALL_OUT" | grep -q 'The sum of 2 and 3 is 5'; then
  echo "✅ everything__get-sum, though cold, still succeeded when called directly by name"
else
  fail "everything__get-sum did not succeed when called directly despite being cold, not unauthorized"
fi

# WHEN: an authorized caller calls github__create_pr with title/head/base
# THEN: those arguments reach the ghapi mock as the JSON request body (not
#       just owner/repo, which are consumed by the :path template) --
#       verifying with.body still forwards the call arguments even though
#       owner/repo are excluded from the body schema. The result summary
#       template "...${result.title}" surfaces ghapi's echoed title back
#       through the tool call result, which is what this grep observes.
call_create_pr() {
  CREATE_PR_OUT=$(mcp_run \
      -e JWT_TOKEN="$JWT_FULL" \
      -e MCP_URL="http://zilla:$PORT/mcp" \
      -e CALL_TOOL="github__create_pr" \
      -e CALL_ARGS='{"owner":"acme","repo":"widget","title":"Add feature","head":"feature","base":"main"}' \
      tools-list-client 2>&1)
  echo "$CREATE_PR_OUT" | grep -q 'Add feature'
}
timed create_pr retry_until 5 3 call_create_pr
echo "CREATE_PR_OUT=$CREATE_PR_OUT"
if echo "$CREATE_PR_OUT" | grep -q 'Add feature'; then
  echo "✅ github__create_pr forwarded title/head/base to ghapi as the request body"
else
  fail "github__create_pr did not forward the call arguments as the request body"
fi

# WHEN: an authorized caller calls petstore__search_pets with {"category":"cat"}
# THEN: the petstore mock observes ?tag=cat, not ?category=cat -- verifying
#       with.params.tag: "${args.category}" renamed the argument back to the
#       OpenAPI parameter's own name before building the request
call_search_pets() {
  mcp_run \
      -e JWT_TOKEN="$JWT_FULL" \
      -e MCP_URL="http://zilla:$PORT/mcp" \
      -e CALL_TOOL="petstore__search_pets" \
      -e CALL_ARGS='{"category":"cat"}' \
      tools-list-client >/dev/null 2>&1
  PETSTORE_LOGS=$(service_logs petstore)
  echo "$PETSTORE_LOGS" | grep -q 'search_pets query: {"tag":"cat"}'
}
timed search_pets retry_deadline 90 3 call_search_pets
echo "PETSTORE_LOGS=$PETSTORE_LOGS"
if echo "$PETSTORE_LOGS" | grep -q 'search_pets query: {"tag":"cat"}'; then
  echo "✅ petstore__search_pets renamed category -> tag via with.params"
else
  fail "petstore__search_pets did not rename the argument as configured"
fi

read_resource() {
  _token=$1
  _uri=$2
  mcp_run -e JWT_TOKEN="$_token" -e MCP_URL="http://zilla:$PORT/mcp" \
      -e READ_RESOURCE="$_uri" \
      tools-list-client 2>&1
}

# WHEN: a github:tools-scoped caller reads the pull_by_number resource template
#       at a concrete URI (params substituted directly into the aggregated
#       "toolkit+uri" address, no separate tools/call involved)
# THEN: the seeded pull request comes back -- proving ${owner}/${repo}/${number}
#       captured from the URI reached ghapi as ${params.x} in :path, and that
#       this read-only resource needed no scope beyond github:tools
read_pull_by_number() {
  PULL_OUT=$(read_resource "$JWT_PARTIAL" "github+pr://acme/widget/42")
  echo "$PULL_OUT" | grep -q 'Seed data for the pull_by_number resource demo'
}
timed pull_by_number retry_until 5 3 read_pull_by_number
echo "PULL_OUT=$PULL_OUT"
if echo "$PULL_OUT" | grep -q 'Seed data for the pull_by_number resource demo'; then
  echo "✅ github+pr://acme/widget/42 read end-to-end via the pull_by_number template"
else
  fail "pull_by_number resource template did not read through as configured"
fi

# WHEN: a petstore:tools-scoped caller reads the static featured_pets resource
# THEN: the seeded featured pet (Bramble) comes back -- proving a resource with
#       no {param} in its uri reads end-to-end same as a templated one
read_featured_pets() {
  FEATURED_OUT=$(read_resource "$JWT_PARTIAL" "petstore+/pets/featured")
  echo "$FEATURED_OUT" | grep -q 'Bramble'
}
timed featured_pets retry_until 10 5 read_featured_pets
echo "FEATURED_OUT=$FEATURED_OUT"
if echo "$FEATURED_OUT" | grep -q 'Bramble'; then
  echo "✅ petstore+/pets/featured read end-to-end"
else
  fail "petstore+/pets/featured did not read through as configured"
fi

# WHEN: a pets:write-scoped caller calls petstore__create_pet
# THEN: the call actually succeeds against the petstore mock (not just listed
#       as available) -- the mcp-openapi OpenAPI-native security requirement
#       permits the call, and the auto-derived request/response schemas round-trip
call_create_pet() {
  CREATE_PET_OUT=$(mcp_run \
      -e JWT_TOKEN="$JWT_FULL" \
      -e MCP_URL="http://zilla:$PORT/mcp" \
      -e CALL_TOOL="petstore__create_pet" \
      -e CALL_ARGS='{"name":"Nibbles","tag":"hamster"}' \
      tools-list-client 2>&1)
  echo "$CREATE_PET_OUT" | grep -q 'Nibbles'
}
timed create_pet retry_until 5 3 call_create_pet
echo "CREATE_PET_OUT=$CREATE_PET_OUT"
if echo "$CREATE_PET_OUT" | grep -q 'Nibbles'; then
  echo "✅ petstore__create_pet succeeded for a pets:write-scoped caller"
else
  fail "petstore__create_pet did not succeed as expected"
fi

SR_SUBJECT="orders-value"
SR_SCHEMA='{\"type\":\"record\",\"name\":\"Order\",\"fields\":[{\"name\":\"id\",\"type\":\"string\"}]}'

# WHEN: a kafka_sr:write-scoped caller calls kafka_sr__register_schema
# THEN: the schema is registered against the real Karapace instance (not a
#       mock) -- mcp-schema-registry kind:client's own generated composite
#       (mcp-openapi -> mcp-http -> http_client) talks to an actual Schema
#       Registry end to end, and the tool's summary interpolates the id
#       Karapace actually assigned via ${result.id}
call_register_schema() {
  REGISTER_OUT=$(mcp_run \
      -e JWT_TOKEN="$JWT_FULL" \
      -e MCP_URL="http://zilla:$PORT/mcp" \
      -e CALL_TOOL="kafka_sr__register_schema" \
      -e CALL_ARGS="{\"subject\":\"$SR_SUBJECT\",\"schemaType\":\"AVRO\",\"schema\":\"$SR_SCHEMA\"}" \
      tools-list-client 2>&1)
  echo "$REGISTER_OUT" | grep -q 'Registered schema with id'
}
timed register_schema retry_until 10 3 call_register_schema
echo "REGISTER_OUT=$REGISTER_OUT"
if echo "$REGISTER_OUT" | grep -q 'Registered schema with id'; then
  echo "✅ kafka_sr__register_schema registered a real schema against Karapace"
else
  fail "kafka_sr__register_schema did not succeed against Karapace"
fi

# WHEN: that same caller calls kafka_sr__list_subjects and describe_subject
# THEN: the subject registered above comes back as real, persisted Karapace
#       state -- not just an echo of the register call
call_list_subjects() {
  LIST_SUBJECTS_OUT=$(mcp_run \
      -e JWT_TOKEN="$JWT_FULL" \
      -e MCP_URL="http://zilla:$PORT/mcp" \
      -e CALL_TOOL="kafka_sr__list_subjects" \
      tools-list-client 2>&1)
  echo "$LIST_SUBJECTS_OUT" | grep -q "$SR_SUBJECT"
}
timed list_subjects retry_until 10 3 call_list_subjects
echo "LIST_SUBJECTS_OUT=$LIST_SUBJECTS_OUT"
if echo "$LIST_SUBJECTS_OUT" | grep -q "$SR_SUBJECT"; then
  echo "✅ kafka_sr__list_subjects saw the registered subject in real Karapace state"
else
  fail "kafka_sr__list_subjects did not see the registered subject"
fi

call_describe_subject() {
  DESCRIBE_SUBJECT_OUT=$(mcp_run \
      -e JWT_TOKEN="$JWT_FULL" \
      -e MCP_URL="http://zilla:$PORT/mcp" \
      -e CALL_TOOL="kafka_sr__describe_subject" \
      -e CALL_ARGS="{\"subject\":\"$SR_SUBJECT\"}" \
      tools-list-client 2>&1)
  echo "$DESCRIBE_SUBJECT_OUT" | grep -q '\[1\]'
}
timed describe_subject retry_until 10 3 call_describe_subject
echo "DESCRIBE_SUBJECT_OUT=$DESCRIBE_SUBJECT_OUT"
if echo "$DESCRIBE_SUBJECT_OUT" | grep -q '\[1\]'; then
  echo "✅ kafka_sr__describe_subject listed version 1 of the registered subject"
else
  fail "kafka_sr__describe_subject did not list the registered version"
fi

# WHEN: that same caller calls kafka_sr__get_schema for the registered
#       subject/version
# THEN: the schema is read back, with two result fields (${result.id},
#       ${result.version}) interpolated into the summary at once
call_get_schema() {
  GET_SCHEMA_OUT=$(mcp_run \
      -e JWT_TOKEN="$JWT_FULL" \
      -e MCP_URL="http://zilla:$PORT/mcp" \
      -e CALL_TOOL="kafka_sr__get_schema" \
      -e CALL_ARGS="{\"subject\":\"$SR_SUBJECT\",\"version\":\"latest\"}" \
      tools-list-client 2>&1)
  echo "$GET_SCHEMA_OUT" | grep -q 'Retrieved schema id 1, version 1'
}
timed get_schema retry_until 10 3 call_get_schema
echo "GET_SCHEMA_OUT=$GET_SCHEMA_OUT"
if echo "$GET_SCHEMA_OUT" | grep -q 'Retrieved schema id 1, version 1'; then
  echo "✅ kafka_sr__get_schema read the schema back with both result fields interpolated"
else
  fail "kafka_sr__get_schema did not read the schema back as expected"
fi

# WHEN: that same caller calls kafka_sr__set_compatibility then
#       get_compatibility for the registered subject
# THEN: a freshly registered subject has no compatibility level configured
#       until set_compatibility establishes one -- a real Karapace behavior
#       this example surfaces rather than papering over with a default
call_set_compatibility() {
  SET_COMPAT_OUT=$(mcp_run \
      -e JWT_TOKEN="$JWT_FULL" \
      -e MCP_URL="http://zilla:$PORT/mcp" \
      -e CALL_TOOL="kafka_sr__set_compatibility" \
      -e CALL_ARGS="{\"subject\":\"$SR_SUBJECT\",\"compatibility\":\"FULL\"}" \
      tools-list-client 2>&1)
  echo "$SET_COMPAT_OUT" | grep -q 'Compatibility level set to FULL'
}
timed set_compatibility retry_until 10 3 call_set_compatibility
echo "SET_COMPAT_OUT=$SET_COMPAT_OUT"
if echo "$SET_COMPAT_OUT" | grep -q 'Compatibility level set to FULL'; then
  echo "✅ kafka_sr__set_compatibility set a compatibility level on the registered subject"
else
  fail "kafka_sr__set_compatibility did not succeed as expected"
fi

call_get_compatibility() {
  GET_COMPAT_OUT=$(mcp_run \
      -e JWT_TOKEN="$JWT_FULL" \
      -e MCP_URL="http://zilla:$PORT/mcp" \
      -e CALL_TOOL="kafka_sr__get_compatibility" \
      -e CALL_ARGS="{\"subject\":\"$SR_SUBJECT\"}" \
      tools-list-client 2>&1)
  echo "$GET_COMPAT_OUT" | grep -q 'Compatibility level is FULL'
}
timed get_compatibility retry_until 10 3 call_get_compatibility
echo "GET_COMPAT_OUT=$GET_COMPAT_OUT"
if echo "$GET_COMPAT_OUT" | grep -q 'Compatibility level is FULL'; then
  echo "✅ kafka_sr__get_compatibility read back the level set above"
else
  fail "kafka_sr__get_compatibility did not succeed as expected"
fi

# WHEN: that same caller calls kafka_sr__check_compatibility against
#       the configured compatibility level
# THEN: the identical schema is reported compatible
call_check_compatibility() {
  CHECK_COMPAT_OUT=$(mcp_run \
      -e JWT_TOKEN="$JWT_FULL" \
      -e MCP_URL="http://zilla:$PORT/mcp" \
      -e CALL_TOOL="kafka_sr__check_compatibility" \
      -e CALL_ARGS="{\"subject\":\"$SR_SUBJECT\",\"version\":\"1\",\"schemaType\":\"AVRO\",\"schema\":\"$SR_SCHEMA\"}" \
      tools-list-client 2>&1)
  echo "$CHECK_COMPAT_OUT" | grep -q 'Compatibility check result: true'
}
timed check_compatibility retry_until 10 3 call_check_compatibility
echo "CHECK_COMPAT_OUT=$CHECK_COMPAT_OUT"
if echo "$CHECK_COMPAT_OUT" | grep -q 'Compatibility check result: true'; then
  echo "✅ kafka_sr__check_compatibility reported the identical schema as compatible"
else
  fail "kafka_sr__check_compatibility did not succeed as expected"
fi

KC_CONNECTOR="file-source-demo"

# WHEN: a kafka_connect:tools-scoped caller calls kafka_connect__list_connector_plugins
# THEN: the bundled FileStream source/sink connector plugins come back from a
#       real Kafka Connect distributed worker (mcp-kafka-connect kind:client's
#       own generated composite -- mcp-openapi -> mcp-http -> http_client --
#       not a mock), proving the worker's plugin.path resolved the broker
#       distribution's own libs/ directory
#
# kafka-connect's healthcheck (`nc -z 127.0.0.1 8083`) only proves the REST
# port is listening -- it says nothing about the worker's own async
# plugin.path scan, which populates /connector-plugins on its own schedule
# and can still be running well after the port accepts connections. This is
# the first call in the script to touch Kafka Connect, so it is the one place
# that race is actually observable; give it the same generous, one-time
# startup budget as cache_hydrated rather than the standard retry budget used
# once the worker is already known to be fully up.
call_kafka_connect_list_plugins() {
  KC_LIST_PLUGINS_OUT=$(mcp_run \
      -e JWT_TOKEN="$JWT_FULL" \
      -e MCP_URL="http://zilla:$PORT/mcp" \
      -e CALL_TOOL="kafka_connect__list_connector_plugins" \
      tools-list-client 2>&1)
  echo "$KC_LIST_PLUGINS_OUT" | grep -q 'FileStreamSourceConnector'
}
timed kafka_connect_list_plugins retry_until 30 5 call_kafka_connect_list_plugins
echo "KC_LIST_PLUGINS_OUT=$KC_LIST_PLUGINS_OUT"
if echo "$KC_LIST_PLUGINS_OUT" | grep -q 'FileStreamSourceConnector'; then
  echo "✅ kafka_connect__list_connector_plugins listed the bundled FileStreamSourceConnector from the real worker"
else
  fail "kafka_connect__list_connector_plugins did not list the bundled FileStreamSourceConnector"
fi

# WHEN: a kafka_connect:admin-scoped caller calls kafka_connect__create_connector
#       to create a FileStreamSourceConnector reading /tmp/kc-source.txt, which
#       the kafka-connect service seeds itself before starting its worker (see
#       compose.yaml) -- so the file is in place before the REST port even opens,
#       and this script needs no way to write inside another container
# THEN: the connector is created against the real worker, gated by its own
#       tool-specific kafka_connect:admin scope layered under the
#       toolkit-level kafka_connect:tools scope -- the same layering
#       mechanism as register_schema/kafka_sr:write, demonstrated on a
#       third toolkit
call_kafka_connect_create_connector() {
  KC_CREATE_OUT=$(mcp_run \
      -e JWT_TOKEN="$JWT_FULL" \
      -e MCP_URL="http://zilla:$PORT/mcp" \
      -e CALL_TOOL="kafka_connect__create_connector" \
      -e CALL_ARGS="{\"name\":\"$KC_CONNECTOR\",\"config\":{\"connector.class\":\"org.apache.kafka.connect.file.FileStreamSourceConnector\",\"tasks.max\":\"1\",\"file\":\"/tmp/kc-source.txt\",\"topic\":\"connect-demo\"}}" \
      tools-list-client 2>&1)
  echo "$KC_CREATE_OUT" | grep -q "$KC_CONNECTOR"
}
timed kafka_connect_create_connector retry_until 10 3 call_kafka_connect_create_connector
echo "KC_CREATE_OUT=$KC_CREATE_OUT"
if echo "$KC_CREATE_OUT" | grep -q "$KC_CONNECTOR"; then
  echo "✅ kafka_connect__create_connector created a real connector on the real worker"
else
  fail "kafka_connect__create_connector did not succeed against the real worker"
fi

# WHEN: that same caller calls kafka_connect__list_connectors and describe_connector
# THEN: the connector created above comes back as real, running worker state --
#       not just an echo of the create call
call_kafka_connect_list_connectors() {
  KC_LIST_OUT=$(mcp_run \
      -e JWT_TOKEN="$JWT_FULL" \
      -e MCP_URL="http://zilla:$PORT/mcp" \
      -e CALL_TOOL="kafka_connect__list_connectors" \
      tools-list-client 2>&1)
  echo "$KC_LIST_OUT" | grep -q "$KC_CONNECTOR"
}
timed kafka_connect_list_connectors retry_until 10 3 call_kafka_connect_list_connectors
echo "KC_LIST_OUT=$KC_LIST_OUT"
if echo "$KC_LIST_OUT" | grep -q "$KC_CONNECTOR"; then
  echo "✅ kafka_connect__list_connectors saw the created connector in real worker state"
else
  fail "kafka_connect__list_connectors did not see the created connector"
fi

call_kafka_connect_describe_connector() {
  KC_DESCRIBE_OUT=$(mcp_run \
      -e JWT_TOKEN="$JWT_FULL" \
      -e MCP_URL="http://zilla:$PORT/mcp" \
      -e CALL_TOOL="kafka_connect__describe_connector" \
      -e CALL_ARGS="{\"connector\":\"$KC_CONNECTOR\"}" \
      tools-list-client 2>&1)
  echo "$KC_DESCRIBE_OUT" | grep -q 'FileStreamSourceConnector'
}
timed kafka_connect_describe_connector retry_until 10 3 call_kafka_connect_describe_connector
echo "KC_DESCRIBE_OUT=$KC_DESCRIBE_OUT"
if echo "$KC_DESCRIBE_OUT" | grep -q 'FileStreamSourceConnector'; then
  echo "✅ kafka_connect__describe_connector read back the real connector's configuration"
else
  fail "kafka_connect__describe_connector did not read back the connector's configuration"
fi

# WHEN: that same caller calls kafka_connect__describe_connector_status
# THEN: the connector and its one task report RUNNING against the real worker
call_kafka_connect_status_running() {
  KC_STATUS_OUT=$(mcp_run \
      -e JWT_TOKEN="$JWT_FULL" \
      -e MCP_URL="http://zilla:$PORT/mcp" \
      -e CALL_TOOL="kafka_connect__describe_connector_status" \
      -e CALL_ARGS="{\"connector\":\"$KC_CONNECTOR\"}" \
      tools-list-client 2>&1)
  echo "$KC_STATUS_OUT" | grep -q 'RUNNING'
}
timed kafka_connect_status_running retry_deadline 120 5 call_kafka_connect_status_running
echo "KC_STATUS_OUT=$KC_STATUS_OUT"
if echo "$KC_STATUS_OUT" | grep -q 'RUNNING'; then
  echo "✅ kafka_connect__describe_connector_status reported the real connector as RUNNING"
else
  fail "kafka_connect__describe_connector_status did not report RUNNING"
fi

# WHEN: that same caller calls kafka_connect__pause_connector then
#       describe_connector_status
# THEN: the real connector transitions to PAUSED
call_kafka_connect_pause() {
  KC_PAUSE_OUT=$(mcp_run \
      -e JWT_TOKEN="$JWT_FULL" \
      -e MCP_URL="http://zilla:$PORT/mcp" \
      -e CALL_TOOL="kafka_connect__pause_connector" \
      -e CALL_ARGS="{\"connector\":\"$KC_CONNECTOR\"}" \
      tools-list-client 2>&1)
  KC_STATUS_PAUSED_OUT=$(mcp_run \
      -e JWT_TOKEN="$JWT_FULL" \
      -e MCP_URL="http://zilla:$PORT/mcp" \
      -e CALL_TOOL="kafka_connect__describe_connector_status" \
      -e CALL_ARGS="{\"connector\":\"$KC_CONNECTOR\"}" \
      tools-list-client 2>&1)
  echo "$KC_STATUS_PAUSED_OUT" | grep -q 'PAUSED'
}
timed kafka_connect_pause retry_until 10 3 call_kafka_connect_pause
echo "KC_PAUSE_OUT=$KC_PAUSE_OUT"
echo "KC_STATUS_PAUSED_OUT=$KC_STATUS_PAUSED_OUT"
if echo "$KC_STATUS_PAUSED_OUT" | grep -q 'PAUSED'; then
  echo "✅ kafka_connect__pause_connector transitioned the real connector to PAUSED"
else
  fail "kafka_connect__pause_connector did not transition the real connector to PAUSED"
fi

# WHEN: that same caller calls kafka_connect__resume_connector then
#       describe_connector_status
# THEN: the real connector transitions back to RUNNING
call_kafka_connect_resume() {
  KC_RESUME_OUT=$(mcp_run \
      -e JWT_TOKEN="$JWT_FULL" \
      -e MCP_URL="http://zilla:$PORT/mcp" \
      -e CALL_TOOL="kafka_connect__resume_connector" \
      -e CALL_ARGS="{\"connector\":\"$KC_CONNECTOR\"}" \
      tools-list-client 2>&1)
  KC_STATUS_RESUMED_OUT=$(mcp_run \
      -e JWT_TOKEN="$JWT_FULL" \
      -e MCP_URL="http://zilla:$PORT/mcp" \
      -e CALL_TOOL="kafka_connect__describe_connector_status" \
      -e CALL_ARGS="{\"connector\":\"$KC_CONNECTOR\"}" \
      tools-list-client 2>&1)
  echo "$KC_STATUS_RESUMED_OUT" | grep -q 'RUNNING'
}
timed kafka_connect_resume retry_until 10 3 call_kafka_connect_resume
echo "KC_RESUME_OUT=$KC_RESUME_OUT"
echo "KC_STATUS_RESUMED_OUT=$KC_STATUS_RESUMED_OUT"
if echo "$KC_STATUS_RESUMED_OUT" | grep -q 'RUNNING'; then
  echo "✅ kafka_connect__resume_connector transitioned the real connector back to RUNNING"
else
  fail "kafka_connect__resume_connector did not transition the real connector back to RUNNING"
fi

# WHEN: that same caller calls kafka_connect__restart_connector
# THEN: the real, running connector restarts successfully, sharing
#       pause_connector/resume_connector's kafka_connect:admin-gated route
call_kafka_connect_restart() {
  KC_RESTART_OUT=$(mcp_run \
      -e JWT_TOKEN="$JWT_FULL" \
      -e MCP_URL="http://zilla:$PORT/mcp" \
      -e CALL_TOOL="kafka_connect__restart_connector" \
      -e CALL_ARGS="{\"connector\":\"$KC_CONNECTOR\"}" \
      tools-list-client 2>&1)
  KC_STATUS_RESTARTED_OUT=$(mcp_run \
      -e JWT_TOKEN="$JWT_FULL" \
      -e MCP_URL="http://zilla:$PORT/mcp" \
      -e CALL_TOOL="kafka_connect__describe_connector_status" \
      -e CALL_ARGS="{\"connector\":\"$KC_CONNECTOR\"}" \
      tools-list-client 2>&1)
  echo "$KC_STATUS_RESTARTED_OUT" | grep -q 'RUNNING'
}
timed kafka_connect_restart retry_until 10 3 call_kafka_connect_restart
echo "KC_RESTART_OUT=$KC_RESTART_OUT"
echo "KC_STATUS_RESTARTED_OUT=$KC_STATUS_RESTARTED_OUT"
if echo "$KC_STATUS_RESTARTED_OUT" | grep -q 'RUNNING'; then
  echo "✅ kafka_connect__restart_connector left the real connector RUNNING"
else
  fail "kafka_connect__restart_connector did not leave the real connector RUNNING"
fi

# WHEN: that same caller calls kafka_connect__delete_connector
# THEN: the real connector is removed, confirmed by list_connectors
#       reporting none remaining -- sharing create_connector's
#       kafka_connect:admin-gated route
call_kafka_connect_delete_connector() {
  KC_DELETE_OUT=$(mcp_run \
      -e JWT_TOKEN="$JWT_FULL" \
      -e MCP_URL="http://zilla:$PORT/mcp" \
      -e CALL_TOOL="kafka_connect__delete_connector" \
      -e CALL_ARGS="{\"connector\":\"$KC_CONNECTOR\"}" \
      tools-list-client 2>&1)
  KC_LIST_AFTER_DELETE_OUT=$(mcp_run \
      -e JWT_TOKEN="$JWT_FULL" \
      -e MCP_URL="http://zilla:$PORT/mcp" \
      -e CALL_TOOL="kafka_connect__list_connectors" \
      tools-list-client 2>&1)
  ! echo "$KC_LIST_AFTER_DELETE_OUT" | grep -q "$KC_CONNECTOR"
}
timed kafka_connect_delete_connector retry_until 10 3 call_kafka_connect_delete_connector
echo "KC_DELETE_OUT=$KC_DELETE_OUT"
echo "KC_LIST_AFTER_DELETE_OUT=$KC_LIST_AFTER_DELETE_OUT"
if ! echo "$KC_LIST_AFTER_DELETE_OUT" | grep -q "$KC_CONNECTOR"; then
  echo "✅ kafka_connect__delete_connector removed the real connector"
else
  fail "kafka_connect__delete_connector did not remove the real connector"
fi

# WHEN: a kafka:write-scoped caller calls kafka__produce_message
# THEN: the record reaches the real, single-node KRaft Kafka broker started by
#       this example -- not the engine's `type: test` double specs/ITs use --
#       proving mcp-kafka's kind:client composite generator (kafka_cache_client
#       -> kafka_client -> tcp_client) talks to an actual broker end to end
call_kafka_produce() {
  KAFKA_PRODUCE_OUT=$(mcp_run \
      -e JWT_TOKEN="$JWT_FULL" \
      -e MCP_URL="http://zilla:$PORT/mcp" \
      -e CALL_TOOL="kafka__produce_message" \
      -e CALL_ARGS='{"topic":"orders","value":"hello from mcp-kafka"}' \
      tools-list-client 2>&1)
  echo "$KAFKA_PRODUCE_OUT" | grep -q 'Produced record to orders topic'
}
timed kafka_produce retry_until 10 3 call_kafka_produce
echo "KAFKA_PRODUCE_OUT=$KAFKA_PRODUCE_OUT"
if echo "$KAFKA_PRODUCE_OUT" | grep -q 'Produced record to orders topic'; then
  echo "✅ kafka__produce_message wrote a record to the real Kafka broker"
else
  fail "kafka__produce_message did not succeed against the real broker"
fi

# WHEN: that same caller calls kafka__consume_messages for the same topic
# THEN: the exact value produced above comes back in structuredContent.messages
#       -- round-tripping through the real broker, not just proving a count
call_kafka_consume_messages() {
  KAFKA_CONSUME_OUT=$(mcp_run \
      -e JWT_TOKEN="$JWT_FULL" \
      -e MCP_URL="http://zilla:$PORT/mcp" \
      -e CALL_TOOL="kafka__consume_messages" \
      -e CALL_ARGS='{"topic":"orders","limit":1}' \
      tools-list-client 2>&1)
  echo "$KAFKA_CONSUME_OUT" | grep -q 'hello from mcp-kafka'
}
timed kafka_consume_messages retry_until 10 3 call_kafka_consume_messages
echo "KAFKA_CONSUME_OUT=$KAFKA_CONSUME_OUT"
if echo "$KAFKA_CONSUME_OUT" | grep -q 'hello from mcp-kafka'; then
  echo "✅ kafka__consume_messages read the produced record back from the real Kafka broker"
else
  fail "kafka__consume_messages did not read the produced record back"
fi

# WHEN: that same caller calls kafka__describe_topic_configs for the orders topic
# THEN: the real broker's effective config comes back, including
#       cleanup.policy -- a config every topic carries by default -- proving
#       describe_topic_configs is read-only and needs no scope beyond the
#       toolkit-level kafka:tools guard already exercised by consume_messages
call_kafka_describe_topic_configs() {
  KAFKA_DESCRIBE_CONFIGS_OUT=$(mcp_run \
      -e JWT_TOKEN="$JWT_FULL" \
      -e MCP_URL="http://zilla:$PORT/mcp" \
      -e CALL_TOOL="kafka__describe_topic_configs" \
      -e CALL_ARGS='{"topic":"orders"}' \
      tools-list-client 2>&1)
  echo "$KAFKA_DESCRIBE_CONFIGS_OUT" | grep -q '"name":"cleanup.policy"'
}
timed kafka_describe_topic_configs retry_until 10 3 call_kafka_describe_topic_configs
echo "KAFKA_DESCRIBE_CONFIGS_OUT=$KAFKA_DESCRIBE_CONFIGS_OUT"
if echo "$KAFKA_DESCRIBE_CONFIGS_OUT" | grep -q '"name":"cleanup.policy"'; then
  echo "✅ kafka__describe_topic_configs read the real broker's effective topic config"
else
  fail "kafka__describe_topic_configs did not return the expected config"
fi

# WHEN: a kafka:admin-scoped caller calls kafka__alter_topic_configs to change the
#       orders topic's cleanup.policy
# THEN: the real broker accepts the change -- proving alter_topic_configs'
#       shared route (coalesced with create_topics/delete_topics under
#       kafka:admin, same reasoning as delete_topics above) is sufficient to
#       actually invoke the tool, not just see it listed
call_kafka_alter_topic_configs() {
  KAFKA_ALTER_CONFIGS_OUT=$(mcp_run \
      -e JWT_TOKEN="$JWT_FULL" \
      -e MCP_URL="http://zilla:$PORT/mcp" \
      -e CALL_TOOL="kafka__alter_topic_configs" \
      -e CALL_ARGS='{"topic":"orders","configs":{"cleanup.policy":"compact"}}' \
      tools-list-client 2>&1)
  echo "$KAFKA_ALTER_CONFIGS_OUT" | grep -q 'Updated configs for topic orders'
}
timed kafka_alter_topic_configs retry_until 10 3 call_kafka_alter_topic_configs
echo "KAFKA_ALTER_CONFIGS_OUT=$KAFKA_ALTER_CONFIGS_OUT"
if echo "$KAFKA_ALTER_CONFIGS_OUT" | grep -q 'Updated configs for topic orders'; then
  echo "✅ kafka__alter_topic_configs updated the topic config on the real Kafka broker"
else
  fail "kafka__alter_topic_configs did not succeed against the real broker"
fi

# WHEN: a kafka:admin-scoped caller calls kafka__create_topics
# THEN: a new topic is created on the real Kafka broker -- proving the
#       tool-specific kafka:admin scope (layered under the toolkit-level
#       kafka:tools guard already exercised above by produce_message/consume_messages) is
#       sufficient to actually invoke the tool, not just see it listed
call_kafka_create_topics() {
  KAFKA_CREATE_TOPICS_OUT=$(mcp_run \
      -e JWT_TOKEN="$JWT_FULL" \
      -e MCP_URL="http://zilla:$PORT/mcp" \
      -e CALL_TOOL="kafka__create_topics" \
      -e CALL_ARGS='{"topics":[{"topic":"widgets","partitions":1,"replicas":1}]}' \
      tools-list-client 2>&1)
  echo "$KAFKA_CREATE_TOPICS_OUT" | grep -q 'Created topic(s): widgets'
}
timed kafka_create_topics retry_until 10 3 call_kafka_create_topics
echo "KAFKA_CREATE_TOPICS_OUT=$KAFKA_CREATE_TOPICS_OUT"
if echo "$KAFKA_CREATE_TOPICS_OUT" | grep -q 'Created topic(s): widgets'; then
  echo "✅ kafka__create_topics created a new topic on the real Kafka broker"
else
  fail "kafka__create_topics did not succeed against the real broker"
fi

# WHEN: that same kafka:admin-scoped caller calls kafka__delete_topics for
#       the topic just created
# THEN: the topic is deleted on the real Kafka broker -- proving
#       delete_topics' coalesced route (sharing create_topics' `when` list
#       and kafka:admin guard, rather than a duplicate route) is sufficient
#       to actually invoke the tool, not just see it listed
call_kafka_delete_topics() {
  KAFKA_DELETE_TOPICS_OUT=$(mcp_run \
      -e JWT_TOKEN="$JWT_FULL" \
      -e MCP_URL="http://zilla:$PORT/mcp" \
      -e CALL_TOOL="kafka__delete_topics" \
      -e CALL_ARGS='{"topics":["widgets"]}' \
      tools-list-client 2>&1)
  echo "$KAFKA_DELETE_TOPICS_OUT" | grep -q 'Deleted topic(s): widgets'
}
timed kafka_delete_topics retry_until 10 3 call_kafka_delete_topics
echo "KAFKA_DELETE_TOPICS_OUT=$KAFKA_DELETE_TOPICS_OUT"
if echo "$KAFKA_DELETE_TOPICS_OUT" | grep -q 'Deleted topic(s): widgets'; then
  echo "✅ kafka__delete_topics deleted the topic from the real Kafka broker"
else
  fail "kafka__delete_topics did not succeed against the real broker"
fi

# WHEN: a kafka:tools-scoped caller calls kafka__list_topics
# THEN: the real topics on the same broker come back (at least the seeded
#       orders topic) -- gated by only the toolkit-level kafka:tools scope,
#       the same tier as consume_messages, no admin/write scope needed
call_kafka_list_topics() {
  KAFKA_LIST_TOPICS_OUT=$(mcp_run \
      -e JWT_TOKEN="$JWT_FULL" \
      -e MCP_URL="http://zilla:$PORT/mcp" \
      -e CALL_TOOL="kafka__list_topics" \
      tools-list-client 2>&1)
  echo "$KAFKA_LIST_TOPICS_OUT" | grep -q '"topic":"orders"'
}
timed kafka_list_topics retry_until 10 3 call_kafka_list_topics
echo "KAFKA_LIST_TOPICS_OUT=$KAFKA_LIST_TOPICS_OUT"
if echo "$KAFKA_LIST_TOPICS_OUT" | grep -q '"topic":"orders"'; then
  echo "✅ kafka__list_topics listed the real orders topic from the real Kafka broker"
else
  fail "kafka__list_topics did not list the orders topic as expected"
fi

# WHEN: that same caller calls kafka__describe_topic for the orders topic
# THEN: its real partition/leader/replica/isr metadata comes back from the
#       same broker -- proving describe_topic's single-topic argument (not a
#       route match) reaches the real Kafka Metadata API
call_kafka_describe_topic() {
  KAFKA_DESCRIBE_TOPIC_OUT=$(mcp_run \
      -e JWT_TOKEN="$JWT_FULL" \
      -e MCP_URL="http://zilla:$PORT/mcp" \
      -e CALL_TOOL="kafka__describe_topic" \
      -e CALL_ARGS='{"topic":"orders"}' \
      tools-list-client 2>&1)
  echo "$KAFKA_DESCRIBE_TOPIC_OUT" | grep -q 'Described topic orders'
}
timed kafka_describe_topic retry_until 10 3 call_kafka_describe_topic
echo "KAFKA_DESCRIBE_TOPIC_OUT=$KAFKA_DESCRIBE_TOPIC_OUT"
if echo "$KAFKA_DESCRIBE_TOPIC_OUT" | grep -q 'Described topic orders' &&
    echo "$KAFKA_DESCRIBE_TOPIC_OUT" | grep -q '"partition":0'; then
  echo "✅ kafka__describe_topic described the real orders topic from the real Kafka broker"
else
  fail "kafka__describe_topic did not describe the orders topic as expected"
fi

# WHEN: that same caller calls kafka__cluster_overview
# THEN: the same real broker's topic/broker counts come back -- sharing
#       list_topics/describe_topic's read-only route (one `when` list, three
#       `tool` entries, no guard beyond the toolkit-level kafka:tools)
call_kafka_cluster_overview() {
  KAFKA_CLUSTER_OVERVIEW_OUT=$(mcp_run \
      -e JWT_TOKEN="$JWT_FULL" \
      -e MCP_URL="http://zilla:$PORT/mcp" \
      -e CALL_TOOL="kafka__cluster_overview" \
      tools-list-client 2>&1)
  echo "$KAFKA_CLUSTER_OVERVIEW_OUT" | grep -q 'Cluster overview:'
}
timed kafka_cluster_overview retry_until 10 3 call_kafka_cluster_overview
echo "KAFKA_CLUSTER_OVERVIEW_OUT=$KAFKA_CLUSTER_OVERVIEW_OUT"
if echo "$KAFKA_CLUSTER_OVERVIEW_OUT" | grep -q '"broker_count":1'; then
  echo "✅ kafka__cluster_overview summarized the real Kafka broker"
else
  fail "kafka__cluster_overview did not summarize the real broker as expected"
fi

# WHEN: a kafka:tools-scoped caller calls kafka__list_brokers
# THEN: the real, single-node KRaft broker started by this example (node id 1,
#       advertised as kafka.examples.dev:29092 on the INTERNAL listener
#       south_mcp_kafka_client connects on) comes back -- proving
#       KafkaApiDescribeClusterClient's shared DescribeCluster request/response
#       path (not the older, fully-decoded mechanism used elsewhere in
#       binding-kafka) reaches a real broker, not the engine's test double
call_kafka_list_brokers() {
  KAFKA_LIST_BROKERS_OUT=$(mcp_run \
      -e JWT_TOKEN="$JWT_FULL" \
      -e MCP_URL="http://zilla:$PORT/mcp" \
      -e CALL_TOOL="kafka__list_brokers" \
      tools-list-client 2>&1)
  echo "$KAFKA_LIST_BROKERS_OUT" | grep -q 'Brokers: 1@kafka.examples.dev:29092'
}
timed kafka_list_brokers retry_until 10 3 call_kafka_list_brokers
echo "KAFKA_LIST_BROKERS_OUT=$KAFKA_LIST_BROKERS_OUT"
if echo "$KAFKA_LIST_BROKERS_OUT" | grep -q 'Brokers: 1@kafka.examples.dev:29092'; then
  echo "✅ kafka__list_brokers listed the real Kafka broker"
else
  fail "kafka__list_brokers did not list the real broker"
fi

# WHEN: that same caller calls kafka__describe_cluster
# THEN: the same single-node broker (node id 1) is reported as controller --
#       proving describe_cluster and list_brokers share one route/guard
#       (kafka:tools only, no admin scope), being read-only cluster
#       introspection like consume_messages rather than a structural mutation
call_kafka_describe_cluster() {
  KAFKA_DESCRIBE_CLUSTER_OUT=$(mcp_run \
      -e JWT_TOKEN="$JWT_FULL" \
      -e MCP_URL="http://zilla:$PORT/mcp" \
      -e CALL_TOOL="kafka__describe_cluster" \
      tools-list-client 2>&1)
  echo "$KAFKA_DESCRIBE_CLUSTER_OUT" | grep -q 'Described cluster .*, controller 1'
}
timed kafka_describe_cluster retry_until 10 3 call_kafka_describe_cluster
echo "KAFKA_DESCRIBE_CLUSTER_OUT=$KAFKA_DESCRIBE_CLUSTER_OUT"
if echo "$KAFKA_DESCRIBE_CLUSTER_OUT" | grep -q 'Described cluster .*, controller 1'; then
  echo "✅ kafka__describe_cluster reported the real broker as controller"
else
  fail "kafka__describe_cluster did not succeed against the real broker"
fi

CONSUMER_GROUP="orders-analytics"

# WHEN: a kafka:tools-scoped caller calls kafka__describe_consumer_group for a
#       group id that has never committed an offset on this broker
# THEN: the real broker reports state "Dead" -- Kafka's actual behavior for a
#       group that does not exist yet, not an error -- proving
#       describe_consumer_group needs only the toolkit-level kafka:tools
#       scope, no admin scope, to describe any group by name
call_describe_group_before_reset() {
  DESCRIBE_GROUP_BEFORE_OUT=$(mcp_run \
      -e JWT_TOKEN="$JWT_FULL" \
      -e MCP_URL="http://zilla:$PORT/mcp" \
      -e CALL_TOOL="kafka__describe_consumer_group" \
      -e CALL_ARGS="{\"group_id\":\"$CONSUMER_GROUP\"}" \
      tools-list-client 2>&1)
  echo "$DESCRIBE_GROUP_BEFORE_OUT" | grep -q "Consumer group $CONSUMER_GROUP is Dead"
}
timed describe_group_before_reset retry_until 10 3 call_describe_group_before_reset
echo "DESCRIBE_GROUP_BEFORE_OUT=$DESCRIBE_GROUP_BEFORE_OUT"
if echo "$DESCRIBE_GROUP_BEFORE_OUT" | grep -q "Consumer group $CONSUMER_GROUP is Dead"; then
  echo "✅ kafka__describe_consumer_group reported state Dead for a never-used group"
else
  fail "kafka__describe_consumer_group did not report state Dead as expected"
fi

# WHEN: a kafka:tools-scoped caller calls kafka__describe_consumer_group_lag
#       for that same never-used group id
# THEN: the real broker's OffsetFetch reports no topics for the group, so the
#       result carries an empty partitions array and total lag 0, not an
#       error -- proving describe_consumer_group_lag needs only the
#       toolkit-level kafka:tools scope, the same route/guard as
#       describe_consumer_group
call_describe_group_lag() {
  DESCRIBE_GROUP_LAG_OUT=$(mcp_run \
      -e JWT_TOKEN="$JWT_FULL" \
      -e MCP_URL="http://zilla:$PORT/mcp" \
      -e CALL_TOOL="kafka__describe_consumer_group_lag" \
      -e CALL_ARGS="{\"group_id\":\"$CONSUMER_GROUP\"}" \
      tools-list-client 2>&1)
  echo "$DESCRIBE_GROUP_LAG_OUT" | grep -q "Consumer group $CONSUMER_GROUP has total lag 0"
}
timed describe_group_lag retry_until 10 3 call_describe_group_lag
echo "DESCRIBE_GROUP_LAG_OUT=$DESCRIBE_GROUP_LAG_OUT"
if echo "$DESCRIBE_GROUP_LAG_OUT" | grep -q "Consumer group $CONSUMER_GROUP has total lag 0"; then
  echo "✅ kafka__describe_consumer_group_lag reported total lag 0 for a never-used group"
else
  fail "kafka__describe_consumer_group_lag did not report total lag 0 as expected"
fi

# WHEN: a kafka:tools-scoped caller calls kafka__list_consumer_groups
# THEN: the call succeeds against the real broker (an empty result is a
#       correct, successful response -- no consumer group in this example
#       ever runs a real consumer session)
#
# NOTE: kafka__reset_offsets' own OffsetCommit stage (the third hop of its
# FindCoordinator -> DescribeGroups -> OffsetCommit flow) is not exercised
# end to end against a real broker here -- driving it to a real broker
# surfaced a hang distinct from the request-encoding bug fixed alongside
# this test, in how the mcp-kafka client's auto-generated composite routes
# an OffsetCommit stream to the dynamically resolved coordinator host/port
# (as opposed to the statically configured options.servers). This is a
# known gap, tracked for follow-up, not silently papered over: FindCoordinator
# and DescribeGroups -- reset_offsets' first two hops -- are covered above
# via kafka__describe_consumer_group, which shares the same DescribeGroups
# call reset_offsets makes internally.
call_kafka_list_consumer_groups() {
  KAFKA_LIST_GROUPS_OUT=$(mcp_run \
      -e JWT_TOKEN="$JWT_FULL" \
      -e MCP_URL="http://zilla:$PORT/mcp" \
      -e CALL_TOOL="kafka__list_consumer_groups" \
      tools-list-client 2>&1)
  echo "$KAFKA_LIST_GROUPS_OUT" | grep -qE "Consumer groups:|No consumer groups found"
}
timed kafka_list_consumer_groups retry_until 10 3 call_kafka_list_consumer_groups
echo "KAFKA_LIST_GROUPS_OUT=$KAFKA_LIST_GROUPS_OUT"
if echo "$KAFKA_LIST_GROUPS_OUT" | grep -qE "Consumer groups:|No consumer groups found"; then
  echo "✅ kafka__list_consumer_groups succeeded against the real Kafka broker"
else
  fail "kafka__list_consumer_groups did not succeed against the real broker"
fi

ACL_PRINCIPAL="User:acl-test-user"

# WHEN: a kafka:acls-scoped caller calls kafka__create_acls for a dedicated
#       test resource (not the orders topic other checks above depend on --
#       once any ACL exists for a resource, StandardAuthorizer stops
#       implicitly allowing every other principal against that same
#       resource, so a shared resource would risk breaking the produce_message/
#       consume_messages/admin checks above)
# THEN: the real broker accepts the grant -- proving create_acls' own
#       kafka:acls scope (deliberately distinct from kafka:admin per
#       KIP-1318, see the routes[] comment in etc/zilla.yaml) is sufficient
#       to actually invoke the tool, not just see it listed
call_kafka_create_acls() {
  KAFKA_CREATE_ACLS_OUT=$(mcp_run \
      -e JWT_TOKEN="$JWT_FULL" \
      -e MCP_URL="http://zilla:$PORT/mcp" \
      -e CALL_TOOL="kafka__create_acls" \
      -e CALL_ARGS="{\"acls\":[{\"resource_type\":\"topic\",\"resource_name\":\"acl-test-topic\",\"pattern_type\":\"literal\",\"principal\":\"$ACL_PRINCIPAL\",\"host\":\"*\",\"operation\":\"read\",\"permission_type\":\"allow\"}]}" \
      tools-list-client 2>&1)
  echo "$KAFKA_CREATE_ACLS_OUT" | grep -q 'Created 1 ACL(s)'
}
timed kafka_create_acls retry_until 10 3 call_kafka_create_acls
echo "KAFKA_CREATE_ACLS_OUT=$KAFKA_CREATE_ACLS_OUT"
if echo "$KAFKA_CREATE_ACLS_OUT" | grep -q 'Created 1 ACL(s)'; then
  echo "✅ kafka__create_acls granted a real ACL on the real Kafka broker"
else
  fail "kafka__create_acls did not succeed against the real broker"
fi

# WHEN: a kafka:tools-scoped caller (list_acls needs no scope beyond the
#       toolkit-level guard, sharing describe_topic_configs' read-only route) calls
#       kafka__list_acls filtered to the principal just granted above
# THEN: the real broker reports the ACL created above -- proving list_acls
#       reaches the real DescribeAcls API, not just a cached/local view
call_kafka_list_acls() {
  KAFKA_LIST_ACLS_OUT=$(mcp_run \
      -e JWT_TOKEN="$JWT_FULL" \
      -e MCP_URL="http://zilla:$PORT/mcp" \
      -e CALL_TOOL="kafka__list_acls" \
      -e CALL_ARGS="{\"principal\":\"$ACL_PRINCIPAL\"}" \
      tools-list-client 2>&1)
  echo "$KAFKA_LIST_ACLS_OUT" | grep -q "\"principal\":\"$ACL_PRINCIPAL\""
}
timed kafka_list_acls retry_until 10 3 call_kafka_list_acls
echo "KAFKA_LIST_ACLS_OUT=$KAFKA_LIST_ACLS_OUT"
if echo "$KAFKA_LIST_ACLS_OUT" | grep -q "\"principal\":\"$ACL_PRINCIPAL\"" &&
    echo "$KAFKA_LIST_ACLS_OUT" | grep -q '"resource_name":"acl-test-topic"'; then
  echo "✅ kafka__list_acls read the real ACL back from the real Kafka broker"
else
  fail "kafka__list_acls did not report the ACL created above"
fi

# WHEN: that same kafka:acls-scoped caller calls kafka__delete_acls for the
#       same filter, cleaning up the ACL granted above
# THEN: the real broker revokes it -- proving delete_acls' shared route with
#       create_acls (one `when` list, both tools, same kafka:acls scope) is
#       sufficient to actually invoke the tool, not just see it listed
call_kafka_delete_acls() {
  KAFKA_DELETE_ACLS_OUT=$(mcp_run \
      -e JWT_TOKEN="$JWT_FULL" \
      -e MCP_URL="http://zilla:$PORT/mcp" \
      -e CALL_TOOL="kafka__delete_acls" \
      -e CALL_ARGS="{\"acls\":[{\"resource_type\":\"topic\",\"resource_name\":\"acl-test-topic\",\"pattern_type\":\"literal\",\"principal\":\"$ACL_PRINCIPAL\",\"operation\":\"read\",\"permission_type\":\"allow\"}]}" \
      tools-list-client 2>&1)
  echo "$KAFKA_DELETE_ACLS_OUT" | grep -q 'Deleted 1 ACL(s)'
}
timed kafka_delete_acls retry_until 10 3 call_kafka_delete_acls
echo "KAFKA_DELETE_ACLS_OUT=$KAFKA_DELETE_ACLS_OUT"
if echo "$KAFKA_DELETE_ACLS_OUT" | grep -q 'Deleted 1 ACL(s)'; then
  echo "✅ kafka__delete_acls revoked the real ACL on the real Kafka broker"
else
  fail "kafka__delete_acls did not succeed against the real broker"
fi

# by this point in the script, options.cache.ttl (PT5M) has very plausibly
# already elapsed at least once since the upfront cache_hydrated wait -- the
# cache is shared and filtered per caller (not re-hydrated per caller, so an
# anonymous caller sees it exactly as fast as JWT_FULL did earlier), but a
# TTL-driven refresh is a second, later warm-up window the upfront check
# cannot see coming. Re-check freshly right before the one assertion that
# still depends on it, rather than re-adding a retry around the assertion
# itself for what is really the same one-time-per-window race as before
timed cache_rehydrated retry_deadline "$CACHE_HYDRATE_TIMEOUT_S" 5 cache_hydrated
if echo "$CACHE_INIT_BODY" | grep -q '"subscribe":true' && full_toolset_present; then
  echo "✅ tools/resources/prompts cache is still hydrated ahead of the resources/subscribe round-trip"
else
  fail "cache went cold again (options.cache.ttl elapsed) and did not re-hydrate in time"
fi

# WHEN: a real MCP SDK client subscribes to an everything resource, calls
#       everything__toggle-subscriber-updates to start the reference server's
#       simulated per-session update interval, and waits for the resulting
#       notification
# THEN: notifications/resources/updated is relayed back end-to-end -- through
#       south_mcp_client_everything, north_mcp_proxy (re-prefixing the URI),
#       and north_mcp_server -- proving resources/subscribe,
#       resources/unsubscribe, and the update notification all pass through
#       every mcp binding kind (aklivity/zilla#2220)
#
# Retried: this client opens its persistent SSE stream and its first
# resources/list request as two nearly-simultaneous connections against the
# same fresh session, and has intermittently (twice, real CI only, never
# reproduced locally) hit an empty-body 200 on resources/list --
# "Unexpected end of JSON input" -- with the cache confirmed warm
# immediately beforehand, so this is not the cache-hydration race above.
# Deep review of McpServerFactory (mcp(server)) and the proxy's cache-served
# list path (McpProxyListFactory/McpProxyLifecycleFactory) did not turn up a
# root cause: per-exchange state is properly isolated, stream ids don't
# collide, and doServerEnd() is structurally gated on data already having
# been queued/flushed, so nothing found there can produce End-before-Data.
# Retrying here is a bounded mitigation for a real but rare (not
# reproduced outside CI) race that would need live packet capture to pin
# down further, not a fix for a race whose cause is understood.
subscribe_resource() {
  SUBSCRIBE_OUT=$(MCP_TIMEOUT_S="$MCP_SUBSCRIBE_TIMEOUT_S" mcp_run resource-subscribe-client 2>&1)
  echo "$SUBSCRIBE_OUT" | grep -q 'OK resource subscription relayed end-to-end'
}
timed subscribe retry_until 3 5 subscribe_resource
echo "SUBSCRIBE_OUT=$SUBSCRIBE_OUT"
if echo "$SUBSCRIBE_OUT" | grep -q 'OK resource subscription relayed end-to-end'; then
  echo "✅ resources/subscribe, notifications/resources/updated, and resources/unsubscribe relayed end-to-end"
else
  fail "resource subscription round-trip did not relay end-to-end"
fi

report_timings
report_failures

exit $EXIT
