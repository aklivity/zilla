#!/bin/bash
# Emits the derived tag facts for a release version as key=value lines,
# suitable for appending to $GITHUB_OUTPUT.
#
#   major   major version, empty unless <version> is x.y.z
#   minor   major.minor version, empty unless <version> is x.y.z
#   latest  docker "latest" tag, empty unless <version> is the highest release
#   highest true when <version> is the highest release, false otherwise
#
# A release is the highest release when no existing release tag sorts above it,
# so the newest released line keeps the latest marker until a higher line ships
# its first release. Deliberately derived from released versions rather than
# from the branch, so this stays correct once develop branches to support/N.x.

set -euo pipefail

VERSION="${1:?usage: release-tags.sh <version>}"

MAJOR=""
MINOR=""
LATEST=""
HIGHEST="false"

if [[ "$VERSION" =~ ^([0-9]+)\.([0-9]+)\.[0-9]+$ ]]; then
  MAJOR="${BASH_REMATCH[1]}"
  MINOR="${BASH_REMATCH[1]}.${BASH_REMATCH[2]}"

  RELEASED=$({ git tag --list | grep -E '^[0-9]+\.[0-9]+\.[0-9]+$' || true; echo "$VERSION"; } | sort -V -u | tail -n1)

  if [[ "$RELEASED" == "$VERSION" ]]; then
    LATEST="latest"
    HIGHEST="true"
  fi
fi

echo "major=$MAJOR"
echo "minor=$MINOR"
echo "latest=$LATEST"
echo "highest=$HIGHEST"
