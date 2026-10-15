#!/usr/bin/env python3
"""Run the normal Compose entrypoint and Chromium UI with an isolated local stub."""
import json
import argparse
import base64
import hashlib
import os
from pathlib import Path
import secrets
import socket
import subprocess
import tempfile
import threading
import time
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

ROOT = Path(__file__).resolve().parents[1]
stub_calls = 0
replay_record = None


class Stub(BaseHTTPRequestHandler):
    def log_message(self, *args):
        pass

    def do_POST(self):
        global stub_calls
        assert self.path == "/v1beta/models/gemini-3.5-flash-lite:generateContent"
        assert self.headers.get("x-goog-api-key") == "local-browser-stub-not-a-real-key"
        request = json.loads(self.rfile.read(int(self.headers["Content-Length"])))
        stub_calls += 1
        if replay_record is not None:
            parts = request["contents"][0]["parts"]
            assert stub_calls == 1, "이미 저장된 결과를 재사용해야 합니다."
            assert len(parts) == 2
            assert parts[1]["inline_data"]["mime_type"] == "image/jpeg"
            uploaded = base64.b64decode(parts[1]["inline_data"]["data"], validate=True)
            assert hashlib.sha256(uploaded).hexdigest() == replay_record["sourceSha256"]
            assert "광고 원문:\n\n주소 힌트" in parts[0]["text"], "이미지만으로 검증합니다."
            result = replay_record["extraction"]
        else:
            assert "개인정보 없는 브라우저 검증 광고" in request["contents"][0]["parts"][0]["text"]
            result = {"rawText": "개인정보 없는 브라우저 검증 광고", "address": None, "depositWon": None,
                      "monthlyRentWon": 420000, "maintenanceFeeWon": 0, "areaSqm": 23.75, "floor": -2}
        body = json.dumps({"candidates": [{"content": {"parts": [{"text": json.dumps(result)}]}}]}).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.end_headers()
        self.wfile.write(body)


def free_port():
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", 0))
        return sock.getsockname()[1]


