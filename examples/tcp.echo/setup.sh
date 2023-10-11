#!/bin/bash
set -ex

# Install Zilla to the Kubernetes cluster with helm and wait for the pod to start up
ZILLA_CHART=oci://ghcr.io/aklivity/charts/zilla
helm install zilla-tcp-echo $ZILLA_CHART --namespace zilla-tcp-echo --create-namespace --wait \
    --values values.yaml \
    --set-file zilla\\.yaml=zilla.yaml

# Start port forwarding
kubectl port-forward --namespace zilla-tcp-echo service/zilla-tcp-echo 12345 > /tmp/kubectl-zilla.log 2>&1 &
until nc -z localhost 12345; do sleep 1; done
