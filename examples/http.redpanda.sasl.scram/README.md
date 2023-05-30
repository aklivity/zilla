# http.redpanda.sasl.scram

Listens on http port `8080` or https port `9090` and will produce messages to the `events` topic in `SASL/SCRAM`
enabled Redpanda cluster, synchronously.

### Requirements

- bash, jq, nc
- Kubernetes (e.g. Docker Desktop with Kubernetes enabled)
- kubectl
- helm 3.0+
- kcat

### Install kcat client

Requires Kafka client, such as `kcat`.

```bash
brew install kcat
```

### Setup

The `setup.sh` script:

- installs Zilla and Redpanda to the Kubernetes cluster with helm and waits for the pods to start up
- creates the `user` user in Redpanda
- creates the `events` topic in Redpanda
- starts port forwarding

```bash
./setup.sh
```

output:

```text
+ ZILLA_CHART=oci://ghcr.io/aklivity/charts/zilla
+ VERSION=0.9.46
+ helm install zilla-http-redpanda-sasl-scram ./zilla-0.1.0.tgz --namespace zilla-http-redpanda-sasl-scram --create-namespace --wait [...]
NAME: zilla-http-redpanda-sasl-scram
LAST DEPLOYED: [...]
NAMESPACE: zilla-http-redpanda-sasl-scram
STATUS: deployed
REVISION: 1
NOTES:
Zilla has been installed.
[...]
+ helm install zilla-http-redpanda-sasl-scram-redpanda chart --namespace zilla-http-redpanda-sasl-scram --create-namespace --wait
NAME: zilla-http-redpanda-sasl-scram-redpanda
LAST DEPLOYED: [...]
NAMESPACE: zilla-http-redpanda-sasl-scram
STATUS: deployed
REVISION: 1
TEST SUITE: None
++ kubectl get pods --namespace zilla-http-redpanda-sasl-scram --selector app.kubernetes.io/instance=redpanda -o name
+ REDPANDA_POD=pod/redpanda-1234567890-abcde
+ kubectl exec --namespace zilla-http-redpanda-sasl-scram pod/redpanda-1234567890-abcde -- rpk acl user create user -p redpanda
Created user "user".
+ kubectl exec --namespace zilla-http-redpanda-sasl-scram pod/redpanda-1234567890-abcde -- rpk topic create events --user user --password redpanda --sasl-mechanism SCRAM-SHA-256
TOPIC   STATUS
events  OK
+ kubectl port-forward --namespace zilla-http-redpanda-sasl-scram service/zilla-http-redpanda-sasl-scram 8080 9090
+ nc -z localhost 8080
+ kubectl port-forward --namespace zilla-http-redpanda-sasl-scram service/redpanda 9092
+ sleep 1
+ nc -z localhost 8080
Connection to localhost port 8080 [tcp/http-alt] succeeded!
+ nc -z localhost 9092
Connection to localhost port 9092 [tcp/XmlIpcRegSvc] succeeded!
```

### Verify behavior

Send a `POST` request with an event body.

```bash
curl -v \
       -X "POST" http://localhost:8080/events \
       -H "Content-Type: application/json" \
       -d "{\"greeting\":\"Hello, world\"}"
```

output:

```text
...
> POST /events HTTP/1.1
> Content-Type: application/json
...
< HTTP/1.1 204 No Content
```

Verify that the event has been produced to the `events` topic in Redpanda cluster.

```bash
kcat -b localhost:9092 -X security.protocol=SASL_PLAINTEXT \
  -X sasl.mechanisms=SCRAM-SHA-256 \
  -X sasl.username=user \
  -X sasl.password=redpanda \
  -t events -J -u | jq .
{
  "topic": "events",
  "partition": 0,
  "offset": 0,
  "tstype": "create",
  "ts": 1652465273281,
  "broker": 1001,
  "headers": [
    "content-type",
    "application/json"
  ],
  "payload": "{\"greeting\":\"Hello, world\"}"
}
```

output:

```text
% Reached end of topic events [0] at offset 1
```

### Teardown

The `teardown.sh` script stops port forwarding, uninstalls Zilla and Redpanda and deletes the namespace.

```bash
./teardown.sh
```

output:

```text
+ pgrep kubectl
99999
99998
+ killall kubectl
+ helm uninstall zilla-http-redpanda-sasl-scram zilla-http-redpanda-sasl-scram-redpanda --namespace zilla-http-redpanda-sasl-scram
release "zilla-http-redpanda-sasl-scram" uninstalled
release "zilla-http-redpanda-sasl-scram-redpanda" uninstalled
+ kubectl delete namespace zilla-http-redpanda-sasl-scram
namespace "zilla-http-redpanda-sasl-scram" deleted
```
