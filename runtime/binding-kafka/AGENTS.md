# Zilla binding-kafka — Agent Guide

Guidance scoped to `runtime/binding-kafka/`. General runtime/binding
conventions (factory pattern, decode strategy, buffer slots, etc.) live in
[../AGENTS.md](../AGENTS.md).

---

## Kafka local cache

Zilla fetches each Kafka topic partition once and stores it as memory-mapped
segment files local to the node. The cache is served to any number of
downstream clients without additional round-trips to Kafka.

- Segment files are mmap'd via `IoUtil` (Agrona)
- Segments must be explicitly `munmap`'d on rotation to avoid TLB exhaustion
  on long-running instances — call `IoUtil.unmap()` on the `MappedByteBuffer`
  when a segment is evicted
- Cache retention is controlled by AUTHORITATIVE timestamps (from Kafka broker)
  and optionally ADVISORY timestamps (from message headers)

---

## KafkaCacheModel — incremental key/value transforms

`KafkaCacheModel` wraps the engine's `ModelPipeline` for a cache entry's key or
value, driven once per DATA fragment via `transform(traceId, bindingId,
authorization, flags, data, index, limit, Output)` — `flags` are the caller's
own frame `FLAGS_INIT` / `FLAGS_FIN`, passed straight through. It reports back
via a reused `Result`: `UNDERFLOW` (more fragments expected, pipeline state
retained), `COMPLETE` (value finished this fragment, pipeline reset), or
`REJECTED` (validation failure, pipeline reset, caller aborts the entry). A
whole-value convenience overload (used by non-streaming callers) delegates
with both flags set. `KafkaCacheModel.NONE` is a plain passthrough.

An optional `ModelTransform` composes with the model's own pipeline via
`andThen`; `KafkaExtractTransform` is the one shipped implementation, one
instance per `extractKey` / `extractHeaders` config entry. It observes a field
at a configured path and copies its value into a `ModelEnvelope` under a
configured name — the field itself still flows through to the model's own
output untouched, so the stage is always identity. `extractKey` writes into a
`KafkaCacheKeyEnvelope` (`KafkaCachePartition` reads the override back off it
in place of the persisted key); `extractHeaders` writes into a
`KafkaCacheTrailerEnvelope` (read back as the entry's `trailers`).

## Write protocol: reserve, stream, finalize

Both the produce path (`KafkaCacheClientProduceFactory`) and the populate path
(`KafkaCacheServerFetchFactory`) write a cache entry in three calls —
`write*EntryStart` / `write*EntryContinue` / `write*Fin`/`write*Finish` — that
reserve a region up front and then either fill it in place or, if the real
content ends up smaller than the reservation, finalize a `length`/`paddingLen`
pair to describe the leftover. `KafkaCachePaddedKey`/`KafkaCachePaddedValue`
(the `paddedKey`/`paddedValue` fields on `KafkaCacheEntry`) both follow this
shape natively: `length` + the field's own bytes + `paddingLen` + padding
octets, with `paddingLen` relocated to sit right after the real (possibly
transformed) length once it's known — `commitKeyOverride` does this for a key,
`writeEntryContinue`/`writeProduceEntryContinue` do it for a value's COMPLETE
transition.

A value transform's output streams directly into the `paddedValue`
reservation as fragments arrive — plaintext never touches the log file when a
value model is configured. When a value model additionally composes an
`extractHeaders` stage, its `KafkaCacheTrailerEnvelope` must be claimed before
the first fragment is driven (fragments arrive well before headers are known
on the populate path), so `writeEntryStart` reserves a combined
headers-worst-case + trailers-sized block up front and reuses the
trailers-sized portion as the envelope's scratch space during the drive;
`writeEntryFinish` later writes the real (smaller) headers and trailers
contiguously over those same bytes and folds whatever remains into the
entry's own trailing `paddingLen`/`padding` fields. The produce path doesn't
need this reordering since it already knows its headers at `Start`.
