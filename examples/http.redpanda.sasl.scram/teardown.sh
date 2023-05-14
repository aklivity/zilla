#!/bin/bash
set -x

# Stop port forwarding
pgrep kubectl && killall kubectl

# Uninstall Zilla and Redpanda
helm uninstall zilla-http-redpanda-sasl-scram zilla-http-redpanda-sasl-scram-redpanda --namespace zilla-http-redpanda-sasl-scram
kubectl delete namespace zilla-http-redpanda-sasl-scram
