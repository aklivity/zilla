#!/bin/bash
set -x

# Stop port forwarding
pgrep kubectl && killall kubectl

# Uninstall Zilla and mosquitto
NAMESPACE="${NAMESPACE:-asyncapi-mqtt-proxy}"
helm uninstall zilla mosquitto --namespace $NAMESPACE
kubectl delete namespace $NAMESPACE
