# Smoke test for the mcp-openapi petstore toolkit, run inside the compose
# network by the `verify` service (see compose.yaml).
#
#   1. initialize negotiates MCP protocol version 2025-11-25
#   2. a token the guard cannot verify is rejected with 401
#   3. petstore:tools alone lists the read operations, a static resource and a
#      resource template, but not create_pet
#   4. adding pets:write lists create_pet as well
#   5. list_pets reaches the mock, so the server override replaced the host
#      the OpenAPI document declares
#   6. search_pets renames its argument: {"category":"cat"} filters on ?tag=cat
#   7. the static featured_pets resource reads through
#   8. the templated pet_by_id resource reads a concrete pet
#   9. create_pet does not succeed without pets:write, and creates no pet
#  10. create_pet succeeds with pets:write, with the summary from its output override
#  11. create_pet rejects arguments that violate its input schema override
#  12. the created pet reads back through the pet_by_id template

. /test-lib.sh

EXIT=0
ZILLA="http://zilla:7114/mcp"
INITIALIZE='{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-11-25","capabilities":{},"clientInfo":{"name":"zilla-mcp-openapi-proxy-test","version":"0.0.1"}}}'

JWT_PARTIAL=$(node /work/client/mint-jwt.mjs "petstore:tools")
JWT_FULL=$(node /work/client/mint-jwt.mjs "petstore:tools pets:write")

# client <token> [VAR=VALUE]...
client()
{
  _token=$1
  shift
  env MCP_URL="$ZILLA" JWT_TOKEN="$_token" "$@" timeout "${CLIENT_TIMEOUT:-60}" node /work/client/client.mjs 2>&1
}

check()
{
  _description=$1
  _passed=$2
  if [ "$_passed" = "0" ]
  then
    echo "✅ $_description"
  else
    fail "$_description"
  fi
}

contains()
{
  echo "$1" | grep -q -- "$2"
}

initialize()
{
  INIT_BODY=$(wget -q -O - -T 10 \
      --header "Authorization: Bearer $JWT_PARTIAL" \
      --header "Content-Type: application/json" \
      --header "Accept: application/json, text/event-stream" \
      --post-data "$INITIALIZE" \
      "$ZILLA")
  contains "$INIT_BODY" '"protocolVersion":"2025-11-25"'
}
retry_until 10 3 initialize && _rc=0 || _rc=$?
echo "INIT_BODY=$INIT_BODY"
check "initialize negotiated 2025-11-25" $_rc

REJECTED_OUT=$(wget -S -q -O - -T 10 \
    --header "Authorization: Bearer not-a-jwt" \
    --header "Content-Type: application/json" \
    --header "Accept: application/json, text/event-stream" \
    --post-data "$INITIALIZE" \
    "$ZILLA" 2>&1)
echo "REJECTED_OUT=$REJECTED_OUT"
contains "$REJECTED_OUT" 'HTTP/1.1 401' && _rc=0 || _rc=1
check "a token the guard cannot verify is rejected with 401" $_rc

list_partial()
{
  TOOLS_PARTIAL=$(client "$JWT_PARTIAL")
  contains "$TOOLS_PARTIAL" '^petstore__list_pets$'
}
retry_until 10 3 list_partial || true
echo "TOOLS_PARTIAL=$TOOLS_PARTIAL"
contains "$TOOLS_PARTIAL" '^petstore__list_pets$' &&
  contains "$TOOLS_PARTIAL" '^petstore__search_pets$' &&
  contains "$TOOLS_PARTIAL" '^resource:petstore+/pets/featured$' &&
  contains "$TOOLS_PARTIAL" '^template:petstore+/pets/{petId}$' &&
  ! contains "$TOOLS_PARTIAL" '^petstore__create_pet$' && _rc=0 || _rc=1
check "petstore:tools: lists list_pets, search_pets, featured_pets and pet_by_id, but not create_pet" $_rc

TOOLS_FULL=$(client "$JWT_FULL")
echo "TOOLS_FULL=$TOOLS_FULL"
contains "$TOOLS_FULL" '^petstore__create_pet$' &&
  contains "$TOOLS_FULL" '^petstore__list_pets$' && _rc=0 || _rc=1
check "petstore:tools + pets:write: create_pet is listed" $_rc

LIST_OUT=$(client "$JWT_PARTIAL" CALL_TOOL=petstore__list_pets)
echo "LIST_OUT=$LIST_OUT"
contains "$LIST_OUT" 'Bramble' && contains "$LIST_OUT" 'Whiskers' && _rc=0 || _rc=1
check "list_pets reached the mock through the server override" $_rc

SEARCH_OUT=$(client "$JWT_PARTIAL" CALL_TOOL=petstore__search_pets CALL_ARGS='{"category":"cat"}')
echo "SEARCH_OUT=$SEARCH_OUT"
contains "$SEARCH_OUT" 'Whiskers' && ! contains "$SEARCH_OUT" 'Bramble' && _rc=0 || _rc=1
check "search_pets renamed category to the tag query parameter" $_rc

FEATURED_OUT=$(client "$JWT_PARTIAL" READ_RESOURCE=petstore+/pets/featured)
echo "FEATURED_OUT=$FEATURED_OUT"
contains "$FEATURED_OUT" 'Bramble' && ! contains "$FEATURED_OUT" 'Whiskers' && _rc=0 || _rc=1
check "static resource petstore+/pets/featured read through" $_rc

PET_OUT=$(client "$JWT_PARTIAL" READ_RESOURCE=petstore+/pets/2)
echo "PET_OUT=$PET_OUT"
contains "$PET_OUT" 'Whiskers' && ! contains "$PET_OUT" 'Bramble' && _rc=0 || _rc=1
check "templated resource petstore+/pets/{petId} read pet 2" $_rc

DENIED_OUT=$(CLIENT_TIMEOUT=10; client "$JWT_PARTIAL" CALL_TOOL=petstore__create_pet CALL_ARGS='{"name":"Nibbles","tag":"hamster"}') && _rc=1 || _rc=0
echo "DENIED_OUT=$DENIED_OUT"
! contains "$DENIED_OUT" 'Added pet' && [ "$_rc" = "0" ] && _rc=0 || _rc=1
check "create_pet does not succeed without pets:write" $_rc

CREATE_OUT=$(client "$JWT_FULL" CALL_TOOL=petstore__create_pet CALL_ARGS='{"name":"Nibbles","tag":"hamster"}')
echo "CREATE_OUT=$CREATE_OUT"
contains "$CREATE_OUT" 'Added pet: Nibbles' && contains "$CREATE_OUT" '"id":3,' && _rc=0 || _rc=1
check "create_pet succeeded with pets:write, and the denied call above had created no pet" $_rc

INVALID_OUT=$(client "$JWT_FULL" CALL_TOOL=petstore__create_pet CALL_ARGS='{"tag":"hamster"}') && _rc=1 || _rc=0
echo "INVALID_OUT=$INVALID_OUT"
check "create_pet rejects arguments without the name its input schema requires" $_rc

CREATED_ID=$(echo "$CREATE_OUT" | sed -n 's/.*"id":\([0-9][0-9]*\).*/\1/p' | head -n 1)
CREATED_OUT=$(client "$JWT_PARTIAL" READ_RESOURCE="petstore+/pets/$CREATED_ID")
echo "CREATED_OUT=$CREATED_OUT"
contains "$CREATED_OUT" 'Nibbles' && _rc=0 || _rc=1
check "created pet reads back through petstore+/pets/$CREATED_ID" $_rc

report_failures
exit $EXIT
