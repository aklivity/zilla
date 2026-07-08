#!/bin/sh
# Drives the build-workflow verification for examples/mcp.proxy.
#
# Assertions, all at the Zilla layer:
#   1. a url-elicitation-capable client initializes and negotiates 2025-11-25
#   2. a real MCP SDK client drives a url-mode elicitation round-trip end-to-end
#      through the gateway (elicitation/create mode:url + completion notification)
#   3. tools/list is filtered by the caller's JWT scopes: unauthorized toolkits
#      and tools are absent from the result, layered per binding hop
#      (mcp proxy toolkit routes, mcp_http per-tool route, mcp_openapi
#      OpenAPI-native per-operation security)
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
#       calls the urlelicit toolkit's authorize tool through the gateway
# THEN: Zilla relays the mode:url elicitation/create request and the subsequent
#       notifications/elicitation/complete back to the client
relay_elicitation() {
  ELICIT_OUT=$(docker compose run --rm --no-deps \
      -e MCP_URL="http://zilla:$PORT/mcp" \
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
JWT_PARTIAL=$(encode_jwt "github:tools petstore:tools")
JWT_FULL=$(encode_jwt "urlelicit:authorize github:tools github:pr:write petstore:tools pets:write")

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
    ! echo "$TOOLS_NONE" | grep -q 'petstore+'
}
retry_until 5 3 assert_no_token
echo "TOOLS_NONE=$TOOLS_NONE"
if echo "$TOOLS_NONE" | grep -q '^everything__' &&
    ! echo "$TOOLS_NONE" | grep -q '^urlelicit__' &&
    ! echo "$TOOLS_NONE" | grep -q '^github__' &&
    ! echo "$TOOLS_NONE" | grep -q '^petstore__' &&
    ! echo "$TOOLS_NONE" | grep -q 'petstore+'; then
  echo "✅ no token: only the ungated everything toolkit is listed"
else
  echo "❌ no token: tools/list did not filter to only the everything toolkit"
  EXIT=1
fi

# WHEN: a caller has toolkit-level scopes (github:tools, petstore:tools) but
#       none of the finer-grained operation scopes
# THEN: petstore__list_pets and both petstore resources are listed (neither
#       list_pets, list_featured_pets, nor get_pet declare their own
#       operation-level security) but petstore__create_pet and
#       github__create_pr are not (they require pets:write / github:pr:write
#       respectively) -- proof that toolkit access alone does not imply
#       access to every tool/resource in it
assert_partial_token() {
  TOOLS_PARTIAL=$(list_tools "$JWT_PARTIAL")
  echo "$TOOLS_PARTIAL" | grep -q '^petstore__list_pets$' &&
    echo "$TOOLS_PARTIAL" | grep -q '^resource:petstore+/pets/featured$' &&
    echo "$TOOLS_PARTIAL" | grep -q '^template:petstore+/pets/{petId}$' &&
    ! echo "$TOOLS_PARTIAL" | grep -q '^petstore__create_pet$' &&
    ! echo "$TOOLS_PARTIAL" | grep -q '^github__create_pr$' &&
    ! echo "$TOOLS_PARTIAL" | grep -q '^urlelicit__'
}
retry_until 5 3 assert_partial_token
echo "TOOLS_PARTIAL=$TOOLS_PARTIAL"
if echo "$TOOLS_PARTIAL" | grep -q '^petstore__list_pets$' &&
    echo "$TOOLS_PARTIAL" | grep -q '^resource:petstore+/pets/featured$' &&
    echo "$TOOLS_PARTIAL" | grep -q '^template:petstore+/pets/{petId}$' &&
    ! echo "$TOOLS_PARTIAL" | grep -q '^petstore__create_pet$' &&
    ! echo "$TOOLS_PARTIAL" | grep -q '^github__create_pr$' &&
    ! echo "$TOOLS_PARTIAL" | grep -q '^urlelicit__'; then
  echo "✅ toolkit-only scope: sees list_pets and both resources, but not create_pet or create_pr"
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
    echo "$TOOLS_FULL" | grep -q '^petstore__create_pet$' &&
    echo "$TOOLS_FULL" | grep -q '^resource:petstore+/pets/featured$' &&
    echo "$TOOLS_FULL" | grep -q '^template:petstore+/pets/{petId}$'
}
retry_until 5 3 assert_full_token
echo "TOOLS_FULL=$TOOLS_FULL"
if echo "$TOOLS_FULL" | grep -q '^everything__' &&
    echo "$TOOLS_FULL" | grep -q '^urlelicit__authorize$' &&
    echo "$TOOLS_FULL" | grep -q '^github__create_pr$' &&
    echo "$TOOLS_FULL" | grep -q '^petstore__list_pets$' &&
    echo "$TOOLS_FULL" | grep -q '^petstore__create_pet$' &&
    echo "$TOOLS_FULL" | grep -q '^resource:petstore+/pets/featured$' &&
    echo "$TOOLS_FULL" | grep -q '^template:petstore+/pets/{petId}$'; then
  echo "✅ full scope: every toolkit's tools and resources are listed"
else
  echo "❌ full scope did not unlock every toolkit"
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

exit $EXIT
