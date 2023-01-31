#!/bin/bash
set -x

# Stop port forwarding
pgrep kubectl && killall kubectl

# Uninstall Zilla engine
helm uninstall zilla-tcp-reflect --namespace zilla-tcp-reflect
kubectl delete namespace zilla-tcp-reflect
