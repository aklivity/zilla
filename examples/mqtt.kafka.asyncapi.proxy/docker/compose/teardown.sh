#!/bin/bash
set -e

NAMESPACE=zilla-mqtt-kafka-asyncapi-proxy
docker-compose -p $NAMESPACE down --remove-orphans
