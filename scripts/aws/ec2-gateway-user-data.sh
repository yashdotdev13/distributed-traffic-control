#!/bin/bash
set -euo pipefail

###############################################################################
# Distributed Traffic Control - EC2 Bootstrap
#
# Required environment variables:
#   NODE_ID
#   AWS_REGION
#   ECR_REGISTRY
#   ECR_REPOSITORY
#   REDIS_HOST
#
# Optional:
#   REDIS_PORT
#   LEASE_CAPACITY
#   CONTAINER_NAME
#   CONTAINER_PORT
###############################################################################

: "${NODE_ID:?NODE_ID must be provided}"
: "${AWS_REGION:?AWS_REGION must be provided}"
: "${ECR_REGISTRY:?ECR_REGISTRY must be provided}"
: "${ECR_REPOSITORY:?ECR_REPOSITORY must be provided}"
: "${REDIS_HOST:?REDIS_HOST must be provided}"

REDIS_PORT="${REDIS_PORT:-6379}"
LEASE_CAPACITY="${LEASE_CAPACITY:-10}"
CONTAINER_NAME="${CONTAINER_NAME:-distributed-traffic-control-gateway}"
CONTAINER_PORT="${CONTAINER_PORT:-8081}"

IMAGE="${ECR_REGISTRY}/${ECR_REPOSITORY}:latest"

echo "============================================================"
echo "Distributed Traffic Control - EC2 Bootstrap"
echo "============================================================"
echo "NODE_ID:        ${NODE_ID}"
echo "AWS_REGION:     ${AWS_REGION}"
echo "ECR_REGISTRY:   ${ECR_REGISTRY}"
echo "ECR_REPOSITORY: ${ECR_REPOSITORY}"
echo "REDIS_HOST:     ${REDIS_HOST}"
echo "REDIS_PORT:     ${REDIS_PORT}"
echo "LEASE_CAPACITY: ${LEASE_CAPACITY}"
echo "CONTAINER_NAME: ${CONTAINER_NAME}"
echo "CONTAINER_PORT: ${CONTAINER_PORT}"
echo "IMAGE:          ${IMAGE}"
echo "============================================================"

echo "[1/6] Starting SSM Agent..."

systemctl enable amazon-ssm-agent
systemctl start amazon-ssm-agent

echo "[2/6] Installing Docker..."

dnf install -y docker

systemctl enable docker
systemctl start docker

echo "[3/6] Authenticating with Amazon ECR..."

aws ecr get-login-password \
  --region "${AWS_REGION}" |
  docker login \
    --username AWS \
    --password-stdin "${ECR_REGISTRY}"

echo "[4/6] Pulling application image..."

docker pull "${IMAGE}"

echo "[5/6] Preparing application container..."

if docker container inspect "${CONTAINER_NAME}" >/dev/null 2>&1; then
    echo "Existing container found. Removing it..."
    docker rm -f "${CONTAINER_NAME}"
fi

echo "[6/6] Starting application..."

docker run -d \
  --name "${CONTAINER_NAME}" \
  --restart unless-stopped \
  -p "${CONTAINER_PORT}:${CONTAINER_PORT}" \
  -e REDIS_HOST="${REDIS_HOST}" \
  -e REDIS_PORT="${REDIS_PORT}" \
  -e NODE_ID="${NODE_ID}" \
  -e LEASE_CAPACITY="${LEASE_CAPACITY}" \
  "${IMAGE}"

echo "============================================================"
echo "Bootstrap completed successfully."
echo "Container: ${CONTAINER_NAME}"
echo "Node ID:   ${NODE_ID}"
echo "============================================================"

docker ps \
  --filter "name=${CONTAINER_NAME}"
