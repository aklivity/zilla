#!/bin/bash
set -e

[[ -z "$KAFKA_HOST" && -z "$KAFKA_PORT" ]] && printf "==== This example requires a running kafka instance, found ($KAFKA_HOST:$KAFKA_PORT) ====\n$USAGE" && exit 0;

# Install Zilla to the Kubernetes cluster with helm and wait for the pod to start up
NAMESPACE=zilla-mqtt-kafka-broker
ZILLA_CHART=oci://ghcr.io/aklivity/charts/zilla
echo "Installing $ZILLA_CHART to $NAMESPACE"
helm upgrade --install zilla $ZILLA_CHART --namespace $NAMESPACE --create-namespace --wait \
    --values values.yaml \
    --set env[1].value="\"$KAFKA_HOST\"",env[2].value="\"$KAFKA_PORT\"" \
    --set-file zilla\\.yaml=../../zilla.yaml \
    --set-file secrets.tls.data.localhost\\.p12=../../tls/localhost.p12

# Create the mqtt topics in Kafka
kubectl run kafka-init-pod --image=bitnami/kafka:3.2 --namespace $NAMESPACE --rm --restart=Never -i -t -- /bin/sh -c "
echo 'Creating topics for $KAFKA_HOST:$KAFKA_PORT'
/opt/bitnami/kafka/bin/kafka-topics.sh --bootstrap-server $KAFKA_HOST:$KAFKA_PORT --create --if-not-exists --topic mqtt-sessions
/opt/bitnami/kafka/bin/kafka-topics.sh --bootstrap-server $KAFKA_HOST:$KAFKA_PORT --create --if-not-exists --topic mqtt-messages --config cleanup.policy=compact
/opt/bitnami/kafka/bin/kafka-topics.sh --bootstrap-server $KAFKA_HOST:$KAFKA_PORT --create --if-not-exists --topic mqtt-retained --config cleanup.policy=compact
"
kubectl wait --namespace $NAMESPACE --for=delete pod/kafka-init-pod

# Start port forwarding
SERVICE_PORTS=$(kubectl get svc --namespace $NAMESPACE zilla --template "{{ range .spec.ports }}{{.port}} {{ end }}")
eval "kubectl port-forward --namespace $NAMESPACE service/zilla $SERVICE_PORTS" > /tmp/kubectl-zilla.log 2>&1 &

if [[ -x "$(command -v nc)" ]]; then
    until nc -z localhost 7183; do sleep 1; done
    until nc -z localhost 7883; do sleep 1; done
fi
