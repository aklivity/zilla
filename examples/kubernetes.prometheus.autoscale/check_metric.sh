#!/bin/bash
BOLD='\033[1;97m'
END='\033[0m'

echo -e "${BOLD}The value of stream_active_received metric${END}"
echo -e "${BOLD}------------------------------------------${END}\n"

echo -e "${BOLD}Prometheus API:${END}"
curl -s http://localhost:9090/api/v1/query\?query\=stream_active_received | jq
echo

echo -e "${BOLD}Kubernetes custom metrics API:${END}"
kubectl get --raw '/apis/custom.metrics.k8s.io/v1beta1/namespaces/zilla-kubernetes-prometheus-autoscale/pod/*/stream_active_received' | jq
