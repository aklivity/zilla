# ws.reflect

Listens on ws port `8080` and will echo back whatever is sent to the server, broadcasting to all clients.
Listens on wss port `9090` and will echo back whatever is sent to the server, broadcasting to all clients.

### Requirements

- bash, jq, nc
- Kubernetes (e.g. Docker Desktop with Kubernetes enabled)
- kubectl
- helm 3.0+
- wscat

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
+ helm install zilla-ws-reflect oci://ghcr.io/aklivity/charts/zilla --namespace zilla-ws-reflect --create-namespace --wait [...]
NAME: zilla-ws-reflect
LAST DEPLOYED: [...]
NAMESPACE: zilla-ws-reflect
STATUS: deployed
REVISION: 1
NOTES:
Zilla has been installed.
[...]
+ nc -z localhost 8080
+ kubectl port-forward --namespace zilla-ws-reflect service/zilla-ws-reflect 8080 9090
+ sleep 1
+ nc -z localhost 8080
Connection to localhost port 8080 [tcp/http-alt] succeeded!
```

### Verify behavior

Connect each client first, then send `Hello, one` from first client, then send `Hello, two` from second client.

```bash
wscat -c ws://localhost:8080/ -s echo
```

output:

```text
Connected (press CTRL+C to quit)
> Hello, one
< Hello, one
< Hello, two
```

```bash
wscat -c wss://localhost:9090/ --ca test-ca.crt -s echo
```

output:

```text
Connected (press CTRL+C to quit)
< Hello, one
> Hello, two
< Hello, two
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
+ helm uninstall zilla-ws-reflect --namespace zilla-ws-reflect
release "zilla-ws-reflect" uninstalled
+ kubectl delete namespace zilla-ws-reflect
namespace "zilla-ws-reflect" deleted
```
