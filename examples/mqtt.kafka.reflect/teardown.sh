#!/bin/bash
set -x

# Stop port forwarding
pgrep kubectl && killall kubectl

# Uninstall Zilla engine
helm uninstall zilla-mqtt-kafka --namespace zilla-mqtt-kafka
kubectl delete namespace zilla-mqtt-kafka
