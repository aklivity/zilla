# tcp.echo.embedding

Listens on tcp port `12345` and echoes back whatever is sent to the server —
unless the message semantically matches one of a configured set of rejected
phrases, in which case the connection is closed instead. Matching is done by
comparing embedding vectors, not exact words, so a message can be rejected
even when it shares no vocabulary at all with the configured `reject`
phrases, as long as it means the same thing.

The moderator uses [`embedding-glove`](../../incubator/embedding-glove), an
OSS, non-vendor-locked `embeddings:` implementation that averages
[GloVe](https://nlp.stanford.edu/projects/glove/) word vectors locally — no
vendor API, no per-request network call, no ML runtime. The vectors download
automatically the first time `moderator0` is used and are cached after that,
so the very first message can take a while depending on network speed.

## Requirements

- nc
- docker compose

## Setup

To `start` the Docker Compose stack defined in the [compose.yaml](compose.yaml) file, use:

```bash
docker compose up -d
```

### Verify behavior

Connect with a plain TCP client:

```bash
nc localhost 12345
```

Send a message that manufactures suspense without delivering — it matches the
semantic pattern of the configured `reject` phrases, and the connection closes:

```text
> Something crazy just happened to me but honestly it's too wild to type out.
```

Nothing is echoed back; the connection ends there.

Reconnect and send the same "something crazy happened" opener, but one that
actually tells the story — no withholding, no match, echoed normally:

```text
> Something crazy just happened to me, a stray cat ran straight into my kitchen!
< Something crazy just happened to me, a stray cat ran straight into my kitchen!
```

Same pattern, different wording — still matches, because the *meaning* is the
same "I won't tell you" gatekeeping move, not the specific words:

```text
> I know a huge piece of gossip about the admin team but my lips are sealed.
```

Nothing is echoed back; the connection ends there.

## Configuration

See [etc/zilla.yaml](etc/zilla.yaml):

```yaml
embeddings:
  moderator0:
    type: glove

bindings:
  north_echo_server:
    type: echo
    kind: server
    options:
      embedding: moderator0
      reject:
        - "You will never believe what happened next."
        - "I have a massive secret but I absolutely cannot tell anyone here."
      threshold: 0.85
```

## Teardown

To remove any resources created by the Docker Compose stack, use:

```bash
docker compose down
```
