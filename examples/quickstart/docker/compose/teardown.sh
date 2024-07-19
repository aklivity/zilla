#!/bin/bash
set -e

NAMESPACE="${NAMESPACE:-zilla-quickstart}"
docker compose -p $NAMESPACE down --remove-orphans
