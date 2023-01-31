# tcp.echo

Listens on tcp port `12345` and will echo back whatever is sent to the server.

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
+ helm install zilla-tcp-echo chart --namespace zilla-tcp-echo --create-namespace --wait
NAME: zilla-tcp-echo
LAST DEPLOYED: [...]
NAMESPACE: zilla-tcp-echo
STATUS: deployed
REVISION: 1
TEST SUITE: None
+ nc -z localhost 12345
+ kubectl port-forward --namespace zilla-tcp-echo service/zilla 12345
+ sleep 1
+ nc -z localhost 12345
Connection to localhost port 12345 [tcp/italk] succeeded!
```

### Verify behavior

```bash
$ nc localhost 12345
Hello, world
Hello, world
```

### Teardown

The `teardown.sh` script stops port forwarding, uninstalls Zilla and deletes the namespace.

```bash
$ ./teardown.sh
+ pgrep kubectl
99999
+ killall kubectl
+ helm uninstall zilla-tcp-echo --namespace zilla-tcp-echo
release "zilla-tcp-echo" uninstalled
+ kubectl delete namespace zilla-tcp-echo
namespace "zilla-tcp-echo" deleted
```
