#!/bin/bash
set -x

# Stop port forwarding
pgrep kubectl && killall kubectl

# Uninstall Zilla and Kafka
NAMESPACE=zilla-mqtt-kafka-broker
helm uninstall zilla kafka --namespace $NAMESPACE
kubectl delete namespace $NAMESPACE
