#!/bin/bash
set -x

# Stop port forwarding
pgrep kubectl && killall kubectl

# Uninstall Zilla engine
helm uninstall zilla-mqtt-reflect --namespace zilla-mqtt-reflect
kubectl delete namespace zilla-mqtt-reflect
