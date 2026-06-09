#!/bin/sh
# Run Zilla examples locally the same way the CI `testing` job does
# (.github/workflows/build.yml): for each example,
#   docker compose up -d --wait  ->  ./.github/test.sh  ->  docker compose down.
#
# Usage:
#   ./.test-all.sh                       # run every example
#   ./.test-all.sh grpc.echo http.proxy  # run only the named examples
#   ZILLA_VERSION=latest ./.test-all.sh  # use the published image instead of a local build
#
# ZILLA_VERSION defaults to develop-SNAPSHOT to match CI, which tests against the
# image produced by the build job. Build it locally first with `./mvnw install`
# from the repo root, or set ZILLA_VERSION=latest for a quick smoke test against
# the published image.
set -u

export ZILLA_VERSION="${ZILLA_VERSION:-develop-SNAPSHOT}"

# Mirror the omit list in build.yml's setup-examples job.
SKIP="tcp.reflect ws.proxy ws.reflect http.echo grpc.kafka.proxy"

ROOT="$(cd "$(dirname "$0")" && pwd)"
CURRENT=""

contains()
{
  needle="$1"
  shift
  for item in $1
  do
    if [ "$item" = "$needle" ]
    then
      return 0
    fi
  done
  return 1
}

teardown()
{
  if [ -n "$CURRENT" ]
  then
    ( cd "$ROOT/$CURRENT" && docker compose down --remove-orphans -v >/dev/null 2>&1 )
    CURRENT=""
  fi
}

# Tear down the in-flight example if interrupted.
trap 'teardown; echo; echo "Interrupted."; exit 130' INT TERM

# Select examples: explicit args, else every subdir with a compose.yaml.
if [ "$#" -gt 0 ]
then
  EXAMPLES="$*"
else
  EXAMPLES=""
  for dir in "$ROOT"/*/
  do
    name="$(basename "$dir")"
    if [ -f "$ROOT/$name/compose.yaml" ]
    then
      EXAMPLES="$EXAMPLES $name"
    fi
  done
fi

SUMMARY=""
FAILED=0

for name in $EXAMPLES
do
  if contains "$name" "$SKIP"
  then
    SUMMARY="$SUMMARY\n  SKIP  $name (omitted in CI)"
    continue
  fi

  if [ ! -f "$ROOT/$name/.github/test.sh" ]
  then
    SUMMARY="$SUMMARY\n  SKIP  $name (no .github/test.sh)"
    continue
  fi

  echo "================================================================"
  echo "# $name   (ZILLA_VERSION=$ZILLA_VERSION)"
  echo "================================================================"

  CURRENT="$name"
  START="$(date +%s)"
  status="PASS"

  if ( cd "$ROOT/$name" && docker compose up -d --wait )
  then
    if ! ( cd "$ROOT/$name" && sh ./.github/test.sh )
    then
      status="FAIL"
    fi
  else
    echo "docker compose up failed"
    status="FAIL"
  fi

  teardown

  END="$(date +%s)"
  ELAPSED=$((END - START))

  if [ "$status" = "FAIL" ]
  then
    FAILED=$((FAILED + 1))
  fi
  SUMMARY="$SUMMARY\n  $status  $name (${ELAPSED}s)"
done

echo
echo "================================================================"
echo "# Summary"
echo "================================================================"
printf "%b\n" "$SUMMARY"

if [ "$FAILED" -gt 0 ]
then
  echo
  echo "$FAILED example(s) failed."
  exit 1
fi
