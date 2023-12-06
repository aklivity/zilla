#!/bin/bash
set -e

NAMESPACE=zilla-redpanda-broker

if [[ -z $(docker-compose -p $NAMESPACE ps -q redpanda) || -z $(docker-compose -p $NAMESPACE ps -q console) ]]; then
  docker-compose -p $NAMESPACE up -d
fi

if [[ -n $(docker-compose -p $NAMESPACE ps -q console) ]]; then
  docker-compose -p $NAMESPACE restart console
fi
