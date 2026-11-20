#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
python_bin=$(command -v python3 || command -v python || true)
if [[ -z "$python_bin" ]]; then
  echo "Python 3가 필요합니다. 설치 후 다시 실행해 주세요." >&2
  exit 1
fi
"$python_bin" - <<'PYENV'
from pathlib import Path
from secrets import token_urlsafe
p = Path('.env')
lines = p.read_text().splitlines() if p.exists() else []
for key in ('ROOM_DB_PASSWORD', 'ROOM_DB_ROOT_PASSWORD'):
    found = False
    for i, line in enumerate(lines):
        name, separator, value = line.partition('=')
        if separator and name.strip() == key:
            found = True
            if not value.strip():
                lines[i] = key + '=' + token_urlsafe(24)
            break
    if not found:
        lines.append(key + '=' + token_urlsafe(24))
p.write_text('\n'.join(lines) + '\n')
p.chmod(0o600)
PYENV
./scripts/build-backend.sh
"$python_bin" scripts/prepare-java-runtime.py
docker compose -f compose.yaml -f .runtime/compose.java-runtime.json up --build -d
