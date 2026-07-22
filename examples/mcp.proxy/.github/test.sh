#!/bin/sh
# Drives the build-workflow verification for examples/mcp.proxy.
#
# Assertions, all at the Zilla layer:
#   1. a url-elicitation-capable client initializes and negotiates 2025-11-25
#   2. a real MCP SDK client drives a url-mode elicitation round-trip end-to-end
#      through the gateway (elicitation/create mode:url + completion notification)
#   3. south_mcp_client_urlelicit forwards the caller's own JWT upstream
#      (options.authorization on a mcp(client) binding)
#   4. tools/list is filtered by the caller's JWT scopes: unauthorized toolkits
#      and tools are absent from the result, layered per binding hop
#      (mcp proxy toolkit routes, mcp_http per-tool route, mcp_openapi
#      OpenAPI-native per-operation security)
#   5. mcp_http's create_pr forwards call arguments as the request body via
#      with.body, scoped to exclude args already consumed by the :path
#   6. mcp_openapi's search_pets renames an argument via with.params before
#      building the request (options.specs.petstore.server also overrides
#      the OpenAPI document's declared server to the local mock)
#   7. mcp_http's pull_by_number resource template ({owner}/{repo}/{number})
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
#  15. kafka__produce writes a record to a real, single-node KRaft Kafka
#      broker (mcp_kafka kind:client's own generated cache_client/client/
#      tcp_client pipeline, not the engine's test double)
#  16. kafka__consume reads that same record back, round-tripping the exact
#      value through the real broker
#  17. kafka_sr__register_schema registers a real schema against a
#      real Karapace instance (mcp_schema_registry kind:client's own
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
#  22. mcp_schema_registry's own routes[].guarded layers a tool-specific
#      scope (kafka_sr:write) under the toolkit-level scope
#      (kafka_sr:tools) for register_schema only -- no OpenAPI
#      security scheme is involved, unlike petstore's create_pet
#
# Streamable HTTP responses arrive as Server-Sent Events; checks grep the
# streamed body / client output rather than asserting exact-string equality.
set -x

. "$(CDPATH= cd -- "$(dirname -- "$0")/../../.github" && pwd)/test-lib.sh"

EXIT=0
PORT="7114"
INITIALIZE='{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-11-25","capabilities":{"elicitation":{"url":{}}},"clientInfo":{"name":"zilla-mcp-proxy-test","version":"0.0.1"}}}'

echo "# Testing mcp.proxy"
echo "PORT=$PORT"

# Mint JWTs for the authn_jwt guard, one per scope combination under test.
# `scope` is a jwt guard `roles` claim: a space-separated list, matched
# against the roles each `guarded:` route requires.
encode_jwt() {
  _scope=$1
  if [ -n "$_scope" ]; then
    docker compose run --rm jwt-cli encode \
        --alg "RS256" --kid "example" \
        --iss "https://auth.example.com" --aud "https://api.example.com" \
        --exp=+1d --no-iat \
        --payload "scope=$_scope" \
        --secret @/private.pem | tr -d '\r\n'
  fi
}

JWT_NONE=""
JWT_URLELICIT=$(encode_jwt "urlelicit:authorize")
JWT_PARTIAL=$(encode_jwt "github:tools petstore:tools kafka_sr:tools")
JWT_FULL=$(encode_jwt "urlelicit:authorize github:tools github:pr:write petstore:tools pets:write kafka_sr:tools kafka_sr:write kafka:tools")

# WHEN: a url-elicitation-capable client initializes against the gateway
# THEN: the gateway negotiates protocol version 2025-11-25 in the response
# retry until the mcp route is live and negotiates the protocol version
initialize_mcp() {
  INIT_BODY=$(curl -sS -N --max-time 10 \
      -X POST "http://localhost:$PORT/mcp" \
      -H "Content-Type: application/json" \
      -H "Accept: application/json, text/event-stream" \
      -d "$INITIALIZE")
  echo "$INIT_BODY" | grep -q '"protocolVersion":"2025-11-25"'
}
retry_until 10 3 initialize_mcp
echo INIT_BODY="$INIT_BODY"
if echo "$INIT_BODY" | grep -q '"protocolVersion":"2025-11-25"'; then
  echo ✅ initialize negotiated 2025-11-25
