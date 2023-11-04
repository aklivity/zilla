#!/bin/bash
set -e

NAMESPACE=zilla-mqtt-kafka-broker
docker-compose -p $NAMESPACE down --remove-orphans
