#!/bin/bash
set -x

# Stop port forwarding
pgrep kubectl && killall kubectl

# Uninstall Zilla and Nginx
NAMESPACE=zilla-http-proxy-schema-inline
helm uninstall zilla nginx --namespace $NAMESPACE
kubectl delete namespace $NAMESPACE
