#!/usr/bin/env bash
# One-time preparation of a fresh Ubuntu 24.04 VPS for Sauti production deploys.
# Run as root after creating the non-root deploy user and authorizing its SSH key:
#   bash bootstrap-vps.sh [deploy-user]
set -euo pipefail

deploy_user="${1:-deploy}"
swap_size_gb="${SWAP_SIZE_GB:-4}"

if [[ "${EUID}" -ne 0 ]]; then
  echo "Run this script as root." >&2
  exit 1
fi

if ! id "${deploy_user}" >/dev/null 2>&1; then
  echo "User ${deploy_user} does not exist. Create it first." >&2
  exit 1
fi

export DEBIAN_FRONTEND=noninteractive

echo "==> Installing base packages"
apt-get update
apt-get install -y ca-certificates curl git gnupg ufw python3

if ! command -v docker >/dev/null 2>&1; then
  echo "==> Installing Docker Engine from Docker's apt repository"
  install -m 0755 -d /etc/apt/keyrings
  curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
  chmod a+r /etc/apt/keyrings/docker.asc
  . /etc/os-release
  echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu ${VERSION_CODENAME} stable" \
    > /etc/apt/sources.list.d/docker.list
  apt-get update
  apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
fi
systemctl enable --now docker

echo "==> Allowing ${deploy_user} to run Docker"
usermod -aG docker "${deploy_user}"

echo "==> Preparing /opt/sauti"
install -d -o "${deploy_user}" -g "${deploy_user}" /opt/sauti

# The deploy builds the backend and dashboard images on this host, so give the
# build headroom on small servers.
if ! swapon --show --noheadings | grep -q .; then
  echo "==> Creating ${swap_size_gb} GB swap file"
  fallocate -l "${swap_size_gb}G" /swapfile
  chmod 600 /swapfile
  mkswap /swapfile
  swapon /swapfile
  grep -q '^/swapfile ' /etc/fstab || echo '/swapfile none swap sw 0 0' >> /etc/fstab
fi

echo "==> Configuring firewall (SSH, HTTP, HTTPS)"
ufw default deny incoming
ufw default allow outgoing
ufw allow 22/tcp
ufw allow 80/tcp
ufw allow 443/tcp
ufw allow 443/udp
ufw --force enable

echo
echo "Done. Next steps:"
echo "  1. Put production secrets in /opt/sauti/.env.production (chmod 600, owned by ${deploy_user})."
echo "  2. Point the sauti.uk and admin.sauti.uk A records at this server."
echo "  3. Update the VPS_HOST, VPS_USER, and VPS_SSH_KEY GitHub secrets, then push to main."
