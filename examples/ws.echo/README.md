# ws.echo

Listens on ws port `8080` and will echo back whatever is sent to the server.
Listens on wss port `9090` and will echo back whatever is sent to the server.

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
$ ./setup.sh
+ helm install zilla-ws-echo chart --namespace zilla-ws-echo --create-namespace --wait
NAME: zilla-ws-echo
LAST DEPLOYED: [...]
NAMESPACE: zilla-ws-echo
STATUS: deployed
REVISION: 1
TEST SUITE: None
+ nc -z localhost 8080
+ kubectl port-forward --namespace zilla-ws-echo service/zilla 8080 9090
+ sleep 1
+ nc -z localhost 8080
Connection to localhost port 8080 [tcp/http-alt] succeeded!
```

### Install wscat
```bash
$ npm install wscat -g
```

### Verify behavior
```bash
$ wscat -c ws://localhost:8080/ -s echo
Connected (press CTRL+C to quit)
> Hello, world
< Hello, world
```
```bash
$ wscat -c wss://localhost:9090/ --ca test-ca.crt -s echo
Connected (press CTRL+C to quit)
> Hello, world
< Hello, world
```

### Teardown

The `teardown.sh` script stops port forwarding, uninstalls Zilla and deletes the namespace.

```bash
$ ./teardown.sh
+ pgrep kubectl
99999
+ killall kubectl
+ helm uninstall zilla-ws-echo --namespace zilla-ws-echo
release "zilla-ws-echo" uninstalled
+ kubectl delete namespace zilla-ws-echo
namespace "zilla-ws-echo" deleted
```
