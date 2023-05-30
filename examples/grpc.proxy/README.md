# grpc.proxy

Listens on https port `9090` and will echo back whatever is published to `grpc-proxy` on tcp port `8080`.

### Requirements

- bash, jq, nc, grpcurl
- Kubernetes (e.g. Docker Desktop with Kubernetes enabled)
- kubectl
- helm 3.0+

### Build `grpc-proxy` service

```bash
docker build -t zilla-examples/grpc-echo:latest .
```

output:

```text
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
./setup.sh
```

output:

```text
+ docker image inspect zilla-examples/grpc-echo:latest --format 'Image Found {{.RepoTags}}'
Image Found [zilla-examples/grpc-echo:latest]
+ ZILLA_CHART=oci://ghcr.io/aklivity/charts/zilla
+ VERSION=0.9.46
+ helm install zilla-grpc-proxy oci://ghcr.io/aklivity/charts/zilla --version 0.9.46 --namespace zilla-grpc-proxy --create-namespace --wait [...]
NAME: zilla-grpc-proxy
LAST DEPLOYED: [...]
NAMESPACE: zilla-grpc-proxy
STATUS: deployed
REVISION: 1
NOTES:
Zilla has been installed.
[...]
+ helm install zilla-grpc-proxy-grpc-echo chart --namespace zilla-grpc-proxy --create-namespace --wait --timeout 2m
NAME: zilla-grpc-proxy-grpc-echo
LAST DEPLOYED: [...]
NAMESPACE: zilla-grpc-proxy
STATUS: deployed
REVISION: 1
TEST SUITE: None
+ kubectl port-forward --namespace zilla-grpc-proxy service/zilla-grpc-proxy 9090
+ kubectl port-forward --namespace zilla-grpc-proxy service/grpc-echo 8080
+ nc -z localhost 9090
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
grpcurl -insecure -proto proto/echo.proto  -d '{"message":"Hello World"}' localhost:9090 example.EchoService.EchoUnary
```

output:

```json
{
  "message": "Hello World"
}
```

#### Bidirectional streaming

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
+ helm uninstall zilla-grpc-proxy zilla-grpc-proxy-grpc-echo --namespace zilla-grpc-proxy
release "zilla-grpc-proxy" uninstalled
release "zilla-grpc-proxy-grpc-echo" uninstalled
+ kubectl delete namespace zilla-grpc-proxy
namespace "zilla-grpc-proxy" deleted
```
