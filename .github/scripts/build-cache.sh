#!/bin/bash
# Moves the Maven build cache between a runner and an OCI registry, so every
# build restores the entries the last build of its branch published.
#
#   install                               installs the pinned oras into $HOME/.local/bin and the PATH
#   restore <dir> <ref> <branch>...       unpacks the first of <ref>:<branch> that exists into <dir>;
#                                         restoring nothing is not an error, the build is just cold
#   pack    <dir> <out>                   packs <dir> into <out>/build-cache.tar.zst
#   push    <out> <ref> <branch>          publishes <out>/build-cache.tar.zst as <ref>:<branch>
#
# A branch becomes a registry tag by replacing characters a tag cannot hold with '-'.
# Credentials come from GITHUB_ACTOR and GITHUB_TOKEN when both are set, and the registry
# host is the first segment of <ref>. COMPRESS names the compressor (default zstd) and
# ORAS_FLAGS adds flags to every oras command, such as --plain-http for a local registry.

set -euo pipefail

ORAS_VERSION=1.2.3
ORAS_SHA256=b4efc97a91f471f323f193ea4b4d63d8ff443ca3aab514151a30751330852827
ARCHIVE=build-cache.tar.zst
ARCHIVE_TYPE=application/zstd
ARTIFACT_TYPE=application/vnd.aklivity.maven-build-cache.v1
COMPRESS="${COMPRESS:-zstd -T0 -3}"
read -r -a ORAS_FLAGS <<< "${ORAS_FLAGS:-}"

install_oras()
{
    local url="https://github.com/oras-project/oras/releases/download/v${ORAS_VERSION}/oras_${ORAS_VERSION}_linux_amd64.tar.gz"
    local work
    work="$(mktemp -d)"

    curl -fsSL --retry 3 -o "$work/oras.tar.gz" "$url"
    echo "$ORAS_SHA256  $work/oras.tar.gz" | sha256sum -c --quiet -
    mkdir -p "$HOME/.local/bin"
    tar -xzf "$work/oras.tar.gz" -C "$HOME/.local/bin" oras
    rm -rf "${work:?}"

    if [[ -n "${GITHUB_PATH:-}" ]]
    then
        echo "$HOME/.local/bin" >> "$GITHUB_PATH"
    fi
}

tag_of()
{
    echo "$1" | sed -E 's/[^A-Za-z0-9_.-]+/-/g' | cut -c1-128
}

login()
{
    local registry="${1%%/*}"

    if [[ -n "${GITHUB_ACTOR:-}" && -n "${GITHUB_TOKEN:-}" ]]
    then
        printf '%s' "$GITHUB_TOKEN" | oras login "$registry" -u "$GITHUB_ACTOR" --password-stdin > /dev/null
    fi
}

restore()
{
    local dir="$1" ref="${2,,}" branch tag work
    shift 2

    login "$ref" || echo "registry login failed, continuing without credentials"

    work="$(mktemp -d)"

    for branch in "$@"
    do
        tag="$(tag_of "$branch")"

        if oras pull "${ORAS_FLAGS[@]}" "$ref:$tag" --output "$work" > /dev/null 2>&1
        then
            mkdir -p "$(dirname "$dir")"
            tar -I "$COMPRESS" -xf "$work/$ARCHIVE" -C "$(dirname "$dir")"
            echo "Restored the build cache from $ref:$tag"
            break
        fi
    done

    if [[ ! -f "$work/$ARCHIVE" ]]
    then
        echo "No build cache found for: $*"
    fi

    rm -rf "${work:?}"
}

pack()
{
    local dir="${1%/}" out="$2"

    mkdir -p "$out"
    tar -I "$COMPRESS" -cf "$out/$ARCHIVE" -C "$(dirname "$dir")" "$(basename "$dir")"
    du -h "$out/$ARCHIVE" | cut -f1
}

push()
{
    local out="$1" ref="${2,,}" tag
    tag="$(tag_of "$3")"

    login "$ref"
    cd "$out"

    oras push "${ORAS_FLAGS[@]}" "$ref:$tag" "$ARCHIVE:$ARCHIVE_TYPE" \
        --artifact-type "$ARTIFACT_TYPE" \
        --annotation "org.opencontainers.image.source=https://github.com/${GITHUB_REPOSITORY:-aklivity/zilla}"
}

COMMAND="${1:?usage: build-cache.sh install|restore|pack|push ...}"
shift

case "$COMMAND" in
install) install_oras ;;
restore) restore "$@" ;;
pack) pack "$@" ;;
push) push "$@" ;;
*) echo "unknown command: $COMMAND" >&2; exit 2 ;;
esac
