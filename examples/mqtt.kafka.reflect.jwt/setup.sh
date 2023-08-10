#!/bin/bash
set -ex

# Install Zilla to the Kubernetes cluster with helm and wait for the pod to start up
ZILLA_CHART=oci://ghcr.io/aklivity/charts/zilla
VERSION=0.9.46
helm install zilla-mqtt-kafka-reflect-jwt $ZILLA_CHART --version $VERSION --namespace zilla-mqtt-kafka-reflect-jwt --create-namespace --wait \
    --values values.yaml \
    --set-file zilla\\.yaml=zilla.yaml \
    --set-file secrets.tls.data.localhost\\.p12=tls/localhost.p12

# Install Kafka to the Kubernetes cluster with helm and wait for the pod to start up
helm install zilla-mqtt-kafka-reflect-jwt-kafka chart --namespace zilla-mqtt-kafka-reflect-jwt --create-namespace --wait

# Create the mqtt_messages topic in Kafka
KAFKA_POD=$(kubectl get pods --namespace zilla-mqtt-kafka-reflect-jwt --selector app.kubernetes.io/instance=kafka -o name)
kubectl exec --namespace zilla-mqtt-kafka-reflect-jwt "$KAFKA_POD" -- \
    /opt/bitnami/kafka/bin/kafka-topics.sh \
        --bootstrap-server localhost:9092 \
        --create \
        --topic mqtt_messages \
        --if-not-exists

kubectl exec --namespace zilla-mqtt-kafka-reflect-jwt "$KAFKA_POD" -- \
    /opt/bitnami/kafka/bin/kafka-topics.sh \
        --bootstrap-server localhost:9092 \
        --create \
        --topic mqtt_retained \
        --config "cleanup.policy=compact" \
        --if-not-exists

# Start port forwarding
kubectl port-forward --namespace zilla-mqtt-kafka-reflect-jwt service/zilla-mqtt-kafka-reflect-jwt 1883 8883 > /tmp/kubectl-zilla.log 2>&1 &
kubectl port-forward --namespace zilla-mqtt-kafka-reflect-jwt service/kafka 9092 29092 > /tmp/kubectl-kafka.log 2>&1 &
until nc -z localhost 1883; do sleep 1; done
until nc -z localhost 9092; do sleep 1; done
