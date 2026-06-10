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
