# http.kafka.crud

This simple http.kafka.crud example illustrates how to configure zilla to expose a REST API that just creates, updates,
deletes and reads messages in `items-snapshots` log-compacted Kafka topic acting as a table.

## Requirements

- docker compose

## Setup

To `start` the Docker Compose stack defined in the [compose.yaml](compose.yaml) file, use:

```bash
docker compose up -d
```

### Endpoints

| Protocol | Method | Endpoint    | Topic           | Description             |
| -------- | ------ | ----------- | --------------- | ----------------------- |
| HTTP     | POST   | /items      | items-snapshots | Create an item.         |
| HTTP     | PUT    | /items/{id} | items-snapshots | Update item by the key. |
| HTTP     | DELETE | /items/{id} | items-snapshots | Delete item by the key. |
| HTTP     | GET    | /items      | items-snapshots | Fetch all items.        |
| HTTP     | GET    | /items/{id} | items-snapshots | Fetch item by the key.  |

### Verify behavior

`POST` request.

Note: You can remove `-H 'Idempotency-Key: 1'` to generate random key.

```bash
curl -k -v -X POST http://localhost:7114/items -H 'Idempotency-Key: 1'  -H 'Content-Type: application/json' -d '{"greeting":"Hello, world1"}'
```

output:

```text
...
POST /items HTTP/2
Host: localhost:7114
user-agent: curl/7.85.0
accept: */*
idempotency-key: 2
content-type: application/json
content-length: 28
* We are completely uploaded and fine
* Connection state changed (MAX_CONCURRENT_STREAMS == 2147483647)!
HTTP/2 204
```

`GET` request to fetch specific item.

```bash
curl -k -v http://localhost:7114/items/1
```

output:

```text
...
* Connection state changed (MAX_CONCURRENT_STREAMS == 2147483647)!
< HTTP/2 200
< content-length: 27
< content-type: application/json
< etag: AQIACA==
<
* Connection #0 to host localhost left intact
{"greeting":"Hello, world1"}%
```

`PUT` request to update specific item.

```bash
curl -k -v -X PUT http://localhost:7114/items/1 -H 'Content-Type: application/json' -d '{"greeting":"Hello, world2"}'
```

output:

```text
...
PUT /items/1 HTTP/2
Host: localhost:7114
user-agent: curl/7.85.0
accept: */*
idempotency-key: 2
content-type: application/json
content-length: 28
* We are completely uploaded and fine
* Connection state changed (MAX_CONCURRENT_STREAMS == 2147483647)!
HTTP/2 204
```

`DELETE` request to delete specific item.

```bash
curl -k -v -X DELETE http://localhost:7114/items/1
```

output:

```text
...
> DELETE /items/1 HTTP/2
> Host: localhost:7114
> user-agent: curl/7.85.0
> accept: */*
>
* Connection state changed (MAX_CONCURRENT_STREAMS == 2147483647)!
< HTTP/2 204
```

## Teardown

To remove any resources created by the Docker Compose stack, use:

```bash
docker compose down
```
