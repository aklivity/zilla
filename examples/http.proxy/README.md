# http.proxy
Listens on https port `9090` and will response back whatever is hosted in `nginx` on that path.

### Requirements

- bash, jq, nc
- Kubernetes (e.g. Docker Desktop with Kubernetes enabled)
- kubectl
- helm 3.0+
- nghttp2 (https://nghttp2.org/)

### Install nghttp2 client

nghttp2 is an implementation of HTTP/2 client.

```bash
$ brew install nghttp2
```

### Setup

The `setup.sh` script:
- installs Zilla and Nginx to the Kubernetes cluster with helm and waits for the pods to start up
- copies the web contents to the Nginx pod
- starts port forwarding

```bash
$ ./setup.sh
+ helm install zilla-http-proxy chart --namespace zilla-http-proxy --create-namespace --wait
NAME: zilla-http-proxy
LAST DEPLOYED: [...]
NAMESPACE: zilla-http-proxy
STATUS: deployed
REVISION: 1
TEST SUITE: None
++ kubectl get pods --namespace zilla-http-proxy --selector app.kubernetes.io/instance=nginx -o json
++ jq -r '.items[0].metadata.name'
+ NGINX_POD=nginx-1234567890-abcde
+ kubectl cp --namespace zilla-http-proxy server/demo.html nginx-1234567890-abcde:/usr/share/nginx/html
+ kubectl cp --namespace zilla-http-proxy server/style.css nginx-1234567890-abcde:/usr/share/nginx/html
+ nc -z localhost 9090
+ kubectl port-forward --namespace zilla-http-proxy service/zilla 9090
+ sleep 1
+ nc -z localhost 9090
Connection to localhost port 9090 [tcp/websm] succeeded!
```

### Verify behavior

```bash
$ nghttp -ansy https://localhost:9090/demo.html
***** Statistics *****

Request timing:
  responseEnd: the  time  when  last  byte of  response  was  received
               relative to connectEnd
 requestStart: the time  just before  first byte  of request  was sent
               relative  to connectEnd.   If  '*' is  shown, this  was
               pushed by server.
      process: responseEnd - requestStart
         code: HTTP status code
         size: number  of  bytes  received as  response  body  without
               inflation.
          URI: request URI

see http://www.w3.org/TR/resource-timing/#processing-model

sorted by 'complete'

id  responseEnd requestStart  process code size request path
 13   +921.19ms       +146us 921.05ms  200  320 /demo.html
  2   +923.02ms *  +912.81ms  10.21ms  200   89 /style.css
```

you get `/style.css` response as push promise that nginx is configured with.

### Teardown

The `teardown.sh` script stops port forwarding, uninstalls Zilla and Nginx and deletes the namespace.

```bash
$ ./teardown.sh
+ pgrep kubectl
99999
+ killall kubectl
+ helm uninstall zilla-http-proxy --namespace zilla-http-proxy
release "zilla-http-proxy" uninstalled
+ kubectl delete namespace zilla-http-proxy
namespace "zilla-http-proxy" deleted
```
