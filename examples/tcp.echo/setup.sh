#!/bin/bash
set -ex

# Install Zilla to the Kubernetes cluster with helm and wait for the pod to start up
ZILLA_CHART=oci://ghcr.io/aklivity/charts/zilla
NAMESPACE=zilla-tcp-echo
helm upgrade --install zilla $ZILLA_CHART --namespace $NAMESPACE --create-namespace --wait \
    --values values.yaml \
    --set-file zilla\\.yaml=zilla.yaml

# Start port forwarding
kubectl port-forward --namespace $NAMESPACE service/zilla 12345 > /tmp/kubectl-zilla.log 2>&1 &
until nc -z localhost 12345; do sleep 1; done
