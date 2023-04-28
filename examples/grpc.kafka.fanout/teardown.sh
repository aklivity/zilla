#!/bin/bash
set -x

# Stop port forwarding
pgrep kubectl && killall kubectl

# Uninstall Zilla engine
helm uninstall zilla-grpc-kafka-fanout --namespace zilla-grpc-kafka-fanout
kubectl delete namespace zilla-grpc-kafka-fanout
