#!/bin/sh

# Assertions, all at the Zilla layer:
#   1. an OpenAI-dialect client talking to the openai-facing frontend
#      (north_llm_server_openai, port 7161) gets back an OpenAI-shaped
#      response, even though the real upstream (mock-anthropic) only speaks
#      the Anthropic dialect -- proving request+response cross-dialect
#      translation, openai in -> anthropic upstream -> openai out
#   2. the same round trip, but the request carries a `tools` definition, and
#      the response carries an OpenAI-shaped `tool_calls` entry translated
#      from the upstream's native Anthropic `tool_use` content block
#   3. an Anthropic-dialect client talking to the anthropic-facing frontend
#      (north_llm_server_anthropic, port 7162) gets back an Anthropic-shaped
#      response, even though the real upstream (mock-openai) only speaks the
#      OpenAI dialect -- the same translation, mirrored
#   4. the same round trip, but with `tools`, translating the upstream's
#      native OpenAI `tool_calls` into an Anthropic-shaped `tool_use` block
#   5. south_llm_client_anthropic forwards the caller's own bearer token
#      upstream as `x-api-key`, unwrapped from "Bearer " and re-wrapped with
#      no prefix (options.authorization on both llm bindings, same guard)
#   6. south_llm_client_openai forwards the caller's own `x-api-key` upstream
#      as `Authorization: Bearer ...`, the same pass-through mirrored

set -x

FAIL_FAST=${FAIL_FAST:-1}

. /test-lib.sh

EXIT=0
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

# In-network hostname, not a published localhost port: this runs on the
# compose network alongside everything else.
OPENAI_URL="http://zilla:7161/v1/chat/completions"
ANTHROPIC_URL="http://zilla:7162/v1/messages"

# WHEN: an OpenAI-dialect client sends a plain chat request to the
#       openai-facing frontend, backed by the Anthropic-only mock-anthropic
# THEN: the response is OpenAI-shaped (choices[0].message.content), proving
#       the anthropic-native reply was translated back to openai on the way out
call_openai_text() {
  OPENAI_TEXT_OUT=$(curl -sS --max-time 10 \
      -X POST "$OPENAI_URL" \
      -H "Content-Type: application/json" \
      -H "Authorization: Bearer test-openai-key" \
      -d '{"model":"gpt-4","messages":[{"role":"user","content":"Hello"}]}')
  echo "$OPENAI_TEXT_OUT" | grep -q '"content":"Hello! How can I help you today?"'
}
timed openai_text retry_until 10 1 call_openai_text
echo OPENAI_TEXT_OUT="$OPENAI_TEXT_OUT"
if echo "$OPENAI_TEXT_OUT" | grep -q '"content":"Hello! How can I help you today?"' \
    && echo "$OPENAI_TEXT_OUT" | grep -q '"finish_reason":"stop"'
then
  echo "✅ openai-facing frontend translated an anthropic-native reply back to openai"
else
  fail "openai-facing frontend did not return an openai-shaped text response"
fi

# WHEN: the same client instead sends a request carrying a `tools` definition
# THEN: the response carries an openai-shaped tool_calls entry, translated
#       from mock-anthropic's native tool_use content block
call_openai_tools() {
  OPENAI_TOOLS_OUT=$(curl -sS --max-time 10 \
      -X POST "$OPENAI_URL" \
      -H "Content-Type: application/json" \
      -H "Authorization: Bearer test-openai-key" \
      -d '{"model":"gpt-4","messages":[{"role":"user","content":"What is the weather in Paris?"}],"tools":[{"type":"function","function":{"name":"get_weather","parameters":{"type":"object","properties":{"city":{"type":"string"}}}}}]}')
  echo "$OPENAI_TOOLS_OUT" | grep -q '"finish_reason":"tool_calls"'
}
timed openai_tools retry_until 10 1 call_openai_tools
echo OPENAI_TOOLS_OUT="$OPENAI_TOOLS_OUT"
if echo "$OPENAI_TOOLS_OUT" | grep -q '"name":"get_weather"' \
    && echo "$OPENAI_TOOLS_OUT" | grep -q '"finish_reason":"tool_calls"'
then
  echo "✅ openai-facing frontend translated mock-anthropic's tool_use into an openai tool_calls entry"
else
  fail "openai-facing frontend did not return an openai-shaped tool_calls response"
fi

# WHEN: an Anthropic-dialect client sends a plain messages request to the
#       anthropic-facing frontend, backed by the OpenAI-only mock-openai
# THEN: the response is Anthropic-shaped (content[0].type=text), proving the
#       openai-native reply was translated back to anthropic on the way out
call_anthropic_text() {
  ANTHROPIC_TEXT_OUT=$(curl -sS --max-time 10 \
      -X POST "$ANTHROPIC_URL" \
      -H "Content-Type: application/json" \
      -H "anthropic-version: 2023-06-01" \
      -H "x-api-key: test-anthropic-key" \
      -d '{"model":"claude-3-opus-20240229","max_tokens":1024,"messages":[{"role":"user","content":"Hello"}]}')
  echo "$ANTHROPIC_TEXT_OUT" | grep -q '"type":"text"'
}
timed anthropic_text retry_until 10 1 call_anthropic_text
echo ANTHROPIC_TEXT_OUT="$ANTHROPIC_TEXT_OUT"
if echo "$ANTHROPIC_TEXT_OUT" | grep -q '"text":"Hello! How can I help you today?"' \
    && echo "$ANTHROPIC_TEXT_OUT" | grep -q '"stop_reason":"end_turn"'
