#!/bin/bash
set -e

NAMESPACE=zilla-redpanda-broker
docker-compose -p $NAMESPACE down --remove-orphans
