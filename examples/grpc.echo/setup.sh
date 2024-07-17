#!/bin/bash
set -ex

# Install Zilla to the Kubernetes cluster with helm and wait for the pod to start up
ZILLA_CHART="${ZILLA_CHART:-oci://ghcr.io/aklivity/charts/zilla}"
ZILLA_VERSION="${ZILLA_VERSION:-^0.9.0}"
NAMESPACE="${NAMESPACE:-zilla-grpc-echo}"

kubectl get ns $NAMESPACE || kubectl create ns $NAMESPACE
kubectl create configmap protobuf-files --from-file=./proto/ -n $NAMESPACE -o yaml --dry-run=client | kubectl apply -f -

# Install Zilla to the Kubernetes cluster with helm and wait for the pod to start up
echo "==== Installing $ZILLA_CHART to $NAMESPACE ===="
helm upgrade --install zilla $ZILLA_CHART --version $ZILLA_VERSION --namespace $NAMESPACE --wait \
    --values values.yaml \
    --set-file zilla\\.yaml=zilla.yaml \
    --set-file secrets.tls.data.localhost\\.p12=tls/localhost.p12

# Start port forwarding
export SERVICE_PORTS=$(kubectl get svc --namespace $NAMESPACE zilla --template "{{ range .spec.ports }}{{.port}} {{ end }}")
eval "kubectl port-forward --namespace $NAMESPACE service/zilla $SERVICE_PORTS > /tmp/kubectl-kafka.log 2>&1 &"

until nc -z localhost 7151; do sleep 1; done
until nc -z localhost 7153; do sleep 1; done
