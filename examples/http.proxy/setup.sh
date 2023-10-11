#!/bin/bash
set -ex

# Install Zilla to the Kubernetes cluster with helm and wait for the pod to start up
ZILLA_CHART=oci://ghcr.io/aklivity/charts/zilla
helm install zilla-http-proxy $ZILLA_CHART --namespace zilla-http-proxy --create-namespace --wait \
    --values values.yaml \
    --set-file zilla\\.yaml=zilla.yaml \
    --set-file secrets.tls.data.localhost\\.p12=tls/localhost.p12 \
    --set-file secrets.tls.data.truststore\\.p12=tls/truststore.p12

# Install Nginx to the Kubernetes cluster with helm and wait for the pod to start up
helm install zilla-http-proxy-nginx chart --namespace zilla-http-proxy --create-namespace --wait

# Copy web files to the persistent volume mounted in the pod's filesystem
NGINX_POD=$(kubectl get pods --namespace zilla-http-proxy --selector app.kubernetes.io/instance=nginx -o json | jq -r '.items[0].metadata.name')
kubectl cp --namespace zilla-http-proxy www/demo.html "$NGINX_POD:/usr/share/nginx/html"
kubectl cp --namespace zilla-http-proxy www/style.css "$NGINX_POD:/usr/share/nginx/html"

# Start port forwarding
kubectl port-forward --namespace zilla-http-proxy service/zilla-http-proxy 9090 > /tmp/kubectl-zilla.log 2>&1 &
until nc -z localhost 9090; do sleep 1; done
