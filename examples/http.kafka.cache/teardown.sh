#!/bin/bash
set -x

# Stop port forwarding
pgrep kubectl && killall kubectl

# Uninstall Zilla and Kafka
NAMESPACE=zilla-http-kafka-cache
helm uninstall zilla kafka --namespace $NAMESPACE
kubectl delete namespace $NAMESPACE
