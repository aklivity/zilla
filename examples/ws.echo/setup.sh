#!/bin/bash
set -ex

# Install Zilla to the Kubernetes cluster with helm and wait for the pod to start up
ZILLA_CHART=oci://ghcr.io/aklivity/charts/zilla
helm install zilla-ws-echo $ZILLA_CHART --namespace zilla-ws-echo --create-namespace --wait \
    --values values.yaml \
    --set-file zilla\\.yaml=zilla.yaml \
    --set-file secrets.tls.data.localhost\\.p12=tls/localhost.p12

# Start port forwarding
kubectl port-forward --namespace zilla-ws-echo service/zilla-ws-echo 8080 9090 > /tmp/kubectl-zilla.log 2>&1 &
until nc -z localhost 8080; do sleep 1; done
