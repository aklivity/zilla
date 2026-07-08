#!/usr/bin/env bash
# Fetch a single example folder from the aklivity/zilla examples/ directory
# without cloning the whole (large) monorepo.
#
# Usage:
#   ./fetch_example.sh <example.name> [target-dir]
#
# Example:
#   ./fetch_example.sh http.kafka.crud ./http.kafka.crud
#
# Requires: git (network access to github.com / codeload.github.com)

set -euo pipefail

EXAMPLE="${1:-}"
TARGET_DIR="${2:-./$EXAMPLE}"
REPO_URL="https://github.com/aklivity/zilla.git"
BRANCH="develop"

if [ -z "$EXAMPLE" ]; then
  echo "Usage: $0 <example.name> [target-dir]" >&2
  echo "See references/examples-catalog.md for the full list of example names." >&2
  exit 1
fi

WORKDIR="$(mktemp -d)"
trap 'rm -rf "$WORKDIR"' EXIT

echo "Fetching examples/$EXAMPLE from $REPO_URL@$BRANCH ..."
git clone --depth 1 --branch "$BRANCH" --filter=blob:none --sparse "$REPO_URL" "$WORKDIR/zilla" >/dev/null 2>&1
(
  cd "$WORKDIR/zilla"
  git sparse-checkout set "examples/$EXAMPLE"
)

if [ ! -d "$WORKDIR/zilla/examples/$EXAMPLE" ]; then
  echo "Error: examples/$EXAMPLE not found in the repo." >&2
  echo "Check the name against references/examples-catalog.md." >&2
  exit 1
fi

mkdir -p "$(dirname "$TARGET_DIR")" 2>/dev/null || true
cp -R "$WORKDIR/zilla/examples/$EXAMPLE" "$TARGET_DIR"

echo "Done. Example copied to: $TARGET_DIR"
echo
echo "Next steps:"
echo "  cd $TARGET_DIR"
echo "  cat README.md          # requirements + verify-behavior commands"
echo "  docker compose up -d   # start the stack"
echo "  docker compose down    # tear it down when finished"
