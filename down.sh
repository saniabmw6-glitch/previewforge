#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
docker ps -aq --filter label=previewforge.preview=true | xargs -r docker rm -f
docker compose down
