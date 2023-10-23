#!/bin/bash
set -x

# Stop port forwarding
pgrep kubectl && killall kubectl

# Uninstall Zilla and Kafka
NAMESPACE=zilla-kafka-broker
kubectl delete namespace $NAMESPACE --force --grace-period=0
