#!/bin/bash
set -x

# Stop port forwarding
pgrep kubectl && killall kubectl

# Uninstall Zilla engine
helm uninstall zilla-http-kafka-crud --namespace zilla-http-kafka-crud
kubectl delete namespace zilla-http-kafka-crud
