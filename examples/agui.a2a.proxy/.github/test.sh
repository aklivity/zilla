#!/bin/sh
set -x

. "$(CDPATH= cd -- "$(dirname -- "$0")/../../.github" && pwd)/test-lib.sh"

EXIT=0

# GIVEN
ENDPOINT="http://localhost:7117/agents/assistant"
echo \# Testing agui.a2a.proxy/
echo ENDPOINT="$ENDPOINT"
echo

run() {
  curl -sS --no-buffer --max-time "${3:-20}" -X POST "$ENDPOINT" \
    -H 'content-type: application/json' -H 'accept: text/event-stream' \
    -d "$1" > "$2"
}

text_of() {
  grep '"type":"TEXT_MESSAGE_CONTENT"' "$1" | sed -E 's/.*"delta":"([^"]*)".*/\1/' | tr -d '\n'
}

check() {
  if [ "$1" -eq 0 ]
  then
    echo "✅ $2"
  else
    fail "$2"
  fi
}

OUT=$(mktemp -d)

# 1. a run streams AG-UI events and echoes the client-chosen thread and run ids
first_run() {
  run '{"threadId":"thread-1","runId":"run-1","messages":[{"id":"msg-1","role":"user","content":"hello"}],"tools":[],"context":[],"forwardedProps":{}}' "$OUT/run1"
  grep -q '"type":"RUN_FINISHED"' "$OUT/run1"
}
retry_until 5 2 first_run
RESULT=$?
cat "$OUT/run1"
check "$RESULT" "run 1 reaches RUN_FINISHED"

grep -q '"type":"RUN_STARTED","threadId":"thread-1","runId":"run-1"' "$OUT/run1"
check $? "run 1 echoes the client thread and run ids on RUN_STARTED"

grep -q '"type":"RUN_FINISHED","threadId":"thread-1","runId":"run-1"' "$OUT/run1"
check $? "run 1 echoes the client thread and run ids on RUN_FINISHED"

grep -q '"type":"TEXT_MESSAGE_START"' "$OUT/run1" && grep -q '"type":"TEXT_MESSAGE_END"' "$OUT/run1"
check $? "run 1 streams a text message"

TEXT1=$(text_of "$OUT/run1")
echo TEXT1="$TEXT1"
echo "$TEXT1" | grep -q 'turn 1\] you said: hello$'
check $? "run 1 reply is the agent's first turn"
CONTEXT1=$(echo "$TEXT1" | sed -E 's/^\[([^ ]*) turn.*/\1/')

# 2. a second run on the same threadId continues the A2A context
run '{"threadId":"thread-1","runId":"run-2","parentRunId":"run-1","messages":[{"id":"msg-1","role":"user","content":"hello"},{"id":"msg-2","role":"assistant","content":"'"$TEXT1"'"},{"id":"msg-3","role":"user","content":"again"}],"tools":[],"context":[],"forwardedProps":{}}' "$OUT/run2"
cat "$OUT/run2"
TEXT2=$(text_of "$OUT/run2")
echo TEXT2="$TEXT2"
echo "$TEXT2" | grep -q "^\[$CONTEXT1 turn 2\] you said: again$"
check $? "run 2 continues the context of thread-1 and forwards only the latest user turn"

grep -q '"type":"MESSAGES_SNAPSHOT"' "$OUT/run2"
if [ $? -ne 0 ]
then
  echo "✅ run 2 emits no MESSAGES_SNAPSHOT while the context is remembered"
else
  fail "run 2 emits no MESSAGES_SNAPSHOT while the context is remembered"
fi

# 3. another thread gets its own A2A context
run '{"threadId":"thread-2","runId":"run-3","messages":[{"id":"msg-4","role":"user","content":"hello"}],"tools":[],"context":[],"forwardedProps":{}}' "$OUT/run3"
TEXT3=$(text_of "$OUT/run3")
echo TEXT3="$TEXT3"
echo "$TEXT3" | grep -q 'turn 1\] you said: hello$' && ! echo "$TEXT3" | grep -q "^\[$CONTEXT1 "
check $? "thread-2 starts a new context"

