# http.filesystem.config.server

## Zilla config server

Listens on http port `8081` and serves files from the pod's `/var/www` subdirectory.
Listens on https port `9091` and serves files from the pod's `/var/www` subdirectory.

## Zilla HTTP echo server

Listens on http port `8080` and will echo back whatever is sent to the server on path `\echo`.
Listens on http port `9090` and will echo back whatever is sent to the server on path `\echo`.

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
$ ./setup.sh
+ ZILLA_CHART=oci://ghcr.io/aklivity/charts/zilla
+ VERSION=0.9.46
+ helm install zilla-config-server-config oci://ghcr.io/aklivity/charts/zilla --version 0.9.46 --namespace zilla-config-server --create-namespace --wait --values zilla-config/values.yaml --set-file 'zilla\.yaml=zilla-config/zilla.yaml' --set-file 'secrets.tls.data.localhost\.p12=tls/localhost.p12'
NAME: zilla-config-server-config
LAST DEPLOYED: Sat May 13 14:30:38 2023
NAMESPACE: zilla-config-server
STATUS: deployed
REVISION: 1
NOTES:
Zilla has been installed.
[...]
++ kubectl get pods --namespace zilla-config-server --selector app.kubernetes.io/instance=zilla-config-server-config -o json
++ jq -r '.items[0].metadata.name'
+ ZILLA_CONFIG_POD=zilla-config-server-config-bc455d4d6-fshdl
+ kubectl cp --namespace zilla-config-server www zilla-config-server-config-bc455d4d6-fshdl:/var/
+ helm install zilla-config-server-http oci://ghcr.io/aklivity/charts/zilla --version 0.9.46 --namespace zilla-config-server --create-namespace --wait --values zilla-http/values.yaml --set-file 'configMaps.prop.data.zilla\.properties=zilla-http/zilla.properties'
NAME: zilla-config-server-http
LAST DEPLOYED: Sat May 13 14:30:50 2023
NAMESPACE: zilla-config-server
STATUS: deployed
REVISION: 1
NOTES:
Zilla has been installed.
[...]
+ kubectl run busybox-pod --image=busybox:1.28 --namespace zilla-config-server --rm --restart=Never -i -t -- /bin/sh -c 'until nc -w 2 zilla-config-server-http 8080; do echo . && sleep 5; done'
+ kubectl wait --namespace zilla-config-server --for=delete pod/busybox-pod
+ kubectl port-forward --namespace zilla-config-server service/zilla-config-server-config 8081 9091
+ nc -z localhost 8081
+ kubectl port-forward --namespace zilla-config-server service/zilla-config-server-http 8080 9090
+ sleep 1
+ nc -z localhost 8081
Connection to localhost port 8081 [tcp/sunproxyadmin] succeeded!
+ nc -z localhost 8080
Connection to localhost port 8080 [tcp/http-alt] succeeded!
```

### Verify behavior of the Zilla config server

```bash
$ curl http://localhost:8081/zilla.yaml
---
name: example
vaults:
  server:
    type: filesystem
    options:
      keys:
        store: localhost.p12
        type: pkcs12
        password: ${{env.KEYSTORE_PASSWORD}}
bindings:
  tcp_server0:
    type: tcp
    kind: server
    options:
      host: 0.0.0.0
      port: 8080
    exit: http_server0
  tcp_server1:
    type: tcp
    kind: server
    options:
      host: 0.0.0.0
      port: 9090
    exit: tls_server0
  tls_server0:
    type: tls
    kind: server
    vault: server
    options:
      keys:
        - localhost
      sni:
        - localhost
      alpn:
        - http/1.1
        - h2
    exit: http_server0
  http_server0:
    type: http
    kind: server
    routes:
      - when:
          - headers:
              :scheme: http
              :authority: localhost:8080
              :path: /echo
          - headers:
              :scheme: https
              :authority: localhost:9090
              :path: /echo
        exit: echo_server0
  echo_server0:
    type: echo
    kind: server
```

The same URL will be used by the Zilla HTTP echo server to query its configuration.

### Verify behavior of the Zilla HTTP echo server

```bash
$ curl -d "Hello, world" -H "Content-Type: text/plain" -X "POST" http://localhost:8080/echo
Hello, world
```

### Change the configuration of the Zilla HTTP echo server
The Zilla HTTP echo server currently echoes only for the /echo HTTP path. Let's change it to /echo_changed path.

```bash
$ ./change_config.sh
++ kubectl get pods --namespace zilla-config-server --selector app.kubernetes.io/instance=zilla-config-server-config -o json
++ jq -r '.items[0].metadata.name'
+ ZILLA_CONFIG_POD=zilla-config-server-config-bc455d4d6-fshdl
+ kubectl cp --namespace zilla-config-server www-updated/zilla.yaml zilla-config-server-config-bc455d4d6-fshdl:/var/www/zilla.yaml
+ curl -s -f -d 'Hello, World' -H 'Content-Type: text/plain' -X POST -v http://localhost:8080/echo_changed
+ sleep 1
+ curl -s -f -d 'Hello, World' -H 'Content-Type: text/plain' -X POST -v http://localhost:8080/echo_changed
```

### Verify behavior of the reconfigured Zilla HTTP echo server

### Verify the `/echo` path is no longer working

```bash
$ curl -i -d "Hello, world" -H "Content-Type: text/plain" -X "POST" http://localhost:8080/echo
HTTP/1.1 404 Not Found
```

### Verify the `/echo_changed` path is working

```bash
$ curl -d "Hello, world" -H "Content-Type: text/plain" -X "POST" http://localhost:8080/echo_changed
Hello, world
```

### Teardown

The `teardown.sh` script stops port forwarding, uninstalls both Zilla and deletes the namespace.

```bash
$ ./teardown.sh
+ pgrep kubectl
99998
99999
+ killall kubectl
+ helm uninstall zilla-config-server-config zilla-config-server-http --namespace zilla-config-server
release "zilla-config-server-config" uninstalled
release "zilla-config-server-http" uninstalled
+ kubectl delete namespace zilla-config-server
namespace "zilla-config-server" deleted
```
