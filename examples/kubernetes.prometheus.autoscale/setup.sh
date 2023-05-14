#!/bin/bash
set -ex

# Install Zilla to the Kubernetes cluster with helm and wait for the pod to start up
ZILLA_CHART=oci://ghcr.io/aklivity/charts/zilla
VERSION=0.9.46
helm install zilla-kubernetes-prometheus-autoscale $ZILLA_CHART --version $VERSION --namespace zilla-kubernetes-prometheus-autoscale --create-namespace --wait \
    --values values.yaml \
    --set-file zilla\\.yaml=zilla.yaml

# Install Prometheus and Prometheus Adapter to the Kubernetes cluster with helm and wait for the pod to start up
helm install zilla-kubernetes-prometheus-autoscale-prometheus chart --namespace zilla-kubernetes-prometheus-autoscale --create-namespace --wait

# Start port forwarding
kubectl port-forward --namespace zilla-kubernetes-prometheus-autoscale service/zilla-kubernetes-prometheus-autoscale 8080 > /tmp/kubectl-zilla.log 2>&1 &
kubectl port-forward --namespace zilla-kubernetes-prometheus-autoscale service/prometheus 9090 > /tmp/kubectl-prometheus.log 2>&1 &
until nc -z localhost 8080; do sleep 1; done
until nc -z localhost 9090; do sleep 1; done
