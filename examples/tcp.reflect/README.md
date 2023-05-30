# tcp.reflect

Listens on tcp port `12345` and will echo back whatever is sent to the server, broadcasting to all clients.

### Requirements

- bash, jq, nc
- Kubernetes (e.g. Docker Desktop with Kubernetes enabled)
- kubectl
- helm 3.0+

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
+ VERSION=0.9.46
+ helm install zilla-tcp-reflect oci://ghcr.io/aklivity/charts/zilla --version 0.9.46 --namespace zilla-tcp-reflect [...]
NAME: zilla-tcp-reflect
LAST DEPLOYED: [...]
NAMESPACE: zilla-tcp-reflect
STATUS: deployed
REVISION: 1
NOTES:
Zilla has been installed.
[...]
+ nc -z localhost 12345
+ kubectl port-forward --namespace zilla-tcp-reflect service/zilla-tcp-reflect 12345
+ sleep 1
+ nc -z localhost 12345
Connection to localhost port 12345 [tcp/italk] succeeded!
```

### Verify behavior

Connect each client first, then send `Hello, one` from first client, then send `Hello, two` from second client.

```bash
nc localhost 12345
```

output:

```text
Hello, one
Hello, one
Hello, two
```

```bash
nc localhost 12345
```

output:

```text
Hello, one
Hello, two
Hello, two
```

### Teardown

The `teardown.sh` script stops port forwarding, uninstalls Zilla and Kafka and deletes the namespace.

```bash
./teardown.sh
```

output:

```text
+ pgrep kubectl
99999
+ killall kubectl
+ helm uninstall zilla-tcp-reflect --namespace zilla-tcp-reflect
release "zilla-tcp-reflect" uninstalled
+ kubectl delete namespace zilla-tcp-reflect
namespace "zilla-tcp-reflect" deleted
```
