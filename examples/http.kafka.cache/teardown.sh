#!/bin/bash
set -x

# Stop port forwarding
pgrep kubectl && killall kubectl

# Uninstall Zilla engine
helm uninstall zilla-http-kafka-cache --namespace zilla-http-kafka-cache
kubectl delete namespace zilla-http-kafka-cache
