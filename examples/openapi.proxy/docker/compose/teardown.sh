#!/bin/bash
set -e

NAMESPACE="${NAMESPACE:-zilla-openapi-proxy}"
docker compose -p $NAMESPACE down --remove-orphans
