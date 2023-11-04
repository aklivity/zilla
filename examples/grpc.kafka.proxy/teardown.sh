#!/bin/bash
set -x

# Stop port forwarding
pgrep kubectl && killall kubectl

# Uninstall Zilla and Kafka
NAMESPACE=zilla-grpc-kafka-proxy
helm uninstall zilla grpc-echo-kafka --namespace $NAMESPACE
kubectl delete namespace $NAMESPACE
