# Lifecycle

## Install

```
$ helm install zilla . --namespace zilla --create-namespace #--values your.custom.values.yaml
NAME: zilla
NAMESPACE: zilla
STATUS: deployed
[...]
```

## Upgrade
```
$ helm upgrade zilla . --namespace zilla
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

Default install is "no-op" zilla:
```
$ helm install zilla . --namespace zilla --create-namespace

$ ZILLA_POD=$(kubectl get pods --namespace zilla --selector app.kubernetes.io/instance=zilla -o json | jq -r '.items[0].metadata.name')
$ kubectl logs --namespace zilla $ZILLA_POD
name: example
started
```

## tcp.echo
```
$ helm install zilla . --namespace zilla --create-namespace \
    --values examples/tcp.echo/values.yaml --set-file zilla_yaml=examples/tcp.echo/zilla.yaml
```

## tcp.reflect
```
$ helm install zilla . --namespace zilla --create-namespace \
    --values examples/tcp.reflect/values.yaml --set-file zilla_yaml=examples/tcp.reflect/zilla.yaml
```

## http.echo 
```
$ helm install zilla . --namespace zilla --create-namespace \
    --values examples/http.echo/values.yaml --set-file zilla_yaml=examples/http.echo/zilla.yaml
```

// TODO: Ati - add more examples
