#!/bin/bash
set -x

# Stop port forwarding
pgrep kubectl && killall kubectl

# Uninstall Zilla and Kafka
NAMESPACE=zilla-grpc-kafka-echo
helm uninstall zilla kafka --namespace $NAMESPACE
kubectl delete namespace $NAMESPACE
