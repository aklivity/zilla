# tcp.reflect

Listens on tcp port `12345` and will echo back whatever is sent to the server, broadcasting to all clients.

## Requirements

- nc
- docker compose

## Setup

To `start` the Docker Compose stack defined in the [compose.yaml](compose.yaml) file, use:

```bash
docker compose up -d
```

### Verify behavior

Connect each client first, then send `Hello, one` from first client, then send `Hello, two` from second client.

```bash
nc localhost 12345
```

Type a `Hello, one` message and press `enter`.

output:

```text
Hello, one
Hello, one
Hello, two
```

```bash
nc localhost 12345
```

Type a `Hello, two` message and press `enter`.

output:

```text
Hello, one
Hello, two
Hello, two
```

## Teardown

To remove any resources created by the Docker Compose stack, use:

```bash
docker compose down
```
