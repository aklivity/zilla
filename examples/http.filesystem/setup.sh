#!/bin/bash
set -ex

# Install Zilla to the Kubernetes cluster with helm and wait for the pod to start up
helm install zilla-http-filesystem chart --namespace zilla-http-filesystem --create-namespace --wait

# Copy web files to the persistent volume mounted in the pod's filesystem
ZILLA_POD=$(kubectl get pods --namespace zilla-http-filesystem --selector app.kubernetes.io/instance=zilla -o json | jq -r '.items[0].metadata.name')
kubectl cp --namespace zilla-http-filesystem www "$ZILLA_POD:/var/"

# Start port forwarding
kubectl port-forward --namespace zilla-http-filesystem service/zilla 8080 9090 > /tmp/kubectl-zilla.log 2>&1 &
until nc -z localhost 8080; do sleep 1; done
