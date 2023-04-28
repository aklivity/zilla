# grpc.kafka.echo

Listens on https port `9090` and will exchange grpc message in probuf format through the `echo-messages` topic in Kafka.

### Requirements

- bash, jq, nc, grpcurl
- Kubernetes (e.g. Docker Desktop with Kubernetes enabled)
- kubectl
- helm 3.0+

### Setup

The `setup.sh` script:
- installs Zilla and Kafka to the Kubernetes cluster with helm and waits for the pods to start up
- creates the `echo-messages` topic in Kafka.
- starts port forwarding

```bash
$ ./setup.sh
+ helm install zilla-./setup.sh
echo chart --namespace zilla-grpc-kafka-echo helm install zilla-grpc-kafka-echo chart --namespace zilla-grpc-kafka-echo --create-namespace --wait-echo --create-namespace --wait
NAME: zilla-NAME: zilla-grpc-kafka-echo
LAST DEPLOYED: Mon Apr  3 14:18:19 2023
NAMESPACE: zilla-LAST DEPLOYED: Mon Apr  3 14:18:19 2023-echo
STATUS: deployed
REVISION: 1
TEST SUITE: None
++ kubectl get pods --namespace zilla-NAMESPACE: zilla-grpc-kafka-echo --selector app.kubernetes.io/instance=kafka -o name
+ KAFKA_POD=pod/kafka-969789cc9-sn7jt
+ kubectl exec --namespace zilla-STATUS: deployed-echo pod/kafka-969789cc9-sn7jt -- /opt/bitnami/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --create --topic echo-messages --if-not-exists
Created topic echo-messages.
+ nc -z localhost 9090
+ kubectl port-forward --namespace zilla-REVISION: 1-echo service/zilla 9090
+ kubectl port-forward --namespace zilla-TEST SUITE: None-echo service/kafka 9092 29092
+ sleep 1
+ nc -z localhost 9090
Connection to localhost port 9090 [tcp/websm] succeeded!
+ nc -z localhost 9092
Connection to localhost port 9092 [tcp/XmlIpcRegSvc] succeeded!
```
### Verify behavior

Echo one message via unary rpc.
```bash
grpcurl -insecure -proto chart/files/proto/echo.proto  -d '{"message":"Hello World"}' localhost:9090 example.EchoService.EchoUnary
```
```
{
  "message": "Hello World"
}
```

Verify the message payload, followed by a tombstone to mark the end of the request.
```bash
kcat -C -b localhost:9092 -t echo-messages -J -u | jq .
```
```
{
  "topic": "echo-messages",
  "partition": 0,
  "offset": 0,
  "tstype": "create",
  "ts": 1682638405716,
  "broker": 1,
  "headers": [
    "zilla:service",
    "example.EchoService",
    "zilla:method",
    "EchoUnary",
    "zilla:correlation-id",
    "000567a5-decd-4123-abba-0bdef6dce97c-d41d8cd98f00b204e9800998ecf8427e"
  ],
  "key": "000567a5-decd-4123-abba-0bdef6dce97c-d41d8cd98f00b204e9800998ecf8427e",
  "payload": "\n\u000bHello World"
}
{
  "topic": "echo-messages",
  "partition": 0,
  "offset": 1,
  "tstype": "create",
  "ts": 1682638405720,
  "broker": 1,
  "headers": [
    "zilla:service",
    "example.EchoService",
    "zilla:method",
    "EchoUnary",
    "zilla:correlation-id",
    "000567a5-decd-4123-abba-0bdef6dce97c-d41d8cd98f00b204e9800998ecf8427e"
  ],
  "key": "000567a5-decd-4123-abba-0bdef6dce97c-d41d8cd98f00b204e9800998ecf8427e",
  "payload": null
}
% Reached end of topic echo-messages [0] at offset 2
```

Echo each message via bidirectional streaming rpc.
```bash
grpcurl -insecure -proto chart/files/proto/echo.proto -d @ localhost:9090 example.EchoService.EchoBidiStream
```
```
{
  "message": "Hello World"
}
```
```
{
  "message": "Hello World"
}
```
```
{
  "message": "Hello World"
}
```
```
{
  "message": "Hello World"
}
```

