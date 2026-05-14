#!/usr/bin/env bash
set -euo pipefail

DEPLOY_DIR="${DEPLOY_DIR:-/opt/warex-backend}"

cd "${DEPLOY_DIR}"
docker compose --env-file .env -f docker-compose.ec2.yml pull
docker compose --env-file .env -f docker-compose.ec2.yml up -d
docker image prune -f
