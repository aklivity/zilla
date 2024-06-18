#!/bin/bash

# GIVEN
PORT="7114"
EXPECTED='[{"id":1,"name":"string","tag":"string"}]'
echo \# Testing openapi.proxy
echo PORT=$PORT
echo EXPECTED=$EXPECTED
echo

# WHEN
for i in $(seq 1 30); do
  OUTPUT=$(curl --silent --location "http://localhost:$PORT/pets" --header 'Accept: application/json')
  RESULT=$?
  if [[ ! -z "$OUTPUT" ]]; then
    break
  fi
  sleep 1
done
echo OUTPUT=$OUTPUT
echo RESULT=$RESULT

# THEN
if [[ $RESULT -eq 0 && "$OUTPUT" == "$EXPECTED" ]]; then
  echo ✅
else
  echo ❌
  exit 1
fi
