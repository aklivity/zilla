# kubernetes.prometheus.autoscale

Listens on http port `8080` and will echo back whatever is sent to the server.

Kubernetes horizontal pod autoscaler is set up to enable the zilla deployment to scale from 1 to 5 pods with the goal
of an average load of 10 active connections per pod.

### Requirements

- bash, jq, nc
- Kubernetes (e.g. Docker Desktop with Kubernetes enabled)
- kubectl
- helm 3.0+

### Setup

The `setup.sh` script:

- installs Zilla, Prometheus and Prometheus Adapter to the Kubernetes cluster with helm and waits for the pods to start up
- sets up horizontal pod autoscaling
- starts port forwarding

```bash
./setup.sh
```

output:

```text
+ ZILLA_CHART=oci://ghcr.io/aklivity/charts/zilla
+ helm install zilla-kubernetes-prometheus-autoscale oci://ghcr.io/aklivity/charts/zilla --namespace zilla-kubernetes-prometheus-autoscale --create-namespace --wait [...]
NAME: zilla-kubernetes-prometheus-autoscale
LAST DEPLOYED: [...]
NAMESPACE: zilla-kubernetes-prometheus-autoscale
STATUS: deployed
REVISION: 1
NOTES:
Zilla has been installed.
[...]
+ helm install zilla-kubernetes-prometheus-autoscale-prometheus chart --namespace zilla-kubernetes-prometheus-autoscale --create-namespace --wait
NAME: zilla-kubernetes-prometheus-autoscale-prometheus
LAST DEPLOYED: [...]
NAMESPACE: zilla-kubernetes-prometheus-autoscale
STATUS: deployed
REVISION: 1
TEST SUITE: None
+ kubectl port-forward --namespace zilla-kubernetes-prometheus-autoscale service/zilla-kubernetes-prometheus-autoscale 8080
+ nc -z localhost 8080
+ kubectl port-forward --namespace zilla-kubernetes-prometheus-autoscale service/prometheus 9090
+ sleep 1
+ nc -z localhost 8080
Connection to localhost port 8080 [tcp/http-alt] succeeded!
+ nc -z localhost 9090
Connection to localhost port 9090 [tcp/websm] succeeded!
```

### Verify behavior

```bash
curl -d "Hello, world" -X "POST" http://localhost:8080
```

output:

```text
Hello, world
```

The initial status is:

- no open connections
- the value of the `stream_active_received` metric should be 0
- there should be 1 zilla pod in the deployment

> If the kubernetes custom metrics API response does not appear correctly please wait a few seconds and try again before proceeding further.

```bash
./check_metric.sh
```

output:

```text
The value of stream_active_received metric
------------------------------------------

Prometheus API:
{
...
        "metric": {
          "__name__": "stream_active_received",
        },
        "value": [
          1683013504.619, # timestamp
          "0" # value
...
}

Kubernetes custom metrics API:
{
...
      "metricName": "stream_active_received",
      "value": "0",
...
}
```

The zilla deployment should consist of 1 pod.

```bash
./check_hpa.sh
```

output:

```text
The status of horizontal pod autoscaling
----------------------------------------

HorizontalPodAutoscaler:
NAME    REFERENCE          TARGETS   MINPODS   MAXPODS   REPLICAS   AGE
zilla   Deployment/zilla   0/10      1         5         1          4m24s

Deployment:
NAME    READY   UP-TO-DATE   AVAILABLE   AGE
zilla   1/1     1            1           4m25s

Pods:
NAME                     READY   STATUS    RESTARTS   AGE
zilla-6db8d879f5-2wxgw   1/1     Running   0          4m25s
```

Open 21 connections to zilla as instances of netcat in the background.

```bash
for i in `seq 1 21`; do nc localhost 8080 &; done
```

output:

```text
[42] 88886
[43] 88887
[44] 88888
...
```

There should be 21 open connections in the background now.

```bash
ps auxw | grep "nc localhost 8080" | grep -v grep | wc -l
```

output:

```text
21
```

Wait for a few seconds so the metrics get updated. The value of stream_active_received metric should be 21 for one of the pods.

```bash
./check_metric.sh
```

output:

```text
The value of stream_active_received metric
------------------------------------------

Prometheus API:
{
...
        "metric": {
          "__name__": "stream_active_received",
        },
        "value": [
          1683013504.619, # timestamp
          "21" # value
...
}

Kubernetes custom metrics API:
{
...
      "metricName": "stream_active_received",
      "value": "21",
...
}
```

Wait for a few seconds so the autoscaler can catch up. The zilla deployment should be soon scaled up to 3 pods.

```bash
./check_hpa.sh
```

output:

```text
The status of horizontal pod autoscaling
----------------------------------------

HorizontalPodAutoscaler:
NAME    REFERENCE          TARGETS   MINPODS   MAXPODS   REPLICAS   AGE
zilla   Deployment/zilla   7/10      1         5         3          7m14s

Deployment:
NAME    READY   UP-TO-DATE   AVAILABLE   AGE
zilla   3/3     3            3           7m15s

Pods:
NAME                     READY   STATUS    RESTARTS   AGE
zilla-6db8d879f5-2wxgw   1/1     Running   0          7m15s
zilla-6db8d879f5-9bnkh   1/1     Running   0          75s
zilla-6db8d879f5-fmgqx   1/1     Running   0          75s
```

Open 21 connections to zilla as instances of netcat in the background.

```bash
for i in `seq 1 21`; do nc localhost 8080 &; done
```

output:

```text
[77] 77775
[78] 77776
[79] 77777
...

There should be 42 open connections in the background now.

```bash
ps auxw | grep "nc localhost 8080" | grep -v grep | wc -l
```

output:

```text
42
```

Wait for a few seconds so the metrics get updated. The value of stream_active_received metric should be 42 for one of the pods.

```bash
./check_metric.sh
```

output:

```text
The value of stream_active_received metric
------------------------------------------

Prometheus API:
{
...
        "metric": {
          "__name__": "stream_active_received",
        },
        "value": [
          1683013504.619, # timestamp
          "42" # value
...
}

Kubernetes custom metrics API:
{
...
      "metricName": "stream_active_received",
      "value": "42",
...
}
```

Wait for a few seconds so the autoscaler can catch up. The zilla deployment should be soon scaled up to 5 pods.

```bash
./check_hpa.sh
```

output:

```text
The status of horizontal pod autoscaling
----------------------------------------

HorizontalPodAutoscaler:
NAME    REFERENCE          TARGETS    MINPODS   MAXPODS   REPLICAS   AGE
zilla   Deployment/zilla   8400m/10   1         5         5          12m

Deployment:
NAME    READY   UP-TO-DATE   AVAILABLE   AGE
zilla   5/5     5            5           12m

Pods:
NAME                     READY   STATUS    RESTARTS   AGE
zilla-6db8d879f5-2wxgw   1/1     Running   0          12m
zilla-6db8d879f5-9bnkh   1/1     Running   0          6m3s
zilla-6db8d879f5-fmgqx   1/1     Running   0          6m3s
zilla-6db8d879f5-g74hl   1/1     Running   0          63s
zilla-6db8d879f5-q5fmm   1/1     Running   0          63s
```

Shut down all running netcat instances.

```bash
ps auxw | grep "nc localhost 8080" | grep -v grep | awk '{print $2}' | xargs kill
```

output:

```text
[23]  + 55555 terminated  nc localhost 8080
[22]  + 55554 terminated  nc localhost 8080
[21]  + 55553 terminated  nc localhost 8080
...

There should be no open connections in the background now.

```bash
ps auxw | grep "nc localhost 8080" | grep -v grep | wc -l
```

output:

```text
0
```

Wait for a few seconds so the metrics get updated. The value of stream_active_received metric should be 0 for all pods.

```bash
./check_metric.sh
```

output:

```text
The value of stream_active_received metric
------------------------------------------

Prometheus API:
{
...
        "metric": {
          "__name__": "stream_active_received",
        },
        "value": [
          1683013504.619, # timestamp
          "0" # value
...
}

Kubernetes custom metrics API:
{
...
      "metricName": "stream_active_received",
      "value": "0",
...
}

Wait for a few seconds so the autoscaler can catch up. The zilla deployment should be soon scaled down to 1 pod.

```bash
./check_hpa.sh
```

output:

```text
The status of horizontal pod autoscaling
----------------------------------------

HorizontalPodAutoscaler:
NAME    REFERENCE          TARGETS   MINPODS   MAXPODS   REPLICAS   AGE
zilla   Deployment/zilla   0/10      1         5         1          14m

Deployment:
NAME    READY   UP-TO-DATE   AVAILABLE   AGE
zilla   1/1     1            1           14m

Pods:
NAME                     READY   STATUS    RESTARTS   AGE
zilla-6db8d879f5-2wxgw   1/1     Running   0          14m
```

### Teardown

The `teardown.sh` script stops port forwarding, uninstalls Zilla, Prometheus, Prometheus Adapter and deletes the namespace.

```bash
./teardown.sh
```

output:

```text
+ pgrep kubectl
99999
99998
+ killall kubectl
+ helm uninstall zilla-kubernetes-prometheus-autoscale zilla-kubernetes-prometheus-autoscale-prometheus --namespace zilla-kubernetes-prometheus-autoscale
release "zilla-kubernetes-prometheus-autoscale" uninstalled
release "zilla-kubernetes-prometheus-autoscale-prometheus" uninstalled
+ kubectl delete namespace zilla-kubernetes-prometheus-autoscale
namespace "zilla-kubernetes-prometheus-autoscale" deleted
```
