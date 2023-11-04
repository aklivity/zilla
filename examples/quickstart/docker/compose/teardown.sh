#!/bin/bash
set -e

NAMESPACE=zilla-quickstart
docker-compose -p $NAMESPACE down --remove-orphans
