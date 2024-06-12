#!/bin/bash
set -e

NAMESPACE="${NAMESPACE:-zilla-kafka-broker}"
export KAFKA_BOOTSTRAP_SERVER="${KAFKA_BOOTSTRAP_SERVER:-kafka.$NAMESPACE.svc.cluster.local:9092}"
# Install Kafka to the Kubernetes cluster with helm and wait for the pod to start up
helm upgrade --install kafka . --namespace $NAMESPACE --create-namespace --wait
helm upgrade --install kafka-ui kafka-ui --version 0.7.5 --namespace $NAMESPACE --repo https://provectus.github.io/kafka-ui-charts --wait \
    --set-string "yamlApplicationConfig.kafka.clusters[0].bootstrapServers=$KAFKA_BOOTSTRAP_SERVER" \
    --values kafka-ui-values.yaml

# Start port forwarding
SERVICE_PORTS=$(kubectl get svc --namespace $NAMESPACE kafka --template "{{ range .spec.ports }}{{.port}} {{ end }}")
eval "kubectl port-forward --namespace $NAMESPACE service/kafka $SERVICE_PORTS" > /tmp/kubectl-kafka.log 2>&1 &
SERVICE_PORTS=$(kubectl get svc --namespace $NAMESPACE kafka-ui --template "{{ range .spec.ports }}{{.port}} {{ end }}")
eval "kubectl port-forward --namespace $NAMESPACE service/kafka-ui $SERVICE_PORTS" > /tmp/kubectl-kafka-ui.log 2>&1 &

if [[ -x "$(command -v nc)" ]]; then
    until nc -z localhost 8080; do sleep 1; done
    until nc -z localhost 29092; do sleep 1; done
fi
