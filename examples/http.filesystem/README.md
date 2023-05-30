# http.filesystem

Listens on http port `8080` and serves files from the pod's `/var/www` subdirectory.
Listens on https port `9090` and serves files from the pod's `/var/www` subdirectory.

### Requirements

- bash, jq, nc
- Kubernetes (e.g. Docker Desktop with Kubernetes enabled)
- kubectl
- helm 3.0+
- curl

### Setup

The `setup.sh` script:

- installs Zilla to the Kubernetes cluster with helm and waits for the pod to start up
- copies the contents of the www directory to the Zilla pod
- starts port forwarding

```bash
./setup.sh
```

output:

```text
+ ZILLA_CHART=oci://ghcr.io/aklivity/charts/zilla
+ VERSION=0.9.46
+ helm install zilla-http-filesystem oci://ghcr.io/aklivity/charts/zilla --version 0.9.46 --namespace zilla-http-filesystem --create-namespace --wait [...]
NAME: zilla-http-filesystem
LAST DEPLOYED: [...]
NAMESPACE: zilla-http-filesystem
STATUS: deployed
REVISION: 1
NOTES:
Zilla has been installed.
[...]
++ kubectl get pods --namespace zilla-http-filesystem --selector app.kubernetes.io/instance=zilla -o json
++ jq -r '.items[0].metadata.name'
+ ZILLA_POD=zilla-1234567890-abcde
+ kubectl cp --namespace zilla-http-filesystem www zilla-1234567890-abcde:/var/
+ nc -z localhost 8080
+ kubectl port-forward --namespace zilla-http-filesystem service/zilla-http-filesystem 8080 9090
+ sleep 1
+ nc -z localhost 8080
Connection to localhost port 8080 [tcp/http-alt] succeeded!
```

### Verify behavior

```bash
curl http://localhost:8080/index.html
```

output:

```html
<html>
<head>
<title>Welcome to Zilla!</title>
</head>
<body>
<h1>Welcome to Zilla!</h1>
</body>
</html>
```

```bash
curl --cacert test-ca.crt https://localhost:9090/index.html
```

output:

```html
<html>
<head>
<title>Welcome to Zilla!</title>
</head>
<body>
<h1>Welcome to Zilla!</h1>
</body>
</html>
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
+ helm uninstall zilla-http-filesystem --namespace zilla-http-filesystem
release "zilla-http-filesystem" uninstalled
+ kubectl delete namespace zilla-http-filesystem
namespace "zilla-http-filesystem" deleted
```