else
  echo ❌ initialize did not negotiate 2025-11-25
  EXIT=1
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
  ELICIT_OUT=$(docker compose run --rm --no-deps \
      -e MCP_URL="http://zilla:$PORT/mcp" \
      -e JWT_TOKEN="$JWT_URLELICIT" \
      urlelicit-client 2>&1)
  echo "$ELICIT_OUT" | grep -q 'OK url-mode elicitation relayed end-to-end'
}
retry_until 10 3 relay_elicitation
echo "$ELICIT_OUT"
if echo "$ELICIT_OUT" | grep -q 'OK url-mode elicitation relayed end-to-end'; then
  echo ✅ url-mode elicitation relayed end-to-end
else
  echo ❌ url-mode elicitation not relayed end-to-end
  EXIT=1
fi

# WHEN: the call above reaches the urlelicit mock through south_mcp_client_urlelicit
# THEN: the mock observes the caller's own JWT_URLELICIT bearer token, not some
#       separate service credential -- proving options.authorization on a
#       mcp(client) binding forwards the session's own credential upstream
URLELICIT_LOGS=$(docker compose logs urlelicit 2>&1)
if echo "$URLELICIT_LOGS" | grep -q "authorization: Bearer $JWT_URLELICIT"; then
  echo "✅ south_mcp_client_urlelicit forwarded the caller's own JWT to urlelicit"
else
  echo "❌ urlelicit did not observe the caller's forwarded JWT"
  EXIT=1
fi

list_tools() {
  _token=$1
  docker compose run --rm --no-deps -e JWT_TOKEN="$_token" -e MCP_URL="http://zilla:$PORT/mcp" \
      tools-list-client 2>/dev/null
}

# WHEN: a caller presents no JWT at all
# THEN: the ungated "everything" toolkit is listed, every guarded toolkit --
#       and its resources -- is not
assert_no_token() {
  TOOLS_NONE=$(list_tools "$JWT_NONE")
  echo "$TOOLS_NONE" | grep -q '^everything__' &&
    ! echo "$TOOLS_NONE" | grep -q '^urlelicit__' &&
    ! echo "$TOOLS_NONE" | grep -q '^github__' &&
    ! echo "$TOOLS_NONE" | grep -q '^petstore__' &&
    ! echo "$TOOLS_NONE" | grep -q '^kafka_sr__' &&
    ! echo "$TOOLS_NONE" | grep -q '^kafka__' &&
    ! echo "$TOOLS_NONE" | grep -q 'petstore+' &&
    ! echo "$TOOLS_NONE" | grep -q 'github+'
}
retry_until 5 3 assert_no_token
echo "TOOLS_NONE=$TOOLS_NONE"
if echo "$TOOLS_NONE" | grep -q '^everything__' &&
    ! echo "$TOOLS_NONE" | grep -q '^urlelicit__' &&
    ! echo "$TOOLS_NONE" | grep -q '^github__' &&
    ! echo "$TOOLS_NONE" | grep -q '^petstore__' &&
    ! echo "$TOOLS_NONE" | grep -q '^kafka__' &&
    ! echo "$TOOLS_NONE" | grep -q 'petstore+' &&
    ! echo "$TOOLS_NONE" | grep -q 'github+'; then
  echo "✅ no token: only the ungated everything toolkit is listed"
else
  echo "❌ no token: tools/list did not filter to only the everything toolkit"
  EXIT=1
fi

