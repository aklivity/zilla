# amqp.reflect (incubator)

Listens on amqp port `5672` and will echo back whatever is sent to the server, broadcasting to all receiving clients.
Listens on amqps port `5671` and will echo back whatever is sent to the server, broadcasting to all receiving clients.

### Requirements

- bash, jq, nc
- Zilla docker image local incubator build, `develop-SNAPSHOT` version
- Kubernetes (e.g. Docker Desktop with Kubernetes enabled)
- kubectl
- helm 3.0+
- cli-rhea

### Setup

The `setup.sh` script:
- installs Zilla to the Kubernetes cluster with helm and waits for the pod to start up
- starts port forwarding

```bash
$ ./setup.sh
+ helm install zilla-amqp-reflect chart --namespace zilla-amqp-reflect --create-namespace --wait
NAME: zilla-amqp-reflect
LAST DEPLOYED: [...]
NAMESPACE: zilla-amqp-reflect
STATUS: deployed
REVISION: 1
TEST SUITE: None
+ nc -z localhost 5671
+ kubectl port-forward --namespace zilla-amqp-reflect service/zilla 5671 5672
+ sleep 1
+ nc -z localhost 5671
Connection to localhost port 5671 [tcp/*] succeeded!
```

### Install amqp client

Requires AMQP 1.0 client, such as `cli-rhea`.

```bash
$ npm install cli-rhea -g
```

### Verify behavior

Connect two receiving clients first, then send `Hello, world` from sending client.

