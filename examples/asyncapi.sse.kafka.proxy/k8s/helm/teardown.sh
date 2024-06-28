#!/bin/bash
set -x

# Stop port forwarding
pgrep kubectl && killall kubectl

# Uninstall Zilla and Kafka
NAMESPACE="${NAMESPACE:-zilla-sse-kafka-asyncapi-proxy}"
helm uninstall zilla --namespace $NAMESPACE
kubectl delete namespace $NAMESPACE
