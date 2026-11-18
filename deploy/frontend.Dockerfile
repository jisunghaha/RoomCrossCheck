# 운영 배포용 프론트엔드 이미지: 빌드한 React 정적 파일을 Caddy가 제공하고 /api는 백엔드로 전달한다.
# 빌드 컨텍스트는 저장소 루트이다. npm workspace의 루트 package-lock.json을 함께 사용해야 하기 때문이다.
FROM node:22-alpine AS build
WORKDIR /src
COPY package.json package-lock.json ./
COPY frontend/package.json frontend/package.json
RUN npm ci --workspace frontend --include-workspace-root=false
COPY frontend frontend
# VITE_ 값은 브라우저 번들에 그대로 들어가므로 공개 가능한 값만 전달한다.
ARG VITE_DATA_MODE=listing-api
ARG VITE_API_BASE_URL=/api
ENV VITE_DATA_MODE=$VITE_DATA_MODE VITE_API_BASE_URL=$VITE_API_BASE_URL
RUN npm --workspace frontend run build

FROM caddy:2.10-alpine
COPY deploy/Caddyfile /etc/caddy/Caddyfile
COPY --from=build /src/frontend/dist /srv
