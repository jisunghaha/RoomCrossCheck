#!/usr/bin/env sh
set -eu

# Proxy and public CA are runtime inputs, never baked into the image.
set --
if [ -n "${JAVA_HTTPS_PROXY_HOST:-}" ]; then
  set -- "$@" "-Dhttps.proxyHost=$JAVA_HTTPS_PROXY_HOST" \
    "-Dhttps.proxyPort=${JAVA_HTTPS_PROXY_PORT:-8080}" \
    '-Dhttp.nonProxyHosts=localhost|127.*|[::1]'
fi
if [ -n "${JAVA_PROXY_CA_FILE:-}" ]; then
  [ -r "$JAVA_PROXY_CA_FILE" ] || { echo 'Configured Java proxy CA is unreadable.' >&2; exit 1; }
  trust_store=$(mktemp /tmp/room-java-cacerts.XXXXXX)
  cp "$JAVA_HOME/lib/security/cacerts" "$trust_store"
  if ! keytool -importcert -noprompt -alias room-runtime-proxy -file "$JAVA_PROXY_CA_FILE" \
    -keystore "$trust_store" -storepass changeit >/dev/null 2>&1; then
    echo 'Unable to add the configured public proxy CA to Java trust.' >&2
    exit 1
  fi
  set -- "$@" "-Djavax.net.ssl.trustStore=$trust_store" '-Djavax.net.ssl.trustStorePassword=changeit'
fi
exec java "$@" -jar /app/app.jar
