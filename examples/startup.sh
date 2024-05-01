#!/bin/bash
set -e

REPO=aklivity/zilla-examples
RELEASE_URL="https://github.com/$REPO/releases/download"
MAIN_URL="https://api.github.com/repos/$REPO/tarball"
VERSION=""
ZILLA_VERSION=""
EXAMPLE_FOLDER=""
KAFKA_FOLDER=""
COMPOSE_FOLDER="docker/compose"
HELM_FOLDER="k8s/helm"
USE_HELM=false
USE_MAIN=false
INIT_KAFKA=true
AUTO_TEARDOWN=false
REMOTE_KAFKA=false
KAFKA_BROKER="kafka"
KAFKA_BOOTSTRAP_SERVER=""
WORKDIR=$(pwd)

# help text
read -r -d '' HELP_TEXT <<-EOF || :
Usage: ${CMD:=${0##*/}} [-hm][-k KAFKA_BOOTSTRAP_SERVER][-d WORKDIR][-v ZILLA_VERSION][-e EX_VERSION][--no-kafka-init][--redpanda] example.name

Operand:
    example.name          The name of the example to use                                 [default: quickstart][string]

Options:
    -d | --workdir        Sets the directory used to download and run the example                             [string]
    -e | --ex-version     Sets the examples version to download                              [default: latest][string]
    -h | --use-helm       Use the helm install, if available, instead of compose                             [boolean]
    -k | --kafka-server   Sets the Kafka Boostrap Server to use                                               [string]
    -m | --use-main       Download the head of the main branch                                               [boolean]
    -v | --zilla-version  Sets the zilla version to use                                      [default: latest][string]
         --auto-teardown  Executes the teardown script immediately after setup                               [boolean]
         --no-kafka-init  The script wont try to bootstrap the kafka broker                                  [boolean]
         --redpanda       Makes the included kafka broker and scripts use Redpanda                           [boolean]
         --help           Print help                                                                         [boolean]

Report a bug: github.com/$REPO/issues/new
EOF
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
    -k | --kafka-server  ) check "$1" "$opt"; KAFKA_BOOTSTRAP_SERVER="$1"; REMOTE_KAFKA=true; shift;;
    -e | --ex-version    ) check "$1" "$opt"; VERSION="$1"; shift;;
    -v | --zilla-version ) check "$1" "$opt"; ZILLA_VERSION="$1"; shift;;
    -h | --use-helm      ) USE_HELM=true;;
    -m | --use-main      ) USE_MAIN=true;;
         --no-kafka-init ) INIT_KAFKA=false;;
         --auto-teardown ) AUTO_TEARDOWN=true;;
         --redpanda      ) KAFKA_BROKER="redpanda";;
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
operand=$*
EXAMPLE_FOLDER=${operand//\//}
[[ -z "$EXAMPLE_FOLDER" ]] && EXAMPLE_FOLDER="quickstart"
[[ -z "$VERSION" ]] && VERSION=$(curl -s https://api.github.com/repos/$REPO/releases/latest | grep -i "tag_name" | awk -F '"' '{print $4}')
[[ -z "$VERSION" ]] && USE_MAIN=true

echo "==== Starting Zilla Example $EXAMPLE_FOLDER at $WORKDIR ===="

! [[ -d "$WORKDIR" ]] && echo "Error: WORKDIR must be a valid directory." && exit2
if [[ -d "$WORKDIR" && ! -d "$WORKDIR/$EXAMPLE_FOLDER" ]]; then
    if [[ $USE_MAIN == true ]]; then
        echo "==== Downloading $MAIN_URL '*/$EXAMPLE_FOLDER/*' to $WORKDIR ===="
        wget -qO- $MAIN_URL | tar -xf - --strip=1 -C "$WORKDIR" "*/$EXAMPLE_FOLDER/*"
    else
        echo "==== Downloading $RELEASE_URL/$VERSION/$EXAMPLE_FOLDER.tar.gz to $WORKDIR ===="
        wget -qO- "$RELEASE_URL"/"$VERSION"/"$EXAMPLE_FOLDER.tar.gz" | tar -xf - -C "$WORKDIR"
    fi
fi

# don't start kafka if the example hasn't been reworked
if [[ ! -d "$WORKDIR/$EXAMPLE_FOLDER/$HELM_FOLDER" && ! -d "$WORKDIR/$EXAMPLE_FOLDER/$COMPOSE_FOLDER" ]]; then
    INIT_KAFKA=false
fi

# use compose if there isn't a helm implimentation
if [[ $USE_HELM == true && ! -d "$WORKDIR/$EXAMPLE_FOLDER/$HELM_FOLDER" ]]; then
    echo "==== This example only supports Compose currently ==="
    USE_HELM=false
fi

# use helm if there isn't a compose implimentation
if [[ $USE_HELM == false && ! -d "$WORKDIR/$EXAMPLE_FOLDER/$COMPOSE_FOLDER" ]]; then
    echo "==== This example only supports Helm currently ==="
    USE_HELM=true
fi

# force use of kafka if helm is being used
if [[ $USE_HELM == true && $KAFKA_BROKER != "kafka" ]]; then
    echo "**** Helm examples only support the Kafka broker currently, switching broker to Kafka ****"
    KAFKA_BROKER="kafka"
fi

KAKFA_TEARDOWN_SCRIPT=""
KAFKA_FOLDER="$KAFKA_BROKER.broker"
if [[ $REMOTE_KAFKA == true ]]; then
    echo "Connecting to remote Kafka at $KAFKA_BOOTSTRAP_SERVER"
elif [[ $INIT_KAFKA == true ]]; then

    if ! [[ -d "$WORKDIR/$KAFKA_FOLDER" ]]; then
        if [[ $USE_MAIN == true ]]; then
            echo "==== Downloading $MAIN_URL '*/$KAFKA_FOLDER/*' to $WORKDIR ===="
            wget -qO - $MAIN_URL | tar -xf - --strip=1 -C "$WORKDIR" "*/$KAFKA_FOLDER/*"
        else
            echo "==== Downloading $RELEASE_URL/$VERSION/$KAFKA_FOLDER.tar.gz to $WORKDIR ===="
            wget -qO- "$RELEASE_URL"/"$VERSION"/"$KAFKA_FOLDER.tar.gz" | tar -xf - -C "$WORKDIR"
        fi
    fi

    export NAMESPACE="zilla-${KAFKA_FOLDER//./-}"
    if [[ $USE_HELM == true ]]; then
        cd "$WORKDIR"/"$KAFKA_FOLDER"/"$HELM_FOLDER"
        KAFKA_BOOTSTRAP_SERVER="$KAFKA_BROKER.$NAMESPACE.svc.cluster.local:9092"
    else
        cd "$WORKDIR"/"$KAFKA_FOLDER"/"$COMPOSE_FOLDER"
        KAFKA_BOOTSTRAP_SERVER="host.docker.internal:9092"
    fi
    chmod u+x teardown.sh
    KAKFA_TEARDOWN_SCRIPT="NAMESPACE=$NAMESPACE $(pwd)/teardown.sh"
    printf "\n\n"
    echo "==== Starting Kafka. Use this script to teardown ===="
    printf '%s\n' "$KAKFA_TEARDOWN_SCRIPT"
    sh setup.sh
    echo "Kafka started at $KAFKA_BOOTSTRAP_SERVER"
fi

export ZILLA_VERSION=$ZILLA_VERSION
export NAMESPACE="zilla-${EXAMPLE_FOLDER//./-}"
export REMOTE_KAFKA=$REMOTE_KAFKA
export INIT_KAFKA=$INIT_KAFKA
export KAFKA_BROKER=$KAFKA_BROKER
export KAFKA_BOOTSTRAP_SERVER=$KAFKA_BOOTSTRAP_SERVER

TEARDOWN_SCRIPT=""
if [[ $USE_HELM == false && -d "$WORKDIR/$EXAMPLE_FOLDER/$COMPOSE_FOLDER" ]]; then
    if ! [[ -x "$(command -v docker)" ]]; then
        echo "Docker is required to run this setup."
        exit
    fi
    if ! [[ -x "$(command -v docker-compose)" ]]; then
        echo "Docker Compose is required to run this setup."
        exit
    fi

    cd "$WORKDIR"/"$EXAMPLE_FOLDER"/"$COMPOSE_FOLDER"
    chmod u+x teardown.sh
    TEARDOWN_SCRIPT="NAMESPACE=$NAMESPACE $(pwd)/teardown.sh"
    printf "\n\n"
    echo "==== Starting Zilla $EXAMPLE_FOLDER with Compose. Use this script to teardown ===="
    printf '%s\n' "$TEARDOWN_SCRIPT"
    sh setup.sh
fi

if [[ $USE_HELM == true ]]; then
    if ! [[ -x "$(command -v helm)" ]]; then
        echo "Helm is required to run this setup."
        exit
    fi
    if ! [[ -x "$(command -v kubectl)" ]]; then
        echo "Kubectl is required to run this setup."
        exit
    fi

    if [[ -d "$WORKDIR/$EXAMPLE_FOLDER/$HELM_FOLDER" ]]; then
        cd "$WORKDIR"/"$EXAMPLE_FOLDER"/"$HELM_FOLDER"
    else
        cd "$WORKDIR"/"$EXAMPLE_FOLDER"
    fi

    chmod u+x teardown.sh
    TEARDOWN_SCRIPT="NAMESPACE=$NAMESPACE $(pwd)/teardown.sh"
    printf "\n\n"
    echo "==== Starting Zilla $EXAMPLE_FOLDER with Helm. Use this script to teardown ===="
    printf '%s\n' "$TEARDOWN_SCRIPT"
    sh setup.sh
fi

if [[ $REMOTE_KAFKA == false ]]; then
    printf "\n\n"
    echo "==== Verify the Kafka topics created ===="
    echo "docker run --tty --rm confluentinc/cp-kafkacat:7.1.9 kafkacat -b host.docker.internal:9092 -L"
    printf "\n\n"
    echo "==== Start a topic consumer to listen for messages ===="
    KCAT_FORMAT="'%t [%p:%o] | %h | %k:%s\n'"
    echo "docker run --tty --rm confluentinc/cp-kafkacat:7.1.9 kafkacat -b host.docker.internal:9092 -C -f $KCAT_FORMAT -t <topic_name>"
fi

printf "\n\n"
echo "==== Check out the README to see how to use this example ==== "
echo "cd $WORKDIR/$EXAMPLE_FOLDER"
echo "cat README.md"
head -n 4 "$WORKDIR"/"$EXAMPLE_FOLDER"/README.md | tail -n 3

printf "\n\n"
echo "==== Finished, use the teardown script(s) to clean up ===="
printf '%s\n' "$TEARDOWN_SCRIPT" "$KAKFA_TEARDOWN_SCRIPT"

if [[ $AUTO_TEARDOWN == true ]]; then
    printf "\n\n"
    echo "==== Auto teardown ===="
    printf '%s\n' "$TEARDOWN_SCRIPT" "$KAKFA_TEARDOWN_SCRIPT"
    [[ -n "$TEARDOWN_SCRIPT" ]] && bash -c "$TEARDOWN_SCRIPT"
    [[ -n "$KAKFA_TEARDOWN_SCRIPT" ]] && bash -c "$KAKFA_TEARDOWN_SCRIPT"
fi
printf "\n"
