#!/usr/bin/env python3
"""Generate public runtime proxy/CA Compose settings; never serialize API keys."""
import json
import os
from pathlib import Path
import socket
from urllib.parse import urlparse


def settings(environ, resolve=socket.gethostbyname):
    backend = {"environment": {}, "volumes": [], "extra_hosts": []}
    proxy = environ.get("HTTPS_PROXY") or environ.get("https_proxy")
    if proxy:
        parsed = urlparse(proxy)
        if parsed.scheme != "http" or not parsed.hostname or parsed.username or parsed.password:
            raise ValueError("Java runtime requires an HTTP CONNECT proxy without URL credentials")
        host = parsed.hostname
        if host in {"localhost", "127.0.0.1", "::1"}:
            host = "host.docker.internal"
        else:
            backend["extra_hosts"].append(f"{host}:{resolve(host)}")
        backend["environment"].update(JAVA_HTTPS_PROXY_HOST=host, JAVA_HTTPS_PROXY_PORT=str(parsed.port or 80))
    ca = environ.get("JAVA_PROXY_CA_FILE") or environ.get("CODEX_PROXY_CERT")
    if ca:
        path = Path(ca).resolve()
        if not path.is_file():
            raise ValueError("Configured public Java proxy CA file is missing")
        backend["volumes"].append(f"{path}:/run/roomcrosscheck/proxy-ca.pem:ro")
        backend["environment"]["JAVA_PROXY_CA_FILE"] = "/run/roomcrosscheck/proxy-ca.pem"
    return {"services": {"backend": backend}}


if __name__ == "__main__":
    output = Path(__file__).resolve().parents[1] / ".runtime/compose.java-runtime.json"
    try:
        value = settings(os.environ)
    except (ValueError, OSError):
        raise SystemExit("Unable to prepare Java proxy/CA runtime settings; check proxy format, DNS and public CA file")
    output.parent.mkdir(exist_ok=True)
    output.write_text(json.dumps(value, indent=2) + "\n")
