#!/bin/bash
set -e

NAMESPACE=zilla-kafka-broker
docker-compose -p $NAMESPACE down --remove-orphans
