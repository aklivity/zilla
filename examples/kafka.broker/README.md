# kafka.broker

This is the resource folder for the running a Kafka broker locally.

## Requirements

- docker

\- OR -

- Kubernetes
- kubectl
- helm 3.0+

## Running locally

This resource can be run using Docker compose or Kubernetes. The setup scripts are in the [compose](./docker/compose) and [helm](./k8s/helm) folders respectively and work the same way.

If you are running this alongside another example you will want to expose these environment variables

```bash
export KAFKA_HOST=host.docker.internal
export KAFKA_PORT=29092
```

### Setup

Wether you chose [compose](./docker/compose) or [helm](./k8s/helm), the `setup.sh` script will:

- create an instance of `docker.io/bitnami/kafka`
- create an instance of `docker.io/provectuslabs/kafka-ui`

```bash
./setup.sh
```

### Teardown

The `teardown.sh` script will remove any resources created.

```bash
./teardown.sh
```
