#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
npm_config_cache="${npm_config_cache:-/tmp/room-npm-cache}" npm ci
