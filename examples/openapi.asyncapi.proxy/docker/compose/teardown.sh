#!/bin/bash
set -e

NAMESPACE="${NAMESPACE:-zilla-openapi-asyncapi-proxy}"
docker compose -p $NAMESPACE down --remove-orphans
