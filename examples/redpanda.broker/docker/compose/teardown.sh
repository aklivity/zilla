#!/bin/bash
set -e

NAMESPACE="${NAMESPACE:-zilla-redpanda-broker}"
docker-compose -p $NAMESPACE down --remove-orphans
