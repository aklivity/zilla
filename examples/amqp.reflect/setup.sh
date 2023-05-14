#!/bin/bash
set -ex

# Install Zilla to the Kubernetes cluster with helm and wait for the pod to start up
ZILLA_CHART=oci://ghcr.io/aklivity/charts/zilla
VERSION=0.9.46
helm install zilla-amqp-reflect $ZILLA_CHART --version $VERSION --namespace zilla-amqp-reflect --create-namespace --wait \
    --values values.yaml \
    --set-file zilla\\.yaml=zilla.yaml \
    --set-file secrets.tls.data.localhost\\.p12=tls/localhost.p12

# Start port forwarding
kubectl port-forward --namespace zilla-amqp-reflect service/zilla-amqp-reflect 5671 5672 > /tmp/kubectl-zilla.log 2>&1 &
until nc -z localhost 5671; do sleep 1; done
