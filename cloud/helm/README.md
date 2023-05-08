# Lifecycle

## Install

```
$ helm install zilla . --namespace zilla --create-namespace --wait #--values your.custom.values.yaml
NAME: zilla
NAMESPACE: zilla
STATUS: deployed
[...]
```

## Upgrade
```
$ helm upgrade zilla . --namespace zilla --wait #--values your.custom.values.yaml
Release "zilla" has been upgraded. Happy Helming!
[...]
```

## Uninstall
```
$ helm uninstall zilla --namespace zilla
release "zilla" uninstalled
```

# Examples

## default

```
$ helm install zilla . --namespace zilla --create-namespace --wait
NAME: zilla
LAST DEPLOYED: [...]
NAMESPACE: zilla
STATUS: deployed
REVISION: 1
NOTES:
Verify behaviour:
-----------------
Default install is no-op zilla:
$ ZILLA_POD=$(kubectl get pods --namespace zilla --selector app.kubernetes.io/instance=zilla -o json | jq -r '.items[0].metadata.name')
$ kubectl logs --namespace zilla $ZILLA_POD
name: example
started
```

## tcp.echo
```
$ helm install zilla . --namespace zilla --create-namespace --wait \
    --values examples/tcp.echo/values.yaml --set-file zilla_yaml=examples/tcp.echo/zilla.yaml
NAME: zilla
LAST DEPLOYED: [...]
NAMESPACE: zilla
STATUS: deployed
REVISION: 1
NOTES:
Expose zilla with port-forward by running these commands:
---------------------------------------------------------
$ export SERVICE_PORTS=$(kubectl get svc --namespace zilla zilla --template "{{ range .spec.ports }}{{.port}} {{ end }}")
$ eval "kubectl port-forward --namespace zilla service/zilla $SERVICE_PORTS" > /tmp/kubectl-zilla.log 2>&1 &

Verify behaviour:
-----------------
$ nc localhost 12345
Hello, world
Hello, world

Cleanup port-forwards:
----------------------
$ pgrep kubectl && killall kubectl
```

## tcp.reflect
```
$ helm install zilla . --namespace zilla --create-namespace --wait \
    --values examples/tcp.reflect/values.yaml --set-file zilla_yaml=examples/tcp.reflect/zilla.yaml
NAME: zilla
LAST DEPLOYED: [...]
NAMESPACE: zilla
STATUS: deployed
REVISION: 1
NOTES:
Expose zilla with port-forward by running these commands:
---------------------------------------------------------
$ export SERVICE_PORTS=$(kubectl get svc --namespace zilla zilla --template "{{ range .spec.ports }}{{.port}} {{ end }}")
$ eval "kubectl port-forward --namespace zilla service/zilla $SERVICE_PORTS" > /tmp/kubectl-zilla.log 2>&1 &

Verify behaviour:
-----------------
$ nc localhost 12345
Hello, one
Hello, one
Hello, two

$ nc localhost 12345
Hello, one
Hello, two
Hello, two

Cleanup port-forwards:
----------------------
$ pgrep kubectl && killall kubectl
```

## http.echo 
```
$ helm install zilla . --namespace zilla --create-namespace --wait \
    --values examples/http.echo/values.yaml --set-file zilla_yaml=examples/http.echo/zilla.yaml
NAME: zilla
LAST DEPLOYED: [...]
NAMESPACE: zilla
STATUS: deployed
REVISION: 1
NOTES:
Expose zilla with port-forward by running these commands:
---------------------------------------------------------
$ export SERVICE_PORTS=$(kubectl get svc --namespace zilla zilla --template "{{ range .spec.ports }}{{.port}} {{ end }}")
$ eval "kubectl port-forward --namespace zilla service/zilla $SERVICE_PORTS" > /tmp/kubectl-zilla.log 2>&1 &

Verify behaviour:
-----------------
$ curl -d "Hello, world" -H "Content-Type: text/plain" -X "POST" http://localhost:8080/
Hello, world

$ curl -d "Hello, world" -H "Content-Type: text/plain" -X "POST" http://localhost:8080/ --http2-prior-knowledge
Hello, world

$ curl --cacert examples/http.echo/test-ca.crt -d "Hello, world" -H "Content-Type: text/plain" -X "POST" https://localhost:9090/ --http1.1
Hello, world

$ curl --cacert examples/http.echo/test-ca.crt -d "Hello, world" -H "Content-Type: text/plain" -X "POST" https://localhost:9090/ --http2
Hello, world

Cleanup port-forwards:
----------------------
$ pgrep kubectl && killall kubectl
```

// TODO: Ati - add more examples
