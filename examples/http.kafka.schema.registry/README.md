# http.kafka.schema.registry

This simple http.kafka.schema.registry example illustrates how to configure Karapace Schema Registry in zilla to validate messages while produce and fetch to a Kafka topic.

### Requirements

- bash, jq, nc
- Kubernetes (e.g. Docker Desktop with Kubernetes enabled)
- kubectl
- helm 3.0+

### Setup

The `setup.sh` script:

- installs Zilla and Kafka to the Kubernetes cluster with helm and waits for the pods to start up
- creates the `items-snapshots` topic in Kafka with the `cleanup.policy=compact` topic configuration
- starts port forwarding

```bash
./setup.sh
```

output:

```text
+ ZILLA_CHART=oci://ghcr.io/aklivity/charts/zilla
+ NAMESPACE=zilla-http-kafka-schema-registry
+ helm upgrade --install zilla oci://ghcr.io/aklivity/charts/zilla --namespace zilla-http-kafka-schema-registry --create-namespace --wait [...]
NAME: zilla-http-kafka-crud
LAST DEPLOYED: [...]
NAMESPACE: zilla-http-kafka-schema-registry
STATUS: deployed
REVISION: 1
NOTES:
Zilla has been installed.
[...]
+ helm upgrade --install zilla-http-kafka-crud-kafka chart --namespace zilla-http-kafka-crud --create-namespace --wait
NAME: zilla-http-kafka-crud-kafka
LAST DEPLOYED: [...]
NAMESPACE: zilla-http-kafka-crud
STATUS: deployed
REVISION: 1
TEST SUITE: None
[...]
Connection to localhost port 7114 [tcp/*] succeeded!
+ nc -z localhost 7143
Connection to localhost port 7143 [tcp/*] succeeded!
+ nc -z localhost 8081
Connection to localhost port 8081 [tcp/sunproxyadmin] succeeded!
+ nc -z localhost 9092
Connection to localhost port 9092 [tcp/XmlIpcRegSvc] succeeded!
```

### Register Schema

```bash
curl 'http://localhost:8081/subjects/items-snapshots-value/versions' \
--header 'Content-Type: application/json' \
--data '{
  "schema":
    "{\"fields\":[{\"name\":\"id\",\"type\":\"string\"},{\"name\":\"status\",\"type\":\"string\"}],\"name\":\"Event\",\"namespace\":\"io.aklivity.example\",\"type\":\"record\"}",
  "schemaType": "AVRO"
}'
```

output:

```text
{"id":1}%
```

### Validate created Schema

```bash
curl 'http://localhost:8081/schemas/ids/1'
```

```bash
curl 'http://localhost:8081/subjects/items-snapshots-value/versions/latest'
```

### Verify behavior for a valid event

`POST` request

```bash
curl -k -v -X POST https://localhost:7143/items -H 'Idempotency-Key: 1'  -H 'Content-Type: application/json' -d '{"id": "123","status": "OK"}'
```

output:

```text
...
> POST /items HTTP/2
> Host: localhost:7143
> User-Agent: curl/8.1.2
> Accept: */*
> Idempotency-Key: 1
> Content-Type: application/json
> Content-Length: 28
>
* We are completely uploaded and fine
< HTTP/2 204
```

`GET` request to fetch specific item.

```bash
curl -k -v https://localhost:7143/items/1
```

output:

```text
...
< HTTP/2 200
< content-length: 26
< content-type: application/json
< etag: AQIAAg==
<
* Connection #0 to host localhost left intact
{"id":"123","status":"OK"}
```

### Verify behavior for Invalid event

`POST` request.

```bash
curl -k -v -X POST https://localhost:7143/items -H 'Idempotency-Key: 2'  -H 'Content-Type: application/json' -d '{"id": 123,"status": "OK"}'
```

output:

```text
...
> POST /items HTTP/2
> Host: localhost:7143
> User-Agent: curl/8.1.2
> Accept: */*
> Idempotency-Key: 1
> Content-Type: application/json
> Content-Length: 26
>
* We are completely uploaded and fine
< HTTP/2 400
```

`GET` request to verify whether Invalid event is produced

```bash
curl -k -v https://localhost:7143/items/2
```

output:

```text
...
> GET /items/2 HTTP/2
> Host: localhost:7143
> User-Agent: curl/8.1.2
> Accept: */*
>
< HTTP/2 404
< content-length: 0
```

### Teardown

The `teardown.sh` script stops port forwarding, uninstalls Zilla and Kafka and deletes the namespace.

```bash
./teardown.sh
```

output:

```text
+ pgrep kubectl
99998
99999
+ killall kubectl
+ NAMESPACE=zilla-http-kafka-schema-registry
+ helm uninstall zilla kafka --namespace zilla-http-kafka-schema-registry
release "zilla" uninstalled
release "kafka" uninstalled
+ kubectl delete namespace zilla-http-kafka-schema-registry
namespace "zilla-http-kafka-schema-registry" deleted
```
