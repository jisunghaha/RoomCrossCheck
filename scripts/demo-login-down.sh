#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
docker compose --env-file .runtime/demo-login.env -f compose.yaml -f compose.demo-login.yaml down
