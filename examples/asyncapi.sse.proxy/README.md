# asyncapi.sse.proxy

Listens on http port `7114` and will stream back whatever is published to `sse_server` on tcp port `7001`.

### Requirements

- bash, jq, nc
- Kubernetes (e.g. Docker Desktop with Kubernetes enabled)
- kubectl
- helm 3.0+

### Setup

The `setup.sh` script:

- installs Zilla and SSE server to the Kubernetes cluster with helm and waits for the pods to start up
- starts port forwarding

```bash
./setup.sh
```

output:

```text
+ helm upgrade --install zilla oci://ghcr.io/aklivity/charts/zilla --version '^0.9.0' --namespace zilla-asyncapi-sse-proxy --create-namespace --wait --values values.yaml --set-file 'zilla\.yaml=zilla.yaml' --set-file 'configMaps.asyncapi.data.sse-asyncapi\.yaml=sse-asyncapi.yaml'
Release "zilla" does not exist. Installing it now.
Pulled: ghcr.io/aklivity/charts/zilla:0.9.82
Digest: sha256:c2c48436921eb87befb8ead4909c7b578147df4b77697e36aa59e6317d44d750
NAME: zilla
LAST DEPLOYED: [...]
NAMESPACE: zilla-asyncapi-sse-proxy
STATUS: deployed
REVISION: 1
Release "sse-server" does not exist. Installing it now.
NAME: sse-server
LAST DEPLOYED: [...]
NAMESPACE: zilla-asyncapi-sse-proxy
STATUS: deployed
REVISION: 1
TEST SUITE: None
+ kubectl port-forward --namespace zilla-asyncapi-sse-proxy service/zilla 7114
+ nc -z localhost 7114
+ kubectl port-forward --namespace zilla-asyncapi-sse-proxy service/sse-server 8001 7001
+ sleep 1
+ nc -z localhost 7114
Connection to localhost port 7114 [tcp/*] succeeded!
+ nc -z localhost 8001
Connection to localhost port 8001 [tcp/vcom-tunnel] succeeded!
+ nc -z localhost 7001
Connection to localhost port 7001 [tcp/afs3-callback] succeeded!
```


### Verify behavior

Connect `curl` client first to Zilla over SSE.

```bash
curl -N --http2 -H "Accept:text/event-stream" -v "http://localhost:7114/events/1"
```

output:

```text
*   Trying 127.0.0.1:7114...
* Connected to localhost (127.0.0.1) port 7114 (#0)
> GET /events/1 HTTP/1.1
> Host: localhost:7114
> User-Agent: curl/7.88.1
> Connection: Upgrade, HTTP2-Settings
> Upgrade: h2c
> HTTP2-Settings: AAMAAABkAAQCAAAAAAIAAAAA
> Accept:text/event-stream
>
< HTTP/1.1 200 OK
< Content-Type: text/event-stream
< Transfer-Encoding: chunked
< Access-Control-Allow-Origin: *
<
event:event name
data:{ "id": 1, "name": "Hello World!" }
```

From another terminal send an invalid data from `nc` client. Note that the invalid event will not arrive to the client.

```bash
echo '{ "name": "event name", "data": { "id": -1, "name": "Hello World!" } }' | nc -c localhost 7001
```

Now send a valid event, where the id is non-negative and the message will arrive to `curl` client.

```bash
echo '{ "name": "event name", "data": { "id": 1, "name": "Hello World!" } }' | nc -c localhost 7001
```

### Teardown

The `teardown.sh` script stops port forwarding, uninstalls Zilla and deletes the namespace.

```bash
./teardown.sh
```

output:

```text
pgrep kubectl
99999
+ killall kubectl
+ NAMESPACE=zilla-asyncapi-sse-proxy
+ helm uninstall zilla sse-server --namespace zilla-asyncapi-sse-proxy
release "zilla" uninstalled
release "sse-server" uninstalled
+ kubectl delete namespace zilla-asyncapi-sse-proxy
namespace "zilla-asyncapi-sse-proxy" deleted

```
