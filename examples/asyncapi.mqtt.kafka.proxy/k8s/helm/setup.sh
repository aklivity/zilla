#!/bin/bash
set -e

ZILLA_VERSION="${ZILLA_VERSION:-^0.9.0}"
NAMESPACE="${NAMESPACE:-zilla-asyncapi-mqtt-kafka-proxy}"
export KAFKA_BROKER="${KAFKA_BROKER:-kafka}"
export KAFKA_BOOTSTRAP_SERVER="${KAFKA_BOOTSTRAP_SERVER:-kafka.$NAMESPACE.svc.cluster.local:9092}"
INIT_KAFKA="${INIT_KAFKA:-true}"
ZILLA_CHART="${ZILLA_CHART:-oci://ghcr.io/aklivity/charts/zilla}"

# Install Zilla to the Kubernetes cluster with helm and wait for the pod to start up
echo "==== Installing $ZILLA_CHART to $NAMESPACE with $KAFKA_BROKER($KAFKA_BOOTSTRAP_SERVER) ===="
helm upgrade --install zilla $ZILLA_CHART --version $ZILLA_VERSION --namespace $NAMESPACE --create-namespace --wait \
    --values values.yaml \
    --set env.KAFKA_BOOTSTRAP_SERVER="$KAFKA_BOOTSTRAP_SERVER" \
    --set-file zilla\\.yaml=../../zilla.yaml \
    --set-file configMaps.specs.data.mqtt-asyncapi\\.yaml=../../specs/mqtt-asyncapi.yaml \
    --set-file configMaps.specs.data.kafka-asyncapi\\.yaml=../../specs/kafka-asyncapi.yaml

# Create the mqtt topics in Kafka
if [[ $INIT_KAFKA == true ]]; then
  kubectl run kafka-init-pod --image=bitnami/kafka:3.5 --namespace $NAMESPACE --rm --restart=Never -i -t -- /bin/sh -c "
  echo 'Creating topics for $KAFKA_BOOTSTRAP_SERVER'
  /opt/bitnami/kafka/bin/kafka-topics.sh --bootstrap-server $KAFKA_BOOTSTRAP_SERVER --create --if-not-exists --topic mqtt-messages
  /opt/bitnami/kafka/bin/kafka-topics.sh --bootstrap-server $KAFKA_BOOTSTRAP_SERVER --create --if-not-exists --topic streetlights
  /opt/bitnami/kafka/bin/kafka-topics.sh --bootstrap-server $KAFKA_BOOTSTRAP_SERVER --create --if-not-exists --topic mqtt-retained --config cleanup.policy=compact
  /opt/bitnami/kafka/bin/kafka-topics.sh --bootstrap-server $KAFKA_BOOTSTRAP_SERVER --create --if-not-exists --topic mqtt-sessions --config cleanup.policy=compact
  "
  kubectl wait --namespace $NAMESPACE --for=delete pod/kafka-init-pod
fi

# Start port forwarding
SERVICE_PORTS=$(kubectl get svc --namespace $NAMESPACE zilla --template "{{ range .spec.ports }}{{.port}} {{ end }}")
eval "kubectl port-forward --namespace $NAMESPACE service/zilla $SERVICE_PORTS" > /tmp/kubectl-zilla.log 2>&1 &

if [[ -x "$(command -v nc)" ]]; then
    until nc -z localhost 7183; do sleep 1; done
fi
