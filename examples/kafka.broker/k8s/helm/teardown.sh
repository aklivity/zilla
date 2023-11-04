#!/bin/bash
set -x

# Stop port forwarding
pgrep kubectl && killall kubectl

# Uninstall Zilla and Kafka
NAMESPACE=zilla-kafka-broker
helm uninstall kafka kafka-ui --namespace $NAMESPACE
kubectl delete namespace $NAMESPACE
