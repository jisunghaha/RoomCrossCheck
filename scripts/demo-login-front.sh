#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
port=$(python3 - <<'PY'
from pathlib import Path
values = dict(line.split('=', 1) for line in Path('.runtime/demo-login.env').read_text().splitlines() if '=' in line)
port = int(values['ROOM_BACKEND_PORT'])
if not 1024 <= port <= 65535:
    raise SystemExit('ROOM_BACKEND_PORT를 확인하세요.')
print(port)
PY
)
VITE_DATA_MODE=local VITE_API_BASE_URL=/api DEV_API_TARGET="http://127.0.0.1:$port" npm run dev
