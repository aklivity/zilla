#!/bin/bash
set -x

# Stop port forwarding
pgrep kubectl && killall kubectl

# Uninstall Zilla engine
helm uninstall zilla-ws-reflect --namespace zilla-ws-reflect
kubectl delete namespace zilla-ws-reflect
