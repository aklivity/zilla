#!/bin/bash
set -ex

# Install Zilla to the Kubernetes cluster with helm and wait for the pod to start up
helm install zilla-config-server chart --namespace zilla-config-server --create-namespace --wait

# Copy web files to the persistent volume mounted in the pod's filesystem
ZILLA_POD=$(kubectl get pods --namespace zilla-config-server --selector app.kubernetes.io/instance=zilla-config -o json | jq -r '.items[0].metadata.name')
kubectl cp --namespace zilla-config-server www "$ZILLA_POD:/var/"

kubectl run busybox-pod --image=busybox:1.28 --namespace zilla-config-server --rm --restart=Never -i -t -- /bin/sh -c 'until nc -w 2 zilla-http 8080; do echo . && sleep 5; done' > /dev/null 2>&1
kubectl wait --namespace zilla-config-server --for=delete pod/busybox-pod


# Start port forwarding
kubectl port-forward --namespace zilla-config-server service/zilla-config 8081 9091 > /tmp/kubectl-zilla-config.log 2>&1 &
kubectl port-forward --namespace zilla-config-server service/zilla-http 8080 9090 > /tmp/kubectl-zilla-http.log 2>&1 &
until nc -z localhost 8081; do sleep 1; done
until nc -z localhost 8080; do sleep 1; done
