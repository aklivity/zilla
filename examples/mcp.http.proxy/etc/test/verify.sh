#!/bin/sh
# Runs inside the compose stack as the `verify` service; .github/test.sh only
# starts it. Assertions, all at the Zilla layer:
#   1. a caller with github:tools lists get_pr and the pull_by_number template
#      but not create_pr (toolkit access is not tool access)
#   2. a caller with github:tools and github:pr:write also lists create_pr
#   3. get_pr reads a pull request through the route's :path template
#   4. create_pr forwards title/head/base as the request body (with.body) and
#      renders the result summary
#   5. ghapi observes the caller identity forwarded by options.authorization
#   6. a create_pr call missing a required argument is rejected by the input schema
#   7. a create_pr call with a wrongly typed argument is rejected by the input schema
#   8. neither rejected call reached ghapi
#   9. the pull_by_number resource template reads through with captured params
#  10. MCP metrics count the tool calls by outcome
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
SCOPE_PARTIAL="github:tools"
SCOPE_FULL="github:tools github:pr:write"

echo "# Testing mcp.http.proxy"

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
    exec timeout 60 node /work/client/client.mjs 2>&1
  )
}

list_tools() {
  mcp_run -e JWT_SCOPE="$1"
}

call_tool() {
  mcp_run -e JWT_SCOPE="$1" -e CALL_TOOL="$2" -e CALL_ARGS="$3"
}

read_resource() {
  mcp_run -e JWT_SCOPE="$1" -e READ_RESOURCE="$2"
}

# WHEN: a github:tools caller lists tools, resources and templates
# THEN: get_pr and the pull_by_number template are listed; create_pr is not
list_partial() {
  TOOLS_PARTIAL=$(list_tools "$SCOPE_PARTIAL")
  echo "$TOOLS_PARTIAL" | grep -q '^github__get_pr$'
}
retry_deadline 60 2 list_partial
echo "TOOLS_PARTIAL=$TOOLS_PARTIAL"
if echo "$TOOLS_PARTIAL" | grep -q '^github__get_pr$' &&
    echo "$TOOLS_PARTIAL" | grep -q '^template:github+pr://{owner}/{repo}/{number}$' &&
    ! echo "$TOOLS_PARTIAL" | grep -q '^github__create_pr$'; then
  echo "✅ github:tools lists get_pr and the pull_by_number template, not create_pr"
else
  fail "github:tools did not list get_pr and the template while hiding create_pr"
fi

# WHEN: a caller with github:tools and github:pr:write lists tools
# THEN: create_pr is listed alongside get_pr and the template
TOOLS_FULL=$(list_tools "$SCOPE_FULL")
echo "TOOLS_FULL=$TOOLS_FULL"
if echo "$TOOLS_FULL" | grep -q '^github__create_pr$' &&
    echo "$TOOLS_FULL" | grep -q '^github__get_pr$' &&
    echo "$TOOLS_FULL" | grep -q '^template:github+pr://{owner}/{repo}/{number}$'; then
  echo "✅ github:pr:write adds create_pr to the listing"
else
  fail "github:pr:write did not add create_pr to the listing"
fi

# WHEN: a github:tools caller calls get_pr for the seeded pull request
# THEN: the summary and result come back, proving ${args.x} filled the :path
GET_PR_OUT=$(call_tool "$SCOPE_PARTIAL" github__get_pr '{"owner":"acme","repo":"widget","number":42}')
echo "GET_PR_OUT=$GET_PR_OUT"
if echo "$GET_PR_OUT" | grep -q 'Pull request #42 is open: Seed data for the pull_by_number resource demo'; then
  echo "✅ github__get_pr read the seeded pull request"
else
  fail "github__get_pr did not read the seeded pull request"
fi

# WHEN: a full-scope caller calls create_pr
# THEN: title/head/base reach ghapi as the JSON body (owner/repo only in the
#       path) and ghapi's echoed title surfaces in the result summary
CREATE_PR_OUT=$(call_tool "$SCOPE_FULL" github__create_pr \
    '{"owner":"acme","repo":"widget","title":"Add feature","head":"feature","base":"main"}')
