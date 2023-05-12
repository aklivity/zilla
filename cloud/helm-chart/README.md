# Install

```
$ helm install zilla . --namespace zilla --create-namespace --wait
NAME: zilla
NAMESPACE: zilla
STATUS: deployed
[...]
```

To use your own `values.yaml` and `zilla.yaml` file:
```
$ helm install zilla . --namespace zilla --create-namespace --wait \
    --values values.yaml \
    --set-file zilla\\.yaml=zilla.yaml
```

# Upgrade

```
$ helm upgrade zilla . --namespace zilla --wait
Release "zilla" has been upgraded. Happy Helming!
[...]
```

To use your own `values.yaml` and `zilla.yaml` file:
```
$ helm upgrade zilla . --namespace zilla --create-namespace --wait \
    --values values.yaml \
    --set-file zilla\\.yaml=zilla.yaml
```

# Uninstall

```
$ helm uninstall zilla --namespace zilla
release "zilla" uninstalled
```

# Examples

Default is no-op zilla.

```
$ helm install zilla . --namespace zilla --create-namespace --wait
```

See the https://github.com/aklivity/zilla-examples repository for examples.
