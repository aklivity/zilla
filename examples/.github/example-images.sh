#!/bin/sh
# Prints "<image> <tarball>" for every third-party image the given examples'
# compose files reference. The tarball name is derived from the image reference
# so the cache-images job (which saves) and each testing job (which loads)
# agree on it. Images built by this workflow, or parameterised with a variable,
# are skipped.
#
# usage: example-images.sh <compose.yaml>...

grep -hE '^[[:space:]]*image:' "$@" \
  | sed -E 's/^[[:space:]]*image:[[:space:]]*//' \
  | tr -d "\"'" \
  | sed -E 's/[[:space:]]*#.*$//; s/[[:space:]]+$//' \
  | grep -v '\$' \
  | grep -v 'ghcr.io/aklivity/zilla' \
  | sort -u \
  | while read -r image; do
      echo "$image $(echo "$image" | tr '/:@' '___').tar"
    done
