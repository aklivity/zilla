# http.echo

Listens on `http://localhost:7114/echo` and will echo back whatever is sent to the server.

## Requirements

- curl
- docker compose

## Setup

To `start` the Docker Compose stack defined in the [compose.yaml](compose.yaml) file, use:

```bash
docker compose up -d
```

### Verify behavior

```bash
curl -d "Hello, world" http://localhost:7114/
```

output:

```text
Hello, world
```

## Teardown

To remove any resources created by the Docker Compose stack, use:

```bash
docker compose down
```
