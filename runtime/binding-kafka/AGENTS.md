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

## KafkaPipeline — whole-message composition

`KafkaPipeline` sits one layer above the engine's `ModelPipeline`. It owns the
key's model pipeline and the value's model pipeline, drives each in turn, and
translates their per-field output into `KafkaEvent` — a lane selector
(`SWITCH_KEY` / `SWITCH_HEADERS` / `SWITCH_VALUE`) plus one shared `FIELD`.
`KafkaTransform` stages compose with `andThen` and append to any lane at the
moment they have something to append; `KafkaExtractTransform` is one stage per
`extractKey` / `extractHeaders` config entry.

Two rules the terminal `KafkaSink` depends on:

- A lane switch selects the destination of the **single** `FIELD` that follows
  it. A field with no switch ahead of it is one the traversal merely surfaced
  and nothing is written for it.
- The pipeline's opening announcement of the lane it is traversing reaches the
  stages but **not** the terminal. Without that, `extractKey` — whose origin
  and target are both the key lane — cannot be told apart from the key's own
  fields flowing past.

The key lane is the one place content is not written the instant it is found:
its destination is the key itself, still being appended by the key's own model
when the match arrives, and `KafkaCacheFile` only grows forward. The extracted
key waits in a single slot in `KafkaCachePartition.KafkaEntrySink` until the
key region is closed. Headers stream with no buffering at all.

Extracted headers land in field-encounter order, not `extractHeaders` config
order — a direct consequence of the streaming design.
