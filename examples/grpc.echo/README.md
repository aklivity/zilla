# grpc.echo

Listens on tcp port `9090` and will echo grpc message sent by client.

### Requirements

- bash, jq, nc, grpcurl
- Kubernetes (e.g. Docker Desktop with Kubernetes enabled)
- kubectl
- helm 3.0+
- ghz

### Setup

The `setup.sh` script:

- installs Zilla to the Kubernetes cluster with helm and waits for the pod to start up
- starts port forwarding

```bash
./setup.sh
```

output:

```text
+ ZILLA_CHART=oci://ghcr.io/aklivity/charts/zilla
+ helm install zilla-grpc-echo oci://ghcr.io/aklivity/charts/zilla --namespace zilla-grpc-echo --wait [...]
NAME: zilla-grpc-echo
LAST DEPLOYED: [...]
NAMESPACE: zilla-grpc-echo
STATUS: deployed
REVISION: 1
NOTES:
Zilla has been installed.
[...]
+ nc -z localhost 9090
+ kubectl port-forward --namespace zilla-grpc-echo service/zilla-grpc-echo 9090
+ sleep 1
+ nc -z localhost 9090
Connection to localhost port 9090 [tcp/italk] succeeded!
```

### Verify behavior

#### Unary Stream

Echo `{"message":"Hello World"}` message via unary rpc using `grpcurl` command.

```bash
grpcurl -insecure -proto proto/echo.proto  -d '{"message":"Hello World"}' localhost:9090 example.EchoService.EchoUnary
```

output:

```json
{
  "message": "Hello World"
}
```

#### Bidirectional Stream

Echo messages via bidirectional streaming rpc.

```bash
grpcurl -insecure -proto proto/echo.proto -d @ localhost:9090 example.EchoService.EchoBidiStream
```

Paste below message.

```json
{
  "message": "Hello World"
}
```

### Bench

```bash
ghz --config bench.json \
    --proto proto/echo.proto \
    --call example.EchoService/EchoBidiStream \
    localhost:9090
```

### Teardown

The `teardown.sh` script stops port forwarding, uninstalls Zilla and deletes the namespace.

```bash
./teardown.sh
```

output:

```text
+ pgrep kubectl
99999
+ killall kubectl
+ helm uninstall zilla-grpc-echo --namespace zilla-grpc-echo
release "zilla-grpc-echo" uninstalled
+ kubectl delete namespace zilla-grpc-echo
namespace "zilla-grpc-echo" deleted
```
