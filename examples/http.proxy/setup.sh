#!/bin/bash
set -ex

# Install Zilla and Nginx to the Kubernetes cluster with helm and wait for the pods to start up
helm install zilla-http-proxy chart --namespace zilla-http-proxy --create-namespace --wait

# Copy web files to the persistent volume mounted in the pod's filesystem
NGINX_POD=$(kubectl get pods --namespace zilla-http-proxy --selector app.kubernetes.io/instance=nginx -o json | jq -r '.items[0].metadata.name')
kubectl cp --namespace zilla-http-proxy www/demo.html "$NGINX_POD:/usr/share/nginx/html"
kubectl cp --namespace zilla-http-proxy www/style.css "$NGINX_POD:/usr/share/nginx/html"

# Start port forwarding
kubectl port-forward --namespace zilla-http-proxy service/zilla 9090 > /tmp/kubectl-zilla.log 2>&1 &
until nc -z localhost 9090; do sleep 1; done
