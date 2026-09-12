# -*- coding: utf-8 -*-
"""Hermes Agent testleri: python3 -m unittest discover -s hermes/agent/tests -v"""

import json
import os
import shutil
import sys
import tempfile
import threading
import time
import unittest
import urllib.error
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
import hermes_agent as ha  # noqa: E402

TOKEN = "test-token-123"


def free_port():
    import socket
    s = socket.socket()
    s.bind(("127.0.0.1", 0))
    port = s.getsockname()[1]
    s.close()
    return port


class AgentTestBase(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tmp = tempfile.mkdtemp(prefix="hermes-test-")
        cls.store = ha.Store(cls.tmp)
        cls.store.config["token"] = TOKEN
        cls.store.config["default_backend"] = "echo"
        cls.store.config["scheduler_enabled"] = False
        cls.store.save_config()
        ha.Handler.store = cls.store
        cls.port = free_port()
        cls.httpd = ThreadingHTTPServer(("127.0.0.1", cls.port), ha.Handler)
        cls.httpd.daemon_threads = True
        cls.thread = threading.Thread(target=cls.httpd.serve_forever, kwargs={"poll_interval": 0.1}, daemon=True)
        cls.thread.start()
        cls.base = "http://127.0.0.1:%d" % cls.port
        time.sleep(0.2)

    @classmethod
    def tearDownClass(cls):
        cls.httpd.shutdown()
        cls.httpd.server_close()
        shutil.rmtree(cls.tmp, ignore_errors=True)

    # -- yardımcılar -------------------------------------------------------
    def call(self, path, method="GET", body=None, token=TOKEN, raw=False):
        req = urllib.request.Request(self.base + path, method=method,
                                     data=json.dumps(body).encode() if body is not None else None)
        req.add_header("Content-Type", "application/json")
        if token:
            req.add_header("Authorization", "Bearer " + token)
        # Test sunucusu yerel; proxy kullanma.
        opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
        with opener.open(req, timeout=30) as resp:
            text = resp.read().decode("utf-8")
        return text if raw else json.loads(text)

    def sse(self, path, body, token=TOKEN, timeout=30):
        """SSE akışını (olay, veri) listesi olarak toplar."""
        req = urllib.request.Request(self.base + path, method="POST",
                                     data=json.dumps(body).encode())
        req.add_header("Content-Type", "application/json")
        req.add_header("Authorization", "Bearer " + token)
        opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
        events, name = [], ""
        with opener.open(req, timeout=timeout) as resp:
            for raw in resp:
                line = raw.decode("utf-8").rstrip("\r\n")
                if line.startswith("event:"):
                    name = line[6:].strip()
                elif line.startswith("data:"):
                    events.append((name, json.loads(line[5:].strip())))
        return events


class TestAuthAndHealth(AgentTestBase):

    def test_token_yoksa_401(self):
        with self.assertRaises(urllib.error.HTTPError) as ctx:
            self.call("/v1/health", token=None)
        self.assertEqual(401, ctx.exception.code)

    def test_yanlis_token_401(self):
        with self.assertRaises(urllib.error.HTTPError) as ctx:
            self.call("/v1/health", token="yanlis")
        self.assertEqual(401, ctx.exception.code)

    def test_health_bilgi_dondurur(self):
        data = self.call("/v1/health")
        self.assertTrue(data["ok"])
        self.assertEqual("hermes-agent", data["name"])
        self.assertIn("echo", data["backends"])
        self.assertIn("http_get", data["tools"])
        self.assertNotIn("shell", data["tools"])  # varsayılan: kapalı

    def test_kok_sayfa_tokensiz_acilir(self):
        html = self.call("/", token=None, raw=True)
        self.assertIn("Hermes Agent", html)

    def test_bilinmeyen_adres_404(self):
        with self.assertRaises(urllib.error.HTTPError) as ctx:
            self.call("/v1/olmayan")
        self.assertEqual(404, ctx.exception.code)

    def test_config_token_sizdirmaz(self):
        data = self.call("/v1/config")
        self.assertEqual("***", data["config"]["token"])


class TestBackends(AgentTestBase):

    def test_hazir_arka_uclar_listelenir(self):
        self.store.config["backends"]["xai"]["api_key"] = "xai-test"
        self.store.config["backends"]["openai"]["api_key"] = ""
        data = self.call("/v1/backends")
        by_name = {b["name"]: b for b in data["backends"]}

        # Anahtarı olan sağlayıcı hazır
        self.assertTrue(by_name["xai"]["ready"])
        self.assertEqual("grok-3", by_name["xai"]["default_model"])
        # Anahtarı olmayan hazır değil ve nedeni yazıyor
        self.assertFalse(by_name["openai"]["ready"])
        self.assertIn("anahtar", by_name["openai"]["note"].lower())
        # Echo her zaman hazır, Ollama base_url ile hazır
        self.assertTrue(by_name["echo"]["ready"])
        self.assertTrue(by_name["ollama"]["ready"])
        # Varsayılan işaretli
        self.assertTrue(by_name[data["default_backend"]]["is_default"])

    def test_anahtarlar_listede_sizmaz(self):
        self.store.config["backends"]["xai"]["api_key"] = "xai-gizli-anahtar"
        raw = self.call("/v1/backends", raw=True)
        self.assertNotIn("xai-gizli-anahtar", raw)

    def test_saglik_hazir_arka_uclari_bildirir(self):
        self.store.config["backends"]["xai"]["api_key"] = "xai-test"
        data = self.call("/v1/health")
        self.assertIn("xai", data["backends_ready"])
        self.assertIn("echo", data["backends_ready"])
        self.assertNotIn("anthropic", data["backends_ready"])


class TestBotCrud(AgentTestBase):

    def test_bot_kaydet_listele_sil(self):
        bot = {"id": "b_test", "name": "Test Botu", "system_prompt": "selam",
               "model": "echo", "tools": ["now"]}
        saved = self.call("/v1/bots/b_test", "PUT", bot)["bot"]
        self.assertEqual("Test Botu", saved["name"])
        self.assertGreater(saved["updated_at"], 0)

        listed = self.call("/v1/bots")["bots"]
        self.assertTrue(any(b["id"] == "b_test" for b in listed))

        self.assertTrue(self.call("/v1/bots/b_test", "DELETE")["ok"])
        listed = self.call("/v1/bots")["bots"]
        self.assertFalse(any(b["id"] == "b_test" for b in listed))

    def test_olmayan_bot_calistirilamaz(self):
        with self.assertRaises(urllib.error.HTTPError) as ctx:
            self.call("/v1/bots/yok/runs", "POST", {"input": "selam", "stream": False})
        self.assertEqual(404, ctx.exception.code)

    def test_bos_girdi_reddedilir(self):
        self.call("/v1/bots/b_bos", "PUT", {"id": "b_bos", "name": "Boş"})
        with self.assertRaises(urllib.error.HTTPError) as ctx:
            self.call("/v1/bots/b_bos/runs", "POST", {"input": "   ", "stream": False})
        self.assertEqual(400, ctx.exception.code)


class TestRun(AgentTestBase):

    def setUp(self):
        self.bot = {"id": "b_run", "name": "Echo Bot", "model": "echo",
                    "system_prompt": "Sen bir test botusun", "memory_turns": 5}
        self.call("/v1/bots/b_run", "PUT", self.bot)

    def test_akisli_calistirma(self):
        events = self.sse("/v1/bots/b_run/runs", {"input": "Merhaba dünya", "stream": True})
        names = [e[0] for e in events]
        self.assertIn("run.started", names)
        self.assertIn("token", names)
        self.assertIn("message", names)
        self.assertIn("run.finished", names)
        text = "".join(d["text"] for n, d in events if n == "token")
        self.assertIn("Merhaba dünya", text)
        finished = [d for n, d in events if n == "run.finished"][0]
        self.assertEqual("ok", finished["status"])

    def test_akissiz_calistirma_ve_gecmis(self):
        run = self.call("/v1/bots/b_run/runs", "POST",
                        {"input": "Selam", "stream": False})["run"]
        self.assertEqual("ok", run["status"])
        self.assertIn("Selam", run["output"])
        messages = self.call("/v1/bots/b_run/messages")["messages"]
        self.assertEqual("user", messages[-2]["role"])
        self.assertEqual("assistant", messages[-1]["role"])

        runs = self.call("/v1/bots/b_run/runs")["runs"]
        self.assertTrue(any(r["run_id"] == run["run_id"] for r in runs))

        detail = self.call("/v1/runs/%s" % run["run_id"])["run"]
        self.assertEqual(run["run_id"], detail["run_id"])

    def test_gecmis_silinir(self):
        self.call("/v1/bots/b_run/runs", "POST", {"input": "bir", "stream": False})
        self.call("/v1/bots/b_run/messages", "DELETE")
        self.assertEqual([], self.call("/v1/bots/b_run/messages")["messages"])

    def test_uygulamadan_gelen_bot_otomatik_kaydedilir(self):
        yeni = {"id": "b_inline", "name": "Satıriçi Bot", "model": "echo"}
        run = self.call("/v1/bots/b_inline/runs", "POST",
                        {"input": "test", "stream": False, "bot": yeni})["run"]
        self.assertEqual("ok", run["status"])
        self.assertTrue(any(b["id"] == "b_inline" for b in self.call("/v1/bots")["bots"]))

    def test_arac_dongusu_calisir(self):
        """Sahte bir model arka ucu ile araç çağrısı → araç sonucu → yanıt akışı."""
        calls = {"n": 0}

        def fake_stream(store, backend, model, messages, tools, temperature, max_tokens):
            calls["n"] += 1
            if calls["n"] == 1:
                yield ("tool_calls", [{"id": "c1", "name": "now", "arguments": "{}"}])
                yield ("done", "tool_calls")
            else:
                # Araç sonucu modele ulaştı mı?
                assert any(m.get("role") == "tool" for m in messages), "araç sonucu eklenmedi"
                yield ("token", "Saat bilgisi alındı.")
                yield ("done", "stop")

        original = ha.stream_backend
        ha.stream_backend = fake_stream
        try:
            bot = {"id": "b_tool", "name": "Araçlı Bot", "model": "echo", "tools": ["now"]}
            self.call("/v1/bots/b_tool", "PUT", bot)
            events = self.sse("/v1/bots/b_tool/runs", {"input": "saat kaç", "stream": True})
        finally:
            ha.stream_backend = original

        names = [e[0] for e in events]
        self.assertIn("tool.call", names)
        self.assertIn("tool.result", names)
        result = [d for n, d in events if n == "tool.result"][0]
        self.assertTrue(result["ok"])
        self.assertEqual("now", result["name"])
        final = [d for n, d in events if n == "message"][0]
        self.assertIn("Saat bilgisi alındı.", final["content"])

    def test_kapali_arac_calistirilmaz(self):
        calls = {"n": 0}

        def fake_stream(store, backend, model, messages, tools, temperature, max_tokens):
            calls["n"] += 1
            if calls["n"] == 1:
                yield ("tool_calls", [{"id": "c1", "name": "shell", "arguments": '{"command":"ls"}'}])
                yield ("done", "tool_calls")
            else:
                yield ("token", "tamam")
                yield ("done", "stop")

        original = ha.stream_backend
        ha.stream_backend = fake_stream
        try:
            self.call("/v1/bots/b_shell", "PUT",
                      {"id": "b_shell", "name": "Kabuk", "model": "echo", "tools": ["shell"]})
            events = self.sse("/v1/bots/b_shell/runs", {"input": "ls çalıştır", "stream": True})
        finally:
            ha.stream_backend = original
        result = [d for n, d in events if n == "tool.result"][0]
        self.assertFalse(result["ok"])
        self.assertIn("açık değil", result["result"])


class TestTools(unittest.TestCase):

    def setUp(self):
        self.tmp = tempfile.mkdtemp(prefix="hermes-tool-")
        self.store = ha.Store(self.tmp)

    def tearDown(self):
        shutil.rmtree(self.tmp, ignore_errors=True)

    def test_now_araci_tarih_dondurur(self):
        out = ha.tool_now(self.store, {})
        self.assertRegex(out, r"\d{2}\.\d{2}\.\d{4} \d{2}:\d{2}")

    def test_dosya_yaz_oku(self):
        ha.tool_write_file(self.store, {"path": "notlar/a.txt", "content": "merhaba"})
        self.assertEqual("merhaba", ha.tool_read_file(self.store, {"path": "notlar/a.txt"}))

    def test_calisma_klasoru_disina_cikilamaz(self):
        with self.assertRaises(ha.AgentError):
            ha.tool_read_file(self.store, {"path": "../../etc/passwd"})
        with self.assertRaises(ha.AgentError):
            ha.tool_write_file(self.store, {"path": "/tmp/kotu.txt", "content": "x"})

    def test_shell_varsayilan_kapali(self):
        with self.assertRaises(ha.AgentError):
            ha.tool_shell(self.store, {"command": "echo merhaba"})

    def test_shell_acikken_calisir(self):
        self.store.config["allow_shell"] = True
        out = ha.tool_shell(self.store, {"command": "echo merhaba"})
        self.assertIn("merhaba", out)
        self.assertIn("çıkış kodu=0", out)

    def test_shell_izin_listesi(self):
        self.store.config["allow_shell"] = True
        self.store.config["shell_allowlist"] = ["df", "free"]
        with self.assertRaises(ha.AgentError):
            ha.tool_shell(self.store, {"command": "rm -rf /"})
        self.assertIn("çıkış kodu=0", ha.tool_shell(self.store, {"command": "df -h"}))

    def test_http_get_html_metne_cevirir(self):
        class Page(BaseHTTPRequestHandler):
            def do_GET(self):
                body = b"<html><head><style>a{}</style></head><body><h1>Baslik</h1><p>Icerik burada</p></body></html>"
                self.send_response(200)
                self.send_header("Content-Type", "text/html")
                self.send_header("Content-Length", str(len(body)))
                self.end_headers()
                self.wfile.write(body)

            def log_message(self, *a):
                pass

        port = free_port()
        srv = ThreadingHTTPServer(("127.0.0.1", port), Page)
        threading.Thread(target=srv.serve_forever, daemon=True).start()
        try:
            old = os.environ.pop("http_proxy", None)
            out = ha.tool_http_get(self.store, {"url": "http://127.0.0.1:%d/" % port})
        finally:
            srv.shutdown()
            if old:
                os.environ["http_proxy"] = old
        self.assertIn("Baslik", out)
        self.assertIn("Icerik burada", out)
        self.assertNotIn("<h1>", out)


class TestScheduleLogic(unittest.TestCase):

    def bot(self, **schedule):
        base = {"mode": "INTERVAL", "every_minutes": 60, "prompt": "kontrol", "tz_offset_minutes": 180}
        base.update(schedule)
        return {"id": "b", "name": "B", "enabled": True, "schedule": base}

    def test_aralikli_ilk_calisma(self):
        self.assertTrue(ha.schedule_due(self.bot(), None, 1_000_000))

    def test_aralik_dolmadan_calismaz(self):
        now = 10_000_000
        self.assertFalse(ha.schedule_due(self.bot(), now - 59 * 60_000, now))
        self.assertTrue(ha.schedule_due(self.bot(), now - 61 * 60_000, now))

    def test_istem_bossa_calismaz(self):
        self.assertFalse(ha.schedule_due(self.bot(prompt=""), None, 1000))

    def test_kapali_bot_calismaz(self):
        b = self.bot()
        b["enabled"] = False
        self.assertFalse(ha.schedule_due(b, None, 1000))

    def test_gunluk_yerel_saatte(self):
        b = self.bot(mode="DAILY", at_hour=8, at_minute=30, tz_offset_minutes=180)
        gun_basi = -180 * 60 * 1000
        self.assertFalse(ha.schedule_due(b, None, gun_basi + (8 * 60 + 29) * 60_000))
        self.assertTrue(ha.schedule_due(b, None, gun_basi + (8 * 60 + 31) * 60_000))

    def test_gunluk_ayni_gun_tekrarlamaz(self):
        b = self.bot(mode="DAILY", at_hour=8, at_minute=30)
        gun_basi = -180 * 60 * 1000
        calisti = gun_basi + (8 * 60 + 31) * 60_000
        self.assertFalse(ha.schedule_due(b, calisti, gun_basi + 9 * 60 * 60_000))
        self.assertTrue(ha.schedule_due(b, calisti, gun_basi + 9 * 60 * 60_000 + ha.DAY_MS))


class TestHelpers(unittest.TestCase):

    def test_eslestirme_kodu_cozulebilir(self):
        import base64 as b64
        code = ha.pairing_code("http://1.2.3.4:8713", "gizli", "vps-1")
        self.assertTrue(code.startswith("HERMES1:"))
        payload = code.split(":", 1)[1]
        payload += "=" * (-len(payload) % 4)
        data = json.loads(b64.urlsafe_b64decode(payload).decode())
        self.assertEqual("http://1.2.3.4:8713", data["u"])
        self.assertEqual("gizli", data["t"])
        self.assertEqual("vps-1", data["n"])

    def test_openai_adres_kurulumu(self):
        self.assertEqual("https://api.x.ai/v1/chat/completions", ha.openai_chat_url("https://api.x.ai/v1"))
        self.assertEqual("https://api.openai.com/v1/chat/completions", ha.openai_chat_url("https://api.openai.com"))
        self.assertEqual("http://a/v1/chat/completions", ha.openai_chat_url("http://a/v1/chat/completions"))

    def test_html_metne_cevirme(self):
        out = ha.html_to_text("<p>Bir</p><script>kotu()</script><p>İki</p>")
        self.assertIn("Bir", out)
        self.assertIn("İki", out)
        self.assertNotIn("kotu()", out)

    def test_uzun_metin_kirpilir(self):
        out = ha.clip("a" * 100, 10)
        self.assertTrue(out.startswith("a" * 10))
        self.assertIn("kırpıldı", out)

    def test_ayar_yazma(self):
        cfg = {}
        ha.set_dotted(cfg, "backends.xai.api_key=xai-123")
        ha.set_dotted(cfg, "allow_shell=true")
        ha.set_dotted(cfg, "port=9000")
        self.assertEqual("xai-123", cfg["backends"]["xai"]["api_key"])
        self.assertIs(True, cfg["allow_shell"])
        self.assertEqual(9000, cfg["port"])


if __name__ == "__main__":
    unittest.main(verbosity=2)


class TestOpenAiStreaming(unittest.TestCase):
    """stream_openai'yi OpenAI uyumlu sahte bir sunucuya karşı sınar."""

    @classmethod
    def setUpClass(cls):
        cls.requests = []

        class FakeOpenAI(BaseHTTPRequestHandler):
            def do_POST(self):
                length = int(self.headers.get("Content-Length") or 0)
                payload = json.loads(self.rfile.read(length).decode())
                cls.requests.append((dict(self.headers), payload))
                # İkinci tur (araç sonucu gönderildikten sonra) düz metin döner.
                has_tool_result = any(m.get("role") == "tool" for m in payload.get("messages", []))
                if has_tool_result:
                    chunks = [
                        '{"choices":[{"delta":{"content":"Disk "}}]}',
                        '{"choices":[{"delta":{"content":"iyi."}}]}',
                        '{"choices":[{"delta":{},"finish_reason":"stop"}],'
                        '"usage":{"prompt_tokens":30,"completion_tokens":4}}',
                        "[DONE]",
                    ]
                else:
                    chunks = [
                        '{"choices":[{"delta":{"role":"assistant"}}]}',
                        '{"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_1",'
                        '"function":{"name":"now","arguments":"{\\"utc"}}]}}]}',
                        '{"choices":[{"delta":{"tool_calls":[{"index":0,'
                        '"function":{"arguments":"_offset_hours\\": 3}"}}]}}]}',
                        '{"choices":[{"delta":{},"finish_reason":"tool_calls"}]}',
                        "[DONE]",
                    ]
                body = "".join("data: %s\n\n" % c for c in chunks).encode()
                self.send_response(200)
                self.send_header("Content-Type", "text/event-stream")
                self.send_header("Content-Length", str(len(body)))
                self.end_headers()
                self.wfile.write(body)

            def do_GET(self):
                body = json.dumps({"data": [{"id": "sahte-model-1"}, {"id": "sahte-model-2"}]}).encode()
                self.send_response(200)
                self.send_header("Content-Type", "application/json")
                self.send_header("Content-Length", str(len(body)))
                self.end_headers()
                self.wfile.write(body)

            def log_message(self, *a):
                pass

        cls.port = free_port()
        cls.srv = ThreadingHTTPServer(("127.0.0.1", cls.port), FakeOpenAI)
        threading.Thread(target=cls.srv.serve_forever, daemon=True).start()
        cls.tmp = tempfile.mkdtemp(prefix="hermes-openai-")
        cls.store = ha.Store(cls.tmp)
        cls.store.config["backends"]["test"] = {
            "base_url": "http://127.0.0.1:%d/v1" % cls.port,
            "api_key": "sahte-anahtar",
            "default_model": "sahte-model-1",
        }
        cls.old_proxy = os.environ.pop("http_proxy", None)

    @classmethod
    def tearDownClass(cls):
        cls.srv.shutdown()
        shutil.rmtree(cls.tmp, ignore_errors=True)
        if cls.old_proxy:
            os.environ["http_proxy"] = cls.old_proxy

    def test_arac_cagrisi_parcali_gelse_de_birlestirilir(self):
        bot = {"id": "b_oa", "name": "OpenAI Bot", "backend": "test",
               "model": "sahte-model-1", "tools": ["now"], "memory_turns": 0}
        events = []
        run = ha.run_bot(self.store, bot, "Disk durumu?", history=[],
                         emit=lambda e, d: events.append((e, d)))

        self.assertEqual("ok", run["status"])
        self.assertEqual("Disk iyi.", run["output"])
        # Parçalı gelen JSON argümanları birleştirildi mi?
        call = [d for e, d in events if e == "tool.call"][0]
        self.assertEqual("now", call["name"])
        self.assertEqual({"utc_offset_hours": 3}, call["args"])
        self.assertTrue([d for e, d in events if e == "tool.result"][0]["ok"])
        # Token kullanımı toplandı mı?
        self.assertEqual(30, run["usage"]["prompt_tokens"])
        # İstek doğru kuruldu mu?
        headers, payload = self.requests[0]
        self.assertEqual("Bearer sahte-anahtar", headers.get("Authorization"))
        self.assertTrue(payload["stream"])
        self.assertEqual("sahte-model-1", payload["model"])
        self.assertEqual("now", payload["tools"][0]["function"]["name"])

    def test_model_listesi_okunur(self):
        self.assertEqual(["sahte-model-1", "sahte-model-2"], ha.list_models(self.store, "test"))

    def test_anahtarsiz_arka_uc_anlasilir_hata_verir(self):
        self.store.config["backends"]["anahtarsiz"] = {"base_url": "http://127.0.0.1:1/v1", "api_key": ""}
        with self.assertRaises(ha.AgentError) as ctx:
            list(ha.stream_backend(self.store, "anahtarsiz", "m", [], [], 0.5, 100))
        self.assertIn("API anahtarı", ctx.exception.message)
