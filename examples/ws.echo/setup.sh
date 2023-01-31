#!/bin/bash
set -ex

# Install Zilla to the Kubernetes cluster with helm and wait for the pod to start up
helm install zilla-ws-echo chart --namespace zilla-ws-echo --create-namespace --wait

# Start port forwarding
kubectl port-forward --namespace zilla-ws-echo service/zilla 8080 9090 > /tmp/kubectl-zilla.log 2>&1 &
until nc -z localhost 8080; do sleep 1; done
