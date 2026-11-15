"""함께타요 프로토타입 서버 (Python 표준 라이브러리만 사용)

실행:  python3 server.py  → http://localhost:8000/car  (차 화면 + 가상 차량 콘솔)
                           http://localhost:8000/p/<여정코드>  (동승자 폰 화면)

정식 버전에서는 FastAPI + WebSocket으로 옮길 예정이며, 지금은 의존성 없이
바로 실행되도록 표준 라이브러리 HTTP 서버와 0.7초 폴링을 사용한다.
"""
import json
import random
import threading
from http.server import ThreadingHTTPServer, BaseHTTPRequestHandler
from pathlib import Path
from urllib.parse import urlparse

from settlement import Trip

STATIC = Path(__file__).parent / "static"
TRIPS: dict[str, Trip] = {}
LOCK = threading.Lock()
LATEST = {"code": None}


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *args):  # 조용히
        pass

    # ---------- helpers ----------
    def _json(self, obj, status=200):
        body = json.dumps(obj, ensure_ascii=False).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Cache-Control", "no-store")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _file(self, name):
        p = STATIC / name
        data = p.read_bytes()
        ctype = "text/html; charset=utf-8" if name.endswith(".html") else "application/octet-stream"
        if name.endswith(".js"):
            ctype = "text/javascript; charset=utf-8"
        if name.endswith(".css"):
            ctype = "text/css; charset=utf-8"
        self.send_response(200)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def _body(self):
        n = int(self.headers.get("Content-Length") or 0)
        return json.loads(self.rfile.read(n) or b"{}")

    def _trip(self, code):
        t = TRIPS.get(code)
        if not t:
            self._json({"error": "여정을 찾을 수 없어요"}, 404)
        return t

    # ---------- routes ----------
    def do_GET(self):
        path = urlparse(self.path).path
        parts = [p for p in path.split("/") if p]
        if path in ("/", "/car"):
            return self._file("car.html")
        if path == "/demo":
            return self._file("demo.html")
        if parts and parts[0] == "p":
            return self._file("phone.html")
        if parts and parts[0] == "static" and len(parts) == 2:
            return self._file(parts[1])
        if path == "/api/latest":
            return self._json({"code": LATEST["code"]})
        if len(parts) == 3 and parts[:2] == ["api", "trips"]:
            t = self._trip(parts[2])
            if t:
                with LOCK:
                    return self._json(t.snapshot())
            return
        self._json({"error": "not found"}, 404)

    def do_POST(self):
        parts = [p for p in urlparse(self.path).path.split("/") if p]
        data = self._body()
        if parts == ["api", "trips"]:
            code = f"{random.randint(1000, 9999)}"
            with LOCK:
                t = Trip(code=code,
                         driver=data.get("driver", "철수"),
                         fuel_price=data.get("fuel_price", 1700),
                         powertrain=data.get("powertrain", "ice"))
                TRIPS[code] = t
                LATEST["code"] = code
                return self._json(t.snapshot())
        if len(parts) == 4 and parts[:2] == ["api", "trips"]:
            t = self._trip(parts[2])
            if not t:
                return
            action = parts[3]
            try:
                with LOCK:
                    if action == "join":
                        pid = t.join(data["name"], data["seat"])
                        return self._json({"passenger_id": pid, **t.snapshot()})
                    if action == "vehicle":      # 차량 앱이 보내는 차량 상태
                        t.on_vehicle(data)
                    elif action == "alight":     # 동승자 폰의 하차 확인 응답
                        t.confirm_alight(data["passenger_id"], bool(data.get("yes")))
                    elif action == "extra":      # 톨비·주차비
                        t.add_extra(int(data.get("amount", 0)), data.get("label", "추가 비용"))
                    elif action == "start":
                        t.start()
                    elif action == "finish":
                        t.finish()
                    else:
                        return self._json({"error": "unknown action"}, 404)
                    return self._json(t.snapshot())
            except (KeyError, ValueError) as e:
                return self._json({"error": str(e)}, 400)
        self._json({"error": "not found"}, 404)


if __name__ == "__main__":
    import sys
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8000
    print(f"함께타요 서버: http://localhost:{port}/car")
    ThreadingHTTPServer(("0.0.0.0", port), Handler).serve_forever()
