#!/bin/sh
set -x

# The verification itself runs inside the compose stack -- see the `verify`
# service in compose.yaml and etc/test/verify.sh. Kept here, and invoked the
# same way as before, so CI's per-example step does not need to change.
#
# Why it moved: every assertion used to be its own `docker compose run` from the
# host, ~48 of them, each starting a fresh container that re-ran `npm install`
# over the same bind-mounted client directory before saying anything. That is
# tens of seconds of overhead spent on nothing, plus a cold install per client
# service. In one container the clients are plain node processes and the install
# happens once. It also means the script resolves zilla and the mocks by service
# name on the compose network instead of through published localhost ports.
#
# `verify` is profile-gated, so a plain `docker compose up` never starts it;
# `docker compose run` enables the profile of the service it targets. Depending
# on zilla's health is enough to gate the whole topology, since zilla already
# depends on every other component.
#
# Minting stays here rather than moving into verify.sh: jwt-cli signs with
# private.pem and is a container of its own, so doing it inside verify would
# mean reaching back out to the docker daemon to start one. Three tokens, once,
# is cheaper than teaching that container to sign.

# Mint JWTs for the authn_jwt guard, one per scope combination under test.
# `scope` is a jwt guard `roles` claim: a space-separated list, matched
# against the roles each `guarded:` route requires.
encode_jwt() {
  _scope=$1
  if [ -n "$_scope" ]; then
    docker compose run --rm jwt-cli encode \
        --alg "RS256" --kid "example" \
        --iss "https://auth.example.com" --aud "https://api.example.com" \
        --exp=+1d --no-iat \
        --payload "scope=$_scope" \
        --secret @/private.pem | tr -d '\r\n'
  fi
}

JWT_URLELICIT=$(encode_jwt "urlelicit:authorize")
JWT_PARTIAL=$(encode_jwt "github:tools petstore:tools kafka_sr:tools kafka_connect:tools")
JWT_FULL=$(encode_jwt "urlelicit:authorize github:tools github:pr:write petstore:tools pets:write kafka_sr:tools kafka_sr:write kafka_connect:tools kafka_connect:admin kafka:tools kafka:write kafka:admin kafka:acls")

exec docker compose run --rm \
    -e JWT_URLELICIT="$JWT_URLELICIT" \
    -e JWT_PARTIAL="$JWT_PARTIAL" \
    -e JWT_FULL="$JWT_FULL" \
    verify
