# Install

```
$ helm install zilla . --namespace zilla --create-namespace --wait #--values your.custom.values.yaml
NAME: zilla
NAMESPACE: zilla
STATUS: deployed
[...]
```

# Upgrade

```
$ helm upgrade zilla . --namespace zilla --wait #--values your.custom.values.yaml
Release "zilla" has been upgraded. Happy Helming!
[...]
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