# WHEN: a caller has toolkit-level scopes (github:tools, petstore:tools,
#       kafka_sr:tools) but none of the finer-grained operation scopes
# THEN: petstore__list_pets, both petstore resources, github's
#       pull_by_number template, and kafka_sr__list_subjects are listed
#       (none of them declare an extra scope beyond toolkit access) but
#       petstore__create_pet, github__create_pr, and
#       kafka_sr__register_schema are not (they require pets:write /
#       github:pr:write / kafka_sr:write respectively) -- proof that
#       toolkit access alone does not imply access to every tool/resource in it
assert_partial_token() {
  TOOLS_PARTIAL=$(list_tools "$JWT_PARTIAL")
  echo "$TOOLS_PARTIAL" | grep -q '^petstore__list_pets$' &&
    echo "$TOOLS_PARTIAL" | grep -q '^petstore__search_pets$' &&
    echo "$TOOLS_PARTIAL" | grep -q '^resource:petstore+/pets/featured$' &&
    echo "$TOOLS_PARTIAL" | grep -q '^template:petstore+/pets/{petId}$' &&
    echo "$TOOLS_PARTIAL" | grep -q '^template:github+pr://{owner}/{repo}/{number}$' &&
    echo "$TOOLS_PARTIAL" | grep -q '^kafka_sr__list_subjects$' &&
    ! echo "$TOOLS_PARTIAL" | grep -q '^petstore__create_pet$' &&
    ! echo "$TOOLS_PARTIAL" | grep -q '^github__create_pr$' &&
    ! echo "$TOOLS_PARTIAL" | grep -q '^kafka_sr__register_schema$' &&
    ! echo "$TOOLS_PARTIAL" | grep -q '^urlelicit__' &&
    ! echo "$TOOLS_PARTIAL" | grep -q '^kafka__'
}
retry_until 5 3 assert_partial_token
echo "TOOLS_PARTIAL=$TOOLS_PARTIAL"
if echo "$TOOLS_PARTIAL" | grep -q '^petstore__list_pets$' &&
    echo "$TOOLS_PARTIAL" | grep -q '^petstore__search_pets$' &&
    echo "$TOOLS_PARTIAL" | grep -q '^resource:petstore+/pets/featured$' &&
    echo "$TOOLS_PARTIAL" | grep -q '^template:petstore+/pets/{petId}$' &&
    echo "$TOOLS_PARTIAL" | grep -q '^template:github+pr://{owner}/{repo}/{number}$' &&
    echo "$TOOLS_PARTIAL" | grep -q '^kafka_sr__list_subjects$' &&
    ! echo "$TOOLS_PARTIAL" | grep -q '^petstore__create_pet$' &&
    ! echo "$TOOLS_PARTIAL" | grep -q '^github__create_pr$' &&
    ! echo "$TOOLS_PARTIAL" | grep -q '^kafka_sr__register_schema$' &&
    ! echo "$TOOLS_PARTIAL" | grep -q '^urlelicit__' &&
    ! echo "$TOOLS_PARTIAL" | grep -q '^kafka__'; then
  echo "✅ toolkit-only scope: sees list_pets, search_pets, list_subjects, and all three read-only resources, but not create_pet, create_pr, or register_schema"
else
  echo "❌ toolkit-only scope did not layer as expected"
  EXIT=1
fi

# WHEN: a caller has every scope required by every guarded route
# THEN: every tool, resource, and resource template across every toolkit is listed
assert_full_token() {
  TOOLS_FULL=$(list_tools "$JWT_FULL")
  echo "$TOOLS_FULL" | grep -q '^everything__' &&
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
    echo "$TOOLS_FULL" | grep -q '^kafka__produce$' &&
    echo "$TOOLS_FULL" | grep -q '^kafka__consume$'
}
retry_until 5 3 assert_full_token
echo "TOOLS_FULL=$TOOLS_FULL"
if echo "$TOOLS_FULL" | grep -q '^everything__' &&
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
    echo "$TOOLS_FULL" | grep -q '^kafka__produce$' &&
    echo "$TOOLS_FULL" | grep -q '^kafka__consume$'; then
  echo "✅ full scope: every toolkit's tools and resources are listed"
else
  echo "❌ full scope did not unlock every toolkit"
  EXIT=1
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
  echo "❌ everything__get-sum was listed despite not matching options.cache.tools.eager.match"
  EXIT=1
fi

