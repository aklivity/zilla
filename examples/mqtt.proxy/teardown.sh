#!/bin/bash
set -x

# Stop port forwarding
pgrep kubectl && killall kubectl

# Uninstall Zilla and mosquitto
helm uninstall mqtt-proxy mqtt-proxy-mosquitto --namespace mqtt-proxy
kubectl delete namespace mqtt-proxy
