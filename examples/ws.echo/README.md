# ws.echo

Listens on ws port `7114` and will echo back whatever is sent to the server.
Listens on wss port `7114` and will echo back whatever is sent to the server.

## Requirements

- docker compose
- wscat

## Setup

To `start` the Docker Compose stack defined in the [compose.yaml](compose.yaml) file, use:

```bash
docker compose up -d
```

### Install wscat

```bash
npm install wscat -g
```

### Verify behavior

```bash
wscat -c ws://localhost:7114/ -s echo
```

Type a `Hello, world` message and press `enter`.

output:

```text
Connected (press CTRL+C to quit)
> Hello, world
< Hello, world
```

```bash
wscat -c wss://localhost:7114/ --ca test-ca.crt -s echo
```

Type a `Hello, world` message and press `enter`.

output:

```text
Connected (press CTRL+C to quit)
> Hello, world
< Hello, world
```

## Teardown

To remove any resources created by the Docker Compose stack, use:

```bash
docker compose down
```
