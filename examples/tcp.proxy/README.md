# tcp.proxy

Listens on tcp port `12345` and will proxy iperf traffic to iperf server.

## Requirements

- docker compose

## Setup

To `start` the Docker Compose stack defined in the [compose.yaml](compose.yaml) file, use:

```bash
docker compose up -d
```

## Teardown

To remove any resources created by the Docker Compose stack, use:

```bash
docker compose down
```
