#!/bin/bash
set -x

# Stop port forwarding
pgrep kubectl && killall kubectl

# Uninstall Zilla engine
helm uninstall zilla-config-server-config zilla-config-server-http --namespace zilla-config-server
kubectl delete namespace zilla-config-server
