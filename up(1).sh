#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
[ -f .env ] || { echo "Copy .env.example to .env and fill it in first."; exit 1; }
docker build -t previewforge-agent:latest jenkins/agent
docker compose up -d --build
echo "Jenkins: http://localhost:8080  (user: admin, password from .env)"
