#!/bin/sh
set -x

# The verification itself runs inside the compose stack -- see the `verify`
# service in compose.yaml and etc/test/verify.py. `verify` is profile-gated,
# so a plain `docker compose up` never starts it; `docker compose run`
# enables the profile of the service it targets. Depending on zilla's health
# is enough to gate the whole topology, since zilla already depends on every
# mock backend -- `.github/.env.test`'s `COMPOSE_FILE` merges in
# `.github/compose.mock.yaml`, which stands the mocks up and points zilla at
# them instead of the real OpenAI, Anthropic and Gemini APIs `etc/zilla.yaml`
# targets by default, so this runs without real API keys or network access to
# any provider.
#
# Unlike examples/mcp.proxy, there is nothing to mint here: the `credentials`
# guard is `type: inline`, which accepts whatever token a caller presents
# and hands it back unchanged -- exactly what the pass-through assertions in
# verify.py need, with no key material to generate up front.

exec docker compose --env-file .github/.env.test run --rm verify