# WHEN: that same caller calls zilla__search_tools for "sum"
# THEN: the cold everything__get-sum tool comes back in structuredContent.tools --
#       proving a tool omitted from tools/list is discoverable by keyword, not gone
search_cold_tool() {
  SEARCH_OUT=$(docker compose run --rm --no-deps \
      -e JWT_TOKEN="$JWT_FULL" \
      -e MCP_URL="http://zilla:$PORT/mcp" \
      -e CALL_TOOL="zilla__search_tools" \
      -e CALL_ARGS='{"query":"sum"}' \
      tools-list-client 2>&1)
  echo "$SEARCH_OUT" | grep -q 'everything__get-sum'
}
retry_until 5 3 search_cold_tool
echo "SEARCH_OUT=$SEARCH_OUT"
if echo "$SEARCH_OUT" | grep -q 'everything__get-sum'; then
  echo "✅ zilla__search_tools surfaced the cold everything__get-sum tool by keyword"
else
  echo "❌ zilla__search_tools did not surface everything__get-sum for query \"sum\""
  EXIT=1
fi

# WHEN: that same caller calls zilla__describe_tool for the cold tool found above
# THEN: the full cached definition (schema included) comes back -- the same
#       shape tools/list would show were it not cold, resolved by exact name
describe_cold_tool() {
  DESCRIBE_OUT=$(docker compose run --rm --no-deps \
      -e JWT_TOKEN="$JWT_FULL" \
      -e MCP_URL="http://zilla:$PORT/mcp" \
      -e CALL_TOOL="zilla__describe_tool" \
      -e CALL_ARGS='{"name":"everything__get-sum"}' \
      tools-list-client 2>&1)
  echo "$DESCRIBE_OUT" | grep -q 'inputSchema'
}
retry_until 5 3 describe_cold_tool
echo "DESCRIBE_OUT=$DESCRIBE_OUT"
if echo "$DESCRIBE_OUT" | grep -q 'inputSchema' && echo "$DESCRIBE_OUT" | grep -q 'everything__get-sum'; then
  echo "✅ zilla__describe_tool resolved the cold everything__get-sum tool's full definition"
else
  echo "❌ zilla__describe_tool did not resolve everything__get-sum's full definition"
  EXIT=1
fi

# WHEN: that same caller calls zilla__execute_tool naming the cold tool found above
# THEN: it actually invokes it -- the same result as calling everything__get-sum
#       directly, proving execute_tool dispatches through the real tools/call path
execute_cold_tool() {
  EXECUTE_OUT=$(docker compose run --rm --no-deps \
      -e JWT_TOKEN="$JWT_FULL" \
      -e MCP_URL="http://zilla:$PORT/mcp" \
      -e CALL_TOOL="zilla__execute_tool" \
      -e CALL_ARGS='{"name":"everything__get-sum","arguments":{"a":2,"b":3}}' \
      tools-list-client 2>&1)
  echo "$EXECUTE_OUT" | grep -q 'The sum of 2 and 3 is 5'
}
retry_until 5 3 execute_cold_tool
echo "EXECUTE_OUT=$EXECUTE_OUT"
if echo "$EXECUTE_OUT" | grep -q 'The sum of 2 and 3 is 5'; then
  echo "✅ zilla__execute_tool invoked the cold everything__get-sum tool by name"
else
  echo "❌ zilla__execute_tool did not successfully invoke everything__get-sum by name"
  EXIT=1
fi

# WHEN: that same caller calls everything__get-sum directly by name, despite
#       it never appearing in tools/list above
# THEN: the call still succeeds -- options.cache.tools.eager only changes what
#       tools/list reports, never what tools/call accepts
call_cold_tool() {
  COLD_CALL_OUT=$(docker compose run --rm --no-deps \
      -e JWT_TOKEN="$JWT_FULL" \
      -e MCP_URL="http://zilla:$PORT/mcp" \
      -e CALL_TOOL="everything__get-sum" \
      -e CALL_ARGS='{"a":2,"b":3}' \
      tools-list-client 2>&1)
  echo "$COLD_CALL_OUT" | grep -q 'The sum of 2 and 3 is 5'
}
retry_until 5 3 call_cold_tool
echo "COLD_CALL_OUT=$COLD_CALL_OUT"
if echo "$COLD_CALL_OUT" | grep -q 'The sum of 2 and 3 is 5'; then
  echo "✅ everything__get-sum, though cold, still succeeded when called directly by name"
