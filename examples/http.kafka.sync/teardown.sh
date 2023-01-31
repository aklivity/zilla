#!/bin/bash
set -x

# Stop port forwarding
pgrep kubectl && killall kubectl

# Uninstall Zilla engine
helm uninstall zilla-http-kafka-sync --namespace zilla-http-kafka-sync
kubectl delete namespace zilla-http-kafka-sync