# 4. a history the agent has no context for is compacted with MESSAGES_SNAPSHOT
run '{"threadId":"thread-3","runId":"run-4","messages":[{"id":"msg-5","role":"user","content":"first"},{"id":"msg-6","role":"assistant","content":"lost"},{"id":"msg-7","role":"user","content":"after restart"}],"tools":[],"context":[],"forwardedProps":{}}' "$OUT/run4"
cat "$OUT/run4"
grep '"type":"MESSAGES_SNAPSHOT"' "$OUT/run4" | grep -q '"id":"msg-7"' && ! grep '"type":"MESSAGES_SNAPSHOT"' "$OUT/run4" | grep -q '"id":"msg-5"'
check $? "an unremembered thread with history emits MESSAGES_SNAPSHOT holding only the latest user turn"

SNAPSHOT_LINE=$(grep -n '"type":"MESSAGES_SNAPSHOT"' "$OUT/run4" | head -1 | cut -d: -f1)
START_LINE=$(grep -n '"type":"TEXT_MESSAGE_START"' "$OUT/run4" | head -1 | cut -d: -f1)
[ -n "$SNAPSHOT_LINE" ] && [ -n "$START_LINE" ] && [ "$SNAPSHOT_LINE" -lt "$START_LINE" ]
check $? "MESSAGES_SNAPSHOT precedes the reply text"

# 5. input-required ends the run as an interrupt and resume continues the same A2A task
run '{"threadId":"thread-4","runId":"run-5","messages":[{"id":"msg-8","role":"user","content":"ask: which one"}],"tools":[],"context":[],"forwardedProps":{}}' "$OUT/run5"
cat "$OUT/run5"
grep -q '"type":"RUN_FINISHED".*"interrupt"' "$OUT/run5"
check $? "input-required ends the run with an interrupt outcome"

INTERRUPT_ID=$(grep '"type":"RUN_FINISHED"' "$OUT/run5" | sed -E 's/.*"interrupts":\[\{"id":"([^"]*)".*/\1/')
echo INTERRUPT_ID="$INTERRUPT_ID"
grep -q 'Which one do you mean?' "$OUT/run5"
check $? "the agent's question travels in the interrupt"

run '{"threadId":"thread-4","runId":"run-6","parentRunId":"run-5","messages":[],"tools":[],"context":[],"forwardedProps":{},"resume":[{"interruptId":"'"$INTERRUPT_ID"'","status":"resolved","payload":"the blue one"}]}' "$OUT/run6"
cat "$OUT/run6"
TEXT6=$(text_of "$OUT/run6")
echo TEXT6="$TEXT6"
echo "$TEXT6" | grep -q 'resumed task-[0-9]* with: the blue one$' && grep -q '"type":"RUN_FINISHED"' "$OUT/run6" && ! grep '"type":"RUN_FINISHED"' "$OUT/run6" | grep -q interrupt
check $? "resume continues the interrupted A2A task and finishes the run"

# 5. an unrouted path is rejected
STATUS=$(curl -sS -o /dev/null -w '%{http_code}' -X POST http://localhost:7117/agents/unknown \
  -H 'content-type: application/json' -H 'accept: text/event-stream' \
  -d '{"threadId":"thread-5","runId":"run-7","messages":[{"id":"msg-9","role":"user","content":"hello"}]}')
echo STATUS="$STATUS"
[ "$STATUS" = "404" ] || [ "$STATUS" = "403" ]
check $? "an agent without a route is rejected"

# 6. aborting a run cancels the A2A task
run '{"threadId":"thread-6","runId":"run-8","messages":[{"id":"msg-10","role":"user","content":"slow: take your time"}],"tools":[],"context":[],"forwardedProps":{}}' "$OUT/run8" 2
cancelled() {
  CANCELLED=$(docker compose -p zilla-agui-a2a-proxy exec -T agent wget -qO- http://127.0.0.1:8080/cancelled)
  [ -n "$CANCELLED" ] && [ "$CANCELLED" != "[]" ]
}
retry_until 5 1 cancelled
check $? "aborting the run cancelled the agent task: $CANCELLED"

report_failures
rm -rf "$OUT"
exit $EXIT
