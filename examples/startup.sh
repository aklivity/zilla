#!/bin/bash
set -e

REPO=aklivity/zilla-examples
RELEASE_URL="https://github.com/$REPO/releases/download"
MAIN_URL="https://api.github.com/repos/$REPO/tarball"
VERSION=""
EXAMPLE_FOLDER=""
KAFKA_FOLDER="kafka.broker"
COMPOSE_FOLDER="compose"
HELM_FOLDER="k8s"
USE_K8S=false
USE_MAIN=false
START_KAFKA=true
AUTO_TEARDOWN=false
REMOTE_KAFKA=false
KAFKA_HOST=""
KAFKA_PORT=""
WORKDIR=$(pwd)

# helper functions
export USAGE="Usage:  ${CMD:=${0##*/}} [ EXAMPLE_FOLDER ] [ -h --kafka-host KAFKA_HOST ] [ -p --kafka-port KAFKA_PORT ] [ -d --workdir WORKDIR] [ -v --version VERSION] [ -k --k8s ] [ -m --use-main ] [ --no-kafka] [ --auto-teardown]"
exit2 () { printf >&2 "%s:  %s: '%s'\n%s\n" "$CMD" "$1" "$2" "$USAGE"; exit 2; }
check () { { [ "$1" != "$EOL" ] && [ "$1" != '--' ]; } || exit2 "missing argument" "$2"; }  # avoid infinite loop

# parse command-line options
set -- "$@" "${EOL:=$(printf '\1\3\3\7')}"  # end-of-list marker
while [ "$1" != "$EOL" ]; do
  opt="$1"; shift
  case "$opt" in

    #EDIT HERE: defined options
    -h | --kafka-host         ) check "$1" "$opt"; KAFKA_HOST="$1"; REMOTE_KAFKA=true; shift;;
    -p | --kafka-port         ) check "$1" "$opt"; KAFKA_PORT="$1"; shift;;
    -d | --workdir       ) check "$1" "$opt"; WORKDIR="$1"; shift;;
    -v | --version       ) check "$1" "$opt"; VERSION="$1"; shift;;
    -k | --k8s           ) USE_K8S=true;;
    -m | --use-main      ) USE_MAIN=true;;
         --no-kafka      ) START_KAFKA=false;;
         --auto-teardown ) AUTO_TEARDOWN=true;;
         --help          ) printf "%s\n" "$USAGE"; exit 0;;

    # process special cases
    --) while [ "$1" != "$EOL" ]; do set -- "$@" "$1"; shift; done;;   # parse remaining as positional
    --[!=]*=*) set -- "${opt%%=*}" "${opt#*=}" "$@";;                  # "--opt=arg"  ->  "--opt" "arg"
    -[A-Za-z0-9] | -*[!A-Za-z0-9]*) exit2 "invalid option" "$opt";;    # anything invalid like '-*'
    -?*) other="${opt#-?}"; set -- "${opt%$other}" "-${other}" "$@";;  # "-abc"  ->  "-a" "-bc"
    *) set -- "$@" "$opt";;                                            # positional, rotate to the end
  esac
done; shift

