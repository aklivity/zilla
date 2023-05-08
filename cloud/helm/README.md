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
$ helm install zilla-tcp-echo . --namespace zilla-tcp-echo --create-namespace --wait \
    --values examples/tcp.echo/values.yaml --set-file zilla_yaml=examples/tcp.echo/zilla.yaml
NAME: zilla-tcp-echo
LAST DEPLOYED: [...]
NAMESPACE: zilla-tcp-echo
STATUS: deployed
REVISION: 1
NOTES:
Expose zilla with port-forward by running these commands:
---------------------------------------------------------
$ export SERVICE_PORTS=$(kubectl get svc --namespace zilla-tcp-echo zilla --template "{{ range .spec.ports }}{{.port}} {{ end }}")
$ eval "kubectl port-forward --namespace zilla-tcp-echo service/zilla $SERVICE_PORTS" > /tmp/kubectl-zilla.log 2>&1 &

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
$ helm install zilla-tcp-reflect . --namespace zilla-tcp-reflect --create-namespace --wait \
    --values examples/tcp.reflect/values.yaml --set-file zilla_yaml=examples/tcp.reflect/zilla.yaml
NAME: zilla-tcp-reflect
LAST DEPLOYED: [...]
NAMESPACE: zilla-tcp-reflect
STATUS: deployed
REVISION: 1
NOTES:
Expose zilla with port-forward by running these commands:
---------------------------------------------------------
$ export SERVICE_PORTS=$(kubectl get svc --namespace zilla-tcp-reflect zilla --template "{{ range .spec.ports }}{{.port}} {{ end }}")
$ eval "kubectl port-forward --namespace zilla-tcp-reflect service/zilla $SERVICE_PORTS" > /tmp/kubectl-zilla.log 2>&1 &

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
$ helm install zilla-http-echo . --namespace zilla-http-echo --create-namespace --wait \
    --values examples/http.echo/values.yaml --set-file zilla_yaml=examples/http.echo/zilla.yaml
NAME: zilla-http-echo
LAST DEPLOYED: [...]
NAMESPACE: zilla-http-echo
STATUS: deployed
REVISION: 1
NOTES:
Expose zilla with port-forward by running these commands:
---------------------------------------------------------
$ export SERVICE_PORTS=$(kubectl get svc --namespace zilla-http-echo zilla --template "{{ range .spec.ports }}{{.port}} {{ end }}")
$ eval "kubectl port-forward --namespace zilla-http-echo service/zilla $SERVICE_PORTS" > /tmp/kubectl-zilla.log 2>&1 &

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

## tls.echo
```
$ helm install zilla-tls-echo . --namespace zilla-tls-echo --create-namespace --wait \
    --values examples/tls.echo/values.yaml --set-file zilla_yaml=examples/tls.echo/zilla.yaml
NAME: zilla-tls-echo
LAST DEPLOYED: Mon May  8 13:09:17 2023
NAMESPACE: zilla-tls-echo
STATUS: deployed
REVISION: 1
NOTES:
Expose zilla with port-forward by running these commands:
---------------------------------------------------------
$ export SERVICE_PORTS=$(kubectl get svc --namespace zilla-tls-echo zilla --template "{{ range .spec.ports }}{{.port}} {{ end }}")
$ eval "kubectl port-forward --namespace zilla-tls-echo service/zilla $SERVICE_PORTS" > /tmp/kubectl-zilla.log 2>&1 &

Verify behaviour:
-----------------
$ openssl s_client -connect localhost:23456 -CAfile examples/tls.echo/test-ca.crt -quiet -alpn echo
depth=1 C = US, ST = California, L = Palo Alto, O = Aklivity, OU = Development, CN = Test CA
verify return:1
depth=0 C = US, ST = California, L = Palo Alto, O = Aklivity, OU = Development, CN = localhost
verify return:1
Hello, world
Hello, world

Cleanup port-forwards:
----------------------
$ pgrep kubectl && killall kubectl
```

## ws.echo
```
$ helm install zilla-ws-echo . --namespace zilla-ws-echo --create-namespace --wait \
    --values examples/ws.echo/values.yaml --set-file zilla_yaml=examples/ws.echo/zilla.yaml
NAME: zilla-ws-echo
LAST DEPLOYED: [...]
NAMESPACE: zilla-ws-echo
STATUS: deployed
REVISION: 1
NOTES:
Expose zilla with port-forward by running these commands:
---------------------------------------------------------
$ export SERVICE_PORTS=$(kubectl get svc --namespace zilla-ws-echo zilla --template "{{ range .spec.ports }}{{.port}} {{ end }}")
$ eval "kubectl port-forward --namespace zilla-ws-echo service/zilla $SERVICE_PORTS" > /tmp/kubectl-zilla.log 2>&1 &

Verify behaviour:
-----------------
$ wscat -c ws://localhost:8080/ -s echo
Connected (press CTRL+C to quit)
> Hello, world
< Hello, world

$ wscat -c wss://localhost:9090/ --ca examples/ws.echo/test-ca.crt -s echo
Connected (press CTRL+C to quit)
> Hello, world
< Hello, world

Cleanup port-forwards:
----------------------
$ pgrep kubectl && killall kubectl
```

