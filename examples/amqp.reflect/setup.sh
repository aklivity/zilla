#!/bin/bash
set -ex

# Install Zilla to the Kubernetes cluster with helm and wait for the pod to start up
helm install zilla-amqp-reflect chart --namespace zilla-amqp-reflect --create-namespace --wait

# Start port forwarding
kubectl port-forward --namespace zilla-amqp-reflect service/zilla 5671 5672 > /tmp/kubectl-zilla.log 2>&1 &
until nc -z localhost 5671; do sleep 1; done
