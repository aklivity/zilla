#!/bin/bash
BOLD='\033[1;97m'
END='\033[0m'

echo -e "${BOLD}The status of horizontal pod autoscaling${END}"
echo -e "${BOLD}----------------------------------------${END}\n"

echo -e "${BOLD}HorizontalPodAutoscaler:${END}"
kubectl get hpa --namespace zilla-kubernetes-prometheus-autoscale
echo

echo -e "${BOLD}Deployment:${END}"
kubectl get deployment zilla-kubernetes-prometheus-autoscale --namespace zilla-kubernetes-prometheus-autoscale
echo

echo -e "${BOLD}Pods:${END}"
kubectl get pods --namespace zilla-kubernetes-prometheus-autoscale --selector app.kubernetes.io/instance=zilla-kubernetes-prometheus-autoscale
