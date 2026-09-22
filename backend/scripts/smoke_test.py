"""Prueba HTTP local de ambos JAR con JWT RS256 de prueba. No utiliza recursos AWS.
Requiere Python 3.10+ y cryptography. Compilar antes con mvnw verify.
Las claves se generan en memoria; nunca se incorporan al backend.
"""
import base64
import json
import os
from pathlib import Path
import socket
import subprocess
import tempfile
import threading
import time
from datetime import datetime, timezone, timedelta
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.request import Request, urlopen
from urllib.error import HTTPError, URLError
from cryptography.hazmat.primitives.asymmetric import rsa, padding
from cryptography.hazmat.primitives import hashes

ROOT = Path(__file__).resolve().parents[1]


def b64(data):
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode()


def free_port():
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", 0))
        return sock.getsockname()[1]


def main():
    key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    numbers = key.public_key().public_numbers()
    jwks = {"keys": [{"kty": "RSA", "kid": "smoke", "alg": "RS256", "use": "sig",
                     "n": b64(numbers.n.to_bytes(256, "big")),
                     "e": b64(numbers.e.to_bytes(3, "big"))}]}

    class Handler(BaseHTTPRequestHandler):
        def do_GET(self):
            if self.path != "/.well-known/jwks.json":
                self.send_error(404)
                return
            body = json.dumps(jwks).encode()
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(body)

        def log_message(self, *args):
            pass

    server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    issuer = f"http://127.0.0.1:{server.server_port}"
    ports = {"eventos": free_port(), "entradas": free_port()}
    processes = {}
    logs = []

    def token(sub, group):
        claims = {"iss": issuer, "sub": sub, "client_id": "smoke-client", "token_use": "access",
                  "iat": int(time.time()) - 1, "exp": int(time.time()) + 600,
                  "cognito:groups": [group],
                  "scope": " ".join("resource-server-riffpass/" + s for s in
                                    ["eventos-read", "eventos-write", "entradas-read", "entradas-write"])}
        payload = b64(json.dumps({"alg": "RS256", "kid": "smoke"}).encode()) + "." + b64(json.dumps(claims).encode())
        return payload + "." + b64(key.sign(payload.encode(), padding.PKCS1v15(), hashes.SHA256()))

    def call(service, method, path, expected, bearer=None, body=None):
        headers = {"Content-Type": "application/json"}
        if bearer:
            headers["Authorization"] = "Bearer " + bearer
        request = Request(f"http://127.0.0.1:{ports[service]}/test{path}",
                          data=json.dumps(body).encode() if body is not None else None,
                          headers=headers, method=method)
        try:
            with urlopen(request, timeout=15) as response:
                status, raw = response.status, response.read()
        except HTTPError as error:
            status, raw = error.code, error.read()
        assert status == expected, f"{method} {path}: expected {expected}, received {status}: {raw.decode()}"
        return json.loads(raw) if raw else None

    ROOT.joinpath("tmp").mkdir(exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="smoke-", dir=ROOT / "tmp") as directory:
        def start(name):
            jar = ROOT / f"riffpass-{name}-service/target/riffpass-{name}-service-1.0.0.jar"
            if not jar.is_file():
                raise RuntimeError("Primero ejecuta mvnw verify.")
            env = dict(os.environ)
            env.pop("SPRING_PROFILES_ACTIVE", None)
            env.update(PORT=str(ports[name]), SERVER_ADDRESS="127.0.0.1", SERVER_SERVLET_CONTEXT_PATH="/test", DB_USERNAME="sa", DB_PASSWORD="",
                       DB_URL=f"jdbc:h2:file:{Path(directory).as_posix()}/{name};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;WRITE_DELAY=0",
                       COGNITO_ISSUER=issuer, COGNITO_CLIENT_ID="smoke-client", COGNITO_AUDIENCE="",
                       EVENTOS_BASE_URL=f"http://127.0.0.1:{ports['eventos']}/test")
            log = open(Path(directory) / f"{name}.log", "a", encoding="utf-8")
            logs.append(log)
            processes[name] = subprocess.Popen(["java", "-Xmx256m", "-jar", str(jar)], env=env,
                                                cwd=directory, stdout=log, stderr=subprocess.STDOUT,
                                                creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))

        def wait_ready(name):
            until = time.monotonic() + 45
            while time.monotonic() < until:
                if processes[name].poll() is not None:
                    raise RuntimeError(f"{name} terminó antes de iniciar.")
                try:
                    call(name, "GET", "/actuator/health", 200)
                    return
                except (URLError, AssertionError):
                    time.sleep(0.25)
            raise RuntimeError(f"Timeout iniciando {name}")

        def stop(name):
            process = processes.get(name)
            if process and process.poll() is None:
                process.terminate()
                try:
                    process.wait(timeout=10)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait()

        try:
            start("eventos")
            start("entradas")
            for name in ports:
                wait_ready(name)
            owner, attendee, stranger = token("owner", "ORGANIZADOR"), token("ana", "ASISTENTE"), token("otro", "ORGANIZADOR")
            call("eventos", "GET", "/api/eventos", 401)
            event = call("eventos", "POST", "/api/eventos", 201, owner,
                         {"nombre": "Smoke concert", "artista": "Banda", "recinto": "Sala", "ciudad": "Santiago",
                          "descripcion": "Prueba automatizada",
                          "fecha": (datetime.now(timezone.utc) + timedelta(days=1)).isoformat()})
            event_id = event["id"]
            call("entradas", "POST", f"/api/eventos/{event_id}/inventario", 201, owner, {"capacidad": 1})
            ticket = call("entradas", "POST", f"/api/eventos/{event_id}/entradas", 201, attendee)
            call("entradas", "POST", f"/api/eventos/{event_id}/entradas", 409, attendee)
            call("entradas", "POST", "/api/entradas/validar", 403, attendee, {"codigo": ticket["codigo"]})
            call("entradas", "POST", "/api/entradas/validar", 403, stranger, {"codigo": ticket["codigo"]})
            call("entradas", "POST", "/api/entradas/validar", 200, owner, {"codigo": ticket["codigo"]})
            call("entradas", "POST", "/api/entradas/validar", 409, owner, {"codigo": ticket["codigo"]})
            assert call("entradas", "GET", "/api/entradas/mias", 200, stranger) == []
            stop("entradas")
            start("entradas")
            wait_ready("entradas")
            result = call("entradas", "GET", "/api/entradas/mias", 200, attendee)
            assert len(result) == 1 and result[0]["estado"] == "UTILIZADA", f"Estado tras reinicio: {result}"
            print("SMOKE OK: 2 JAR, JWT RS256, HTTP entre servicios, emisión, propiedad, validación única y persistencia tras reinicio.")
        except Exception:
            for name in ports:
                log_path = Path(directory) / f"{name}.log"
                if log_path.exists():
                    print(log_path.read_text(encoding="utf-8")[-5000:])
            raise
        finally:
            for name in ports:
                stop(name)
            server.shutdown()
            server.server_close()
            for log in logs:
                log.close()


if __name__ == "__main__":
    main()
