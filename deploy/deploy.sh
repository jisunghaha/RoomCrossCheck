#!/usr/bin/env bash
# 운영 서버에서 현재 브랜치를 최신으로 받고 이미지를 다시 빌드해 재시작한다.
# DB·업로드·인증서 볼륨은 유지한다. 처음 실행하기 전에 deploy/.env를 채운다.
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ ! -f deploy/.env ]]; then
  echo "deploy/.env가 없습니다. deploy/.env.example을 복사해 값을 채워 주세요." >&2
  exit 1
fi
git pull --ff-only
docker compose -f deploy/compose.yaml up -d --build --remove-orphans
docker image prune -f >/dev/null
docker compose -f deploy/compose.yaml ps
