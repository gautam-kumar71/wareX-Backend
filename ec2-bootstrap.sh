#!/usr/bin/env bash
set -euo pipefail

if [[ "${EUID}" -ne 0 ]]; then
  echo "Run this script as root or with sudo." >&2
  exit 1
fi

dnf -y update || yum -y update
dnf -y install docker git || yum -y install docker git

systemctl enable --now docker
usermod -aG docker ec2-user || true

mkdir -p /usr/local/lib/docker/cli-plugins
curl -SL "https://github.com/docker/compose/releases/download/v2.39.4/docker-compose-linux-x86_64" -o /usr/local/lib/docker/cli-plugins/docker-compose
chmod +x /usr/local/lib/docker/cli-plugins/docker-compose

mkdir -p /opt/warex-backend/docker/mysql
chown -R ec2-user:ec2-user /opt/warex-backend
