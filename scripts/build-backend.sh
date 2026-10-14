#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p backend/.mvn-cache
setup_dir=$(mktemp -d)
trap 'rm -rf "$setup_dir"' EXIT
mounts=(-v "$PWD/backend:/app")
if [[ -n ${HTTPS_PROXY:-} ]]; then
  python - "$setup_dir/settings.xml" <<'PY'
import os, sys
from urllib.parse import urlparse
from xml.sax.saxutils import escape
p = urlparse(os.environ['HTTPS_PROXY'])
if p.username or p.password:
    raise SystemExit('Authenticated proxy requires a local Maven settings file.')
proxies = ''.join(f'<proxy><id>{kind}</id><active>true</active><protocol>{kind}</protocol><host>{escape(p.hostname)}</host><port>{p.port}</port></proxy>' for kind in ('http', 'https'))
open(sys.argv[1], 'w').write('<settings xmlns="http://maven.apache.org/SETTINGS/1.2.0"><proxies>'+proxies+'</proxies></settings>')
PY
  mounts+=(-v "$setup_dir/settings.xml:/tmp/maven-settings.xml:ro")
fi
if [[ -n ${HTTPS_PROXY:-} && -n ${CODEX_PROXY_CERT:-} && -f ${CODEX_PROXY_CERT:-} ]]; then
  mounts+=(-v "$CODEX_PROXY_CERT:/tmp/proxy.crt:ro")
  docker run --rm --network host -u "$(id -u):$(id -g)" "${mounts[@]}" -w /app maven:3.9-eclipse-temurin-21 sh -c 'cp "$JAVA_HOME/lib/security/cacerts" /tmp/room-cacerts && keytool -importcert -noprompt -alias room-cloud-proxy -file /tmp/proxy.crt -keystore /tmp/room-cacerts -storepass changeit >/dev/null && MAVEN_OPTS="-Djavax.net.ssl.trustStore=/tmp/room-cacerts -Djavax.net.ssl.trustStorePassword=changeit" mvn -B -Dmaven.repo.local=/app/.mvn-cache -s /tmp/maven-settings.xml test package'
else
  if [[ -n ${HTTPS_PROXY:-} ]]; then
    docker run --rm --network host -u "$(id -u):$(id -g)" "${mounts[@]}" -w /app maven:3.9-eclipse-temurin-21 mvn -B -Dmaven.repo.local=/app/.mvn-cache -s /tmp/maven-settings.xml test package
  else
    docker run --rm --network host -u "$(id -u):$(id -g)" "${mounts[@]}" -w /app maven:3.9-eclipse-temurin-21 mvn -B -Dmaven.repo.local=/app/.mvn-cache test package
  fi
fi
