#!/bin/bash
set -e

if [[ -z "$KAFKA_HOST" && -z "$KAFKA_PORT" ]]; then
  export KAFKA_HOST=kafka.zilla-kafka-broker.svc.cluster.local
  export KAFKA_PORT=9092
  echo "==== This example requires env vars KAFKA_HOST and KAFKA_PORT for a running kafka instance. Setting to the default ($KAFKA_HOST:$KAFKA_PORT) ===="
fi

# Install Zilla to the Kubernetes cluster with helm and wait for the pod to start up
NAMESPACE=zilla-mqtt-kafka-broker
ZILLA_CHART=oci://ghcr.io/aklivity/charts/zilla
echo "Installing $ZILLA_CHART to $NAMESPACE with Kafka at $KAFKA_HOST:$KAFKA_PORT"
helm upgrade --install zilla $ZILLA_CHART --namespace $NAMESPACE --create-namespace --wait \
    --values values.yaml \
    --set extraEnv[1].value="\"$KAFKA_HOST\"",extraEnv[2].value="\"$KAFKA_PORT\"" \
    --set-file zilla\\.yaml=../../zilla.yaml \
    --set-file secrets.tls.data.localhost\\.p12=../../tls/localhost.p12

# Create the mqtt topics in Kafka
kubectl run kafka-init-pod --image=bitnami/kafka:3.2 --namespace $NAMESPACE --rm --restart=Never -i -t -- /bin/sh -c "
echo 'Creating topics for $KAFKA_HOST:$KAFKA_PORT'
/opt/bitnami/kafka/bin/kafka-topics.sh --bootstrap-server $KAFKA_HOST:$KAFKA_PORT --create --if-not-exists --topic mqtt-messages
/opt/bitnami/kafka/bin/kafka-topics.sh --bootstrap-server $KAFKA_HOST:$KAFKA_PORT --create --if-not-exists --topic mqtt-devices --config cleanup.policy=compact
/opt/bitnami/kafka/bin/kafka-topics.sh --bootstrap-server $KAFKA_HOST:$KAFKA_PORT --create --if-not-exists --topic mqtt-retained --config cleanup.policy=compact
/opt/bitnami/kafka/bin/kafka-topics.sh --bootstrap-server $KAFKA_HOST:$KAFKA_PORT --create --if-not-exists --topic mqtt-sessions --config cleanup.policy=compact
"
kubectl wait --namespace $NAMESPACE --for=delete pod/kafka-init-pod

# Start port forwarding
SERVICE_PORTS=$(kubectl get svc --namespace $NAMESPACE zilla --template "{{ range .spec.ports }}{{.port}} {{ end }}")
eval "kubectl port-forward --namespace $NAMESPACE service/zilla $SERVICE_PORTS" > /tmp/kubectl-zilla.log 2>&1 &

if [[ -x "$(command -v nc)" ]]; then
    until nc -z localhost 7183; do sleep 1; done
    until nc -z localhost 7883; do sleep 1; done
fi