# pull the example folder from the end of the params and set defaults
EXAMPLE_FOLDER="$*"
[[ -z "$EXAMPLE_FOLDER" ]] && EXAMPLE_FOLDER="quickstart"
[[ -z "$VERSION" ]] && VERSION=$(curl -s https://api.github.com/repos/$REPO/releases/latest | grep -i "tag_name" | awk -F '"' '{print $4}')
[[ -z "$VERSION" ]] && USE_MAIN=true

! [[ -d "$WORKDIR" ]] && printf "Error: WORKDIR must be a valid directory." && exit2
if [[ -d "$WORKDIR" && ! -d "$WORKDIR/$EXAMPLE_FOLDER" ]]; then
    if [[ $USE_MAIN == true ]]; then
        printf "\n==== Downloading $MAIN_URL '*/$EXAMPLE_FOLDER/*' to $WORKDIR ====\n"
        wget -qO- $MAIN_URL | tar -xf - --strip=1 -C $WORKDIR "*/$EXAMPLE_FOLDER/*"
    else
        printf "\n==== Downloading $RELEASE_URL/$VERSION/$EXAMPLE_FOLDER.tar.gz to $WORKDIR ====\n"
        wget -qO- $RELEASE_URL/$VERSION/$EXAMPLE_FOLDER.tar.gz | tar -xf - -C $WORKDIR
    fi
fi

# don't start kafka if the example hasn't been reworked
if [[ ! -d "$WORKDIR/$EXAMPLE_FOLDER/$HELM_FOLDER" && ! -d "$WORKDIR/$EXAMPLE_FOLDER/$COMPOSE_FOLDER" ]]; then
    START_KAFKA=false
fi

# use helm if there isn't a compose implimentation, remove after adding to all examples
if [[ $USE_K8S == false && ! -d "$WORKDIR/$EXAMPLE_FOLDER/$COMPOSE_FOLDER" ]]; then
    USE_K8S=true
fi

KAKFA_TEARDOWN_SCRIPT=""
if [[ $REMOTE_KAFKA == true ]]; then
    printf "Connecting to remote Kafka at $KAFKA_HOST:$KAFKA_PORT"
elif [[ $START_KAFKA == true ]]; then

    if ! [[ -d "$WORKDIR/$KAFKA_FOLDER" ]]; then
        if [[ $USE_MAIN == true ]]; then
            printf "\n==== Downloading $MAIN_URL '*/$KAFKA_FOLDER/*' to $WORKDIR ====\n"
            wget -qO - $MAIN_URL | tar -xf - --strip=1 -C $WORKDIR "*/$KAFKA_FOLDER/*"
        else
            printf "\n==== Downloading $RELEASE_URL/$VERSION/$KAFKA_FOLDER.tar.gz to $WORKDIR ====\n"
            wget -qO- $RELEASE_URL/$VERSION/$KAFKA_FOLDER.tar.gz | tar -xf - -C $WORKDIR
        fi
    fi

    if [[ $USE_K8S == true ]]; then
        KAFKA_FOLDER="resource.kafka.helm"
        cd $WORKDIR/$KAFKA_FOLDER/
    else
        cd $WORKDIR/$KAFKA_FOLDER/$COMPOSE_FOLDER
    fi
    KAFKA_HOST="host.docker.internal"
    KAFKA_PORT=29092
    chmod u+x teardown.sh
    KAKFA_TEARDOWN_SCRIPT="$(pwd)/teardown.sh"
    printf "\n==== Starting Kafka Use this script to teardown: $KAKFA_TEARDOWN_SCRIPT ====\n"
    sh setup.sh
    printf "\n==== Kafka started at $KAFKA_HOST:$KAFKA_PORT ====\n"
fi
if [[ $REMOTE_KAFKA == true || $START_KAFKA == true ]]; then
    export KAFKA_HOST=$KAFKA_HOST
    export KAFKA_PORT=$KAFKA_PORT
fi

TEARDOWN_SCRIPT=""
if [[ $USE_K8S == false && -d "$WORKDIR/$EXAMPLE_FOLDER/$COMPOSE_FOLDER" ]]; then
    if ! [[ -x "$(command -v docker)" ]]; then
        printf "Docker is required to run this setup."
        exit
    fi
    if ! [[ -x "$(command -v docker-compose)" ]]; then
        printf "Docker Compose is required to run this setup."
        exit
    fi

    cd $WORKDIR/$EXAMPLE_FOLDER/$COMPOSE_FOLDER
    chmod u+x teardown.sh
    TEARDOWN_SCRIPT="$(pwd)/teardown.sh"
    printf "\n==== Starting Zilla $EXAMPLE_FOLDER with Compose. Use this script to teardown: $(pwd)/teardown.sh ====\n"
    sh setup.sh
fi

if [[ $USE_K8S == true ]]; then
    if ! [[ -x "$(command -v helm)" ]]; then
        printf "Helm is required to run this setup."
        exit
    fi
    if ! [[ -x "$(command -v kubectl)" ]]; then
        printf "Kubectl is required to run this setup."
        exit
    fi

    if [[ -d "$WORKDIR/$EXAMPLE_FOLDER/$HELM_FOLDER" ]]; then
        cd $WORKDIR/$EXAMPLE_FOLDER/$HELM_FOLDER
    else
        cd $WORKDIR/$EXAMPLE_FOLDER
    fi

    chmod u+x teardown.sh
    TEARDOWN_SCRIPT="$(pwd)/teardown.sh"
    printf "\n==== Starting Zilla $EXAMPLE_FOLDER with Helm. Use this script to teardown: $(pwd)/teardown.sh ====\n"
    sh setup.sh
fi

if ! [[ -z "$KAFKA_HOST" && -z "$KAFKA_PORT" ]]; then
    printf "\n\n==== Verify the Kafka topics created ====\n"
    echo "docker run --tty --rm confluentinc/cp-kafkacat:7.1.9 kafkacat -b $KAFKA_HOST:$KAFKA_PORT -L"

    printf "\n==== Start a topic consumer to listen for messages ====\n"
    echo "docker run --tty --rm confluentinc/cp-kafkacat:7.1.9 kafkacat -b $KAFKA_HOST:$KAFKA_PORT -C -f '%t [%p:%o] | %h | %k:%s\n' -t <topic_name>"
    printf "\n"
fi

printf "\n==== Check out the README to see how to use this example ==== \n$WORKDIR/$EXAMPLE_FOLDER/README.md\n$(head -n 4 $WORKDIR/$EXAMPLE_FOLDER/README.md | tail -n 3)\n"
printf "\n==== Finished, use the teardown script(s) to clean up ==== \n$TEARDOWN_SCRIPT\n$KAKFA_TEARDOWN_SCRIPT\n"

if [[ $AUTO_TEARDOWN == true ]]; then
    printf "\n==== Auto teardown ==== \n$TEARDOWN_SCRIPT\n$KAKFA_TEARDOWN_SCRIPT\n"
    ! [[ -z "$TEARDOWN_SCRIPT" ]] && $TEARDOWN_SCRIPT
    ! [[ -z "$KAKFA_TEARDOWN_SCRIPT" ]] && $KAKFA_TEARDOWN_SCRIPT
fi