else
  echo "❌ everything__get-sum did not succeed when called directly despite being cold, not unauthorized"
  EXIT=1
fi

# WHEN: an authorized caller calls github__create_pr with title/head/base
# THEN: those arguments reach the ghapi mock as the JSON request body (not
#       just owner/repo, which are consumed by the :path template) --
#       verifying with.body still forwards the call arguments even though
#       owner/repo are excluded from the body schema. The result summary
#       template "...${result.title}" surfaces ghapi's echoed title back
#       through the tool call result, which is what this grep observes.
call_create_pr() {
  CREATE_PR_OUT=$(docker compose run --rm --no-deps \
      -e JWT_TOKEN="$JWT_FULL" \
      -e MCP_URL="http://zilla:$PORT/mcp" \
      -e CALL_TOOL="github__create_pr" \
      -e CALL_ARGS='{"owner":"acme","repo":"widget","title":"Add feature","head":"feature","base":"main"}' \
      tools-list-client 2>&1)
  echo "$CREATE_PR_OUT" | grep -q 'Add feature'
}
retry_until 5 3 call_create_pr
echo "CREATE_PR_OUT=$CREATE_PR_OUT"
if echo "$CREATE_PR_OUT" | grep -q 'Add feature'; then
  echo "✅ github__create_pr forwarded title/head/base to ghapi as the request body"
else
  echo "❌ github__create_pr did not forward the call arguments as the request body"
  EXIT=1
fi

# WHEN: an authorized caller calls petstore__search_pets with {"category":"cat"}
# THEN: the petstore mock observes ?tag=cat, not ?category=cat -- verifying
#       with.params.tag: "${args.category}" renamed the argument back to the
#       OpenAPI parameter's own name before building the request
call_search_pets() {
  docker compose run --rm --no-deps \
      -e JWT_TOKEN="$JWT_FULL" \
      -e MCP_URL="http://zilla:$PORT/mcp" \
      -e CALL_TOOL="petstore__search_pets" \
      -e CALL_ARGS='{"category":"cat"}' \
      tools-list-client >/dev/null 2>&1
  PETSTORE_LOGS=$(docker compose logs petstore 2>&1)
  echo "$PETSTORE_LOGS" | grep -q 'search_pets query: {"tag":"cat"}'
}
retry_until 5 3 call_search_pets
echo "PETSTORE_LOGS=$PETSTORE_LOGS"
if echo "$PETSTORE_LOGS" | grep -q 'search_pets query: {"tag":"cat"}'; then
  echo "✅ petstore__search_pets renamed category -> tag via with.params"
else
  echo "❌ petstore__search_pets did not rename the argument as configured"
  EXIT=1
fi

