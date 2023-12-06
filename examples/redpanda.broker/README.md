# redpanda.broker

This is the resource folder for the running a [Redpanda](https://redpanda.com/) broker locally.

## Requirements

- docker

## Running locally

This resource can be run using Docker compose. The setup scripts are in the [compose](./docker/compose) folder.

### Setup

The `setup.sh` script will:

- create an instance of `docker.redpanda.com/redpandadata/redpanda`
- create an instance of `docker.redpanda.com/redpandadata/console`

```bash
./setup.sh
```

### Teardown

The `teardown.sh` script will remove any resources created.

```bash
./teardown.sh
```
