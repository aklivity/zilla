# http.filesystem.config.server

## Zilla config server

Listens on http port `7115` and serves files from the pod's `/var/www` subdirectory.
Listens on https port `7144` and serves files from the pod's `/var/www` subdirectory.

## Zilla HTTP echo server

Listens on http port `7114` and will echo back whatever is sent to the server on path `\echo`.
Listens on http port `7143` and will echo back whatever is sent to the server on path `\echo`.

### Requirements

- bash, jq, nc
- Kubernetes (e.g. Docker Desktop with Kubernetes enabled)
- kubectl
- helm 3.0+
- curl

### Setup

The `setup.sh` script:

- installs Zilla config server to the Kubernetes cluster with helm and waits for the pod to start up
- places the contents of the www directory to the Zilla pod
- starts port forwarding
- starts a Zilla instance with http echo configuration served by the Zilla config server and waits for the pod to start up

```bash
./setup.sh
```

output:

```text
+ ZILLA_CHART=oci://ghcr.io/aklivity/charts/zilla
+ helm upgrade --install zilla-config oci://ghcr.io/aklivity/charts/zilla --namespace zilla-config-server --create-namespace --wait --values zilla-config/values.yaml --set-file 'zilla\.yaml=zilla-config/zilla.yaml' --set-file 'secrets.tls.data.localhost\.p12=tls/localhost.p12'
NAME: zilla-config
LAST DEPLOYED: Sat May 13 14:30:38 2023
NAMESPACE: zilla-config-server
STATUS: deployed
REVISION: 1
NOTES:
Zilla has been installed.
[...]
++ kubectl get pods --namespace zilla-config-server --selector app.kubernetes.io/instance=zilla-config -o json
++ jq -r '.items[0].metadata.name'
+ ZILLA_CONFIG_POD=zilla-config-bc455d4d6-fshdl
+ kubectl cp --namespace zilla-config-server www zilla-config-bc455d4d6-fshdl:/var/
+ helm upgrade --install zilla-http oci://ghcr.io/aklivity/charts/zilla --namespace zilla-config-server --create-namespace --wait --values zilla-http/values.yaml --set-file 'configMaps.prop.data.zilla\.properties=zilla-http/zilla.properties'
NAME: zilla-http
LAST DEPLOYED: Sat May 13 14:30:50 2023
NAMESPACE: zilla-config-server
STATUS: deployed
REVISION: 1
NOTES:
Zilla has been installed.
[...]
+ kubectl port-forward --namespace zilla-config-server service/zilla-config 7115 7144
+ nc -z localhost 7115
+ kubectl port-forward --namespace zilla-config-server service/zilla-http 7114 7143
+ sleep 1
+ nc -z localhost 7115
Connection to localhost port 7115 [tcp/sunproxyadmin] succeeded!
+ nc -z localhost 7114
Connection to localhost port 7114 [tcp/http-alt] succeeded!
```

### Verify behavior of the Zilla config server

```bash
curl http://localhost:7115/zilla.yaml
```

output:

```text
---
name: example
vaults:
  my_servers:
    type: filesystem
    options:
      keys:
        store: localhost.p12
        type: pkcs12
        password: ${{env.KEYSTORE_PASSWORD}}
bindings:
  north_tcp_server:
    type: tcp
    kind: server
    options:
      host: 0.0.0.0
      port: 7114
    exit: north_http_server
  tcp_server1:
    type: tcp
    kind: server
    options:
      host: 0.0.0.0
      port: 7143
    exit: north_tls_server
  north_tls_server:
    type: tls
    kind: server
    vault: my_servers
    options:
      keys:
        - localhost
      sni:
        - localhost
      alpn:
        - http/1.1
        - h2
    exit: north_http_server
  north_http_server:
    type: http
    kind: server
    routes:
      - when:
          - headers:
              :scheme: http
              :authority: localhost:7114
              :path: /echo
          - headers:
              :scheme: https
              :authority: localhost:7143
              :path: /echo
        exit: north_echo_server
  north_echo_server:
    type: echo
    kind: server
```

The same URL will be used by the Zilla HTTP echo server to query its configuration.

### Verify behavior of the Zilla HTTP echo server

```bash
curl -d "Hello, world" -H "Content-Type: text/plain" -X "POST" http://localhost:7114/echo
```

output:

```text
Hello, world
```

### Change the configuration of the Zilla HTTP echo server

The Zilla HTTP echo server currently echoes only for the /echo HTTP path. Let's change it to /echo_changed path.

```bash
./change_config.sh
```

output:

```text
++ kubectl get pods --namespace zilla-config-server --selector app.kubernetes.io/instance=zilla-config -o json
++ jq -r '.items[0].metadata.name'
+ ZILLA_CONFIG_POD=zilla-config-bc455d4d6-fshdl
+ kubectl cp --namespace zilla-config-server www-updated/zilla.yaml zilla-config-bc455d4d6-fshdl:/var/www/zilla.yaml
+ curl -s -f -d 'Hello, World' -H 'Content-Type: text/plain' -X POST -v http://localhost:7114/echo_changed
+ sleep 1
+ curl -s -f -d 'Hello, World' -H 'Content-Type: text/plain' -X POST -v http://localhost:7114/echo_changed
```

### Verify behavior of the reconfigured Zilla HTTP echo server

### Verify the `/echo` path is no longer working

```bash
curl -i -d "Hello, world" -H "Content-Type: text/plain" -X "POST" http://localhost:7114/echo
```

output:

```text
HTTP/1.1 404 Not Found
```

### Verify the `/echo_changed` path is working

```bash
curl -d "Hello, world" -H "Content-Type: text/plain" -X "POST" http://localhost:7114/echo_changed
```

output:

```text
Hello, world
```

### Teardown

The `teardown.sh` script stops port forwarding, uninstalls both Zilla and deletes the namespace.

```bash
./teardown.sh
```

output:

```text
+ pgrep kubectl
99998
99999
+ killall kubectl
+ helm uninstall zilla-config zilla-http --namespace zilla-config-server
release "zilla-config" uninstalled
release "zilla-http" uninstalled
+ kubectl delete namespace zilla-config-server
namespace "zilla-config-server" deleted
```
