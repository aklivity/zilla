#!/bin/bash
set -x

# Stop port forwarding
pgrep kubectl && killall kubectl

# Uninstall Zilla engine
helm uninstall zilla-grpc-echo --namespace zilla-grpc-echo
kubectl delete namespace zilla-grpc-echo
