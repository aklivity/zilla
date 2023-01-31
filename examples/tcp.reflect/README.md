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
$ ./setup.sh
+ helm install zilla-tcp-reflect chart --namespace zilla-tcp-reflect --create-namespace --wait
NAME: zilla-tcp-reflect
LAST DEPLOYED: [...]
NAMESPACE: zilla-tcp-reflect
STATUS: deployed
REVISION: 1
TEST SUITE: None
+ nc -z localhost 12345
+ kubectl port-forward --namespace zilla-tcp-reflect service/zilla 12345
+ sleep 1
+ nc -z localhost 12345
Connection to localhost port 12345 [tcp/italk] succeeded!
```

### Verify behavior

Connect each client first, then send `Hello, one` from first client, then send `Hello, two` from second client.

```bash
$ nc localhost 12345
Hello, one
Hello, one
Hello, two
```

```bash
$ nc localhost 12345
Hello, one
Hello, two
Hello, two
```

### Teardown

The `teardown.sh` script stops port forwarding, uninstalls Zilla and Kafka and deletes the namespace.

```bash
$ ./teardown.sh
+ pgrep kubectl
99999
+ killall kubectl
+ helm uninstall zilla-tcp-reflect --namespace zilla-tcp-reflect
release "zilla-tcp-reflect" uninstalled
+ kubectl delete namespace zilla-tcp-reflect
namespace "zilla-tcp-reflect" deleted
```
