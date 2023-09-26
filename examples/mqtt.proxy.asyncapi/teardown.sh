#!/bin/bash
set -x

# Stop port forwarding
pgrep kubectl && killall kubectl

# Uninstall Zilla and mosquitto
helm uninstall mqtt-proxy-asyncapi mqtt-proxy-asyncapi-mosquitto --namespace mqtt-proxy-asyncapi
kubectl delete namespace mqtt-proxy-asyncapi
