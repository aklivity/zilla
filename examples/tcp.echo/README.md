# tcp.echo

Listens on tcp port `12345` and will echo back whatever is sent to the server.

## Requirements

- nc
- docker compose

## Setup

To `start` the Docker Compose stack defined in the [compose.yaml](compose.yaml) file, use:

```bash
docker compose up -d
```

### Verify behavior

```bash
nc localhost 12345
```

Type a `Hello, world` message and press `enter`.

output:

```text
Hello, world
Hello, world
```

## Teardown

To remove any resources created by the Docker Compose stack, use:

```bash
docker compose down
```
