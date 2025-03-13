#!/bin/sh
set -x

EXIT=0

# GIVEN
PORT="7114"
PROTO_FILE="./request.proto"
INPUT="message:'Hello, world',count:10"
ENCODED_FILE="encoded_input.bin"
EXPECTED="204"

echo \# Testing http.kafka.proto.oneway/
echo PORT="$PORT"
echo INPUT="$INPUT"
echo EXPECTED="$EXPECTED"
echo

# Generate encoded Protobuf input once if not already generated
if [ ! -f "$ENCODED_FILE" ]; then
  echo "$INPUT" | protoc --encode=Request "$PROTO_FILE" > "$ENCODED_FILE"
fi

# WHEN
OUTPUT=$(curl -w "%{http_code}" -s --request POST http://localhost:$PORT/requests \
  --header "Content-Type: application/protobuf" \
  --data-binary @"$ENCODED_FILE")
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
