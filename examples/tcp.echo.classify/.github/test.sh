#!/bin/sh
set -x

. "$(CDPATH= cd -- "$(dirname -- "$0")/../../.github" && pwd)/test-lib.sh"

EXIT=0
PORT="12345"

# The reject phrases are embedded the first time the model is used, which can
# take past a normal request's timeout. Retry a throwaway message until it
# echoes, so the assertions below aren't racing that one-time embedding.
echo \# Warming up tcp.echo.classify/ embedding0 \(first-use reject phrase embedding\)
warm_up() {
  OUTPUT=$(printf '%s\n' "warm up" | nc -w 20 localhost $PORT)
  [ "$OUTPUT" = "warm up" ]
}
retry_until 10 3 warm_up
echo RESULT=$?
echo

# GIVEN
INPUT="Something crazy just happened to me, a stray cat ran straight into my kitchen!"
EXPECTED="$INPUT"
echo \# Testing tcp.echo.classify/ accepted message
echo PORT="$PORT"
echo INPUT="$INPUT"
echo EXPECTED="$EXPECTED"
echo

# WHEN
accepted_message() {
  OUTPUT=$(printf '%s\n' "$INPUT" | nc -w 5 localhost $PORT)
  [ "$OUTPUT" = "$EXPECTED" ]
}
retry_until 5 3 accepted_message
RESULT=$?
echo RESULT="$RESULT"

# THEN
echo OUTPUT="$OUTPUT"
echo EXPECTED="$EXPECTED"
echo
if [ "$RESULT" -eq 0 ] && [ "$OUTPUT" = "$EXPECTED" ]; then
  echo ✅
else
  fail "tcp.echo.classify/ accepted message was not echoed back unchanged"
fi

# GIVEN
INPUT="Something crazy just happened to me but honestly it's too wild to type out."
echo \# Testing tcp.echo.classify/ rejected message \(same opener, withholds the story\)
echo PORT="$PORT"
echo INPUT="$INPUT"
echo EXPECTED="(connection closes, nothing echoed)"
echo

# WHEN
OUTPUT=$(printf '%s\n' "$INPUT" | nc -w 5 localhost $PORT)
RESULT=$?

# THEN
echo OUTPUT="$OUTPUT"
echo RESULT="$RESULT"
echo
if [ -z "$OUTPUT" ]; then
  echo ✅
else
  fail "tcp.echo.classify/ rejected message was echoed back instead of closing the connection"
fi

# GIVEN
INPUT="I know a huge piece of gossip about the admin team but my lips are sealed."
echo \# Testing tcp.echo.classify/ rejected message \(different vocabulary, same gatekeeping pattern\)
echo PORT="$PORT"
echo INPUT="$INPUT"
echo EXPECTED="(connection closes, nothing echoed)"
echo

# WHEN
OUTPUT=$(printf '%s\n' "$INPUT" | nc -w 5 localhost $PORT)
RESULT=$?

# THEN
echo OUTPUT="$OUTPUT"
echo RESULT="$RESULT"
echo
if [ -z "$OUTPUT" ]; then
  echo ✅
else
  fail "tcp.echo.classify/ rejected message (different vocabulary) was echoed back instead of closing the connection"
fi

assert_rejected() {
  DESCRIPTION="$1"
  INPUT="$2"
  echo \# Testing tcp.echo.classify/ rejected message \("$DESCRIPTION"\)
  echo PORT="$PORT"
  echo INPUT="$INPUT"
  echo EXPECTED="(connection closes, nothing echoed)"
  echo

  OUTPUT=$(printf '%s\n' "$INPUT" | nc -w 5 localhost $PORT)
  RESULT=$?

  echo OUTPUT="$OUTPUT"
  echo RESULT="$RESULT"
  echo
  if [ -z "$OUTPUT" ]; then
    echo ✅
  else
    fail "tcp.echo.classify/ rejected message ($DESCRIPTION) was echoed back instead of closing the connection"
  fi
}

assert_rejected "AWS access key" "Here is my key AKIAIOSFODNN7EXAMPLE please keep it safe"
assert_rejected "GitHub token" "Use ghp_aBcDeFgHiJkLmNoPqRsTuVwXyZ0123456789 to push the change"
assert_rejected "email address" "Reach me at jane.doe@example.com for the details"
assert_rejected "person name" "Please forward this to John Smith in accounting"

report_failures
exit $EXIT
