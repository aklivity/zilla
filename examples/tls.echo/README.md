# tls.echo

Listens on tls port `23456` and will echo back whatever is sent to the server.

### Requirements

- bash, jq, nc
- Kubernetes (e.g. Docker Desktop with Kubernetes enabled)
- kubectl
- helm 3.0+
- openssl

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
+ helm upgrade --install zilla-tls-echo oci://ghcr.io/aklivity/charts/zilla --namespace zilla-tls-echo --create-namespace --wait [...]
NAME: zilla-tls-echo
LAST DEPLOYED: [...]
NAMESPACE: zilla-tls-echo
STATUS: deployed
REVISION: 1
NOTES:
Zilla has been installed.
[...]
+ nc -z localhost 23456
+ kubectl port-forward --namespace zilla-tls-echo service/zilla  23456
+ sleep 1
+ nc -z localhost 23456
Connection to localhost port 23456 [tcp/*] succeeded!
```

### Verify behavior

```bash
openssl s_client -connect localhost:23456 -CAfile test-ca.crt -quiet -alpn echo
```

output:

```text
depth=1 C = US, ST = California, L = Palo Alto, O = Aklivity, OU = Development, CN = Test CA
verify return:1
depth=0 C = US, ST = California, L = Palo Alto, O = Aklivity, OU = Development, CN = localhost
verify return:1
```

Type a `Hello, world` message and press `enter`.

output:

```text
Hello, world
Hello, world
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
+ helm uninstall zilla-tls-echo --namespace zilla-tls-echo
release "zilla-tls-echo" uninstalled
+ kubectl delete namespace zilla-tls-echo
namespace "zilla-tls-echo" deleted
```
