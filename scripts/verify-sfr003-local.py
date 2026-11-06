#!/usr/bin/env python3
"""Isolated MySQL + current Spring jar smoke test. Defaults to a local stub.

--live-smoke --free-tier-confirmed enables ONE recorded, synthetic live sample.

Requires Docker, Java 21 and backend/target/backend-0.0.1-SNAPSHOT.jar.
Uses a temporary MySQL container and generated, in-memory credentials.
"""
import json
import os
from pathlib import Path
import secrets
import shutil
import socket
import subprocess
import sys
import tempfile
import threading
import time
import urllib.error
import urllib.request
from urllib.parse import urlparse
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

ROOT = Path(__file__).resolve().parents[1]
EXTRACTED = {"rawText": "가상 광고", "address": None, "depositWon": None,
             "monthlyRentWon": 123456, "maintenanceFeeWon": 0, "areaSqm": 20, "floor": -20}
stub_calls = 0


class Stub(BaseHTTPRequestHandler):
    def log_message(self, *args):
        pass

    def do_POST(self):
        global stub_calls
        assert self.path == "/v1beta/models/local-stub:generateContent"
        assert self.headers.get("x-goog-api-key") == "local-stub-not-a-real-key"
        json.loads(self.rfile.read(int(self.headers["Content-Length"])))
        stub_calls += 1
        body = json.dumps({"candidates": [{"content": {"parts": [{"text": json.dumps(EXTRACTED)}]}}]}).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.end_headers()
        self.wfile.write(body)


