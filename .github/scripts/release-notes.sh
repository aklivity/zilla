#!/bin/bash
# Prints the version-specific section of a generated CHANGELOG.md: the
# "## [<version>](...)" heading through to the line before the next version
# heading, with trailing blank lines removed. Prints nothing when the changelog
# has no section for <version>, leaving the fallback to the caller.

set -euo pipefail

VERSION="${1:?usage: release-notes.sh <version> [changelog]}"
CHANGELOG="${2:-CHANGELOG.md}"

if [[ -f "$CHANGELOG" ]]; then
  awk -v prefix="## [$VERSION]" '
    substr($0, 1, 4) == "## [" { if (found) exit; found = substr($0, 1, length(prefix)) == prefix }
    found { print }
  ' "$CHANGELOG" | sed -e :a -e '/^\n*$/{$d;N;};/\n$/ba'
fi
