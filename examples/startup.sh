#!/bin/sh
set -e

REPO=aklivity/zilla-examples
RELEASE_URL="https://github.com/$REPO/releases/download"
MAIN_URL="https://api.github.com/repos/$REPO/tarball"
VERSION=""
ZILLA_VERSION="${ZILLA_VERSION:-latest}"
ZILLA_PULL_POLICY="${ZILLA_PULL_POLICY:-always}"
EXAMPLE_FOLDER=""
USE_MAIN=false
AUTO_TEARDOWN=false
WORKDIR=$(pwd)

# help text
HELP_TEXT=$(cat <<EOF
Usage: ${CMD:=${0##*/}} [-m][-d WORKDIR][-v ZILLA_VERSION][-e EX_VERSION] example.name

Operand:
    example.name          The name of the example to use                                 [default: quickstart][string]

Options:
    -d | --workdir        Sets the directory used to download and run the example                             [string]
    -e | --ex-version     Sets the examples version to download                              [default: latest][string]
    -m | --use-main       Download the head of the main branch                                               [boolean]
    -v | --zilla-version  Sets the zilla version to use                                      [default: latest][string]
         --auto-teardown  Executes the teardown script immediately after setup                               [boolean]
         --help           Print help                                                                         [boolean]

Report a bug: github.com/aklivity/zilla/issues/new/choose
EOF
)
export USAGE="$HELP_TEXT"
exit2 () { printf >&2 "%s:  %s: '%s'\n%s\n" "$CMD" "$1" "$2" "$USAGE"; exit 2; }
check () { { [ "$1" != "$EOL" ] && [ "$1" != '--' ]; } || exit2 "missing argument" "$2"; } # avoid infinite loop

# parse command-line options
set -- "$@" "${EOL:=$(printf '\1\3\3\7')}"  # end-of-list marker
while [ "$1" != "$EOL" ]; do
  opt="$1"; shift
  case "$opt" in

    #defined options
    -d | --workdir       ) check "$1" "$opt"; WORKDIR="$1"; shift;;
    -e | --ex-version    ) check "$1" "$opt"; VERSION="$1"; shift;;
    -v | --zilla-version ) check "$1" "$opt"; ZILLA_VERSION="$1"; shift;;
    -m | --use-main      ) USE_MAIN=true;;
         --auto-teardown ) AUTO_TEARDOWN=true;;
         --help          ) printf "%s\n" "$USAGE"; exit 0;;

    # process special cases
    --) while [ "$1" != "$EOL" ]; do set -- "$@" "$1"; shift; done;;   # parse remaining as positional
    --[!=]*=*) set -- "${opt%%=*}" "${opt#*=}" "$@";;                  # "--opt=arg"  ->  "--opt" "arg"
    -[A-Za-z0-9] | -*[!A-Za-z0-9]*) exit2 "invalid option" "$opt";;    # anything invalid like '-*'
    -?*) other="${opt#-?}"; set -- "${opt%"$other"}" "-${other}" "$@";;  # "-abc"  ->  "-a" "-bc"
    *) set -- "$@" "$opt";;                                            # positional, rotate to the end
  esac
done; shift

# check ability to run Zilla with docker
! [ -x "$(command -v docker)" ] && echo "WARN: Docker is required to run this setup."
! [ -x "$(command -v docker compose)" ] && echo "WARN: Docker Compose is required to run this setup."

# pull the example folder from the end of the params and set defaults
operand=$*
EXAMPLE_FOLDER=$(echo "$operand" | sed 's/\///g')
[ -z "$EXAMPLE_FOLDER" ] && EXAMPLE_FOLDER="quickstart"

# check all of the repos for the correct example to run
RELEASES_JSON=$(curl -s https://api.github.com/repos/$REPO/releases/latest)
if [ $(echo "$RELEASES_JSON" | grep "name" | grep -Li "$EXAMPLE_FOLDER") ]; then
    REPO="aklivity/zilla-docs"
    echo "no example"
    RELEASES_JSON=$(curl -s https://api.github.com/repos/$REPO/releases/latest)
    if [ $(echo "$RELEASES_JSON" | grep "name" | grep -Li "$EXAMPLE_FOLDER") ]; then
        REPO="aklivity/zilla-demos"
        echo "no docs"
        RELEASES_JSON=$(curl -s https://api.github.com/repos/$REPO/releases/latest)
        if [ $(echo "$RELEASES_JSON" | grep "name" | grep -Li "$EXAMPLE_FOLDER") ]; then
            echo "Unable to find the $EXAMPLE_FOLDER example to run."
            echo "no demo"
        fi
    fi
fi

[ -z "$VERSION" ] && VERSION=$(echo "$RELEASES_JSON" | grep -i "tag_name" | awk -F '"' '{print $4}')
[ -z "$VERSION" ] && USE_MAIN=true

echo "==== Starting Zilla Example $EXAMPLE_FOLDER at $WORKDIR ===="

! [ -d "$WORKDIR" ] && echo "Error: WORKDIR must be a valid directory." && exit2
if [ -d "$WORKDIR" ] && ! [ -d "$WORKDIR/$EXAMPLE_FOLDER" ]; then
    if [ $USE_MAIN = true ]; then
        echo "==== Downloading $MAIN_URL '*/$EXAMPLE_FOLDER/*' to $WORKDIR ===="
        wget -qO- $MAIN_URL | tar -xf - --strip=1 -C "$WORKDIR" "*/$EXAMPLE_FOLDER/*"
    else
        echo "==== Downloading $RELEASE_URL/$VERSION/$EXAMPLE_FOLDER.tar.gz to $WORKDIR ===="
        wget -qO- "$RELEASE_URL"/"$VERSION"/"$EXAMPLE_FOLDER.tar.gz" | tar -xf - -C "$WORKDIR"
    fi
fi

export ZILLA_VERSION="$ZILLA_VERSION"
export ZILLA_PULL_POLICY="$ZILLA_PULL_POLICY"
export EXAMPLE_DIR="$WORKDIR/$EXAMPLE_FOLDER"

TEARDOWN_SCRIPT=""
cd "$WORKDIR"/"$EXAMPLE_FOLDER"

chmod u+x teardown.sh
TEARDOWN_SCRIPT="$(pwd)/teardown.sh"
printf "\n\n"
echo "==== Starting Zilla $EXAMPLE_FOLDER. Use this script to teardown ===="
printf '%s\n' "$TEARDOWN_SCRIPT"
sh setup.sh

printf "\n\n"
echo "==== Check out the README to see how to use this example ==== "
echo "cd $WORKDIR/$EXAMPLE_FOLDER"
echo "cat README.md"
head -n 4 "$WORKDIR"/"$EXAMPLE_FOLDER"/README.md | tail -n 3

printf "\n\n"
echo "==== Finished, use the teardown script(s) to clean up ===="
printf '%s\n' "$TEARDOWN_SCRIPT"

if [ $AUTO_TEARDOWN = true ]; then
    printf "\n\n"
    echo "==== Auto teardown ===="
    printf '%s\n' "$TEARDOWN_SCRIPT"
    [ -n "$TEARDOWN_SCRIPT" ] && bash -c "$TEARDOWN_SCRIPT"
fi
printf "\n"
