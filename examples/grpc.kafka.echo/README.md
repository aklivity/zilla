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
+ ZILLA_CHART=oci://ghcr.io/aklivity/charts/zilla
+ VERSION=0.9.46
+ helm install zilla-grpc-kafka-echo oci://ghcr.io/aklivity/charts/zilla --version 0.9.46 --namespace zilla-grpc-kafka-echo --wait [...]
NAME: zilla-grpc-kafka-echo
LAST DEPLOYED: [...]
NAMESPACE: zilla-grpc-kafka-echo
STATUS: deployed
NOTES:
Zilla has been installed.
[...]
+ helm install zilla-grpc-kafka-echo-kafka chart --namespace zilla-grpc-kafka-echo --create-namespace --wait
NAME: zilla-grpc-kafka-echo-kafka
LAST DEPLOYED: [...]
NAMESPACE: zilla-grpc-kafka-echo
STATUS: deployed
REVISION: 1
TEST SUITE: None
++ kubectl get pods --namespace zilla-grpc-kafka-echo --selector app.kubernetes.io/instance=kafka -o name
+ KAFKA_POD=pod/kafka-74675fbb8-kpkm8
+ kubectl exec --namespace zilla-grpc-kafka-echo pod/kafka-74675fbb8-kpkm8 -- /opt/bitnami/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --create --topic echo-messages --if-not-exists
Created topic echo-messages.
+ kubectl port-forward --namespace zilla-grpc-kafka-echo service/zilla-grpc-kafka-echo 9090
+ nc -z localhost 9090
+ kubectl port-forward --namespace zilla-grpc-kafka-echo service/kafka 9092 29092
+ sleep 1
+ nc -z localhost 9090
Connection to localhost port 9090 [tcp/websm] succeeded!
+ nc -z localhost 9092
Connection to localhost port 9092 [tcp/XmlIpcRegSvc] succeeded!
```
### Verify behavior

#### Unary Stream

Echo `{"message":"Hello World"}` message via unary rpc using `grpcurl` client.

```bash
$ grpcurl -insecure -proto proto/echo.proto  -d '{"message":"Hello World"}' localhost:9090 example.EchoService.EchoUnary
{
  "message": "Hello World"
}
```

Verify the message payload, followed by a tombstone to mark the end of the request.
```bash
$ kcat -C -b localhost:9092 -t echo-messages -J -u | jq .
{
  "topic": "echo-messages",
  "partition": 0,
  "offset": 0,
  "tstype": "create",
  "ts": 1683827977709,
  "broker": 1,
  "headers": [
    "zilla:service",
    "example.EchoService",
    "zilla:method",
    "EchoUnary",
    "zilla:reply-to",
    "echo-messages",
    "zilla:correlation-id",
    "13360c3e-6c68-4c1f-bb7b-3cbd832a6007-74b89a7e944bd502db9d81165bda4983"
  ],
  "key": "13360c3e-6c68-4c1f-bb7b-3cbd832a6007-74b89a7e944bd502db9d81165bda4983",
  "payload": "\n\u000bHello World"
}
{
  "topic": "echo-messages",
  "partition": 0,
  "offset": 1,
  "tstype": "create",
  "ts": 1683827977742,
  "broker": 1,
  "headers": [
    "zilla:service",
    "example.EchoService",
    "zilla:method",
    "EchoUnary",
    "zilla:reply-to",
    "echo-messages",
    "zilla:correlation-id",
    "13360c3e-6c68-4c1f-bb7b-3cbd832a6007-74b89a7e944bd502db9d81165bda4983"
  ],
  "key": "13360c3e-6c68-4c1f-bb7b-3cbd832a6007-74b89a7e944bd502db9d81165bda4983",
  "payload": null
}
% Reached end of topic echo-messages [0] at offset 2
```

#### Bidirectional Stream

Echo messages via bidirectional streaming rpc.

```bash
$ grpcurl -insecure -proto proto/echo.proto -d @ localhost:9090 example.EchoService.EchoBidiStream
```

Paste below message.

```
{
  "message": "Hello World"
}
```

Verify the message payloads, followed by a tombstone to mark the end of each request.

```bash
$ kcat -C -b localhost:9092 -t echo-messages -J -u | jq .
...
{
  "topic": "echo-messages",
  "partition": 0,
  "offset": 2,
  "tstype": "create",
  "ts": 1683828250706,
  "broker": 1,
  "headers": [
    "zilla:service",
    "example.EchoService",
    "zilla:method",
    "EchoBidiStream",
    "zilla:reply-to",
    "echo-messages",
    "zilla:correlation-id",
    "3eb292f7-c503-42d2-a579-82da7bc853f8-45b1b1a7b121d744d1d1a68b30ebc5ef"
  ],
  "key": "3eb292f7-c503-42d2-a579-82da7bc853f8-45b1b1a7b121d744d1d1a68b30ebc5ef",
  "payload": "\n\u000bHello World"
}
{
  "topic": "echo-messages",
  "partition": 0,
  "offset": 3,
  "tstype": "create",
  "ts": 1683828252352,
  "broker": 1,
  "headers": [
    "zilla:service",
    "example.EchoService",
    "zilla:method",
    "EchoBidiStream",
    "zilla:reply-to",
    "echo-messages",
    "zilla:correlation-id",
    "3eb292f7-c503-42d2-a579-82da7bc853f8-45b1b1a7b121d744d1d1a68b30ebc5ef"
  ],
  "key": "3eb292f7-c503-42d2-a579-82da7bc853f8-45b1b1a7b121d744d1d1a68b30ebc5ef",
  "payload": null
}
% Reached end of topic echo-messages [0] at offset 4
```

### Teardown

The `teardown.sh` script stops port forwarding, uninstalls Zilla and deletes the namespace.

```bash
$ ./teardown.sh
+ pgrep kubectl
99998
99999
+ killall kubectl
+ helm uninstall zilla-grpc-kafka-echo zilla-grpc-kafka-echo-kafka --namespace zilla-grpc-kafka-echo
release "zilla-grpc-kafka-echo" uninstalled
release "zilla-grpc-kafka-echo-kafka" uninstalled
+ kubectl delete namespace zilla-grpc-echo
namespace "zilla-grpc-kafka-echo" deleted
```
