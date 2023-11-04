#!/bin/bash
set -ex

# Install Zilla to the Kubernetes cluster with helm and wait for the pod to start up
ZILLA_CHART=oci://ghcr.io/aklivity/charts/zilla
NAMESPACE=zilla-http-filesystem
helm upgrade --install zilla $ZILLA_CHART --namespace $NAMESPACE --create-namespace --wait \
    --values values.yaml \
    --set-file zilla\\.yaml=zilla.yaml \
    --set-file secrets.tls.data.localhost\\.p12=tls/localhost.p12

# Copy web files to the persistent volume mounted in the pod's filesystem
ZILLA_POD=$(kubectl get pods --namespace $NAMESPACE --selector app.kubernetes.io/instance=zilla -o json | jq -r '.items[0].metadata.name')
kubectl cp --namespace $NAMESPACE www "$ZILLA_POD:/var/"

# Start port forwarding
kubectl port-forward --namespace $NAMESPACE service/zilla 7114 7143 > /tmp/kubectl-zilla.log 2>&1 &
until nc -z localhost 7114; do sleep 1; done
until nc -z localhost 7143; do sleep 1; done