## mqtt.reflect
```
$ helm install zilla-mqtt-reflect . --namespace zilla-mqtt-reflect --create-namespace --wait \
    --values examples/mqtt.reflect/values.yaml --set-file zilla_yaml=examples/mqtt.reflect/zilla.yaml
NAME: zilla-mqtt-reflect
LAST DEPLOYED: [...]
NAMESPACE: zilla-mqtt-reflect
STATUS: deployed
REVISION: 1
NOTES:
Expose zilla with port-forward by running these commands:
---------------------------------------------------------
$ export SERVICE_PORTS=$(kubectl get svc --namespace zilla-mqtt-reflect zilla --template "{{ range .spec.ports }}{{.port}} {{ end }}")
$ eval "kubectl port-forward --namespace zilla-mqtt-reflect service/zilla $SERVICE_PORTS" > /tmp/kubectl-zilla.log 2>&1 &

Verify behaviour:
-----------------
Connect two subscribing clients first, then send `Hello, world` from publishing client.

$ mosquitto_sub -V '5' -t 'zilla' -d
Client null sending CONNECT
Client 43516069-9fa3-493d-9ab1-17e5e891e5be received CONNACK (0)
Client 43516069-9fa3-493d-9ab1-17e5e891e5be sending SUBSCRIBE (Mid: 1, Topic: zilla, QoS: 0, Options: 0x00)
Client 43516069-9fa3-493d-9ab1-17e5e891e5be received SUBACK
Subscribed (mid: 1): 0
Client 43516069-9fa3-493d-9ab1-17e5e891e5be received PUBLISH (d0, q0, r0, m0, 'zilla', ... (12 bytes))
Hello, world

$ mosquitto_sub -V '5' -t 'zilla' --cafile examples/mqtt.reflect/test-ca.crt -d
Client null sending CONNECT
Client 42c70f3c-fe67-41f9-8de3-9fae26ba6318  received CONNACK (0)
Client 42c70f3c-fe67-41f9-8de3-9fae26ba6318  sending SUBSCRIBE (Mid: 1, Topic: zilla, QoS: 0, Options: 0x00)
Client 42c70f3c-fe67-41f9-8de3-9fae26ba6318  received SUBACK
Subscribed (mid: 1): 0
Client 42c70f3c-fe67-41f9-8de3-9fae26ba6318 received PUBLISH (d0, q0, r0, m0, 'zilla', ... (12 bytes))
Hello, world

$ mosquitto_pub -V '5' -t 'zilla' -m 'Hello, world' -d
Client null sending CONNECT
Client 5beb7f61-1b92-460c-8a2d-30a38156c601 received CONNACK (0)
Client 5beb7f61-1b92-460c-8a2d-30a38156c601 sending PUBLISH (d0, q0, r0, m1, 'zilla', ... (12 bytes))
Client 5beb7f61-1b92-460c-8a2d-30a38156c601 sending DISCONNECT

Cleanup port-forwards:
----------------------
$ pgrep kubectl && killall kubectl
```

## grpc.echo
```
$ helm install zilla-grpc-echo . --namespace zilla-grpc-echo --create-namespace --wait \
    --values examples/grpc.echo/values.yaml --set-file zilla_yaml=examples/grpc.echo/zilla.yaml
NAME: zilla-grpc-echo
LAST DEPLOYED: [...]
NAMESPACE: zilla-grpc-echo
STATUS: deployed
REVISION: 1
NOTES:
Expose zilla with port-forward by running these commands:
---------------------------------------------------------
$ export SERVICE_PORTS=$(kubectl get svc --namespace zilla-grpc-echo zilla-grpc-echo --template "{{ range .spec.ports }}{{.port}} {{ end }}")
$ eval "kubectl port-forward --namespace zilla-grpc-echo service/zilla-grpc-echo $SERVICE_PORTS" > /tmp/kubectl-zilla.log 2>&1 &

Verify behaviour:
-----------------
$ grpcurl -insecure -proto examples/grpc.echo/proto/echo.proto -d '{"message":"Hello World"}' localhost:9090 example.EchoService.EchoUnary
{
  "message": "Hello World"
}

$ grpcurl -insecure -proto examples/grpc.echo/proto/echo.proto -d @ localhost:9090 example.EchoService.EchoBidiStream
# type this:
{ "message": "Hello!" }
# response:
{
  "message": "Hello!"
}

Cleanup port-forwards:
----------------------
$ pgrep kubectl && killall kubectl
```
