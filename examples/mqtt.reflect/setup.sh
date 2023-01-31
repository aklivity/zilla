#!/bin/bash
set -ex

# Install Zilla to the Kubernetes cluster with helm and wait for the pod to start up
helm install zilla-mqtt-reflect chart --namespace zilla-mqtt-reflect --create-namespace --wait

# Start port forwarding
kubectl port-forward --namespace zilla-mqtt-reflect service/zilla 1883 8883 > /tmp/kubectl-zilla.log 2>&1 &
until nc -z localhost 1883; do sleep 1; done
