# tcp.echo.embedding

Listens on tcp port `12345` and echoes back whatever is sent to the server —
unless the message semantically matches one of a configured set of rejected
phrases, in which case the connection is closed instead. Matching is done by
comparing embedding vectors, not exact words, so a message can be rejected
even when it shares no vocabulary at all with the configured `reject`
phrases, as long as it means the same thing.

The moderation itself is implemented by
[`model-vector`](../../incubator/model-vector), a generic `model:` that
rejects a value whose embedding is similar enough to a configured list of
reject phrases — `echo` just references it like any binding-agnostic model,
the same way a binding might reference `json` or `avro`. The embedding
vectors come from [`embedding-openai`](../../runtime/embedding-openai), which
calls a `/v1/embeddings` endpoint, OpenAI's by default. This example sets its
`endpoint` option to a local
[text-embeddings-inference](https://github.com/huggingface/text-embeddings-inference)
(`tei`) server hosting `sentence-transformers/all-MiniLM-L6-v2` — no vendor
account or API key. `tei` downloads the model the first time it starts, so the
first `docker compose up` can take a while depending on network speed; the
model is cached in a Docker volume (see [compose.yaml](compose.yaml)) that
survives `docker compose down`/`up`, and `docker compose down -v` clears it.
The `cpu-1.9` image is x86_64 only; set `TEI_VERSION=cpu-arm64-1.9` on an arm64
host.

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
    type: openai
    options:
      model: sentence-transformers/all-MiniLM-L6-v2
      endpoint: http://tei:80/v1/embeddings
      credentials:
        api-key: unused

bindings:
  north_echo_server:
    type: echo
    kind: server
    options:
      value:
        model: vector
        embedding: moderator0
        reject:
          - "You will never believe what happened next."
          - "I have a massive secret but I absolutely cannot tell anyone here."
        threshold: 0.185
```

`tei` ignores the `model` and `credentials` values, which the `openai`
embedding type requires.
Cosine similarity between a whole message and a short reject phrase is low for
this model, so `threshold` is far lower than it would be for averaged word
vectors: the accepted message above scores about `0.15` against the closest
phrase and the rejected ones about `0.22` and `0.40`, so `0.185` sits between
them with a narrow margin. Tune it, and add more reject phrases, for your own
messages.

## Teardown

To remove any resources created by the Docker Compose stack, use:

```bash
docker compose down
```
