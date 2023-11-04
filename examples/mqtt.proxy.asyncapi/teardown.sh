#!/bin/bash
set -x

# Stop port forwarding
pgrep kubectl && killall kubectl

# Uninstall Zilla and mosquitto
NAMESPACE=mqtt-proxy-asyncapi
helm uninstall zilla mosquitto --namespace $NAMESPACE
kubectl delete namespace $NAMESPACE
