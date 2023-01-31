#!/bin/bash
set -x

# Stop port forwarding
pgrep kubectl && killall kubectl

# Uninstall Zilla engine
helm uninstall zilla-sse-kafka-fanout --namespace zilla-sse-kafka-fanout
kubectl delete namespace zilla-sse-kafka-fanout
