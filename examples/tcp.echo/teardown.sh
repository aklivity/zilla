#!/bin/bash
set -x

# Stop port forwarding
pgrep kubectl && killall kubectl

# Uninstall Zilla engine
helm uninstall zilla-tcp-echo --namespace zilla-tcp-echo
kubectl delete namespace zilla-tcp-echo