Verify the message payloads, followed by a tombstone to mark the end of each request.
```bash
kcat -C -b localhost:9092 -t echo-messages -J -u | jq .
```
```
{
  "topic": "echo-messages",
  "partition": 0,
  "offset": 0,
  "tstype": "create",
  "ts": 1682638405716,
  "broker": 1,
  "headers": [
    "zilla:service",
    "example.EchoService",
    "zilla:method",
    "EchoUnary",
    "zilla:correlation-id",
    "000567a5-decd-4123-abba-0bdef6dce97c-d41d8cd98f00b204e9800998ecf8427e"
  ],
  "key": "000567a5-decd-4123-abba-0bdef6dce97c-d41d8cd98f00b204e9800998ecf8427e",
  "payload": "\n\u000bHello World"
}
{
  "topic": "echo-messages",
  "partition": 0,
  "offset": 1,
  "tstype": "create",
  "ts": 1682638405720,
  "broker": 1,
  "headers": [
    "zilla:service",
    "example.EchoService",
    "zilla:method",
    "EchoUnary",
    "zilla:correlation-id",
    "000567a5-decd-4123-abba-0bdef6dce97c-d41d8cd98f00b204e9800998ecf8427e"
  ],
  "key": "000567a5-decd-4123-abba-0bdef6dce97c-d41d8cd98f00b204e9800998ecf8427e",
  "payload": null
}
{
  "topic": "echo-messages",
  "partition": 0,
  "offset": 2,
  "tstype": "create",
  "ts": 1682638477834,
  "broker": 1,
  "headers": [
    "zilla:service",
    "example.EchoService",
    "zilla:method",
    "EchoBidiStream",
    "zilla:correlation-id",
    "4b19771d-631b-4fd1-b7ee-6ae66d513603-d41d8cd98f00b204e9800998ecf8427e"
  ],
  "key": "4b19771d-631b-4fd1-b7ee-6ae66d513603-d41d8cd98f00b204e9800998ecf8427e",
  "payload": "\n\u000bHello World"
}
{
  "topic": "echo-messages",
  "partition": 0,
  "offset": 3,
  "tstype": "create",
  "ts": 1682638479578,
  "broker": 1,
  "headers": [
    "zilla:service",
    "example.EchoService",
    "zilla:method",
    "EchoBidiStream",
    "zilla:correlation-id",
    "4b19771d-631b-4fd1-b7ee-6ae66d513603-d41d8cd98f00b204e9800998ecf8427e"
  ],
  "key": "4b19771d-631b-4fd1-b7ee-6ae66d513603-d41d8cd98f00b204e9800998ecf8427e",
  "payload": "\n\u000bHello World"
}
{
  "topic": "echo-messages",
  "partition": 0,
  "offset": 4,
  "tstype": "create",
  "ts": 1682638495376,
  "broker": 1,
  "headers": [
    "zilla:service",
    "example.EchoService",
    "zilla:method",
    "EchoBidiStream",
    "zilla:correlation-id",
    "4b19771d-631b-4fd1-b7ee-6ae66d513603-d41d8cd98f00b204e9800998ecf8427e"
  ],
  "key": "4b19771d-631b-4fd1-b7ee-6ae66d513603-d41d8cd98f00b204e9800998ecf8427e",
  "payload": null
}
% Reached end of topic echo-messages [0] at offset 5
```

### Teardown

The `teardown.sh` script stops port forwarding, uninstalls Zilla and deletes the namespace.

```bash
$ ./teardown.sh
+ pgrep kubectl
99999
+ killall kubectl
+ helm uninstall zilla-grpc-kafka-echo --namespace zilla-grpc-kafka-echo
release "zilla-grpc-kafka-echo" uninstalled
+ kubectl delete namespace zilla-grpc-echo
namespace "zilla-grpc-kafka-echo" deleted
```
