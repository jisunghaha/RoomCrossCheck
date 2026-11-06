#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
bash scripts/demo-login-setup.sh
docker info >/dev/null
bash scripts/build-backend.sh
docker compose --env-file .runtime/demo-login.env -f compose.yaml -f compose.demo-login.yaml up --build -d
python3 - <<'PY'
import json, time, urllib.request
from pathlib import Path
values = dict(line.split('=', 1) for line in Path('.runtime/demo-login.env').read_text().splitlines() if '=' in line)
port = int(values['ROOM_BACKEND_PORT'])
if not 1024 <= port <= 65535:
    raise SystemExit('ROOM_BACKEND_PORT는 1024~65535로 지정하세요.')
opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
for _ in range(100):
    try:
        with opener.open(f'http://127.0.0.1:{port}/api/auth/config', timeout=1) as response:
            if json.load(response).get('demoEnabled') is True:
                print('시연 서버 준비 완료. 다음 명령: bash scripts/demo-login-front.sh')
                break
    except OSError:
        pass
    time.sleep(0.5)
else:
    raise SystemExit('서버 준비를 확인하지 못했습니다. 실행 안내의 로그 확인 명령을 사용하세요.')
PY