```bash
$ cli-rhea-receiver --address 'zilla' --log-lib 'TRANSPORT_DRV' --log-msgs 'body' --broker localhost:5671 --conn-ssl-trust-store test-ca.crt
  rhea:events [connection-1] Connection got event: connection_open +0ms
  rhea:events [5c6a67c4-7e0b-fa4e-916c-b8e78ac6ba2e] Container got event: connection_open +0ms
  rhea:events [connection-1] Session got event: session_open +0ms
  rhea:events [connection-1] Connection got event: session_open +0ms
  rhea:events [5c6a67c4-7e0b-fa4e-916c-b8e78ac6ba2e] Container got event: session_open +0ms
  rhea:events [connection-1] Link got event: receiver_open +1ms
  rhea:events [connection-1] Session got event: receiver_open +0ms
  rhea:events [connection-1] Connection got event: receiver_open +0ms
  rhea:events [5c6a67c4-7e0b-fa4e-916c-b8e78ac6ba2e] Container got event: receiver_open +0ms
  rhea:events [connection-1] Link got event: message +52s
  rhea:events [connection-1] Session got event: message +0ms
  rhea:events [connection-1] Connection got event: message +0ms
  rhea:events [5c6a67c4-7e0b-fa4e-916c-b8e78ac6ba2e] Container got event: message +0ms
"Hello, world"
  rhea:events [connection-1] Connection got event: connection_close +7ms
  rhea:events [5c6a67c4-7e0b-fa4e-916c-b8e78ac6ba2e] Container got event: connection_close +0ms
```
```bash
$ cli-rhea-receiver --address 'zilla' --log-lib 'TRANSPORT_DRV' --log-msgs 'body'
  rhea:events [connection-1] Connection got event: connection_open +0ms
  rhea:events [fc4447a6-ce6d-9943-8847-33dab2317567] Container got event: connection_open +0ms
  rhea:events [connection-1] Session got event: session_open +1ms
  rhea:events [connection-1] Connection got event: session_open +0ms
  rhea:events [fc4447a6-ce6d-9943-8847-33dab2317567] Container got event: session_open +0ms
  rhea:events [connection-1] Link got event: receiver_open +1ms
  rhea:events [connection-1] Session got event: receiver_open +0ms
  rhea:events [connection-1] Connection got event: receiver_open +0ms
  rhea:events [fc4447a6-ce6d-9943-8847-33dab2317567] Container got event: receiver_open +0ms
  rhea:events [connection-1] Link got event: message +30s
  rhea:events [connection-1] Session got event: message +0ms
  rhea:events [connection-1] Connection got event: message +0ms
  rhea:events [fc4447a6-ce6d-9943-8847-33dab2317567] Container got event: message +0ms
"Hello, world"
  rhea:events [connection-1] Connection got event: connection_close +7ms
  rhea:events [fc4447a6-ce6d-9943-8847-33dab2317567] Container got event: connection_close +0ms
```
```bash
$ cli-rhea-sender --address 'zilla' --msg-content 'Hello, world' --log-lib 'TRANSPORT_DRV'
  rhea:events [connection-1] Connection got event: connection_open +0ms
  rhea:events [efd09fe2-4090-4141-91e6-5ce5223d1dbc] Container got event: connection_open +1ms
  rhea:events [connection-1] Session got event: session_open +0ms
  rhea:events [connection-1] Connection got event: session_open +0ms
  rhea:events [efd09fe2-4090-4141-91e6-5ce5223d1dbc] Container got event: session_open +0ms
  rhea:events [connection-1] Link got event: sender_open +1ms
  rhea:events [connection-1] Session got event: sender_open +0ms
  rhea:events [connection-1] Connection got event: sender_open +0ms
  rhea:events [efd09fe2-4090-4141-91e6-5ce5223d1dbc] Container got event: sender_open +0ms
  rhea:events [connection-1] Link got event: sender_flow +0ms
  rhea:events [connection-1] Session got event: sender_flow +1ms
  rhea:events [connection-1] Connection got event: sender_flow +0ms
  rhea:events [efd09fe2-4090-4141-91e6-5ce5223d1dbc] Container got event: sender_flow +0ms
  rhea:events [connection-1] Link got event: sender_flow +0ms
  rhea:events [connection-1] Session got event: sender_flow +0ms
  rhea:events [connection-1] Connection got event: sender_flow +0ms
  rhea:events [efd09fe2-4090-4141-91e6-5ce5223d1dbc] Container got event: sender_flow +0ms
  rhea:events [connection-1] Link got event: sendable +0ms
  rhea:events [connection-1] Session got event: sendable +0ms
  rhea:events [connection-1] Connection got event: sendable +0ms
  rhea:events [efd09fe2-4090-4141-91e6-5ce5223d1dbc] Container got event: sendable +0ms
  rhea:events [connection-1] Connection got event: disconnected +3ms
  rhea:events [efd09fe2-4090-4141-91e6-5ce5223d1dbc] Container got event: disconnected +0ms
  rhea:events [connection-1] Connection got event: connection_open +105ms
  rhea:events [efd09fe2-4090-4141-91e6-5ce5223d1dbc] Container got event: connection_open +0ms
  rhea:events [connection-1] Session got event: session_open +0ms
  rhea:events [connection-1] Connection got event: session_open +1ms
  rhea:events [efd09fe2-4090-4141-91e6-5ce5223d1dbc] Container got event: session_open +0ms
  rhea:events [connection-1] Link got event: sender_open +0ms
  rhea:events [connection-1] Session got event: sender_open +0ms
  rhea:events [connection-1] Connection got event: sender_open +0ms
  rhea:events [efd09fe2-4090-4141-91e6-5ce5223d1dbc] Container got event: sender_open +0ms
  rhea:events [connection-1] Link got event: sender_flow +0ms
  rhea:events [connection-1] Session got event: sender_flow +0ms
  rhea:events [connection-1] Connection got event: sender_flow +0ms
  rhea:events [efd09fe2-4090-4141-91e6-5ce5223d1dbc] Container got event: sender_flow +0ms
  rhea:events [connection-1] Link got event: sender_flow +1ms
  rhea:events [connection-1] Session got event: sender_flow +0ms
  rhea:events [connection-1] Connection got event: sender_flow +0ms
  rhea:events [efd09fe2-4090-4141-91e6-5ce5223d1dbc] Container got event: sender_flow +0ms
  rhea:events [connection-1] Link got event: sendable +0ms
  rhea:events [connection-1] Session got event: sendable +0ms
  rhea:events [connection-1] Connection got event: sendable +0ms
  rhea:events [efd09fe2-4090-4141-91e6-5ce5223d1dbc] Container got event: sendable +0ms
```

### Teardown

The `teardown.sh` script stops port forwarding, uninstalls Zilla and deletes the namespace.

```bash
$ ./teardown.sh
+ pgrep kubectl
99999
+ killall kubectl
+ helm uninstall zilla-amqp-echo --namespace zilla-amqp-echo
release "zilla-amqp-echo" uninstalled
+ kubectl delete namespace zilla-amqp-echo
namespace "zilla-amqp-echo" deleted
```