def main():
    global replay_record
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--replay-image-001", action="store_true",
                        help="실제 응답 기록을 로컬 재생합니다. 외부 Gemini 요청은 하지 않습니다.")
    options = parser.parse_args()
    if options.replay_image_001:
        replay_record = json.loads((ROOT / "docs/sfr003/image-001-result.json").read_text())
        assert replay_record["status"] == "success" and replay_record["httpStatus"] == 200
        source = ROOT / replay_record["localSourcePath"]
        if not source.is_file():
            raise SystemExit("원본 JPEG가 없습니다. 기록의 localSourcePath에 원본을 복원하세요. API는 호출하지 않습니다.")
        assert hashlib.sha256(source.read_bytes()).hexdigest() == replay_record["sourceSha256"]
    env = os.environ.copy()
    for key in ("DOCKER_HOST", "DOCKER_CONTEXT", "DOCKER_TLS", "DOCKER_TLS_VERIFY", "DOCKER_CERT_PATH"):
        env.pop(key, None)
    backend_port, vite_port = free_port(), free_port()
    stub = ThreadingHTTPServer(("0.0.0.0", 0), Stub)
    threading.Thread(target=stub.serve_forever, daemon=True).start()
    env.update(COMPOSE_PROJECT_NAME="sfr003-browser-" + secrets.token_hex(4), ROOM_BACKEND_PORT=str(backend_port),
               ROOM_DB_PASSWORD=secrets.token_urlsafe(24), ROOM_DB_ROOT_PASSWORD=secrets.token_urlsafe(24),
               GEMINI_ENABLED="true", GEMINI_API_KEY="local-browser-stub-not-a-real-key",
               GEMINI_MODEL="gemini-3.5-flash-lite", GEMINI_BASE_URL=f"http://host.docker.internal:{stub.server_port}/v1beta",
               VITE_DATA_MODE="listing-api", VITE_API_BASE_URL="/api", DEV_API_TARGET=f"http://127.0.0.1:{backend_port}",
               SFR_BROWSER_URL=f"http://127.0.0.1:{vite_port}")
    compose = ["docker", "--host=unix:///var/run/docker.sock", "compose", "-f", "compose.yaml", "-f", ".runtime/compose.java-runtime.json"]
    frontend = None
    try:
        subprocess.run(["bash", "scripts/dev-up.sh"], cwd=ROOT, env=env, check=True)
        opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
        for _ in range(60):
            try:
                with opener.open(f"http://127.0.0.1:{backend_port}/api/listings", timeout=2) as response:
                    if response.status == 200:
                        break
            except OSError:
                time.sleep(1)
        else:
            raise RuntimeError("Compose backend did not become ready")
        if options.replay_image_001:
            frontend = subprocess.Popen(["node", str(ROOT / "node_modules/vite/bin/vite.js"), "--host", "127.0.0.1", "--port", str(vite_port), "--strictPort"],
                                        cwd=ROOT / "frontend", env=env, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            for _ in range(30):
                try:
                    with opener.open(env["SFR_BROWSER_URL"], timeout=2):
                        break
                except OSError:
                    time.sleep(1)
            else:
                raise RuntimeError("Vite did not become ready")
            replay_browser_flow(env, compose, opener, backend_port)
            return
        # Confirm the ordinary container entrypoint applied proxy and CA JVM flags.
        container = subprocess.check_output(compose + ["ps", "-q", "backend"], cwd=ROOT, env=env, text=True).strip()
        args = subprocess.check_output(["docker", "--host=unix:///var/run/docker.sock", "exec", container, "cat", "/proc/1/cmdline"], env=env).split(b"\0")
        if env.get("HTTPS_PROXY"):
            assert any(arg.startswith(b"-Dhttps.proxyHost=") for arg in args)
        if env.get("CODEX_PROXY_CERT") or env.get("JAVA_PROXY_CA_FILE"):
            assert any(arg.startswith(b"-Djavax.net.ssl.trustStore=") for arg in args)
        # Exercise the same JVM proxy/trust flags against a read-only Google URL.
        # No API key is sent and no generateContent request is made.
        probe_source = '''import java.net.URI;
import java.net.http.*;
import java.time.Duration;
public class RoomProxyProbe {
  public static void main(String[] args) throws Exception {
    var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    var request = HttpRequest.newBuilder(URI.create("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash-lite"))
      .timeout(Duration.ofSeconds(20)).GET().build();
    var response = client.send(request, HttpResponse.BodyHandlers.ofString());
    if (!response.headers().firstValue("content-type").orElse("").contains("application/json"))
      throw new IllegalStateException("Expected Google JSON response after TLS connection");
    if (response.statusCode() != 200 && response.statusCode() != 400 && response.statusCode() != 401 && response.statusCode() != 403)
      throw new IllegalStateException("Unexpected model metadata status");
    System.out.println(response.statusCode());
  }
}'''
        with tempfile.TemporaryDirectory(prefix="sfr003-tls-probe-") as directory:
            source = Path(directory) / "RoomProxyProbe.java"
            source.write_text(probe_source)
            subprocess.run(["docker", "--host=unix:///var/run/docker.sock", "run", "--rm", "--pull=never",
                            "--network", "none", "--entrypoint", "javac", "-u", f"{os.getuid()}:{os.getgid()}",
                            "-v", f"{directory}:/probe", "-w", "/probe", "maven:3.9-eclipse-temurin-21", "RoomProxyProbe.java"],
                           env=env, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            subprocess.run(["docker", "--host=unix:///var/run/docker.sock", "cp", str(source.with_suffix(".class")), container + ":/tmp/RoomProxyProbe.class"],
                           env=env, check=True, stdout=subprocess.DEVNULL)
        flags = [arg.decode() for arg in args if arg.startswith((b"-Dhttps.proxyHost=", b"-Dhttps.proxyPort=",
                 b"-Dhttp.nonProxyHosts=", b"-Djavax.net.ssl.trustStore=", b"-Djavax.net.ssl.trustStorePassword="))]
        probe_status = int(subprocess.check_output(["docker", "--host=unix:///var/run/docker.sock", "exec", container,
                          "java", *flags, "-cp", "/tmp", "RoomProxyProbe"], env=env, text=True).strip())
        frontend = subprocess.Popen(["node", str(ROOT / "node_modules/vite/bin/vite.js"), "--host", "127.0.0.1", "--port", str(vite_port), "--strictPort"],
                                    cwd=ROOT / "frontend", env=env, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        for _ in range(30):
            try:
                with opener.open(env["SFR_BROWSER_URL"], timeout=2):
                    break
            except OSError:
                time.sleep(1)
        subprocess.run(["node", "scripts/verify-sfr003-browser.mjs"], cwd=ROOT, env=env, check=True)
        assert stub_calls == 1
        result_file = ROOT / "docs/sfr003/browser-flow-result.json"
        result = json.loads(result_file.read_text())
        result.update(normalComposeProxyAndCaApplied=True, proxyTlsProbeHttpStatus=probe_status,
                      proxyTlsProbeScope="unauthenticated read-only model metadata; no generation", stubCalls=stub_calls)
        result_file.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n")
        print(json.dumps({"normalComposeProxyAndCaApplied": True, "proxyTlsProbeHttpStatus": probe_status,
                          "stubCalls": stub_calls, "realGeminiGenerationCalls": 0}))
    except Exception as error:
        if options.replay_image_001:
            result_path = ROOT / "docs/sfr003/image-001-replay-result.json"
            result = json.loads(result_path.read_text()) if result_path.exists() else {}
            result.update(status="failed", errorType=type(error).__name__, externalGeminiGenerationCalls=0)
            result_path.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n")
        raise
    finally:
        if frontend is not None:
            frontend.terminate()
            frontend.wait(timeout=10)
        cleanup = subprocess.run(compose + ["down", "--volumes", "--remove-orphans"], cwd=ROOT, env=env, check=False)
        stub.shutdown()
        stub.server_close()
        if options.replay_image_001:
            result_path = ROOT / "docs/sfr003/image-001-replay-result.json"
            if result_path.exists():
                result = json.loads(result_path.read_text())
                result["temporaryContainersAndVolumesRemovedAfterRun"] = cleanup.returncode == 0
                result_path.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n")


def replay_browser_flow(env, compose, opener, backend_port):
    """브라우저 저장과 컨테이너 재시작 전후 DB를 실제로 비교한다."""
    result_path = ROOT / "docs/sfr003/image-001-replay-result.json"
    result_path.write_text(json.dumps({"status": "running", "externalGeminiGenerationCalls": 0}) + "\n")
    subprocess.run(["node", "scripts/verify-sfr003-image-replay.mjs"], cwd=ROOT, env=env, check=True)

    def snapshot():
        data = {}
        for table in ("listings", "advertisements"):
            with opener.open(f"http://127.0.0.1:{backend_port}/api/{table}", timeout=10) as response:
                data[table] = json.load(response)
        return data

    before = snapshot()
    assert len(before["listings"]) == len(before["advertisements"]) == 1
    docker = ["docker", "--host=unix:///var/run/docker.sock"]
    backend = subprocess.check_output(compose + ["ps", "-q", "backend"], cwd=ROOT, env=env, text=True).strip()
    db = subprocess.check_output(compose + ["ps", "-q", "db"], cwd=ROOT, env=env, text=True).strip()
    image_path = "/data/advertisements/"
    # 업로드 저장 키는 API 응답에 노출하지 않으므로 DB에서 확인한다.
    query = """SELECT JSON_OBJECT('rent', rent, 'fee', fee, 'deposit', deposit,
        'area', area, 'floor', floor, 'original', original) FROM listings;
        SELECT JSON_OBJECT('originalText', original_text, 'imageStorageKey', image_storage_key,
        'extraction', CAST(extraction_json AS JSON), 'reviewed', CAST(reviewed_json AS JSON))
        FROM advertisements;"""

    def database_rows():
        raw = subprocess.check_output(docker + ["exec", "-i", db, "sh", "-c",
            'MYSQL_PWD="$MYSQL_PASSWORD" mysql --default-character-set=utf8mb4 -uroom_app -N -B --raw roomcrosscheck'],
            input=query, cwd=ROOT, env=env, text=True)
        return [json.loads(line) for line in raw.splitlines()]

    rows = database_rows()
    listing_row, ad_row = rows
    assert listing_row["rent"] == 520000
    assert listing_row["fee"] == 50000 and listing_row["deposit"] == 10000000
    assert listing_row["area"] == 17.5 and listing_row["floor"] == 1
    assert ad_row["originalText"] in (None, "")
    assert ad_row["extraction"] == replay_record["extraction"]
    assert ad_row["reviewed"]["monthlyRentWon"] == 510000
    assert listing_row["original"] == ad_row["reviewed"]["rawText"]

    def stored_image_hash():
        stored = subprocess.check_output(docker + ["exec", backend, "cat", image_path + ad_row["imageStorageKey"]], env=env)
        return hashlib.sha256(stored).hexdigest()

    assert stored_image_hash() == replay_record["sourceSha256"]
    subprocess.run(compose + ["stop", "backend"], cwd=ROOT, env=env, check=True)
    subprocess.run(compose + ["restart", "db"], cwd=ROOT, env=env, check=True)
    for _ in range(60):
        health = subprocess.check_output(docker + ["inspect", "--format", "{{.State.Health.Status}}", db], env=env, text=True).strip()
        if health == "healthy":
            break
        time.sleep(1)
    else:
        raise RuntimeError("MySQL did not recover after restart")
    subprocess.run(compose + ["start", "backend"], cwd=ROOT, env=env, check=True)
    for _ in range(60):
        try:
            after = snapshot()
            break
        except OSError:
            time.sleep(1)
    else:
        raise RuntimeError("Backend did not recover after restart")
    assert after == before, "서버 재시작 후 광고·매물 값이 변경됐습니다."
    assert database_rows() == rows
    assert stored_image_hash() == replay_record["sourceSha256"]
    subprocess.run(["node", "scripts/verify-sfr003-image-replay.mjs", "--after-restart"], cwd=ROOT, env=env, check=True)
    assert stub_calls == 1
    result = json.loads(result_path.read_text())
    result.update(status="passed", localReplayCalls=stub_calls, externalGeminiGenerationCalls=0,
        backendRestartPassed=True, mysqlRestartPassed=True, apiSnapshotUnchanged=True,
        mysqlDirectQueryPassed=True, originalImageHashPreserved=True,
        duplicateExtractUsedStoredResult=True, reviewedAdReExtractionBlocked=True)
    result_path.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps(result, ensure_ascii=False))


if __name__ == "__main__":
    main()
