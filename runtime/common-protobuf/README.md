# common-protobuf

A format-native, provider-free streaming Protobuf library for the hot path: descriptor-based
decode and encode over Agrona `DirectBuffer`s, with protobuf ↔ JSON transcoding that composes with
[`common-json`](../common-json). Peer to `common-json`; reused by the `model-protobuf` converter.

This library owns the Protobuf side only. It validates against a native Protobuf descriptor model
and exposes its own format-specific API — it is not a repurposing of the JSON validator. When
converting protobuf ↔ JSON it decodes/encodes the wire with `common-protobuf` and drives the
`common-json` buffer-backed generator/parser for the JSON side. It takes no dependency on a shared
cross-format validator or event model.

## Descriptor model

`ProtobufSchema` is a compiled, immutable model — build one per `schemaId` and cache it. It is
**provider-free**: there is no dependency on `protobuf-java`. Construct the model with the public
builders (`ProtobufSchema.Builder`, `ProtobufMessage.Builder`, `ProtobufField.Builder`,
`ProtobufEnum.Builder`); a consumer such as `model-protobuf` populates field metadata from its own
`.proto` compilation.

```java
ProtobufSchema schema = StreamingProtobuf.schema()
    .message(ProtobufMessage.builder("Person")
        .field(ProtobufField.builder().number(1).name("name").type(ProtobufType.STRING).build())
        .field(ProtobufField.builder().number(2).name("id").type(ProtobufType.INT32).build())
        .build())
    .build();

ProtobufToJson toJson = StreamingProtobuf.protobufToJson(schema);
int jsonLength = toJson.convert("Person", wire, offset, length, out, 0);

JsonToProtobuf toProtobuf = StreamingProtobuf.jsonToProtobuf(schema);
int wireLength = toProtobuf.convert("Person", json, offset, length, out, 0);
```

A map `field` is modeled exactly as Protobuf represents it on the wire: a repeated reference to a
synthetic map-entry message (`mapEntry(true)`) with `key` as field 1 and `value` as field 2.

## Bounded-buffer contract

Protobuf fields are length-delimited and may arrive in any order, and a repeated field's elements
may be interleaved with other fields — which complicates a strictly forward-streaming decode under
a no-full-document-buffer goal, unlike JSON's clean forward streaming.

`common-protobuf` resolves this with a **documented bounded-buffer contract**: a converter operates
on a single, fully-buffered message. The engine delivers the reassembled payload, so:

- **Decode** is a single bounded pass over the message bytes. To emit JSON — where each member
  appears once with its array elements contiguous — fields are emitted in descriptor declaration
  order by re-scanning the message region once per field. The only bound is the size of the
  message handed in; nothing beyond it is buffered.
- **Encode** stages each length-delimited nested message in a per-depth scratch buffer (its length
  is unknown until its body is built, and JSON is forward-only), then splices it with its length
  into the parent. Staging is bounded by message nesting depth.

Neither direction buffers an unbounded document. Truncated or overlong varints, lengths that run
past the message, and wire types incompatible with the descriptor are rejected with a
`ProtobufException`.

## proto3 JSON mapping

| Protobuf | JSON |
| --- | --- |
| `int32`, `sint32`, `sfixed32` | number |
| `uint32`, `fixed32` | number (unsigned) |
| `int64`, `uint64`, `sint64`, `fixed64`, `sfixed64` | string (to survive JSON's 53-bit mantissa) |
| `float`, `double` | number; `"NaN"`, `"Infinity"`, `"-Infinity"` |
| `bool` | `true` / `false` |
| `string` | string |
| `bytes` | base64 string (standard, padded) |
| `enum` | value name, or its number when unknown |
| `message` | object |
| repeated | array (packed and unpacked scalars accepted on decode) |
| map | object (integral and bool keys rendered as strings) |
| `oneof` | only the set member appears |

Field names use the proto3 JSON name (lowerCamelCase) on output; both the JSON name and the
original proto field name are accepted on input. Unknown JSON members are dropped on encode;
unknown wire fields are skipped on decode.

### Well-known types

`google.protobuf` well-known types follow their proto3 JSON forms: `Timestamp` (RFC 3339 string),
`Duration` (seconds string with `s`), the scalar wrappers (`Int32Value`, `StringValue`, … → their
underlying value), `FieldMask` (comma-joined camelCase paths), `Struct` / `Value` / `ListValue`
(arbitrary JSON), and `Empty` (`{}`). `google.protobuf.Any` requires a type registry and is not yet
supported.

## Run performance benchmarks

Build the benchmark jar from this directory:

```bash
../../mvnw clean install -Pbench
```
