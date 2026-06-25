#!/bin/sh
set -x

. "$(CDPATH= cd -- "$(dirname -- "$0")/../../.github" && pwd)/test-lib.sh"

EXIT=0

# GIVEN
PORT="7114"
EXPECTED='<html>
<head>
<title>Welcome to Zilla!</title>
</head>
<body>
<h1>Welcome to Zilla!</h1>
</body>
</html>'
echo \# Testing http.filesystem/
echo PORT="$PORT"
echo EXPECTED="$EXPECTED"
echo

# WHEN
get_index() {
  OUTPUT=$(curl http://localhost:$PORT/index.html)
  [ $? -eq 0 ] && [ "$OUTPUT" = "$EXPECTED" ]
}
retry_until 5 2 get_index
RESULT=$?
echo RESULT="$RESULT"

# THEN
echo OUTPUT="$OUTPUT"
echo EXPECTED="$EXPECTED"
echo
if [ "$RESULT" -eq 0 ] && [ "$OUTPUT" = "$EXPECTED" ]; then
  echo ✅
else
  echo ❌
  EXIT=1
fi

exit $EXIT
