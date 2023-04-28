# grpc.proxy

Listens on https port `9090` and will echo back whatever is published to `grpc_echo` on tcp port `8080`.

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

+ helm install zilla-grpc-proxy chart --namespace zilla-grpc-proxy --create-namespace --wait --timeout 2m
NAME: zilla-grpc-proxy
LAST DEPLOYED: Tue Apr 18 14:46:24 2023
NAMESPACE: zilla-grpc-proxy
STATUS: deployed
REVISION: 1
TEST SUITE: None
+ kubectl port-forward --namespace zilla-grpc-proxy service/zilla 9090
+ nc -z localhost 9090
+ kubectl port-forward --namespace zilla-grpc-proxy service/grpc-echo 8080
+ sleep 1
+ nc -z localhost 9090
Connection to localhost port 9090 [tcp/websm] succeeded!
+ nc -z localhost 8080
Connection to localhost port 8080 [tcp/http-alt] succeeded!
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

Echo each message via bidirectional streaming rpc.
```bash
grpcurl -insecure -proto chart/files/proto/echo.proto -d @ localhost:9090 example.EchoService.EchoBidiStream
```
```
{
  "message": "Hello World"
}
{
  "message": "Hello World"
}
```
```
{
  "message": "Hello World"
}
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
+ helm uninstall zilla-grpc-proxy --namespace zilla-grpc-proxy
release "zilla-grpc-proxy" uninstalled
+ kubectl delete namespace zilla-grpc-proxy
namespace "zilla-grpc-proxy" deleted
```
