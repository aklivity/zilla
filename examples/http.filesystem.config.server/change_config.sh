#!/bin/bash
set -ex

# change configfile
NAMESPACE=zilla-config-server
ZILLA_CONFIG_POD=$(kubectl get pods --namespace $NAMESPACE --selector app.kubernetes.io/instance=zilla-config -o json | jq -r '.items[0].metadata.name')
kubectl cp --namespace $NAMESPACE www-updated/zilla.yaml "$ZILLA_CONFIG_POD:/var/www/zilla.yaml"

until curl -s -f -d 'Hello, World' -H 'Content-Type: text/plain' -X 'POST' -v http://localhost:7114/echo_changed > /dev/null 2>&1 ; do sleep 1 ; done
