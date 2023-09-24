#!/bin/bash
set -e
if [ -x "$(command -v docker)" ]; then
    docker-compose down --remove-orphans
    docker-compose build
    docker-compose up -d
else
    echo "Docker is required to run this setup."
fi
