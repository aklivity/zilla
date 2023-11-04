#!/bin/bash
set -ex
# Install Zilla to the Kubernetes cluster with helm and wait for the pod to start up
ZILLA_CHART=oci://ghcr.io/aklivity/charts/zilla
NAMESPACE=zilla-grpc-kafka-fanout
helm upgrade --install zilla $ZILLA_CHART --namespace $NAMESPACE --create-namespace --wait \
    --values values.yaml \
    --set-file zilla\\.yaml=zilla.yaml \
    --set-file configMaps.proto.data.fanout\\.proto=proto/fanout.proto \
    --set-file secrets.tls.data.localhost\\.p12=tls/localhost.p12

# Install Zilla and Kafka to the Kubernetes cluster with helm and wait for the pod to start up
helm upgrade --install kafka chart --namespace $NAMESPACE --create-namespace --wait

# Create the messages topic in Kafka
KAFKA_POD=$(kubectl get pods --namespace $NAMESPACE --selector app.kubernetes.io/instance=kafka -o name)
kubectl exec --namespace $NAMESPACE "$KAFKA_POD" -- \
    /opt/bitnami/kafka/bin/kafka-topics.sh \
        --bootstrap-server localhost:9092 \
        --create \
        --topic messages \
        --if-not-exists

# Start port forwarding
kubectl port-forward --namespace $NAMESPACE service/zilla 7153 > /tmp/kubectl-zilla.log 2>&1 &
kubectl port-forward --namespace $NAMESPACE service/kafka 9092 29092 > /tmp/kubectl-kafka.log 2>&1 &
until nc -z localhost 7153; do sleep 1; done
until nc -z localhost 9092; do sleep 1; done