then
  echo "✅ anthropic-facing frontend translated an openai-native reply back to anthropic"
else
  fail "anthropic-facing frontend did not return an anthropic-shaped text response"
fi

# WHEN: the same client instead sends a request carrying a `tools` definition
# THEN: the response carries an anthropic-shaped tool_use block, translated
#       from mock-openai's native tool_calls entry
call_anthropic_tools() {
  ANTHROPIC_TOOLS_OUT=$(curl -sS --max-time 10 \
      -X POST "$ANTHROPIC_URL" \
      -H "Content-Type: application/json" \
      -H "anthropic-version: 2023-06-01" \
      -H "x-api-key: test-anthropic-key" \
      -d '{"model":"claude-3-opus-20240229","max_tokens":1024,"messages":[{"role":"user","content":"What is the weather in Paris?"}],"tools":[{"name":"get_weather","input_schema":{"type":"object","properties":{"city":{"type":"string"}}}}]}')
  echo "$ANTHROPIC_TOOLS_OUT" | grep -q '"stop_reason":"tool_use"'
}
timed anthropic_tools retry_until 10 1 call_anthropic_tools
echo ANTHROPIC_TOOLS_OUT="$ANTHROPIC_TOOLS_OUT"
if echo "$ANTHROPIC_TOOLS_OUT" | grep -q '"type":"tool_use"' \
    && echo "$ANTHROPIC_TOOLS_OUT" | grep -q '"name":"get_weather"'
then
  echo "✅ anthropic-facing frontend translated mock-openai's tool_calls into an anthropic tool_use block"
else
  fail "anthropic-facing frontend did not return an anthropic-shaped tool_use response"
fi

# Reads a compose service's own docker logs, by compose project + service
# label, so the credential pass-through assertions below can confirm what a
# mock backend actually received on the wire.
service_logs() {
  docker logs "$(docker ps -aq \
      --filter "label=com.docker.compose.project=$COMPOSE_PROJECT" \
      --filter "label=com.docker.compose.service=$1")" 2>&1
}

COMPOSE_PROJECT=$(docker inspect -f '{{ index .Config.Labels "com.docker.compose.project" }}' "$(hostname)")

# WHEN: a caller presents its own bearer token to the openai-facing frontend
# THEN: south_llm_client_anthropic forwards that exact token upstream as
#       x-api-key (no "Bearer " prefix) -- the caller's own credential, not a
#       separate secret configured in zilla.yaml
FORWARDED_OPENAI_TOKEN="caller-openai-token-$$"
call_credential_pass_through_to_anthropic() {
  curl -sS --max-time 10 -o /dev/null \
      -X POST "$OPENAI_URL" \
      -H "Content-Type: application/json" \
      -H "Authorization: Bearer $FORWARDED_OPENAI_TOKEN" \
      -d '{"model":"gpt-4","messages":[{"role":"user","content":"Hello"}]}'
  MOCK_ANTHROPIC_LOGS=$(service_logs mock-anthropic)
  echo "$MOCK_ANTHROPIC_LOGS" | grep -q "x-api-key: $FORWARDED_OPENAI_TOKEN"
}
timed credential_pass_through_to_anthropic retry_until 10 1 call_credential_pass_through_to_anthropic
echo MOCK_ANTHROPIC_LOGS="$MOCK_ANTHROPIC_LOGS"
if echo "$MOCK_ANTHROPIC_LOGS" | grep -q "x-api-key: $FORWARDED_OPENAI_TOKEN"
then
  echo "✅ south_llm_client_anthropic forwarded the caller's own token to mock-anthropic as x-api-key"
else
  fail "south_llm_client_anthropic did not forward the caller's token to mock-anthropic"
fi

# WHEN: a caller presents its own x-api-key to the anthropic-facing frontend
# THEN: south_llm_client_openai forwards that exact token upstream as
#       Authorization: Bearer ... -- the caller's own credential, not a
#       separate secret configured in zilla.yaml
FORWARDED_ANTHROPIC_TOKEN="caller-anthropic-token-$$"
call_credential_pass_through_to_openai() {
  curl -sS --max-time 10 -o /dev/null \
      -X POST "$ANTHROPIC_URL" \
      -H "Content-Type: application/json" \
      -H "anthropic-version: 2023-06-01" \
      -H "x-api-key: $FORWARDED_ANTHROPIC_TOKEN" \
      -d '{"model":"claude-3-opus-20240229","max_tokens":1024,"messages":[{"role":"user","content":"Hello"}]}'
  MOCK_OPENAI_LOGS=$(service_logs mock-openai)
  echo "$MOCK_OPENAI_LOGS" | grep -q "authorization: Bearer $FORWARDED_ANTHROPIC_TOKEN"
}
timed credential_pass_through_to_openai retry_until 10 1 call_credential_pass_through_to_openai
echo MOCK_OPENAI_LOGS="$MOCK_OPENAI_LOGS"
if echo "$MOCK_OPENAI_LOGS" | grep -q "authorization: Bearer $FORWARDED_ANTHROPIC_TOKEN"
then
  echo "✅ south_llm_client_openai forwarded the caller's own token to mock-openai as Authorization: Bearer"
else
  fail "south_llm_client_openai did not forward the caller's token to mock-openai"
fi

report_timings
report_failures

exit $EXIT
