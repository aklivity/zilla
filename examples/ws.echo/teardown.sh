#!/bin/bash
set -x

# Stop port forwarding
pgrep kubectl && killall kubectl

# Uninstall Zilla engine
helm uninstall zilla-ws-echo --namespace zilla-ws-echo
kubectl delete namespace zilla-ws-echo
