# http.kafka.avro.json

This example illustrates how to configure the Karapace Schema Registry in Zilla to validate messages during produce and fetch to a Kafka topic.

## Requirements

- docker compose

## Setup

To `start` the Docker Compose stack defined in the [compose.yaml](compose.yaml) file, use:

```bash
docker compose up -d
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

## Validate created Schema

```bash
curl 'http://localhost:8081/schemas/ids/1'
```

```bash
curl 'http://localhost:8081/subjects/items-snapshots-value/versions/latest'
```

## Verify behavior for a valid event

`POST` request

```bash
curl -k -v http://localhost:7114/items -H 'Idempotency-Key: 1'  -H 'Content-Type: application/json' -d '{"id": "123","status": "OK"}'
```

output:

```text
...
> POST /items HTTP/2
> Host: localhost:7114
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
curl -k http://localhost:7114/items/1
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

## Verify behavior for Invalid event

`POST` request.

```bash
curl -k -v http://localhost:7114/items -H 'Idempotency-Key: 2'  -H 'Content-Type: application/json' -d '{"id": 123,"status": "OK"}'
```

output:

```text
...
> POST /items HTTP/2
> Host: localhost:7114
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
curl -k -v http://localhost:7114/items/2
```

output:

```text
...
> GET /items/2 HTTP/2
> Host: localhost:7114
> User-Agent: curl/8.1.2
> Accept: */*
>
< HTTP/2 404
< content-length: 0
```

## Teardown

To remove any resources created by the Docker Compose stack, use:

```bash
docker compose down
```