def main():
    live = "--live-smoke" in sys.argv
    if live and "--free-tier-confirmed" not in sys.argv:
        raise SystemExit("Live smoke requires explicit --free-tier-confirmed")
    live_record_path = ROOT / "docs/sfr003/smoke-006-result.json"
    if live and live_record_path.exists():
        raise SystemExit("This live sample already has a record; no repeat API call allowed")
    jar = ROOT / "backend/target/backend-0.0.1-SNAPSHOT.jar"
    assert jar.exists(), "Build the current backend first"
    task_env = os.environ.copy()
    for name in ("DOCKER_HOST", "DOCKER_CONTEXT", "DOCKER_TLS", "DOCKER_TLS_VERIFY", "DOCKER_CERT_PATH"):
        task_env.pop(name, None)
    task_env.update(MYSQL_DATABASE="roomcrosscheck", MYSQL_USER="room_app",
                    MYSQL_PASSWORD=secrets.token_urlsafe(24), MYSQL_ROOT_PASSWORD=secrets.token_urlsafe(24))
    docker = ["docker", "--host=unix:///var/run/docker.sock"]
    db_name = "sfr003-check-" + secrets.token_hex(5)
    backend = None
    trust_dir = None
    db_started = False
    stub = ThreadingHTTPServer(("127.0.0.1", 0), Stub)
    threading.Thread(target=stub.serve_forever, daemon=True).start()
    # Use a local opener so proxy settings cannot redirect localhost traffic.
    local = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    with socket.socket() as port_socket:
        port_socket.bind(("127.0.0.1", 0))
        backend_port = port_socket.getsockname()[1]
    base = f"http://127.0.0.1:{backend_port}/api"

    def run(args):
        return subprocess.check_output(docker + args, env=task_env, text=True, stderr=subprocess.DEVNULL).strip()

    def api(path, method="GET", value=None, headers=None, encoded=None):
        body = encoded if encoded is not None else (json.dumps(value).encode() if value is not None else None)
        req = urllib.request.Request(base + path, data=body, method=method,
                                     headers=headers or {"Content-Type": "application/json"})
        try:
            with local.open(req, timeout=70 if path.endswith("/extract") else 10) as response:
                text = response.read()
                return response.status, json.loads(text) if text else None
        except urllib.error.HTTPError as error:
            return error.code, None

    def start_backend(enabled=True, real=False):
        nonlocal trust_dir
        java_env = task_env.copy()
        java_env.update(DB_URL=f"jdbc:mysql://127.0.0.1:{db_port}/roomcrosscheck", DB_USER="room_app",
                        DB_PASSWORD=task_env["MYSQL_PASSWORD"], SERVER_PORT=str(backend_port),
                        SERVER_ADDRESS="127.0.0.1", GEMINI_ENABLED=str(enabled).lower(),
                        GEMINI_API_KEY="local-stub-not-a-real-key", GEMINI_MODEL="local-stub",
                        GEMINI_BASE_URL=f"http://127.0.0.1:{stub.server_port}/v1beta")
        java_args = ["java"]
        if real:
            if not os.environ.get("GEMINI_API_KEY"):
                raise RuntimeError("Injected Gemini key is missing")
            java_env.update(GEMINI_API_KEY=os.environ["GEMINI_API_KEY"], GEMINI_MODEL="gemini-3.5-flash-lite",
                            GEMINI_BASE_URL="https://generativelanguage.googleapis.com/v1beta")
            # Java HttpClient does not automatically read HTTPS_PROXY or Python CA vars.
            proxy = urlparse(os.environ.get("HTTPS_PROXY", ""))
            if proxy.hostname:
                java_args += [f"-Dhttps.proxyHost={proxy.hostname}", f"-Dhttps.proxyPort={proxy.port or 8080}",
                              "-Dhttp.nonProxyHosts=localhost|127.*"]
            ca = os.environ.get("CODEX_PROXY_CERT")
            if ca and Path(ca).is_file():
                trust_dir = tempfile.mkdtemp(prefix="sfr003-java-trust-")
                java_home = Path(shutil.which("java")).resolve().parents[1]
                trust_store = Path(trust_dir) / "cacerts"
                shutil.copyfile(java_home / "lib/security/cacerts", trust_store)
                subprocess.run(["keytool", "-importcert", "-noprompt", "-alias", "sfr003-proxy",
                                "-file", ca, "-keystore", str(trust_store), "-storepass", "changeit"],
                               check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
                java_args += [f"-Djavax.net.ssl.trustStore={trust_store}", "-Djavax.net.ssl.trustStorePassword=changeit"]
        process = subprocess.Popen(java_args + ["-jar", str(jar)], env=java_env,
                                   stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        for _ in range(90):
            if process.poll() is not None:
                raise RuntimeError("Local Spring Boot failed to start")
            try:
                if api("/listings")[0] == 200:
                    return process
            except (OSError, urllib.error.URLError):
                pass
            time.sleep(1)
        process.terminate()
        process.wait(timeout=15)
        raise RuntimeError("Local Spring Boot startup timed out")

    def stop_backend(process):
        process.terminate()
        process.wait(timeout=20)

    try:
        run(["run", "-d", "--pull=never", "--name", db_name, "-p", "127.0.0.1::3306",
             "-e", "MYSQL_DATABASE", "-e", "MYSQL_USER", "-e", "MYSQL_PASSWORD", "-e", "MYSQL_ROOT_PASSWORD", "mysql:8.4"])
        db_started = True
        db_port = run(["port", db_name, "3306/tcp"]).split(":")[-1]
        for _ in range(90):
            try:
                run(["exec", db_name, "mysqladmin", "ping", "--silent"])
                break
            except subprocess.CalledProcessError:
                time.sleep(1)
        backend = start_backend()
        boundary = "sfr003-local-boundary"
        body = (f'--{boundary}\r\nContent-Disposition: form-data; name="text"\r\n\r\n'
                f'개인정보 없는 로컬 가상 광고\r\n--{boundary}--\r\n').encode()
        status, ad = api("/advertisements", "POST", encoded=body,
                         headers={"Content-Type": f"multipart/form-data; boundary={boundary}", "Idempotency-Key": "sfr003-local-smoke"})
        assert status == 201 and ad["status"] == "DRAFT"
        path = "/advertisements/" + ad["id"]
        assert api(path + "/extract", "POST")[0] == 409
        assert api(path + "/confirm", "POST")[1]["status"] == "EXTRACTION_PENDING"
        status, extracted = api(path + "/extract", "POST")
        assert status == 200 and extracted["status"] == "EXTRACTION_READY"
        assert extracted["extraction"] == EXTRACTED
        assert api(path + "/extract", "POST")[0] == 200 and stub_calls == 1
        reviewed = {**EXTRACTED, "areaSqm": 21}
        status, saved_review = api(path + "/extraction", "PUT", reviewed)
        assert status == 200 and saved_review["status"] == "REVIEWED"
        assert saved_review["reviewed"] == reviewed
        listing = {"name": "로컬 검증 매물", "address": "가상 주소", "deposit": None, "rent": 123456,
                   "fee": 0, "area": 21, "areaType": "미상", "floor": -20, "original": "가" * 10000}
        status, saved = api("/listings", "POST", listing)
        assert status == 201
        listing_path = "/listings/" + saved["id"]
        for key, value in listing.items():
            assert saved[key] == value, key
        assert "official" not in saved
        assert api("/listings", "POST", {**listing, "floor": -21})[0] == 400
        assert api("/listings", "POST", {**listing, "original": "가" * 10001})[0] == 400
        stop_backend(backend)
        backend = None
        backend = start_backend(enabled=False)
        assert api(path)[1]["reviewed"] == reviewed
        persisted = api(listing_path)[1]
        assert all(persisted[key] == value for key, value in listing.items())
        assert api(path + "/extract", "POST")[0] == 409 and stub_calls == 1
        print(json.dumps({"scope": "local_stub_and_real_mysql", "externalGeminiCalls": 0, "stubCalls": stub_calls,
                          "advertisementReview": "passed", "listingPersistenceAfterRestart": "passed",
                          "nullZeroWonLongTextBasement": "passed", "invalidListingRejected": "passed"}))
        if live:
            stop_backend(backend)
            backend = None
            backend = start_backend(real=True)
            text = "가상 광고 006: 보증금 900만원, 월세 52만원, 관리비 6만원, 면적 23.75㎡, 지하 2층. 주소 미기재. 개인정보 없는 합성 테스트입니다."
            expected = {"address": None, "depositWon": 9000000, "monthlyRentWon": 520000,
                        "maintenanceFeeWon": 60000, "areaSqm": 23.75, "floor": -2}
            live_body = (f'--{boundary}\r\nContent-Disposition: form-data; name="text"\r\n\r\n'
                         f'{text}\r\n--{boundary}--\r\n').encode()
            status, live_ad = api("/advertisements", "POST", encoded=live_body,
                                 headers={"Content-Type": f"multipart/form-data; boundary={boundary}",
                                          "Idempotency-Key": "sfr003-live-smoke-006"})
            assert status == 201
            live_path = "/advertisements/" + live_ad["id"]
            assert api(live_path + "/confirm", "POST")[0] == 200
            record = {"sampleId": "synthetic-smoke-006", "sampleType": "synthetic_smoke_not_representative_dataset",
                      "model": "gemini-3.5-flash-lite", "sourceText": text, "expected": expected,
                      "scope": "Spring Boot advertisement API -> real Gemini -> actual MySQL",
                      "freeTierEvidence": "User-confirmed free-tier account", "attempts": 1,
                      "status": "request_started"}
            live_record_path.write_text(json.dumps(record, ensure_ascii=False, indent=2) + "\n")
            status, live_result = api(live_path + "/extract", "POST")
            record["springHttpStatus"] = status
            if status == 200:
                record.update(status="success", extraction=live_result["extraction"],
                              fieldMatches={k: live_result["extraction"].get(k) == v for k, v in expected.items()})
                assert api(live_path)[1]["extraction"] == live_result["extraction"]
                record["mysqlExtractionPersistence"] = "passed"
            else:
                record.update(status="failed", error=api(live_path)[1].get("extractionError"))
            live_record_path.write_text(json.dumps(record, ensure_ascii=False, indent=2) + "\n")
            print(json.dumps(record, ensure_ascii=False))
    finally:
        if backend is not None:
            stop_backend(backend)
        stub.shutdown()
        stub.server_close()
        if db_started:
            run(["rm", "-f", "-v", db_name])
        if trust_dir:
            shutil.rmtree(trust_dir)


if __name__ == "__main__":
    main()
