#!/bin/sh
set -x

# The verification itself runs inside the compose stack -- see the `verify`
# service in compose.yaml and etc/test/verify.sh. `verify` is profile-gated,
# so a plain `docker compose up` never starts it; `docker compose run`
# enables the profile of the service it targets. Depending on zilla's health
# is enough to gate the whole topology, since zilla already depends on both
# mock backends.
#
# Unlike examples/mcp.proxy, there is nothing to mint here: the `credentials`
# guard is `type: inline`, which accepts whatever token a caller presents
# and hands it back unchanged -- exactly what the pass-through assertions in
# verify.sh need, with no key material to generate up front.

exec docker compose run --rm verify
