#!/bin/bash
set -e

NAMESPACE=zilla-kafka-broker

if [[ -z $(docker-compose -p $NAMESPACE ps -q kafka) || -z $(docker-compose -p $NAMESPACE ps -q kafka-ui) ]]; then
  docker-compose -p $NAMESPACE up -d
fi

if [[ -n $(docker-compose -p $NAMESPACE ps -q kafka-ui) ]]; then
  docker-compose -p $NAMESPACE restart kafka-ui
fi
