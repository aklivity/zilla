#!/bin/bash
set -x

# Stop port forwarding
pgrep kubectl && killall kubectl

# Uninstall Zilla and Kafka
NAMESPACE=zilla-sse-kafka-fanout
helm uninstall zilla kafka --namespace $NAMESPACE
kubectl delete namespace $NAMESPACE
