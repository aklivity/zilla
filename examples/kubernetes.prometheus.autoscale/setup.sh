#!/bin/bash
set -ex

# Install Zilla to the Kubernetes cluster with helm and wait for the pod to start up
ZILLA_CHART=oci://ghcr.io/aklivity/charts/zilla
NAMESPACE=zilla-kubernetes-prometheus-autoscale
helm upgrade --install zilla $ZILLA_CHART --namespace $NAMESPACE --create-namespace --wait \
    --values values.yaml \
    --set-file zilla\\.yaml=zilla.yaml

# Install Prometheus and Prometheus Adapter to the Kubernetes cluster with helm and wait for the pod to start up
helm upgrade --install prometheus chart --namespace $NAMESPACE --create-namespace --wait

# Start port forwarding
kubectl port-forward --namespace $NAMESPACE service/zilla 7114 7190 > /tmp/kubectl-zilla.log 2>&1 &
kubectl port-forward --namespace $NAMESPACE service/prometheus 9090 > /tmp/kubectl-prometheus.log 2>&1 &
until nc -z localhost 7114; do sleep 1; done
until nc -z localhost 7190; do sleep 1; done
until nc -z localhost 9090; do sleep 1; done
