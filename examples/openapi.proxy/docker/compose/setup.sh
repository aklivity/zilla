#!/bin/bash
set -e

NAMESPACE="${NAMESPACE:-zilla-openapi-proxy}"
export ZILLA_VERSION="${ZILLA_VERSION:-latest}"

# Start or restart Zilla
if [[ -z $(docker-compose -p $NAMESPACE ps -q zilla) ]]; then
  docker-compose -p $NAMESPACE up -d
else
  docker-compose -p $NAMESPACE restart --no-deps zilla
fi
