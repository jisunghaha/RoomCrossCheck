#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
python3 - <<'PY'
from pathlib import Path
from secrets import token_urlsafe, token_hex
runtime = Path('.runtime')
runtime.mkdir(exist_ok=True)
path = runtime / 'demo-login.env'
if not path.exists():
    values = {
        'COMPOSE_PROJECT_NAME': 'roomcrosscheck-auth-demo-' + token_hex(4),
        'ROOM_BACKEND_PORT': '8082',
        'ROOM_DB_PASSWORD': token_urlsafe(24),
        'ROOM_DB_ROOT_PASSWORD': token_urlsafe(24),
        'DEMO_MEMBER1_PASSWORD': token_urlsafe(18),
        'DEMO_MEMBER2_PASSWORD': token_urlsafe(18),
        'DEMO_ADMIN_PASSWORD': token_urlsafe(18),
    }
    with open(path, 'x', opener=lambda p, flags: __import__('os').open(p, flags, 0o600)) as target:
        target.write(''.join(f'{key}={value}\n' for key, value in values.items()))
path.chmod(0o600)
print('시연 환경 준비 완료: .runtime/demo-login.env (기존 설정은 보존합니다.)')
print('시연 아이디: member1 / member2 / admin. 비밀번호는 위 로컬 파일에서 확인하세요.')
PY
