# grpc.kafka.fanout

Listens on https port `9090` and fanout messages from `messages` topic in Kafka.

### Requirements

- bash, jq, nc, grpcurl, protoc
- Kubernetes (e.g. Docker Desktop with Kubernetes enabled)
- kubectl
- helm 3.0+

### Setup

The `setup.sh` script:

- installs Zilla and Kafka to the Kubernetes cluster with helm and waits for the pods to start up
- creates the `messages` topic in Kafka.
- starts port forwarding

```bash
./setup.sh
```

output:

```text
+ ZILLA_CHART=oci://ghcr.io/aklivity/charts/zilla
+ helm install zilla-grpc-kafka-fanout oci://ghcr.io/aklivity/charts/zilla --namespace zilla-grpc-kafka-fanout --create-namespace --wait [...]
NAME: zilla-grpc-kafka-fanout
LAST DEPLOYED: [...]
NAMESPACE: zilla-grpc-kafka-fanout
STATUS: deployed
REVISION: 1
NOTES:
Zilla has been installed.
[...]
+ helm install zilla-grpc-kafka-fanout-kafka chart --namespace zilla-grpc-kafka-fanout --create-namespace --wait
NAME: zilla-grpc-kafka-fanout-kafka
LAST DEPLOYED: [...]
NAMESPACE: zilla-grpc-kafka-fanout
STATUS: deployed
REVISION: 1
TEST SUITE: None
++ kubectl get pods --namespace zilla-grpc-kafka-fanout --selector app.kubernetes.io/instance=kafka -o name
+ KAFKA_POD=pod/kafka-969789cc9-mxd98
+ kubectl exec --namespace zilla-grpc-kafka-fanout pod/kafka-969789cc9-mxd98 -- /opt/bitnami/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --create --topic messages --if-not-exists
Created topic messages.
+ kubectl port-forward --namespace zilla-grpc-kafka-fanout service/zilla-grpc-kafka-fanout 9090
+ nc -z localhost 9090
+ kubectl port-forward --namespace zilla-grpc-kafka-fanout service/kafka 9092 29092
+ sleep 1
+ nc -z localhost 9090
Connection to localhost port 9090 [tcp/websm] succeeded!
+ nc -z localhost 9092
Connection to localhost port 9092 [tcp/XmlIpcRegSvc] succeeded!
```

### Verify behavior

#### Unreliable server streaming

Prepare protobuf message to send to Kafka topic.

```bash
echo 'message: "test"' | protoc --encode=example.FanoutMessage proto/fanout.proto > binary.data
```

Produce protobuf message to Kafka topic, repeat to produce multiple messages.

```bash
kcat -P -b localhost:9092 -t messages -k -e ./binary.data
```

Stream messages via server streaming rpc.

```bash
grpcurl -insecure -proto proto/fanout.proto -d '' localhost:9090 example.FanoutService.FanoutServerStream
```

output:

```json
{
  "message": "test"
}
```

This output repeats for each message produced to Kafka.

#### Reliable server streaming

Build the reliable streaming client which uses `32767` field as last message id to send as metadata to resume streaming from last received message.

```bash
cd grpc.reliable.streaming/
./mvnw clean install
cd ..
```

Connect with the reliable streaming client.

```bash
java -jar grpc.reliable.streaming/target/grpc-example-develop-SNAPSHOT-jar-with-dependencies.jar
```

output:

```text
...
INFO: Found message: message: "test"
32767: "\001\002\000\002"
```

Simulate connection loss by stopping the `zilla` service in the `docker` stack.

```bash
kubectl scale --replicas=0 --namespace=zilla-grpc-kafka-fanout deployment/zilla-grpc-kafka-fanout
```

Simulate connection recovery by starting the `zilla` service again.

```bash
kubectl scale --replicas=1 --namespace=zilla-grpc-kafka-fanout deployment/zilla-grpc-kafka-fanout
```

Now you need to restart the port-forward.

```bash
kubectl port-forward --namespace zilla-grpc-kafka-fanout service/zilla-grpc-kafka-fanout 9090 > /tmp/kubectl-zilla.log 2>&1 &
```

Then produce another protobuf message to Kafka, repeat to produce multiple messages.

```bash
kcat -P -b localhost:9092 -t messages -k -e ./binary.data
```

The reliable streaming client will recover and zilla deliver only the new message.

```text
...
INFO: Found message: message: "test"
32767: "\001\002\000\004"
```

This output repeats for each message produced to Kafka after the zilla service is restart.

### Teardown

The `teardown.sh` script stops port forwarding, uninstalls Zilla and deletes the namespace.

```bash
./teardown.sh
```

output:

```text
+ pgrep kubectl
99998
99999
+ killall kubectl
+ helm uninstall zilla-grpc-kafka-fanout zilla-grpc-kafka-fanout-kafka --namespace zilla-grpc-kafka-fanout
release "zilla-grpc-kafka-fanout" uninstalled
release "zilla-grpc-kafka-fanout-kafka" uninstalled
+ kubectl delete namespace zilla-grpc-fanout
namespace "zilla-grpc-kafka-fanout" deleted
```
