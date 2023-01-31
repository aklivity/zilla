#!/bin/bash
set -x

# Stop port forwarding
pgrep kubectl && killall kubectl

# Uninstall Zilla engine
helm uninstall zilla-http-kafka-async --namespace zilla-http-kafka-async
kubectl delete namespace zilla-http-kafka-async
