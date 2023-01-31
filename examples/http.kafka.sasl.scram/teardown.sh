#!/bin/bash
set -x

# Stop port forwarding
pgrep kubectl && killall kubectl

# Uninstall Zilla engine
helm uninstall zilla-http-kafka-sasl-scram --namespace zilla-http-kafka-sasl-scram
kubectl delete namespace zilla-http-kafka-sasl-scram
