# tls.reflect

Listens on tls port `23456` and will echo back whatever is sent to the server, broadcasting to all clients.

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
+ VERSION=0.9.46
+ helm install zilla-tls-reflect oci://ghcr.io/aklivity/charts/zilla --version 0.9.46 --namespace zilla-tls-reflect --create-namespace --wait [...]
NAME: zilla-tls-reflect
LAST DEPLOYED: [...]
NAMESPACE: zilla-tls-reflect
STATUS: deployed
REVISION: 1
NOTES:
Zilla has been installed.
[...]
+ nc -z localhost 23456
+ kubectl port-forward --namespace zilla-tls-reflect service/zilla-tls-reflect 23456
+ sleep 1
+ nc -z localhost 23456
Connection to localhost port 23456 [tcp/*] succeeded!
```

### Verify behavior

Connect each client first, then send `Hello, one` from first client, then send `Hello, two` from second client.

```bash
openssl s_client -connect localhost:23456 -CAfile test-ca.crt -quiet -alpn echo
```

output:

```text
depth=1 C = US, ST = California, L = Palo Alto, O = Aklivity, OU = Development, CN = Test CA
verify return:1
depth=0 C = US, ST = California, L = Palo Alto, O = Aklivity, OU = Development, CN = localhost
verify return:1
Hello, one
Hello, one
Hello, two
```

```bash
openssl s_client -connect localhost:23456 -CAfile test-ca.crt -quiet -alpn echo
```

output:

```text
depth=1 C = US, ST = California, L = Palo Alto, O = Aklivity, OU = Development, CN = Test CA
verify return:1
depth=0 C = US, ST = California, L = Palo Alto, O = Aklivity, OU = Development, CN = localhost
verify return:1
Hello, one
Hello, two
Hello, two
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
+ helm uninstall zilla-reflect --namespace zilla-reflect
release "zilla-reflect" uninstalled
+ kubectl delete namespace zilla-reflect
namespace "zilla-reflect" deleted
```
