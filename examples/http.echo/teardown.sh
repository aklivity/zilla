#!/bin/bash
set -x

# Stop port forwarding
pgrep kubectl && killall kubectl

# Uninstall Zilla engine
helm uninstall zilla-http-echo --namespace zilla-http-echo
kubectl delete namespace zilla-http-echo
