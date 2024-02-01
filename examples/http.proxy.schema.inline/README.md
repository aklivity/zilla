# http.proxy.schema.inline

Listens on https port `7143` and will response back whatever is hosted in `nginx` on that path after enforcing validation.

### Requirements

- bash, jq, nc
- Kubernetes (e.g. Docker Desktop with Kubernetes enabled)
- kubectl
- helm 3.0+

### Setup

The `setup.sh` script:

- installs Zilla and Nginx to the Kubernetes cluster with helm and waits for the pods to start up
- copies the web contents to the Nginx pod
- starts port forwarding

```bash
./setup.sh
```

output:

```text
+ ZILLA_CHART=oci://ghcr.io/aklivity/charts/zilla
+ NAMESPACE=zilla-http-proxy-schema-inline
+ helm upgrade --install zilla oci://ghcr.io/aklivity/charts/zilla --namespace zilla-http-proxy-schema-inline --create-namespace --wait [...]
NAME: zilla-http-proxy
LAST DEPLOYED: [...]
NAMESPACE: zilla-http-proxy-schema-inline
STATUS: deployed
REVISION: 1
NOTES:
Zilla has been installed.
[...]
+ helm upgrade --install zilla-http-proxy-nginx chart --namespace zilla-http-proxy --create-namespace --wait
NAME: zilla-http-proxy-nginx
LAST DEPLOYED: [...]
NAMESPACE: zilla-http-proxy
STATUS: deployed
REVISION: 1
TEST SUITE: None
++ kubectl get pods --namespace zilla-http-proxy-schema-inline --selector app.kubernetes.io/instance=nginx -o json
++ jq -r '.items[0].metadata.name'
+ NGINX_POD=nginx-5cffb84898-swzlb
[...]
+ kubectl cp --namespace zilla-http-proxy-schema-inline www/valid.json nginx-5cffb84898-swzlb:/usr/share/nginx/html
+ kubectl cp --namespace zilla-http-proxy-schema-inline www/invalid.json nginx-5cffb84898-swzlb:/usr/share/nginx/html
+ nc -z localhost 7143
+ kubectl port-forward --namespace zilla-http-proxy-schema-inline service/zilla 7143
+ sleep 1
+ nc -z localhost 7143
Connection to localhost port 7143 [tcp/*] succeeded!
```

### Verify behavior for valid content

```bash
curl --cacert test-ca.crt https://localhost:7143/valid.json
```

output:

```text
{
    "id": 42,
    "status": "Active"
}
```

### Verify behavior for invalid content

```bash
curl --cacert test-ca.crt https://localhost:7143/invalid.json
```

output:

```text
curl: (18) HTTP/2 stream 1 was reset
```

### Teardown

The `teardown.sh` script stops port forwarding, uninstalls Zilla and Nginx and deletes the namespace.

```bash
./teardown.sh
```

output:

```text
+ pgrep kubectl
58110
+ killall kubectl
+ NAMESPACE=zilla-http-proxy-schema-inline
+ helm uninstall zilla nginx --namespace zilla-http-proxy-schema-inline
release "zilla" uninstalled
release "nginx" uninstalled
+ kubectl delete namespace zilla-http-proxy-schema-inline
namespace "zilla-http-proxy-schema-inline" deleted
```
