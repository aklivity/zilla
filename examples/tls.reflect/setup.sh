#!/bin/bash
set -ex

# Install Zilla to the Kubernetes cluster with helm and wait for the pod to start up
helm install zilla-tls-reflect chart --namespace zilla-tls-reflect --create-namespace --wait

# Start port forwarding
kubectl port-forward --namespace zilla-tls-reflect service/zilla 23456 > /tmp/kubectl-zilla.log 2>&1 &
until nc -z localhost 23456; do sleep 1; done
