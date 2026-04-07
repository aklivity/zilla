# PRD: Metrics with Arbitrary Attributes

**Issue:** [aklivity/zilla#1636](https://github.com/aklivity/zilla/issues/1636)
**Author:** Akram Yakubov
**Date:** 2026-04-03
**Status:** Draft

---

## 1. Problem Statement

Zilla's telemetry system currently records metrics per binding with only two fixed dimensions: `namespace` and `binding`. There is no way for users to attach per-request attributes (e.g., HTTP method, path, response status) to metrics. This limits observability — operators cannot break down metrics like `http.duration` by method, route, or status code without this capability.

## 2. Goal

Allow users to declare arbitrary attributes on binding-level telemetry metrics, with values that are either literal strings or runtime expressions resolved from stream data. These attributes become additional dimensions/labels on exported metric data points.

## 3. Target Configuration Syntax

```yaml
bindings:
  http_server:
    type: http
    kind: server
    options:
      ...
    routes:
      ...
    telemetry:
      metrics:
        - http.duration
        - http.request.size
        - http.response.size
      attributes:
        method: ${http.request.method}
        path: ${http.request.path}
        status: ${http.response.status}
```

Attribute values can be:
- **Literal strings:** `service: "my-api"` — static label applied to all data points
- **Runtime expressions:** `method: ${http.request.method}` — resolved per-request from stream frame data (e.g., HTTP headers)

## 4. Functional Requirements

### 4.1 Configuration

| # | Requirement |
|---|-------------|
| F1 | The `telemetry` section of a binding config accepts an optional `attributes` object alongside the existing `metrics` array. |
| F2 | Each attribute is a key-value pair where the key is the attribute name and the value is a string (literal or `${expression}`). |
| F3 | Attribute names follow the existing pattern: `^[a-zA-Z]+[a-zA-Z0-9._-]*$`. |
| F4 | The JSON schema validates the new `attributes` property. |
| F5 | Bindings without `attributes` continue to work exactly as today (backward compatible). |

### 4.2 Runtime Expression Resolution

| # | Requirement |
|---|-------------|
| F6 | Expressions use the syntax `${<namespace>.<field>}` where the namespace corresponds to a binding type (e.g., `http`). |
| F7 | HTTP binding expressions resolve from HTTP pseudo-headers and headers in stream frames. |
| F8 | Initial supported HTTP expressions: |

| Expression | Source |
|---|---|
| `${http.request.method}` | `:method` pseudo-header from request `BeginFW` |
| `${http.request.path}` | `:path` pseudo-header from request `BeginFW` |
| `${http.response.status}` | `:status` pseudo-header from response `BeginFW` |

### 4.3 Metric Aggregation

| # | Requirement |
|---|-------------|
| F9 | Each unique combination of resolved attribute values produces a separate metric aggregation bucket. For example, `(GET, /items, 200)` and `(POST, /items, 201)` are separate data points for the same metric. |
| F10 | A cardinality limit caps the number of unique attribute combinations per `(binding, metric)` pair to prevent unbounded memory growth. When the limit is reached, new combinations fall into an overflow bucket. |

### 4.4 Export

| # | Requirement |
|---|-------------|
| F11 | OTLP exporter includes resolved attributes as additional key-value pairs in each data point's `attributes` array. |
| F12 | Prometheus exporter includes resolved attributes as additional labels on metric lines. |
| F13 | Existing fixed attributes (`namespace`, `binding`, `service.name`) continue to be exported alongside the new arbitrary attributes. |

**Example Prometheus output:**
```
http_server_duration_bucket{le="100",namespace="example",binding="http_server",method="GET",path="/items",status="200"} 42
```

**Example OTLP data point attributes:**
```json
{
  "attributes": [
    {"key": "namespace", "value": {"stringValue": "example"}},
    {"key": "binding", "value": {"stringValue": "http_server"}},
    {"key": "method", "value": {"stringValue": "GET"}},
    {"key": "path", "value": {"stringValue": "/items"}},
    {"key": "status", "value": {"stringValue": "200"}}
  ]
}
```

## 5. Non-Functional Requirements

| # | Requirement |
|---|-------------|
| NF1 | Zero overhead for bindings that do not declare attributes (no behavioral change to existing metric pipeline). |
| NF2 | Attribute resolution happens on the I/O thread with no synchronization — same as existing metric recording. |
| NF3 | Shared memory layout changes must be backward compatible or versioned to avoid breaking rolling upgrades. |

## 6. Architecture Summary

### Current State
```
NamespaceConfig
├── TelemetryConfig (attributes, metrics, exporters)
└── BindingConfig[]
    └── TelemetryRefConfig
        └── MetricRefConfig[] (metric name references only)
```

Metrics stored in shared memory as flat records keyed by `(bindingId, metricId)`. One slot per binding+metric per worker thread. Exporters read via `Collector` interface and produce `MetricRecord` objects with `namespace()`, `binding()`, `metric()`.

### Proposed State
```
NamespaceConfig
├── TelemetryConfig (attributes, metrics, exporters)
└── BindingConfig[]
    └── TelemetryRefConfig
        ├── MetricRefConfig[] (metric name references)
        └── AttributeConfig[] (name + value/expression)   <-- NEW
```

Metrics stored keyed by `(bindingId, metricId, attributesId)`. Each unique attribute combination is registered as a label via the existing `LabelManager` — serialized as a deterministic string (e.g., `"method=GET,path=/items,status=200"`) and assigned a stable integer ID. `LabelManager.lookupLabel(id)` provides reverse lookup for export. Each attribute combination slot has identical storage cost to any existing metric slot. `MetricContext` handlers extract attribute values from stream frames and call `supplyLabelId` to obtain the `attributesId`.

### Affected Components

| Component | Change |
|---|---|
| `TelemetryRefConfig` / Builder / Adapter | Add `attributes` field, parsing, serialization |
| `engine.schema.json` | Add `attributes` property to binding telemetry |
| `MetricsLayout` / `ScalarsLayout` / `HistogramsLayout` | Extend record key with `attributesId` |
| `LabelManager` (existing) | Register attribute combination strings, reverse lookup for export |
| `Collector` interface | Return triples instead of pairs; add 3-arg overloads |
| `MetricContext` interface | New `supply` overload accepting attributes + registry |
| HTTP metric handlers (`HttpDurationMetricContext`, etc.) | Extract HTTP headers, resolve expressions |
| `MetricRecord` / `ScalarRecord` / `HistogramRecord` | Add `attributes()` method |
| `MetricsReader` | Pass `attributesId` through to records |
| `OtlpMetricsSerializer` | Include resolved attributes in data point export |
| `PrometheusMetricsPrinter` | Include resolved attributes as Prometheus labels |
| `NamespaceRegistry` / `EngineManager` | Wire attribute config to metric handlers |

## 7. Phased Delivery

| Phase | Scope | Deliverable |
|---|---|---|
| **Phase 1** | Config parsing | `attributes` field in `TelemetryRefConfig`, JSON adapter, schema, tests. Can be merged independently. |
| **Phase 2** | Storage + resolution | `AttributeSetRegistry`, shared memory layout extension, `MetricContext` overload, HTTP handler attribute extraction. |
| **Phase 3** | Export | `MetricRecord.attributes()`, OTLP and Prometheus exporter updates, integration tests. |

## 8. Open Questions

| # | Question | Impact |
|---|---|---|
| Q1 | Should we support expressions for non-HTTP bindings (e.g., Kafka topic, partition) in the initial release or defer? | Scope of Phase 2 |
| Q2 | Should `${http.request.path}` resolve the full path or support pattern-based grouping (e.g., `/items/{id}` -> `/items/*`)? Path grouping significantly reduces cardinality. | Cardinality, usability |
| Q3 | Should shared memory capacity be increased or made configurable to accommodate the additional metric slots created by attribute combinations? | Shared memory sizing |

## 9. Success Criteria

- Users can configure `attributes` on binding telemetry and see per-attribute-combination metric breakdowns in Prometheus and OTLP-compatible backends (e.g., Grafana, Datadog)
- Existing configurations without attributes work identically to before
- No measurable performance regression for bindings without attributes
