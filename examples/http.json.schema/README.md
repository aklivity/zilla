# http.json.schema

Listens on https port `7114` and will response back whatever is hosted in `nginx` on that path after enforcing validation.

## Requirements

- docker compose

## Setup

To `start` the Docker Compose stack defined in the [compose.yaml](compose.yaml) file, use:

```bash
docker compose up -d
```

### Verify behavior for valid content

```bash
curl http://localhost:7114/valid.json
```

output:

```text
{
    "id": 42,
    "status": "Active"
}
```

### Verify behavior for invalid content

```bash
curl http://localhost:7114/invalid.json
```

output:

```text
curl: (18) transfer closed with 37 bytes remaining to read
```

## Teardown

To remove any resources created by the Docker Compose stack, use:

```bash
docker compose down
```
