# Zilla packaged by Aklivity

Zilla abstracts Apache Kafka® for web applications, IoT clients and microservices via declaratively defined OpenAPIs and AsyncAPIs.

With no coding requirements or external dependencies, Zilla is the most advanced yet approachable Kafka integration solution. It natively supports the Kafka wire protocol and uses novel protocol mediation techniques to establish stateless API entry points into Kafka.

[Zilla by Aklivity](https://www.aklivity.io/)

## TL;DR

```shell
helm install zilla oci://ghcr.io/aklivity/charts/zilla --namespace zilla --create-namespace --wait \
    --values values.yaml \
    --set-file zilla\\.yaml=zilla.yaml \
    --set-file configMaps.proto.data.echo\\.proto=proto/echo.proto \
    --set-file secrets.tls.data.localhost\\.p12=tls/localhost.p12
```

## Introduction

This chart bootstraps a [Zilla](https://github.com/aklivity/zilla) deployment on a [Kubernetes](https://kubernetes.io)
cluster using the [Helm](https://helm.sh) package manager.

## Requirements

Kubernetes: `>=1.23.x`
Helm: `>=3.8.x`

## Install Chart

```console
helm install [RELEASE_NAME] oci://ghcr.io/aklivity/charts/zilla
```

The command deploys aklivity/zilla on the Kubernetes cluster in the default configuration.

## Uninstall Chart

```console
helm uninstall [RELEASE_NAME]
```

This removes all the Kubernetes components associated with the chart and deletes the release.

## Configuration

Zilla specific configuration is in the `zilla.yaml` file which can be included in the helm install by adding
`--set-file zilla\\.yaml=zilla.yaml` to your command. 

Additional files can be deployed to configmaps and secrets by adding e.g. `--set-file configMaps.proto.data.echo\\.proto=proto/echo.proto`
and `--set-file secrets.tls.data.localhost\\.p12=tls/localhost.p12` to your command.

See the [aklivity/zilla-examples](https://github.com/aklivity/zilla-examples) repository for examples.
