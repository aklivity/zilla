#!/bin/sh
set -x

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
OUTPUT=$(curl http://localhost:$PORT/index.html)
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
