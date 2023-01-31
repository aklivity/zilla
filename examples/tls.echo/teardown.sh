#!/bin/bash
set -x

# Stop port forwarding
pgrep kubectl && killall kubectl

# Uninstall Zilla engine
helm uninstall zilla-tls-echo --namespace zilla-tls-echo
kubectl delete namespace zilla-tls-echo
