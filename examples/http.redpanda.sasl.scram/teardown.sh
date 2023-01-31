#!/bin/bash
set -x

# Stop port forwarding
pgrep kubectl && killall kubectl

# Uninstall Zilla engine
helm uninstall zilla-http-redpanda-sasl-scram --namespace zilla-http-redpanda-sasl-scram
kubectl delete namespace zilla-http-redpanda-sasl-scram
