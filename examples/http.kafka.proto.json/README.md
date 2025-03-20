# http.kafka.proto.json

This example allows a protobuf object to be sent to a REST endpoint as JSON that gets validated and converted to the protobuf when it is produced onto Kafka.

## Setup

To `start` the Docker Compose stack defined in the [compose.yaml](compose.yaml) file, use:

```bash
docker compose up -d
```

### Verify behavior for a valid event

```bash
curl 'http://localhost:7114/requests' \
--header 'Content-Type: application/json' \
--data '{
    "message": "hello message",
    "count": 10
}' -v
```

Output:

```bash
* Host localhost:7114 was resolved.
...
> Content-Type: application/json
> Content-Length: 51
>
* upload completely sent off: 51 bytes
< HTTP/1.1 204 No Content
```

### Verify behavior for Invalid event

```bash
curl 'http://localhost:7114/requests' \
--header 'Content-Type: application/json' \
--data '{
    "message": "hello world",
    "count": 10,
    "invalid": "field"
}' -v
```

Output:

```bash
* Host localhost:7114 was resolved.
...
> Content-Type: application/json
> Content-Length: 73
>
* upload completely sent off: 73 bytes
< HTTP/1.1 400 Bad Request
```

## Teardown

To remove any resources created by the Docker Compose stack, use:

```bash
docker compose down
```
