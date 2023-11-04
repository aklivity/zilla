# ws.echo

Listens on ws port `7114` and will echo back whatever is sent to the server.
Listens on wss port `7143` and will echo back whatever is sent to the server.

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
+ helm upgrade --install zilla-ws-echo oci://ghcr.io/aklivity/charts/zilla --namespace zilla-ws-echo --create-namespace --wait [...]
NAME: zilla-ws-echo
LAST DEPLOYED: [...]
NAMESPACE: zilla-ws-echo
STATUS: deployed
REVISION: 1
NOTES:
Zilla has been installed.
[...]
+ nc -z localhost 7114
+ kubectl port-forward --namespace zilla-ws-echo service/zilla 7114 7143
+ sleep 1
+ nc -z localhost 7114
Connection to localhost port 7114 [tcp/http-alt] succeeded!
```

### Install wscat

```bash
npm install wscat -g
```

### Verify behavior

```bash
wscat -c ws://localhost:7114/ -s echo
```

Type a `Hello, world` message and press `enter`.

output:

```text
Connected (press CTRL+C to quit)
> Hello, world
< Hello, world
```

```bash
wscat -c wss://localhost:7143/ --ca test-ca.crt -s echo
```

Type a `Hello, world` message and press `enter`.

output:

```text
Connected (press CTRL+C to quit)
> Hello, world
< Hello, world
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
+ helm uninstall zilla-ws-echo --namespace zilla-ws-echo
release "zilla-ws-echo" uninstalled
+ kubectl delete namespace zilla-ws-echo
namespace "zilla-ws-echo" deleted
```
