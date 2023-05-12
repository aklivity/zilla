# grpc..kafka.proxy

Listens on https port `9090` and uses kafka as proxy to talk to `grpc_echo` on tcp port `8080`.

### Requirements

- bash, jq, nc, grpcurl
- Kubernetes (e.g. Docker Desktop with Kubernetes enabled)
- kubectl
- helm 3.0+

### Build `grpc-echo` service

```bash
$ docker build -t zilla-examples/grpc-echo:latest .
 => exporting to image
  => => exporting layers
 => => writing image sha256:8ad3819be40334045c01d189000c63a1dfe22b2a97ef376d0c6e56616de132c7 
 => => naming to docker.io/zilla-examples/grpc-echo:latest
```

### Setup

The `setup.sh` script:
- installs Zilla to the Kubernetes cluster with helm and waits for the pod to start up
- starts port forwarding

```bash
$ ./setup.sh
docker image inspect zilla-examples/grpc-echo:latest --format 'Image Found {{.RepoTags}}'

+ helm install zilla-grpc-kafka-proxy chart --namespace zilla-grpc-kafka-proxy --create-namespace --wait --timeout 3m
NAME: zilla-grpc-kafka-proxy
LAST DEPLOYED: Tue Apr 18 14:46:24 2023
NAMESPACE: zilla-grpc-kafka-proxy
STATUS: deployed
REVISION: 1
TEST SUITE: None
++ kubectl get pods --namespace zilla-grpc-kafka-proxy --selector app.kubernetes.io/instance=kafka -o name
+ KAFKA_POD=pod/kafka-74675fbb8-bg8kl
+ kubectl exec --namespace zilla-grpc-kafka-proxy pod/kafka-74675fbb8-bg8kl -- /opt/bitnami/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --create --topic echo-requests --if-not-exists
Created topic echo-requests.
++ kubectl get pods --namespace zilla-grpc-kafka-proxy --selector app.kubernetes.io/instance=kafka -o name
+ KAFKA_POD=pod/kafka-74675fbb8-bg8kl
+ kubectl exec --namespace zilla-grpc-kafka-proxy pod/kafka-74675fbb8-bg8kl -- /opt/bitnami/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --create --topic echo-responses --if-not-exists
Created topic echo-responses.
+ kubectl port-forward --namespace zilla-grpc-kafka-proxy service/zilla 9090
+ nc -z localhost 9090
+ kubectl port-forward --namespace zilla-grpc-kafka-proxy service/grpc-echo 8080
+ sleep 1
+ nc -z localhost 9090
Connection to localhost port 9090 [tcp/websm] succeeded!
+ nc -z localhost 8080
Connection to localhost port 8080 [tcp/http-alt] succeeded!
```

### Verify behavior

#### Unary Stream

Echo `{"message":"Hello World"}` message via unary rpc.

```bash
grpcurl -insecure -proto chart/files/proto/echo.proto  -d '{"message":"Hello World"}' localhost:9090 example.EchoService.EchoUnary
{
  "message": "Hello World"
}
```

Verify the message payload, followed by a tombstone to mark the end of the request.

```bash
kcat -C -b localhost:9092 -t echo-requests -J -u | jq .
{
  "topic": "echo-requests",
  "partition": 0,
  "offset": 0,
  "tstype": "create",
  "ts": 1683828554432,
  "broker": 1,
  "headers": [
    "zilla:service",
    "example.EchoService",
    "zilla:method",
    "EchoUnary",
    "zilla:reply-to",
    "echo-responses",
    "zilla:correlation-id",
    "c3c3eb97-313f-4cf0-aa6c-f83c1080e649-cdd8170a6db4597eb33ba423f67e19e2"
  ],
  "key": "c3c3eb97-313f-4cf0-aa6c-f83c1080e649-cdd8170a6db4597eb33ba423f67e19e2",
  "payload": "\n\u000bHello World"
}
{
  "topic": "echo-requests",
  "partition": 0,
  "offset": 1,
  "tstype": "create",
  "ts": 1683828554442,
  "broker": 1,
  "headers": [
    "zilla:service",
    "example.EchoService",
    "zilla:method",
    "EchoUnary",
    "zilla:reply-to",
    "echo-responses",
    "zilla:correlation-id",
    "c3c3eb97-313f-4cf0-aa6c-f83c1080e649-cdd8170a6db4597eb33ba423f67e19e2"
  ],
  "key": "c3c3eb97-313f-4cf0-aa6c-f83c1080e649-cdd8170a6db4597eb33ba423f67e19e2",
  "payload": null
}
% Reached end of topic echo-requests [0] at offset 2
```

Verify the message payload, followed by a tombstone to mark the end of the response.

```bash
{
  "topic": "echo-responses",
  "partition": 0,
  "offset": 0,
  "tstype": "create",
  "ts": 1683828555010,
  "broker": 1,
  "headers": [
    "zilla:correlation-id",
    "c3c3eb97-313f-4cf0-aa6c-f83c1080e649-cdd8170a6db4597eb33ba423f67e19e2"
  ],
  "key": "c3c3eb97-313f-4cf0-aa6c-f83c1080e649-cdd8170a6db4597eb33ba423f67e19e2",
  "payload": "\n\u000bHello World"
}
{
  "topic": "echo-responses",
  "partition": 0,
  "offset": 1,
  "tstype": "create",
  "ts": 1683828555018,
  "broker": 1,
  "headers": [
    "zilla:correlation-id",
    "c3c3eb97-313f-4cf0-aa6c-f83c1080e649-cdd8170a6db4597eb33ba423f67e19e2",
    "zilla:status",
    "0"
  ],
  "key": "c3c3eb97-313f-4cf0-aa6c-f83c1080e649-cdd8170a6db4597eb33ba423f67e19e2",
  "payload": null
}
% Reached end of topic echo-responses [0] at offset 2
```

#### Bidirectional streaming 

Echo messages via bidirectional streaming rpc.

```bash
grpcurl -insecure -proto chart/files/proto/echo.proto -d @ localhost:9090 example.EchoService.EchoBidiStream
```

Past below message.

```
{
  "message": "Hello World"
}
```

### Teardown

The `teardown.sh` script stops port forwarding, uninstalls Zilla and deletes the namespace.

```bash
$ ./teardown.sh
+ pgrep kubectl
24494
24495
+ killall kubectl
+ helm uninstall zilla-grpc-kafka-proxy --namespace zilla-grpc-kafka-proxy
release "zilla-grpc-kafka-proxy" uninstalled
+ kubectl delete namespace zilla-grpc-kafka-proxy
namespace "zilla-grpc-kafka-proxy" deleted
```
