# 운영 배포 (VM 1대 + Docker Compose)

클라우드 VM 한 대에서 다음 세 컨테이너를 실행한다. 로컬 개발용 루트 `compose.yaml`과 `.env`는 사용하지 않는다.

| 서비스 | 역할 | 외부 공개 |
|---|---|---|
| `web` | Caddy. React 빌드 파일 제공, `/api/*`를 백엔드로 전달, HTTPS 인증서 자동 발급 | 80, 443 |
| `backend` | Spring Boot (`prod` 프로필) | 없음 |
| `db` | MySQL 8.4 | 없음 |

브라우저는 한 주소에서 화면과 `/api`를 모두 받는다. 세션 쿠키와 CSRF 쿠키(`SameSite=Lax`)가 별도 CORS 설정 없이 동작하는 이유이다.

## 1. 서버 준비

- Ubuntu 22.04/24.04, 메모리 1GB 이상. 1GB면 서버에서 백엔드를 빌드할 때 스왑을 2GB 정도 추가한다.
- 방화벽(보안 그룹)에서 22(SSH), 80, 443/TCP와 443/UDP만 연다. 3306과 8080은 열지 않는다.
- [Docker Engine 공식 설치 문서](https://docs.docker.com/engine/install/ubuntu/)로 Docker와 Compose 플러그인을 설치한다.

```sh
# 메모리 1GB 서버일 때만
sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile
sudo mkswap /swapfile && sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
```

## 2. 첫 배포

```sh
git clone <저장소 주소> RoomCrossCheck && cd RoomCrossCheck
git switch <배포할 브랜치>
cp deploy/.env.example deploy/.env && chmod 600 deploy/.env
# deploy/.env에서 ROOM_DB_PASSWORD, ROOM_DB_ROOT_PASSWORD를 채운다
./deploy/deploy.sh
```

`SITE_ADDRESS`가 비어 있으면 `http://<서버 IP>`로 접속한다. 이 단계에서는 HTTPS가 아니라서 쿠키에 Secure가 붙지 않는다. 확인용으로만 쓴다.

## 3. 도메인과 HTTPS

1. 도메인의 A 레코드를 서버 IP로 지정한다.
2. `deploy/.env`에 `SITE_ADDRESS=room.example.com`을 넣는다.
3. `./deploy/deploy.sh`를 다시 실행한다. Caddy가 Let's Encrypt 인증서를 발급하고 HTTP를 HTTPS로 전환한다.

발급에 실패하면 `docker compose -f deploy/compose.yaml logs web`에서 DNS 전파와 80/443 포트 개방 여부를 확인한다.

구글 OAuth를 연결할 때는 Google Cloud Console에 `https://<도메인>` 기반 리디렉션 URI를 등록한다. 서버 쪽 OAuth 로그인 흐름은 아직 구현되지 않았다([회원 인증 명세](../docs/api/AUTH.md)).

## 4. 업데이트 배포

```sh
./deploy/deploy.sh
```

`git pull --ff-only` 후 이미지를 다시 빌드한다. 운영 이미지 빌드는 테스트를 생략하므로 배포 전에 로컬에서 `npm test`와 `./scripts/build-backend.sh`(백엔드 테스트 포함)를 통과시킨다.

## 5. 운영 명령

```sh
docker compose -f deploy/compose.yaml ps
docker compose -f deploy/compose.yaml logs -f backend
docker compose -f deploy/compose.yaml down        # 볼륨(DB·업로드·인증서)은 유지
```

DB 백업과 복원:

```sh
docker compose -f deploy/compose.yaml exec -T db sh -c 'mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --single-transaction roomcrosscheck' > backup-$(date +%F).sql
docker compose -f deploy/compose.yaml exec -T db sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" roomcrosscheck' < backup-YYYY-MM-DD.sql
```

## 주의

- 스키마는 `ddl-auto: update`로 관리한다. 컬럼 추가는 자동 반영되지만 삭제나 이름 변경은 반영되지 않는다. 엔티티를 바꾸는 배포 전에는 백업한다.
- 광고 이미지는 `room_uploads` 볼륨에 저장된다. 서버를 옮길 때 DB와 함께 이 볼륨도 옮긴다.
- `deploy/.env`의 API 키와 비밀번호는 서버에만 둔다. `VITE_` 값은 브라우저 번들에 공개된다.