read_resource() {
  _token=$1
  _uri=$2
  docker compose run --rm --no-deps -e JWT_TOKEN="$_token" -e MCP_URL="http://zilla:$PORT/mcp" \
      -e READ_RESOURCE="$_uri" \
      tools-list-client 2>/dev/null
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
retry_until 5 3 read_pull_by_number
echo "PULL_OUT=$PULL_OUT"
if echo "$PULL_OUT" | grep -q 'Seed data for the pull_by_number resource demo'; then
  echo "✅ github+pr://acme/widget/42 read end-to-end via the pull_by_number template"
else
  echo "❌ pull_by_number resource template did not read through as configured"
  EXIT=1
fi

# WHEN: a petstore:tools-scoped caller reads the static featured_pets resource
# THEN: the seeded featured pet (Bramble) comes back -- proving a resource with
#       no {param} in its uri reads end-to-end same as a templated one
read_featured_pets() {
  FEATURED_OUT=$(read_resource "$JWT_PARTIAL" "petstore+/pets/featured")
  echo "$FEATURED_OUT" | grep -q 'Bramble'
}
retry_until 5 3 read_featured_pets
echo "FEATURED_OUT=$FEATURED_OUT"
if echo "$FEATURED_OUT" | grep -q 'Bramble'; then
  echo "✅ petstore+/pets/featured read end-to-end"
else
  echo "❌ petstore+/pets/featured did not read through as configured"
  EXIT=1
fi

# WHEN: a pets:write-scoped caller calls petstore__create_pet
# THEN: the call actually succeeds against the petstore mock (not just listed
#       as available) -- the mcp_openapi OpenAPI-native security requirement
#       permits the call, and the auto-derived request/response schemas round-trip
call_create_pet() {
  CREATE_PET_OUT=$(docker compose run --rm --no-deps \
      -e JWT_TOKEN="$JWT_FULL" \
      -e MCP_URL="http://zilla:$PORT/mcp" \
      -e CALL_TOOL="petstore__create_pet" \
      -e CALL_ARGS='{"name":"Nibbles","tag":"hamster"}' \
      tools-list-client 2>&1)
  echo "$CREATE_PET_OUT" | grep -q 'Nibbles'
}
retry_until 5 3 call_create_pet
echo "CREATE_PET_OUT=$CREATE_PET_OUT"
if echo "$CREATE_PET_OUT" | grep -q 'Nibbles'; then
  echo "✅ petstore__create_pet succeeded for a pets:write-scoped caller"
else
  echo "❌ petstore__create_pet did not succeed as expected"
  EXIT=1
fi

SR_SUBJECT="orders-value"
SR_SCHEMA='{\"type\":\"record\",\"name\":\"Order\",\"fields\":[{\"name\":\"id\",\"type\":\"string\"}]}'

# WHEN: a kafka_sr:write-scoped caller calls kafka_sr__register_schema
# THEN: the schema is registered against the real Karapace instance (not a
#       mock) -- mcp_schema_registry kind:client's own generated composite
#       (mcp_openapi -> mcp_http -> http_client) talks to an actual Schema
#       Registry end to end, and the tool's summary interpolates the id
#       Karapace actually assigned via ${result.id}
call_register_schema() {
  REGISTER_OUT=$(docker compose run --rm --no-deps \
      -e JWT_TOKEN="$JWT_FULL" \
      -e MCP_URL="http://zilla:$PORT/mcp" \
      -e CALL_TOOL="kafka_sr__register_schema" \
      -e CALL_ARGS="{\"subject\":\"$SR_SUBJECT\",\"schemaType\":\"AVRO\",\"schema\":\"$SR_SCHEMA\"}" \
      tools-list-client 2>&1)
  echo "$REGISTER_OUT" | grep -q 'Registered schema with id'
}
retry_until 10 3 call_register_schema
echo "REGISTER_OUT=$REGISTER_OUT"
if echo "$REGISTER_OUT" | grep -q 'Registered schema with id'; then
  echo "✅ kafka_sr__register_schema registered a real schema against Karapace"
else
  echo "❌ kafka_sr__register_schema did not succeed against Karapace"
  EXIT=1
fi

# WHEN: that same caller calls kafka_sr__list_subjects and describe_subject
# THEN: the subject registered above comes back as real, persisted Karapace
#       state -- not just an echo of the register call
call_list_subjects() {
  LIST_SUBJECTS_OUT=$(docker compose run --rm --no-deps \
      -e JWT_TOKEN="$JWT_FULL" \
      -e MCP_URL="http://zilla:$PORT/mcp" \
      -e CALL_TOOL="kafka_sr__list_subjects" \
      tools-list-client 2>&1)
  echo "$LIST_SUBJECTS_OUT" | grep -q "$SR_SUBJECT"
}
retry_until 5 3 call_list_subjects
echo "LIST_SUBJECTS_OUT=$LIST_SUBJECTS_OUT"
if echo "$LIST_SUBJECTS_OUT" | grep -q "$SR_SUBJECT"; then
  echo "✅ kafka_sr__list_subjects saw the registered subject in real Karapace state"
else
  echo "❌ kafka_sr__list_subjects did not see the registered subject"
  EXIT=1
fi

call_describe_subject() {
  DESCRIBE_SUBJECT_OUT=$(docker compose run --rm --no-deps \
      -e JWT_TOKEN="$JWT_FULL" \
      -e MCP_URL="http://zilla:$PORT/mcp" \
      -e CALL_TOOL="kafka_sr__describe_subject" \
      -e CALL_ARGS="{\"subject\":\"$SR_SUBJECT\"}" \
      tools-list-client 2>&1)
  echo "$DESCRIBE_SUBJECT_OUT" | grep -q '\[1\]'
}
retry_until 5 3 call_describe_subject
echo "DESCRIBE_SUBJECT_OUT=$DESCRIBE_SUBJECT_OUT"
if echo "$DESCRIBE_SUBJECT_OUT" | grep -q '\[1\]'; then
  echo "✅ kafka_sr__describe_subject listed version 1 of the registered subject"
else
  echo "❌ kafka_sr__describe_subject did not list the registered version"
  EXIT=1
fi

# WHEN: that same caller calls kafka_sr__get_schema for the registered
#       subject/version
# THEN: the schema is read back, with two result fields (${result.id},
#       ${result.version}) interpolated into the summary at once
call_get_schema() {
  GET_SCHEMA_OUT=$(docker compose run --rm --no-deps \
      -e JWT_TOKEN="$JWT_FULL" \
      -e MCP_URL="http://zilla:$PORT/mcp" \
      -e CALL_TOOL="kafka_sr__get_schema" \
      -e CALL_ARGS="{\"subject\":\"$SR_SUBJECT\",\"version\":\"latest\"}" \
      tools-list-client 2>&1)
  echo "$GET_SCHEMA_OUT" | grep -q 'Retrieved schema id 1, version 1'
}
retry_until 5 3 call_get_schema
echo "GET_SCHEMA_OUT=$GET_SCHEMA_OUT"
if echo "$GET_SCHEMA_OUT" | grep -q 'Retrieved schema id 1, version 1'; then
  echo "✅ kafka_sr__get_schema read the schema back with both result fields interpolated"
else
  echo "❌ kafka_sr__get_schema did not read the schema back as expected"
  EXIT=1
fi

# WHEN: that same caller calls kafka_sr__set_compatibility then
#       get_compatibility for the registered subject
# THEN: a freshly registered subject has no compatibility level configured
#       until set_compatibility establishes one -- a real Karapace behavior
#       this example surfaces rather than papering over with a default
call_set_compatibility() {
  SET_COMPAT_OUT=$(docker compose run --rm --no-deps \
      -e JWT_TOKEN="$JWT_FULL" \
      -e MCP_URL="http://zilla:$PORT/mcp" \
      -e CALL_TOOL="kafka_sr__set_compatibility" \
      -e CALL_ARGS="{\"subject\":\"$SR_SUBJECT\",\"compatibility\":\"FULL\"}" \
      tools-list-client 2>&1)
  echo "$SET_COMPAT_OUT" | grep -q 'Compatibility level set to FULL'
}
retry_until 5 3 call_set_compatibility
echo "SET_COMPAT_OUT=$SET_COMPAT_OUT"
if echo "$SET_COMPAT_OUT" | grep -q 'Compatibility level set to FULL'; then
  echo "✅ kafka_sr__set_compatibility set a compatibility level on the registered subject"
else
  echo "❌ kafka_sr__set_compatibility did not succeed as expected"
  EXIT=1
fi

call_get_compatibility() {
  GET_COMPAT_OUT=$(docker compose run --rm --no-deps \
      -e JWT_TOKEN="$JWT_FULL" \
      -e MCP_URL="http://zilla:$PORT/mcp" \
      -e CALL_TOOL="kafka_sr__get_compatibility" \
      -e CALL_ARGS="{\"subject\":\"$SR_SUBJECT\"}" \
      tools-list-client 2>&1)
  echo "$GET_COMPAT_OUT" | grep -q 'Compatibility level is FULL'
}
retry_until 5 3 call_get_compatibility
echo "GET_COMPAT_OUT=$GET_COMPAT_OUT"
if echo "$GET_COMPAT_OUT" | grep -q 'Compatibility level is FULL'; then
  echo "✅ kafka_sr__get_compatibility read back the level set above"
else
  echo "❌ kafka_sr__get_compatibility did not succeed as expected"
  EXIT=1
fi

# WHEN: that same caller calls kafka_sr__check_compatibility against
#       the configured compatibility level
# THEN: the identical schema is reported compatible
call_check_compatibility() {
  CHECK_COMPAT_OUT=$(docker compose run --rm --no-deps \
      -e JWT_TOKEN="$JWT_FULL" \
      -e MCP_URL="http://zilla:$PORT/mcp" \
      -e CALL_TOOL="kafka_sr__check_compatibility" \
      -e CALL_ARGS="{\"subject\":\"$SR_SUBJECT\",\"version\":\"1\",\"schemaType\":\"AVRO\",\"schema\":\"$SR_SCHEMA\"}" \
      tools-list-client 2>&1)
  echo "$CHECK_COMPAT_OUT" | grep -q 'Compatibility check result: true'
}
retry_until 5 3 call_check_compatibility
echo "CHECK_COMPAT_OUT=$CHECK_COMPAT_OUT"
if echo "$CHECK_COMPAT_OUT" | grep -q 'Compatibility check result: true'; then
  echo "✅ kafka_sr__check_compatibility reported the identical schema as compatible"
else
  echo "❌ kafka_sr__check_compatibility did not succeed as expected"
  EXIT=1
fi

# WHEN: a kafka:tools-scoped caller calls kafka__produce
# THEN: the record reaches the real, single-node KRaft Kafka broker started by
#       this example -- not the engine's `type: test` double specs/ITs use --
#       proving mcp_kafka's kind:client composite generator (kafka_cache_client
#       -> kafka_client -> tcp_client) talks to an actual broker end to end
call_kafka_produce() {
  KAFKA_PRODUCE_OUT=$(docker compose run --rm --no-deps \
      -e JWT_TOKEN="$JWT_FULL" \
      -e MCP_URL="http://zilla:$PORT/mcp" \
      -e CALL_TOOL="kafka__produce" \
      -e CALL_ARGS='{"topic":"orders","value":"hello from mcp-kafka"}' \
      tools-list-client 2>&1)
  echo "$KAFKA_PRODUCE_OUT" | grep -q 'Produced record to orders topic'
}
retry_until 10 3 call_kafka_produce
echo "KAFKA_PRODUCE_OUT=$KAFKA_PRODUCE_OUT"
if echo "$KAFKA_PRODUCE_OUT" | grep -q 'Produced record to orders topic'; then
  echo "✅ kafka__produce wrote a record to the real Kafka broker"
else
  echo "❌ kafka__produce did not succeed against the real broker"
  EXIT=1
fi

# WHEN: that same caller calls kafka__consume for the same topic
# THEN: the exact value produced above comes back in structuredContent.messages
#       -- round-tripping through the real broker, not just proving a count
call_kafka_consume() {
  KAFKA_CONSUME_OUT=$(docker compose run --rm --no-deps \
      -e JWT_TOKEN="$JWT_FULL" \
      -e MCP_URL="http://zilla:$PORT/mcp" \
      -e CALL_TOOL="kafka__consume" \
      -e CALL_ARGS='{"topic":"orders","limit":1}' \
      tools-list-client 2>&1)
  echo "$KAFKA_CONSUME_OUT" | grep -q 'hello from mcp-kafka'
}
retry_until 10 3 call_kafka_consume
echo "KAFKA_CONSUME_OUT=$KAFKA_CONSUME_OUT"
if echo "$KAFKA_CONSUME_OUT" | grep -q 'hello from mcp-kafka'; then
  echo "✅ kafka__consume read the produced record back from the real Kafka broker"
else
  echo "❌ kafka__consume did not read the produced record back"
  EXIT=1
fi

exit $EXIT