echo "CREATE_PR_OUT=$CREATE_PR_OUT"
if echo "$CREATE_PR_OUT" | grep -q 'Created pull request #101: Add feature'; then
  echo "✅ github__create_pr forwarded title/head/base to ghapi as the request body"
else
  fail "github__create_pr did not forward the call arguments as the request body"
fi

# WHEN: the pull request created above is read back
# THEN: ghapi recorded who opened it from the x-user-identity header Zilla
#       forwards via options.authorization -- the caller's own JWT subject
OPENED_OUT=$(call_tool "$SCOPE_FULL" github__get_pr '{"owner":"acme","repo":"widget","number":101}')
echo "OPENED_OUT=$OPENED_OUT"
if echo "$OPENED_OUT" | grep -q '"opened_by":"alice"'; then
  echo "✅ ghapi observed the caller's identity forwarded by options.authorization"
else
  fail "ghapi did not observe the caller's forwarded identity"
fi

# WHEN: a full-scope caller omits the required title
# THEN: the input schema rejects the call before any upstream request is built
MISSING_OUT=$(call_tool "$SCOPE_FULL" github__create_pr \
    '{"owner":"acme","repo":"widget","head":"feature","base":"main"}')
echo "MISSING_OUT=$MISSING_OUT"
if echo "$MISSING_OUT" | grep -q '^ERROR MCP error -32602'; then
  echo "✅ a call missing a required argument is rejected by the input schema"
else
  fail "a call missing a required argument was not rejected"
fi

# WHEN: a full-scope caller passes title as a number
# THEN: the input schema rejects the call
WRONG_TYPE_OUT=$(call_tool "$SCOPE_FULL" github__create_pr \
    '{"owner":"acme","repo":"widget","title":42,"head":"feature","base":"main"}')
echo "WRONG_TYPE_OUT=$WRONG_TYPE_OUT"
if echo "$WRONG_TYPE_OUT" | grep -q '^ERROR MCP error -32602'; then
  echo "✅ a wrongly typed argument is rejected by the input schema"
else
  fail "a wrongly typed argument was not rejected"
fi

# WHEN: the rejected calls above are checked against ghapi
# THEN: neither created a pull request -- only #101 exists, #102 does not
AFTER_OUT=$(call_tool "$SCOPE_FULL" github__get_pr '{"owner":"acme","repo":"widget","number":102}')
echo "AFTER_OUT=$AFTER_OUT"
if echo "$AFTER_OUT" | grep -q 'pull request not found'; then
  echo "✅ rejected calls never reached ghapi"
else
  fail "a rejected call created a pull request"
fi

# WHEN: a github:tools caller reads the pull_by_number template at a concrete URI
# THEN: the pull request comes back, proving the captured ${params.x} filled :path
PULL_OUT=$(read_resource "$SCOPE_PARTIAL" "github+pr://acme/widget/42")
echo "PULL_OUT=$PULL_OUT"
if echo "$PULL_OUT" | grep -q 'Seed data for the pull_by_number resource demo'; then
  echo "✅ github+pr://acme/widget/42 read end-to-end via the pull_by_number template"
else
  fail "pull_by_number resource template did not read through as configured"
fi

# WHEN: the Prometheus endpoint is scraped after the calls above
# THEN: github__create_pr is counted as ok for the created pull request and
#       as error for the two rejected calls
METRICS=$(wget -qO- -T 10 "$METRICS_ENDPOINT" | grep '^mcp_tools_call_total')
echo "METRICS=$METRICS"
if echo "$METRICS" | grep -q 'outcome="ok",tool="github__create_pr"} [1-9]' &&
    echo "$METRICS" | grep -q 'outcome="error",tool="github__create_pr"} [1-9]'; then
  echo "✅ mcp_tools_call_total counts github__create_pr by outcome"
else
  fail "mcp_tools_call_total did not count github__create_pr by outcome"
fi

report_failures
exit $EXIT
