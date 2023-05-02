#!/bin/bash
set -ex

# change configfile
ZILLA_POD=$(kubectl get pods --namespace zilla-config-server --selector app.kubernetes.io/instance=zilla-config -o json | jq -r '.items[0].metadata.name')
kubectl cp --namespace zilla-config-server zilla.yaml "$ZILLA_POD:/var/www/zilla.yaml"

until curl -s -f -d 'Hello, World' -H 'Content-Type: text/plain' -X 'POST' -v http://localhost:8080/echo_changed > /dev/null 2>&1 ; do sleep 1 ; done
