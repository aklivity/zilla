#!/bin/bash
set -e

NAMESPACE="${NAMESPACE:-zilla-kafka-broker}"
docker-compose -p $NAMESPACE down --remove-orphans
