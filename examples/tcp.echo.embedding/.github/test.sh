#!/bin/sh
set -x

. "$(CDPATH= cd -- "$(dirname -- "$0")/../../.github" && pwd)/test-lib.sh"

EXIT=0
PORT="12345"

# The GloVe vectors download on first use, which can take well past a normal
# request's timeout. Retry a throwaway message until it echoes, so the
# assertions below aren't racing the one-time download/parse.
echo \# Warming up tcp.echo.embedding/ moderator0 \(first-use vectors download\)
warm_up() {
  OUTPUT=$(printf '%s\n' "warm up" | nc -w 20 localhost $PORT)
  [ "$OUTPUT" = "warm up" ]
}
retry_until 20 15 warm_up
echo RESULT=$?
echo

# GIVEN
INPUT="Something crazy just happened to me, a stray cat ran straight into my kitchen!"
EXPECTED="$INPUT"
echo \# Testing tcp.echo.embedding/ accepted message
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
  fail "tcp.echo.embedding/ accepted message was not echoed back unchanged"
fi

# GIVEN
INPUT="Something crazy just happened to me but honestly it's too wild to type out."
echo \# Testing tcp.echo.embedding/ rejected message \(same opener, withholds the story\)
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
  fail "tcp.echo.embedding/ rejected message was echoed back instead of closing the connection"
fi

# GIVEN
INPUT="I know a huge piece of gossip about the admin team but my lips are sealed."
echo \# Testing tcp.echo.embedding/ rejected message \(different vocabulary, same gatekeeping pattern\)
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
  fail "tcp.echo.embedding/ rejected message (different vocabulary) was echoed back instead of closing the connection"
fi

report_failures
exit $EXIT
