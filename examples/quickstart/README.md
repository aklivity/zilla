# quickstart

Creates a full featured instance of Zilla on port `7114`. Follow the [Zilla Quickstart](https://docs.aklivity.io/zilla/latest/tutorials/quickstart/kafka-proxies.html) to discover some of what Zilla can do!

## Requirements

- docker

## Setup

The `setup.sh` script:

- creates a Zilla instance running in docker.
- creates an instance of `docker.io/bitnami/kafka`
- adds the necessary topics
- hosts a `provectuslabs/kafka-ui` [instance](http://localhost:80)
- starts the route_guide_server and route_guide_client from the [gRPC basics tutorial](https://grpc.io/docs/languages/go/basics/)
- runs an [mqtt-simulator](https://github.com/DamascenoRafael/mqtt-simulator) to produce mock iot messages
- hosts [prometheus metrics](http://localhost:7190/metrics)

```bash
./setup.sh
```

## Teardown

The `teardown.sh` script stops running containers and removes orphans.

```bash
./teardown.sh
```
