#!/bin/bash
set -ex

# Verify Grpc Echo image already available locally
docker image inspect zilla-examples/grpc-echo:latest --format 'Image Found {{.RepoTags}}'

# Install Zilla to the Kubernetes cluster with helm and wait for the pod to start up
ZILLA_CHART=oci://ghcr.io/aklivity/charts/zilla
NAMESPACE=zilla-grpc-kafka-proxy
helm upgrade --install zilla $ZILLA_CHART --namespace $NAMESPACE --create-namespace --wait \
    --values values.yaml \
    --set-file zilla\\.yaml=zilla.yaml \
    --set-file configMaps.proto.data.echo\\.proto=proto/echo.proto \
    --set-file secrets.tls.data.localhost\\.p12=tls/localhost.p12

# Install Grpc Echo and Kafka to the Kubernetes cluster with helm and wait for the pods to start up
helm upgrade --install grpc-echo-kafka chart --namespace $NAMESPACE --create-namespace --wait --timeout 2m

# Create the echo-requests and echo-responses topic in Kafka
KAFKA_POD=$(kubectl get pods --namespace $NAMESPACE --selector app.kubernetes.io/instance=kafka -o name)
kubectl exec --namespace $NAMESPACE "$KAFKA_POD" -- \
    /opt/bitnami/kafka/bin/kafka-topics.sh \
        --bootstrap-server localhost:9092 \
        --create \
        --topic echo-requests \
        --if-not-exists
kubectl exec --namespace $NAMESPACE "$KAFKA_POD" -- \
    /opt/bitnami/kafka/bin/kafka-topics.sh \
        --bootstrap-server localhost:9092 \
        --create \
        --topic echo-responses \
        --if-not-exists

# Start port forwarding
kubectl port-forward --namespace $NAMESPACE service/zilla 7153 > /tmp/kubectl-zilla.log 2>&1 &
kubectl port-forward --namespace $NAMESPACE service/kafka 9092 29092 > /tmp/kubectl-kafka.log 2>&1 &
kubectl port-forward --namespace $NAMESPACE service/grpc-echo 8080 > /tmp/kubectl-grpc-echo.log 2>&1 &
until nc -z localhost 7153; do sleep 1; done
until nc -z localhost 9092; do sleep 1; done
until nc -z localhost 8080; do sleep 1; done
