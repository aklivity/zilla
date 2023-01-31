#!/bin/bash
set -ex

# Install Zilla to the Kubernetes cluster with helm and wait for the pod to start up
helm install zilla-tcp-echo chart --namespace zilla-tcp-echo --create-namespace --wait

# Start port forwarding
kubectl port-forward --namespace zilla-tcp-echo service/zilla 12345 > /tmp/kubectl-zilla.log 2>&1 &
until nc -z localhost 12345; do sleep 1; done
