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
```

## tcp.echo
```
$ helm install zilla-tcp-echo . --namespace zilla-tcp-echo --create-namespace --wait \
    --values examples/tcp.echo/values.yaml --set-file zilla_yaml=examples/tcp.echo/zilla.yaml
```

## tcp.reflect
```
$ helm install zilla-tcp-reflect . --namespace zilla-tcp-reflect --create-namespace --wait \
    --values examples/tcp.reflect/values.yaml --set-file zilla_yaml=examples/tcp.reflect/zilla.yaml
```

## http.echo 
```
$ helm install zilla-http-echo . --namespace zilla-http-echo --create-namespace --wait \
    --values examples/http.echo/values.yaml --set-file zilla_yaml=examples/http.echo/zilla.yaml
```

## tls.echo
```
$ helm install zilla-tls-echo . --namespace zilla-tls-echo --create-namespace --wait \
    --values examples/tls.echo/values.yaml --set-file zilla_yaml=examples/tls.echo/zilla.yaml
```

## ws.echo
```
$ helm install zilla-ws-echo . --namespace zilla-ws-echo --create-namespace --wait \
    --values examples/ws.echo/values.yaml --set-file zilla_yaml=examples/ws.echo/zilla.yaml
```

## mqtt.reflect
```
$ helm install zilla-mqtt-reflect . --namespace zilla-mqtt-reflect --create-namespace --wait \
    --values examples/mqtt.reflect/values.yaml --set-file zilla_yaml=examples/mqtt.reflect/zilla.yaml
```

## grpc.echo
```
$ helm install zilla-grpc-echo . --namespace zilla-grpc-echo --create-namespace --wait \
    --values examples/grpc.echo/values.yaml --set-file zilla_yaml=examples/grpc.echo/zilla.yaml
```
