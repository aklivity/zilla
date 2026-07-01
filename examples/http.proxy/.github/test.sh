#!/bin/sh
set -x

. "$(CDPATH= cd -- "$(dirname -- "$0")/../../.github" && pwd)/test-lib.sh"

EXIT=0

# GIVEN
PORT="7143"
EXPECTED='<!doctype html>
<html>
<head>
    <title>This is the title of the webpage!</title>
    <link rel="stylesheet" href="/style.css">
</head>
<body>
<p>This is an example paragraph. Anything in the <strong>body</strong> tag will appear on the page, just like this <strong>p</strong> tag and its contents.</p>
</body>
</html>'
echo \# Testing http.proxy/
echo PORT="$PORT"
echo EXPECTED="$EXPECTED"
echo

# WHEN
get_demo() {
  OUTPUT=$(docker compose -p zilla-http-proxy exec nghttp nghttp --no-verify https://zilla.examples.dev:$PORT/demo.html)
  [ $? -eq 0 ] && [ "$OUTPUT" = "$EXPECTED" ]
}
retry_until 5 2 get_demo
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
