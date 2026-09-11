#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Hermes Agent — telefondaki "Hermes Bot Konsol" uygulamasının bağlandığı sunucu.

Tek dosya, SIFIR harici bağımlılık: sadece Python 3.8+ standart kütüphanesi.
VPS'te (Hostinger, Contabo, Hetzner...) veya kendi bilgisayarınızda çalışır.

    python3 hermes_agent.py                 # ilk çalıştırma: token üretir ve yazar
    python3 hermes_agent.py --help

Botlar burada kayıtlıdır; zamanlanmış botlar telefon kapalıyken de çalışır.
"""

import argparse
import base64
import hmac
import json
import os
import queue
import random
import re
import shlex
import socket
import ssl
import string
import subprocess
import sys
import threading
import time
import traceback
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timedelta, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

VERSION = "1.0.0"
AGENT_NAME = "hermes-agent"
DEFAULT_PORT = 8713
MAX_BODY = 4 * 1024 * 1024          # 4 MB istek sınırı
MAX_TOOL_OUTPUT = 8000              # araç çıktısı karakter sınırı
MAX_TOOL_STEPS = 6                  # araç çağrısı döngüsü üst sınırı
RATE_LIMIT_PER_MIN = 240

START_TIME = time.time()


# --------------------------------------------------------------------------
# Yardımcılar
# --------------------------------------------------------------------------

def now_ms():
    return int(time.time() * 1000)


def iso(ts=None):
    return datetime.fromtimestamp(ts or time.time(), tz=timezone.utc).isoformat()


def rand_id(prefix):
    alphabet = string.ascii_lowercase + string.digits
    return "%s_%s%s" % (
        prefix,
        format(int(time.time() * 1000), "x"),
        "".join(random.choice(alphabet) for _ in range(4)),
    )


def log(level, msg, *args):
    if args:
        msg = msg % args
    line = "%s [%s] %s" % (datetime.now().strftime("%Y-%m-%d %H:%M:%S"), level, msg)
    print(line, flush=True)
    Bus.publish("log", {"line": line, "level": level})


def clip(text, limit=MAX_TOOL_OUTPUT):
    text = text if isinstance(text, str) else str(text)
    if len(text) <= limit:
        return text
    return text[:limit] + "\n… (%d karakter kırpıldı)" % (len(text) - limit)


def html_to_text(html):
    """Bağımlılıksız, kaba ama işe yarar HTML -> metin dönüşümü."""
    html = re.sub(r"(?is)<(script|style|noscript)[^>]*>.*?</\1>", " ", html)
    html = re.sub(r"(?is)<br\s*/?>", "\n", html)
    html = re.sub(r"(?is)</(p|div|li|tr|h[1-6])>", "\n", html)
    text = re.sub(r"(?s)<[^>]+>", " ", html)
    text = (text.replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<")
                .replace("&gt;", ">").replace("&quot;", '"').replace("&#39;", "'"))
    text = re.sub(r"[ \t\x0b\f\r]+", " ", text)
    text = re.sub(r"\n\s*\n\s*", "\n\n", text)
    return text.strip()


class Bus:
    """Canlı olay yayını (uygulamanın /v1/events akışı için)."""
    _lock = threading.Lock()
    _subscribers = []

    @classmethod
    def subscribe(cls):
        q = queue.Queue(maxsize=200)
        with cls._lock:
            cls._subscribers.append(q)
        return q

    @classmethod
    def unsubscribe(cls, q):
        with cls._lock:
            if q in cls._subscribers:
                cls._subscribers.remove(q)

    @classmethod
    def publish(cls, event, data):
        with cls._lock:
            subs = list(cls._subscribers)
        for q in subs:
            try:
                q.put_nowait((event, data))
            except queue.Full:
                pass


# --------------------------------------------------------------------------
# Ayarlar ve kalıcı depo
# --------------------------------------------------------------------------

DEFAULT_CONFIG = {
    "token": "",
    "default_backend": "echo",
    "backends": {
        "openai": {"base_url": "https://api.openai.com/v1", "api_key": "", "default_model": "gpt-4o-mini"},
        "xai": {"base_url": "https://api.x.ai/v1", "api_key": "", "default_model": "grok-3"},
        "openrouter": {"base_url": "https://openrouter.ai/api/v1", "api_key": "", "default_model": ""},
        "groq": {"base_url": "https://api.groq.com/openai/v1", "api_key": "", "default_model": ""},
        "ollama": {"base_url": "http://127.0.0.1:11434", "api_key": "", "default_model": "llama3.1:8b"},
        "anthropic": {"base_url": "https://api.anthropic.com", "api_key": "", "default_model": "claude-sonnet-4-5"},
        "echo": {"base_url": "", "api_key": "", "default_model": "echo"},
    },
    "allow_shell": False,
    "shell_allowlist": [],
    "shell_timeout": 25,
    "workspace": "workspace",
    "http_timeout": 30,
    "scheduler_enabled": True,
}


class Store:
    """Diskte JSON tutan, kilitli, basit depo."""

    def __init__(self, data_dir):
        self.dir = os.path.abspath(data_dir)
        self.lock = threading.RLock()
        os.makedirs(self.dir, exist_ok=True)
        os.makedirs(os.path.join(self.dir, "runs"), exist_ok=True)
        os.makedirs(os.path.join(self.dir, "sessions"), exist_ok=True)
        self.config = self._load("config.json", dict(DEFAULT_CONFIG))
        # Eksik anahtarları tamamla (sürüm yükseltmelerinde bozulmasın).
        merged = dict(DEFAULT_CONFIG)
        merged.update(self.config or {})
        backends = dict(DEFAULT_CONFIG["backends"])
        backends.update((self.config or {}).get("backends") or {})
        merged["backends"] = backends
        self.config = merged
        self.bots = self._load("bots.json", {})
        self.last_runs = self._load("last_runs.json", {})

    # -- düşük seviye ------------------------------------------------------
    def _path(self, name):
        return os.path.join(self.dir, name)

    def _load(self, name, default):
        try:
            with open(self._path(name), "r", encoding="utf-8") as f:
                return json.load(f)
        except Exception:
            return default

    def _save(self, name, data):
        tmp = self._path(name + ".tmp")
        with open(tmp, "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False, indent=2)
        os.replace(tmp, self._path(name))

    # -- ayarlar -----------------------------------------------------------
    def save_config(self):
        with self.lock:
            self._save("config.json", self.config)

    # -- botlar ------------------------------------------------------------
    def list_bots(self):
        with self.lock:
            return list(self.bots.values())

    def get_bot(self, bot_id):
        with self.lock:
            return self.bots.get(bot_id)

    def put_bot(self, bot):
        with self.lock:
            bot_id = bot.get("id")
            if not bot_id:
                bot_id = rand_id("bot")
                bot["id"] = bot_id
            bot["updated_at"] = now_ms()
            bot.setdefault("created_at", now_ms())
            self.bots[bot_id] = bot
            self._save("bots.json", self.bots)
            return bot

    def delete_bot(self, bot_id):
        with self.lock:
            existed = self.bots.pop(bot_id, None) is not None
            if existed:
                self._save("bots.json", self.bots)
            return existed

    # -- oturum (sohbet hafızası) -----------------------------------------
    def session_path(self, bot_id, session):
        safe = re.sub(r"[^A-Za-z0-9_.-]", "_", "%s__%s" % (bot_id, session))
        return os.path.join("sessions", safe + ".json")

    def get_session(self, bot_id, session="default"):
        return self._load(self.session_path(bot_id, session), [])

    def append_session(self, bot_id, session, messages, keep=60):
        with self.lock:
            history = self.get_session(bot_id, session)
            history.extend(messages)
            history = history[-keep:]
            self._save(self.session_path(bot_id, session), history)
            return history

    def clear_session(self, bot_id, session="default"):
        with self.lock:
            try:
                os.remove(self._path(self.session_path(bot_id, session)))
            except OSError:
                pass

    # -- çalışmalar --------------------------------------------------------
    def save_run(self, run):
        with self.lock:
            self._save(os.path.join("runs", run["run_id"] + ".json"), run)

    def get_run(self, run_id):
        if not re.match(r"^[A-Za-z0-9_.-]+$", run_id or ""):
            return None
        return self._load(os.path.join("runs", run_id + ".json"), None)

    def list_runs(self, bot_id=None, limit=20):
        runs_dir = self._path("runs")
        try:
            names = os.listdir(runs_dir)
        except OSError:
            return []
        items = []
        for name in names:
            if not name.endswith(".json"):
                continue
            full = os.path.join(runs_dir, name)
            try:
                items.append((os.path.getmtime(full), name[:-5]))
            except OSError:
                continue
        items.sort(reverse=True)
        out = []
        for _, run_id in items:
            run = self.get_run(run_id)
            if not run:
                continue
            if bot_id and run.get("bot_id") != bot_id:
                continue
            out.append({
                "run_id": run.get("run_id"),
                "bot_id": run.get("bot_id", ""),
                "status": run.get("status", ""),
                "started_at": run.get("started_at", 0),
                "duration_ms": run.get("duration_ms", 0),
                "preview": run.get("preview", ""),
                "trigger": run.get("trigger", "manual"),
            })
            if len(out) >= limit:
                break
        return out

    def set_last_run(self, bot_id, ts_ms):
        with self.lock:
            self.last_runs[bot_id] = ts_ms
            self._save("last_runs.json", self.last_runs)


# --------------------------------------------------------------------------
# Hatalar ve HTTP istemcisi
# --------------------------------------------------------------------------

class AgentError(Exception):
    def __init__(self, message, status=400):
        Exception.__init__(self, message)
        self.message = message
        self.status = status


SSL_CTX = ssl.create_default_context()


def http_open(url, method="GET", headers=None, body=None, timeout=60):
    data = body.encode("utf-8") if isinstance(body, str) else body
    req = urllib.request.Request(url, data=data, method=method)
    for key, value in (headers or {}).items():
        if value:
            req.add_header(key, value)
    try:
        return urllib.request.urlopen(req, timeout=timeout, context=SSL_CTX)
    except urllib.error.HTTPError as e:
        detail = ""
        try:
            detail = e.read().decode("utf-8", "replace")[:800]
        except Exception:
            pass
        message = detail
        try:
            parsed = json.loads(detail)
            if isinstance(parsed, dict):
                err = parsed.get("error")
                if isinstance(err, dict):
                    message = err.get("message") or detail
                elif isinstance(err, str):
                    message = err
                else:
                    message = parsed.get("message") or detail
        except Exception:
            pass
        raise AgentError("Model sağlayıcı hatası (HTTP %s): %s" % (e.code, (message or "").strip()[:400]),
                         status=502)
    except urllib.error.URLError as e:
        raise AgentError("Model sağlayıcıya bağlanılamadı: %s" % e.reason, status=502)


def http_json(url, method="GET", headers=None, body=None, timeout=60):
    with http_open(url, method, headers, body, timeout) as resp:
        raw = resp.read().decode("utf-8", "replace")
    try:
        return json.loads(raw)
    except ValueError:
        raise AgentError("Sağlayıcıdan geçersiz JSON geldi: %s" % raw[:200], status=502)


# --------------------------------------------------------------------------
# Model arka uçları
# --------------------------------------------------------------------------
# Hepsi aynı olay dizisini üretir:
#   ("token", metin) | ("tool_calls", [ {id,name,arguments} ]) |
#   ("usage", {...}) | ("done", bitiş_nedeni)

OPENAI_STYLE = ("openai", "xai", "openrouter", "groq", "together", "custom")


def backend_conf(store, name):
    conf = dict(store.config.get("backends", {}).get(name) or {})
    if not conf and name in DEFAULT_CONFIG["backends"]:
        conf = dict(DEFAULT_CONFIG["backends"][name])
    return conf


def openai_chat_url(base):
    base = (base or "").rstrip("/")
    if base.endswith("/chat/completions"):
        return base
    if base.endswith("/v1"):
        return base + "/chat/completions"
    return base + "/v1/chat/completions"


def stream_openai(conf, model, messages, tools, temperature, max_tokens, timeout):
    payload = {
        "model": model,
        "messages": messages,
        "stream": True,
        "temperature": temperature,
    }
    if max_tokens:
        payload["max_tokens"] = max_tokens
    if tools:
        payload["tools"] = tools
        payload["tool_choice"] = "auto"
    headers = {"Content-Type": "application/json"}
    if conf.get("api_key"):
        headers["Authorization"] = "Bearer " + conf["api_key"]
    url = openai_chat_url(conf.get("base_url", ""))

    pending = {}
    with http_open(url, "POST", headers, json.dumps(payload), timeout) as resp:
        for raw in resp:
            line = raw.decode("utf-8", "replace").strip()
            if not line or line.startswith(":"):
                continue
            if not line.startswith("data:"):
                continue
            data = line[5:].strip()
            if data == "[DONE]":
                break
            try:
                chunk = json.loads(data)
            except ValueError:
                continue
            if isinstance(chunk.get("error"), dict):
                raise AgentError(chunk["error"].get("message", "Sağlayıcı hatası"), status=502)
            choices = chunk.get("choices") or []
            if chunk.get("usage"):
                yield ("usage", chunk["usage"])
            if not choices:
                continue
            choice = choices[0]
            delta = choice.get("delta") or choice.get("message") or {}
            piece = delta.get("content")
            if piece:
                yield ("token", piece)
            for call in (delta.get("tool_calls") or []):
                idx = call.get("index", 0)
                slot = pending.setdefault(idx, {"id": "", "name": "", "arguments": ""})
                if call.get("id"):
                    slot["id"] = call["id"]
                fn = call.get("function") or {}
                if fn.get("name"):
                    slot["name"] = fn["name"]
                if fn.get("arguments"):
                    slot["arguments"] += fn["arguments"]
            finish = choice.get("finish_reason")
            if finish:
                if pending:
                    yield ("tool_calls", [pending[k] for k in sorted(pending)])
                    pending = {}
                yield ("done", finish)
                return
    if pending:
        yield ("tool_calls", [pending[k] for k in sorted(pending)])
    yield ("done", "stop")


def stream_ollama(conf, model, messages, tools, temperature, max_tokens, timeout):
    payload = {
        "model": model,
        "messages": messages,
        "stream": True,
        "options": {"temperature": temperature},
    }
    if max_tokens:
        payload["options"]["num_predict"] = max_tokens
    if tools:
        payload["tools"] = tools
    base = (conf.get("base_url") or "http://127.0.0.1:11434").rstrip("/")
    headers = {"Content-Type": "application/json"}
    with http_open(base + "/api/chat", "POST", headers, json.dumps(payload), timeout) as resp:
        for raw in resp:
            line = raw.decode("utf-8", "replace").strip()
            if not line:
                continue
            try:
                chunk = json.loads(line)
            except ValueError:
                continue
            if chunk.get("error"):
                raise AgentError(str(chunk["error"]), status=502)
            message = chunk.get("message") or {}
            if message.get("content"):
                yield ("token", message["content"])
            calls = message.get("tool_calls") or []
            if calls:
                normalized = []
                for call in calls:
                    fn = call.get("function") or {}
                    args = fn.get("arguments")
                    if not isinstance(args, str):
                        args = json.dumps(args or {}, ensure_ascii=False)
                    normalized.append({"id": call.get("id") or rand_id("call"),
                                       "name": fn.get("name", ""), "arguments": args})
                yield ("tool_calls", normalized)
            if chunk.get("done"):
                yield ("usage", {"prompt_tokens": chunk.get("prompt_eval_count", 0),
                                 "completion_tokens": chunk.get("eval_count", 0)})
                yield ("done", chunk.get("done_reason") or "stop")
                return
    yield ("done", "stop")


def stream_anthropic(conf, model, messages, tools, temperature, max_tokens, timeout):
    """Claude API (metin akışı). Araç kullanımı bu arka uçta desteklenmez."""
    system = " ".join(m["content"] for m in messages if m.get("role") == "system")
    convo = []
    for m in messages:
        role = m.get("role")
        if role in ("user", "assistant") and m.get("content"):
            convo.append({"role": role, "content": m["content"]})
    payload = {
        "model": model,
        "max_tokens": max_tokens or 1024,
        "temperature": temperature,
        "stream": True,
        "messages": convo or [{"role": "user", "content": "Merhaba"}],
    }
    if system.strip():
        payload["system"] = system.strip()
    headers = {
        "Content-Type": "application/json",
        "x-api-key": conf.get("api_key", ""),
        "anthropic-version": "2023-06-01",
    }
    base = (conf.get("base_url") or "https://api.anthropic.com").rstrip("/")
    with http_open(base + "/v1/messages", "POST", headers, json.dumps(payload), timeout) as resp:
        for raw in resp:
            line = raw.decode("utf-8", "replace").strip()
            if not line.startswith("data:"):
                continue
            data = line[5:].strip()
            if not data or data == "[DONE]":
                continue
            try:
                chunk = json.loads(data)
            except ValueError:
                continue
            kind = chunk.get("type")
            if kind == "content_block_delta":
                delta = chunk.get("delta") or {}
                if delta.get("type") == "text_delta" and delta.get("text"):
                    yield ("token", delta["text"])
            elif kind == "message_delta":
                usage = chunk.get("usage") or {}
                if usage:
                    yield ("usage", {"prompt_tokens": usage.get("input_tokens", 0),
                                     "completion_tokens": usage.get("output_tokens", 0)})
            elif kind == "message_stop":
                yield ("done", "stop")
                return
            elif kind == "error":
                raise AgentError((chunk.get("error") or {}).get("message", "Claude API hatası"), status=502)
    yield ("done", "stop")


def stream_echo(conf, model, messages, tools, temperature, max_tokens, timeout):
    """Anahtarsız test arka ucu: son kullanıcı mesajını yankılar."""
    last = ""
    for m in reversed(messages):
        if m.get("role") == "user":
            last = m.get("content") or ""
            break
    reply = "🔁 Echo arka ucu çalışıyor. Aldığım mesaj: %s" % (last.strip() or "(boş)")
    for word in reply.split(" "):
        yield ("token", word + " ")
        time.sleep(0.01)
    yield ("usage", {"prompt_tokens": sum(len((m.get("content") or "")) for m in messages) // 4,
                     "completion_tokens": len(reply) // 4})
    yield ("done", "stop")


STREAMERS = {
    "ollama": stream_ollama,
    "anthropic": stream_anthropic,
    "echo": stream_echo,
}


def stream_backend(store, backend, model, messages, tools, temperature, max_tokens):
    conf = backend_conf(store, backend)
    timeout = int(store.config.get("http_timeout", 30)) + 90
    streamer = STREAMERS.get(backend, stream_openai)
    if backend not in STREAMERS and not conf.get("base_url"):
        raise AgentError("'%s' arka ucu tanımsız. hermes.json içinde base_url girin." % backend)
    if backend not in ("echo", "ollama") and not conf.get("api_key"):
        raise AgentError("'%s' arka ucu için API anahtarı ayarlı değil "
                         "(hermes.json > backends > %s > api_key)." % (backend, backend))
    return streamer(conf, model, messages, tools, temperature, max_tokens, timeout)


def list_models(store, backend):
    conf = backend_conf(store, backend)
    if backend == "echo":
        return ["echo"]
    if backend == "ollama":
        base = (conf.get("base_url") or "").rstrip("/")
        data = http_json(base + "/api/tags", timeout=15)
        return sorted(m.get("name", "") for m in (data.get("models") or []))
    if backend == "anthropic":
        return ["claude-sonnet-4-5", "claude-opus-4-1", "claude-haiku-4-5"]
    base = (conf.get("base_url") or "").rstrip("/")
    if not base:
        return []
    url = (base + "/models") if base.endswith("/v1") else (base + "/v1/models")
    headers = {"Authorization": "Bearer " + conf.get("api_key", "")} if conf.get("api_key") else {}
    data = http_json(url, headers=headers, timeout=15)
    return sorted(m.get("id", "") for m in (data.get("data") or []) if m.get("id"))


# --------------------------------------------------------------------------
# Araçlar (tools)
# --------------------------------------------------------------------------

def workspace_dir(store):
    path = store.config.get("workspace") or "workspace"
    if not os.path.isabs(path):
        path = os.path.join(store.dir, path)
    os.makedirs(path, exist_ok=True)
    return os.path.realpath(path)


def safe_workspace_path(store, rel):
    root = workspace_dir(store)
    target = os.path.realpath(os.path.join(root, rel or ""))
    if target != root and not target.startswith(root + os.sep):
        raise AgentError("Dosya yolu çalışma klasörünün dışına çıkamaz: %s" % rel)
    return target


def tool_now(store, args):
    tz = timedelta(hours=float(args.get("utc_offset_hours", 3)))
    local = datetime.now(timezone.utc).astimezone(timezone(tz))
    return local.strftime("%d.%m.%Y %H:%M:%S (UTC%z)")


def tool_http_get(store, args):
    url = (args.get("url") or "").strip()
    if not url:
        raise AgentError("url parametresi gerekli")
    if not url.startswith(("http://", "https://")):
        url = "https://" + url
    headers = {"User-Agent": "HermesAgent/%s (+https://github.com/fatsar/fatsar)" % VERSION,
               "Accept": "text/html,application/json;q=0.9,*/*;q=0.8"}
    timeout = int(store.config.get("http_timeout", 30))
    with http_open(url, "GET", headers, None, timeout) as resp:
        ctype = resp.headers.get("Content-Type", "")
        raw = resp.read(2 * 1024 * 1024).decode("utf-8", "replace")
    if "json" in ctype:
        return clip(raw)
    return clip(html_to_text(raw))


def tool_web_search(store, args):
    query = (args.get("query") or "").strip()
    if not query:
        raise AgentError("query parametresi gerekli")
    url = "https://duckduckgo.com/html/?q=" + urllib.parse.quote(query)
    headers = {"User-Agent": "Mozilla/5.0 (compatible; HermesAgent/%s)" % VERSION}
    with http_open(url, "GET", headers, None, int(store.config.get("http_timeout", 30))) as resp:
        html = resp.read(1024 * 1024).decode("utf-8", "replace")
    results = []
    for match in re.finditer(r'(?is)<a[^>]+class="result__a"[^>]*href="([^"]+)"[^>]*>(.*?)</a>', html):
        link, title = match.group(1), html_to_text(match.group(2))
        if "uddg=" in link:
            parsed = urllib.parse.parse_qs(urllib.parse.urlparse(link).query).get("uddg")
            if parsed:
                link = parsed[0]
        results.append("%d. %s\n   %s" % (len(results) + 1, title, link))
        if len(results) >= 8:
            break
    return "\n".join(results) if results else "Sonuç bulunamadı."


def tool_read_file(store, args):
    target = safe_workspace_path(store, args.get("path", ""))
    if os.path.isdir(target):
        return "Klasör içeriği:\n" + "\n".join(sorted(os.listdir(target))[:200])
    if not os.path.exists(target):
        raise AgentError("Dosya yok: %s" % args.get("path"))
    with open(target, "r", encoding="utf-8", errors="replace") as f:
        return clip(f.read())


def tool_write_file(store, args):
    target = safe_workspace_path(store, args.get("path", ""))
    content = args.get("content", "")
    os.makedirs(os.path.dirname(target), exist_ok=True)
    with open(target, "w", encoding="utf-8") as f:
        f.write(content)
    return "Yazıldı: %s (%d karakter)" % (args.get("path"), len(content))


def tool_shell(store, args):
    if not store.config.get("allow_shell"):
        raise AgentError("Kabuk komutu kapalı. Agent'ı --allow-shell ile başlatın "
                         "veya hermes.json içinde allow_shell=true yapın.")
    command = (args.get("command") or "").strip()
    if not command:
        raise AgentError("command parametresi gerekli")
    allowlist = store.config.get("shell_allowlist") or []
    if allowlist and not any(command.startswith(prefix) for prefix in allowlist):
        raise AgentError("Bu komut izin listesinde değil. İzin verilenler: %s" % ", ".join(allowlist))
    timeout = int(store.config.get("shell_timeout", 25))
    log("INFO", "shell: %s", command)
    try:
        proc = subprocess.run(command, shell=True, capture_output=True, text=True,
                              timeout=timeout, cwd=workspace_dir(store))
    except subprocess.TimeoutExpired:
        return "Komut %d saniyede bitmedi ve durduruldu." % timeout
    out = (proc.stdout or "") + (("\n[stderr]\n" + proc.stderr) if proc.stderr else "")
    return clip("çıkış kodu=%d\n%s" % (proc.returncode, out.strip()))


TOOLS = {
    "now": {
        "fn": tool_now,
        "schema": {
            "type": "function",
            "function": {
                "name": "now",
                "description": "Sunucunun güncel tarih ve saatini döndürür.",
                "parameters": {
                    "type": "object",
                    "properties": {"utc_offset_hours": {"type": "number", "description": "Saat dilimi farkı, varsayılan 3 (Türkiye)"}},
                },
            },
        },
    },
    "http_get": {
        "fn": tool_http_get,
        "schema": {
            "type": "function",
            "function": {
                "name": "http_get",
                "description": "Verilen adresi indirir ve metin içeriğini döndürür.",
                "parameters": {
                    "type": "object",
                    "properties": {"url": {"type": "string", "description": "Tam adres"}},
                    "required": ["url"],
                },
            },
        },
    },
    "web_search": {
        "fn": tool_web_search,
        "schema": {
            "type": "function",
            "function": {
                "name": "web_search",
                "description": "İnternette arama yapar ve ilk sonuçların başlık/adreslerini verir.",
                "parameters": {
                    "type": "object",
                    "properties": {"query": {"type": "string"}},
                    "required": ["query"],
                },
            },
        },
    },
    "read_file": {
        "fn": tool_read_file,
        "schema": {
            "type": "function",
            "function": {
                "name": "read_file",
                "description": "Agent çalışma klasöründeki bir dosyayı veya klasör listesini okur.",
                "parameters": {
                    "type": "object",
                    "properties": {"path": {"type": "string"}},
                    "required": ["path"],
                },
            },
        },
    },
    "write_file": {
        "fn": tool_write_file,
        "schema": {
            "type": "function",
            "function": {
                "name": "write_file",
                "description": "Agent çalışma klasörüne dosya yazar.",
                "parameters": {
                    "type": "object",
                    "properties": {"path": {"type": "string"}, "content": {"type": "string"}},
                    "required": ["path", "content"],
                },
            },
        },
    },
    "shell": {
        "fn": tool_shell,
        "schema": {
            "type": "function",
            "function": {
                "name": "shell",
                "description": "Sunucuda kabuk komutu çalıştırır ve çıktısını verir.",
                "parameters": {
                    "type": "object",
                    "properties": {"command": {"type": "string", "description": "Çalıştırılacak komut"}},
                    "required": ["command"],
                },
            },
        },
    },
}


def available_tools(store):
    names = []
    for name in TOOLS:
        if name == "shell" and not store.config.get("allow_shell"):
            continue
        names.append(name)
    return sorted(names)


# --------------------------------------------------------------------------
# Çalıştırma motoru
# --------------------------------------------------------------------------

CANCELLED = {}
CANCEL_LOCK = threading.Lock()


def cancel_run(run_id):
    with CANCEL_LOCK:
        event = CANCELLED.get(run_id)
    if event:
        event.set()
        return True
    return False


def run_bot(store, bot, user_input, history=None, session="default", emit=None, trigger="manual"):
    """
    Botu çalıştırır. emit(olay_adı, veri) ile olayları bildirir.
    Dönüş: run sözlüğü (kaydedilmiş hâli).
    """
    emit = emit or (lambda *a, **k: None)
    run_id = rand_id("run")
    started = time.time()
    stop_event = threading.Event()
    with CANCEL_LOCK:
        CANCELLED[run_id] = stop_event

    backend = (bot.get("backend") or store.config.get("default_backend") or "echo").strip()
    conf = backend_conf(store, backend)
    model = (bot.get("model") or conf.get("default_model") or "").strip()
    temperature = float(bot.get("temperature", 0.7))
    max_tokens = int(bot.get("max_tokens", 1024) or 0)
    memory_turns = int(bot.get("memory_turns", 12) or 0)

    wanted = [t for t in (bot.get("tools") or []) if t in TOOLS]
    if not store.config.get("allow_shell"):
        wanted = [t for t in wanted if t != "shell"]
    tool_schemas = [TOOLS[t]["schema"] for t in wanted] if backend != "anthropic" else []

    messages = []
    if (bot.get("system_prompt") or "").strip():
        messages.append({"role": "system", "content": bot["system_prompt"].strip()})
    if history is None:
        history = store.get_session(bot["id"], session)
        if memory_turns > 0:
            history = history[-(memory_turns * 2):]
        else:
            history = []
    for m in history:
        role, content = m.get("role"), m.get("content")
        if role in ("user", "assistant") and content:
            messages.append({"role": role, "content": content})
    messages.append({"role": "user", "content": user_input})

    emit("run.started", {"run_id": run_id, "bot_id": bot.get("id"), "backend": backend,
                         "model": model, "trigger": trigger, "ts": time.time()})
    Bus.publish("bot.status", {"bot_id": bot.get("id"), "status": "running", "run_id": run_id})

    events = []
    answer_parts = []
    usage_total = {"prompt_tokens": 0, "completion_tokens": 0}
    status = "ok"
    error_message = ""

    try:
        for step in range(MAX_TOOL_STEPS):
            if stop_event.is_set():
                status = "cancelled"
                break
            text_parts = []
            tool_calls = []
            for kind, payload in stream_backend(store, backend, model, messages,
                                                tool_schemas, temperature, max_tokens):
                if stop_event.is_set():
                    status = "cancelled"
                    break
                if kind == "token":
                    text_parts.append(payload)
                    emit("token", {"text": payload})
                elif kind == "tool_calls":
                    tool_calls = payload
                elif kind == "usage":
                    usage_total["prompt_tokens"] += int(payload.get("prompt_tokens", 0) or 0)
                    usage_total["completion_tokens"] += int(payload.get("completion_tokens", 0) or 0)
                elif kind == "done":
                    break
            if status == "cancelled":
                break

            text = "".join(text_parts)
            if text:
                answer_parts.append(text)

            if not tool_calls:
                break

            assistant_msg = {"role": "assistant", "content": text or None, "tool_calls": [
                {"id": c["id"] or rand_id("call"), "type": "function",
                 "function": {"name": c["name"], "arguments": c["arguments"] or "{}"}}
                for c in tool_calls
            ]}
            messages.append(assistant_msg)

            for call in tool_calls:
                name = call.get("name", "")
                try:
                    args = json.loads(call.get("arguments") or "{}")
                    if not isinstance(args, dict):
                        args = {}
                except ValueError:
                    args = {}
                emit("tool.call", {"name": name, "args": args, "call_id": call.get("id", "")})
                events.append({"type": "tool.call", "name": name, "args": args})
                try:
                    if name not in TOOLS or name not in wanted:
                        raise AgentError("'%s' aracı bu bot için açık değil." % name)
                    result = TOOLS[name]["fn"](store, args)
                    ok = True
                except AgentError as e:
                    result, ok = "HATA: " + e.message, False
                except Exception as e:  # araç patlarsa model devam edebilsin
                    result, ok = "HATA: %s" % e, False
                emit("tool.result", {"name": name, "ok": ok, "result": clip(result, 1500),
                                     "call_id": call.get("id", "")})
                events.append({"type": "tool.result", "name": name, "ok": ok, "result": clip(result, 2000)})
                messages.append({"role": "tool", "tool_call_id": call.get("id") or rand_id("call"),
                                 "name": name, "content": clip(result)})
        else:
            status = "tool_limit"
    except AgentError as e:
        status, error_message = "error", e.message
        emit("error", {"message": e.message})
    except Exception as e:
        status, error_message = "error", str(e)
        log("ERROR", "run %s: %s\n%s", run_id, e, traceback.format_exc())
        emit("error", {"message": str(e)})

    answer = "\n".join(p for p in answer_parts if p).strip()
    duration = int((time.time() - started) * 1000)

    if status in ("ok", "tool_limit") and answer:
        emit("message", {"role": "assistant", "content": answer})
    if usage_total["prompt_tokens"] or usage_total["completion_tokens"]:
        emit("usage", usage_total)
    emit("run.finished", {"run_id": run_id, "status": status, "duration_ms": duration})
    Bus.publish("bot.status", {"bot_id": bot.get("id"), "status": "idle", "run_id": run_id,
                               "last_status": status})

    run = {
        "run_id": run_id,
        "bot_id": bot.get("id"),
        "bot_name": bot.get("name", ""),
        "status": status,
        "trigger": trigger,
        "backend": backend,
        "model": model,
        "input": user_input,
        "output": answer,
        "error": error_message,
        "usage": usage_total,
        "events": events,
        "started_at": started,
        "duration_ms": duration,
        "preview": (answer or error_message or "")[:160],
    }
    store.save_run(run)
    store.set_last_run(bot.get("id"), now_ms())
    if status in ("ok", "tool_limit") and answer:
        store.append_session(bot["id"], session, [
            {"role": "user", "content": user_input, "ts": now_ms()},
            {"role": "assistant", "content": answer, "ts": now_ms()},
        ])
    with CANCEL_LOCK:
        CANCELLED.pop(run_id, None)
    return run


# --------------------------------------------------------------------------
# Zamanlayıcı (telefon kapalıyken de çalışır)
# --------------------------------------------------------------------------

DAY_MS = 24 * 60 * 60 * 1000


def schedule_due(bot, last_run, now_ms_value):
    sched = bot.get("schedule") or {}
    mode = (sched.get("mode") or "OFF").upper()
    prompt = (sched.get("prompt") or "").strip()
    if not bot.get("enabled", True) or mode == "OFF" or not prompt:
        return False
    if mode == "INTERVAL":
        every = max(1, int(sched.get("every_minutes", 60))) * 60 * 1000
        return last_run is None or (now_ms_value - last_run) >= every
    if mode == "DAILY":
        offset = int(sched.get("tz_offset_minutes", 180)) * 60 * 1000
        local = now_ms_value + offset
        start_of_day = local - (local % DAY_MS)
        minute_of_day = max(0, min(23, int(sched.get("at_hour", 9)))) * 60 + \
            max(0, min(59, int(sched.get("at_minute", 0))))
        target = start_of_day + minute_of_day * 60 * 1000 - offset
        return now_ms_value >= target and (last_run is None or last_run < target)
    return False


def scheduler_loop(store, stop_event):
    log("INFO", "Zamanlayıcı başladı (30 sn'de bir kontrol).")
    while not stop_event.is_set():
        try:
            now = now_ms()
            for bot in store.list_bots():
                if stop_event.is_set():
                    break
                if not schedule_due(bot, store.last_runs.get(bot.get("id")), now):
                    continue
                prompt = (bot.get("schedule") or {}).get("prompt", "")
                log("INFO", "Zamanlanmış çalışma: %s", bot.get("name"))
                store.set_last_run(bot["id"], now)   # çift tetiklemeyi önce engelle
                try:
                    run_bot(store, bot, prompt, session="schedule", trigger="schedule")
                except Exception as e:
                    log("ERROR", "Zamanlanmış çalışma hatası (%s): %s", bot.get("name"), e)
        except Exception as e:
            log("ERROR", "Zamanlayıcı hatası: %s", e)
        stop_event.wait(30)


# --------------------------------------------------------------------------
# HTTP sunucusu
# --------------------------------------------------------------------------

class RateLimiter:
    def __init__(self, per_minute=RATE_LIMIT_PER_MIN):
        self.per_minute = per_minute
        self.lock = threading.Lock()
        self.hits = {}

    def allow(self, ip):
        now = time.time()
        with self.lock:
            bucket = [t for t in self.hits.get(ip, []) if now - t < 60]
            if len(bucket) >= self.per_minute:
                self.hits[ip] = bucket
                return False
            bucket.append(now)
            self.hits[ip] = bucket
            return True


RATE = RateLimiter()


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"
    server_version = "HermesAgent/" + VERSION
    store = None            # main() tarafından atanır

    # -- altyapı -----------------------------------------------------------
    def log_message(self, fmt, *args):
        pass  # kendi log'umuzu kullanıyoruz

    def _client_ip(self):
        return self.client_address[0] if self.client_address else "?"

    def _send(self, status, payload, ctype="application/json; charset=utf-8", extra=None):
        body = payload if isinstance(payload, bytes) else payload.encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Headers", "Authorization, Content-Type")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
        for key, value in (extra or {}).items():
            self.send_header(key, value)
        self.end_headers()
        try:
            self.wfile.write(body)
        except (BrokenPipeError, ConnectionResetError):
            pass

    def _json(self, status, data):
        self._send(status, json.dumps(data, ensure_ascii=False))

    def _error(self, status, message):
        self._json(status, {"ok": False, "error": {"message": message}})

    def _begin_sse(self):
        self.send_response(200)
        self.send_header("Content-Type", "text/event-stream; charset=utf-8")
        self.send_header("Cache-Control", "no-cache, no-store")
        self.send_header("X-Accel-Buffering", "no")
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Connection", "close")
        self.end_headers()

    def _sse(self, event, data):
        chunk = "event: %s\ndata: %s\n\n" % (event, json.dumps(data, ensure_ascii=False))
        self.wfile.write(chunk.encode("utf-8"))
        self.wfile.flush()

    def _body(self):
        length = int(self.headers.get("Content-Length") or 0)
        if length <= 0:
            return {}
        if length > MAX_BODY:
            raise AgentError("İstek gövdesi çok büyük", status=413)
        raw = self.rfile.read(length).decode("utf-8", "replace")
        try:
            return json.loads(raw) if raw.strip() else {}
        except ValueError:
            raise AgentError("Geçersiz JSON gövdesi")

    def _authorized(self, query):
        expected = self.store.config.get("token") or ""
        if not expected:
            return True
        header = self.headers.get("Authorization", "")
        given = header[7:].strip() if header.lower().startswith("bearer ") else ""
        if not given:
            given = (query.get("token") or [""])[0]
        return hmac.compare_digest(given, expected)

    # -- yönlendirme -------------------------------------------------------
    def do_OPTIONS(self):
        self._send(204, b"")

    def do_GET(self):
        self._route("GET")

    def do_POST(self):
        self._route("POST")

    def do_PUT(self):
        self._route("PUT")

    def do_DELETE(self):
        self._route("DELETE")

    def _route(self, method):
        parsed = urllib.parse.urlparse(self.path)
        path = parsed.path.rstrip("/") or "/"
        query = urllib.parse.parse_qs(parsed.query)
        try:
            if not RATE.allow(self._client_ip()):
                return self._error(429, "Çok fazla istek, biraz bekleyin.")
            if path == "/" and method == "GET":
                return self._send(200, STATUS_PAGE % {"version": VERSION,
                                                      "uptime": int(time.time() - START_TIME)},
                                  ctype="text/html; charset=utf-8")
            if not path.startswith("/v1"):
                return self._error(404, "Bilinmeyen adres: %s" % path)
            if not self._authorized(query):
                return self._error(401, "Token hatalı veya eksik.")
            return self._dispatch(method, path, query)
        except AgentError as e:
            self._error(e.status, e.message)
        except (BrokenPipeError, ConnectionResetError):
            pass
        except Exception as e:
            log("ERROR", "%s %s -> %s\n%s", method, path, e, traceback.format_exc())
            try:
                self._error(500, "Sunucu hatası: %s" % e)
            except Exception:
                pass

    def _dispatch(self, method, path, query):
        store = self.store

        if path == "/v1/health" and method == "GET":
            return self._json(200, {
                "ok": True, "name": AGENT_NAME, "version": VERSION,
                "uptime_s": int(time.time() - START_TIME),
                "host": socket.gethostname(),
                "bots": len(store.bots),
                "backends": sorted(store.config.get("backends", {}).keys()),
                "default_backend": store.config.get("default_backend", "echo"),
                "tools": available_tools(store),
                "shell_enabled": bool(store.config.get("allow_shell")),
                "scheduler": bool(store.config.get("scheduler_enabled", True)),
            })

        if path == "/v1/models" and method == "GET":
            backend = (query.get("backend") or [store.config.get("default_backend", "echo")])[0]
            return self._json(200, {"backend": backend, "models": list_models(store, backend)})

        if path == "/v1/bots" and method == "GET":
            return self._json(200, {"bots": store.list_bots()})

        m = re.match(r"^/v1/bots/([^/]+)$", path)
        if m:
            bot_id = urllib.parse.unquote(m.group(1))
            if method == "PUT":
                bot = self._body()
                bot["id"] = bot_id
                saved = store.put_bot(bot)
                log("INFO", "Bot kaydedildi: %s (%s)", saved.get("name"), bot_id)
                return self._json(200, {"ok": True, "bot": saved})
            if method == "GET":
                bot = store.get_bot(bot_id)
                if not bot:
                    return self._error(404, "Bot bulunamadı: %s" % bot_id)
                return self._json(200, {"bot": bot})
            if method == "DELETE":
                ok = store.delete_bot(bot_id)
                store.clear_session(bot_id, "default")
                return self._json(200 if ok else 404, {"ok": ok})

        m = re.match(r"^/v1/bots/([^/]+)/runs$", path)
        if m:
            bot_id = urllib.parse.unquote(m.group(1))
            if method == "GET":
                limit = int((query.get("limit") or ["20"])[0])
                return self._json(200, {"runs": store.list_runs(bot_id, min(limit, 100))})
            if method == "POST":
                return self._run(bot_id, self._body())

        m = re.match(r"^/v1/bots/([^/]+)/messages$", path)
        if m:
            bot_id = urllib.parse.unquote(m.group(1))
            session = (query.get("session") or ["default"])[0]
            if method == "GET":
                return self._json(200, {"messages": store.get_session(bot_id, session)})
            if method == "DELETE":
                store.clear_session(bot_id, session)
                return self._json(200, {"ok": True})

        m = re.match(r"^/v1/runs/([^/]+)/cancel$", path)
        if m and method == "POST":
            return self._json(200, {"ok": cancel_run(urllib.parse.unquote(m.group(1)))})

        m = re.match(r"^/v1/runs/([^/]+)$", path)
        if m and method == "GET":
            run = store.get_run(urllib.parse.unquote(m.group(1)))
            if not run:
                return self._error(404, "Çalışma bulunamadı.")
            return self._json(200, {"run": run})

        if path == "/v1/runs" and method == "GET":
            limit = int((query.get("limit") or ["20"])[0])
            return self._json(200, {"runs": store.list_runs(None, min(limit, 100))})

        if path == "/v1/events" and method == "GET":
            return self._events()

        if path == "/v1/config" and method == "GET":
            safe = json.loads(json.dumps(store.config))
            safe["token"] = "***"
            for name, conf in (safe.get("backends") or {}).items():
                if conf.get("api_key"):
                    conf["api_key"] = conf["api_key"][:4] + "…" + conf["api_key"][-4:]
            return self._json(200, {"config": safe})

        return self._error(404, "Bilinmeyen adres: %s" % path)

    # -- bot çalıştırma ----------------------------------------------------
    def _run(self, bot_id, body):
        store = self.store
        incoming = body.get("bot")
        if isinstance(incoming, dict) and incoming.get("id"):
            bot = store.put_bot(incoming)          # uygulamadaki tanım sunucuya da yazılır
        else:
            bot = store.get_bot(bot_id)
        if not bot:
            raise AgentError("Bot bulunamadı: %s. Uygulamadan bot tanımını gönderin." % bot_id, status=404)

        user_input = (body.get("input") or "").strip()
        if not user_input:
            raise AgentError("input alanı boş olamaz.")
        session = body.get("session") or "default"
        history = body.get("history")
        if history is not None and not isinstance(history, list):
            history = None
        stream = bool(body.get("stream", True))

        if not stream:
            run = run_bot(store, bot, user_input, history=history, session=session, trigger="api")
            return self._json(200, {"run": run})

        self._begin_sse()
        lock = threading.Lock()
        live = {"open": True}

        def emit(event, data):
            # Telefon bağlantıyı koparsa çalışma sunucuda sürer ve kaydedilir;
            # uygulama sonucu daha sonra "çalışma geçmişi"nden görür.
            if not live["open"]:
                return
            with lock:
                try:
                    self._sse(event, data)
                except (BrokenPipeError, ConnectionResetError, OSError, ValueError):
                    live["open"] = False
                    log("INFO", "İstemci akışı kapattı; çalışma arka planda sürüyor.")

        try:
            run_bot(store, bot, user_input, history=history, session=session,
                    emit=emit, trigger=body.get("trigger") or "manual")
        except AgentError as e:
            if live["open"]:
                try:
                    self._sse("error", {"message": e.message})
                except Exception:
                    pass
        self.close_connection = True

    # -- canlı olay akışı --------------------------------------------------
    def _events(self):
        q = Bus.subscribe()
        self._begin_sse()
        try:
            self._sse("hello", {"name": AGENT_NAME, "version": VERSION, "ts": time.time()})
            last_ping = time.time()
            while True:
                try:
                    event, data = q.get(timeout=5)
                    self._sse(event, data)
                except queue.Empty:
                    if time.time() - last_ping > 15:
                        self.wfile.write(b": ping\n\n")
                        self.wfile.flush()
                        last_ping = time.time()
        except (BrokenPipeError, ConnectionResetError, OSError):
            pass
        finally:
            Bus.unsubscribe(q)
            self.close_connection = True


STATUS_PAGE = """<!doctype html>
<html lang="tr"><head><meta charset="utf-8"><title>Hermes Agent</title>
<meta name="viewport" content="width=device-width,initial-scale=1">
<style>body{font-family:system-ui,sans-serif;background:#0f1115;color:#e6e8ee;display:flex;
min-height:90vh;align-items:center;justify-content:center;text-align:center}
.card{border:1px solid #2a2f3a;border-radius:16px;padding:32px;max-width:420px}
h1{margin:0 0 8px;font-size:20px}p{color:#9aa3b2;margin:6px 0;font-size:14px}
code{background:#1a1e27;padding:2px 6px;border-radius:6px}</style></head>
<body><div class="card"><h1>🤖 Hermes Agent çalışıyor</h1>
<p>Sürüm %(version)s · %(uptime)s saniyedir açık</p>
<p>Telefondaki <b>Hermes Bot Konsol</b> uygulamasından bu adresi ve token'ı girin.</p>
<p>API kökü: <code>/v1/health</code></p></div></body></html>"""


# --------------------------------------------------------------------------
# Eşleştirme kodu ve başlangıç
# --------------------------------------------------------------------------

def pairing_code(url, token, name):
    payload = json.dumps({"u": url, "t": token, "n": name}, ensure_ascii=False, separators=(",", ":"))
    encoded = base64.urlsafe_b64encode(payload.encode("utf-8")).decode("ascii").rstrip("=")
    return "HERMES1:" + encoded


def local_ips():
    found = []
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.settimeout(0.3)
        s.connect(("8.8.8.8", 80))
        found.append(s.getsockname()[0])
        s.close()
    except Exception:
        pass
    try:
        for info in socket.getaddrinfo(socket.gethostname(), None, socket.AF_INET):
            ip = info[4][0]
            if ip not in found and not ip.startswith("127."):
                found.append(ip)
    except Exception:
        pass
    return found


def set_dotted(config, expression):
    if "=" not in expression:
        raise SystemExit("--set biçimi: anahtar=değer (ör. --set backends.xai.api_key=xai-123)")
    key, value = expression.split("=", 1)
    parts = key.split(".")
    node = config
    for part in parts[:-1]:
        node = node.setdefault(part, {})
    if value.lower() in ("true", "false"):
        parsed = value.lower() == "true"
    else:
        try:
            parsed = int(value)
        except ValueError:
            parsed = value
    node[parts[-1]] = parsed


def build_parser():
    p = argparse.ArgumentParser(
        description="Hermes Agent — Android 'Hermes Bot Konsol' uygulamasının sunucu tarafı.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""Örnekler:
  python3 hermes_agent.py                                  # varsayılan: 0.0.0.0:8713
  python3 hermes_agent.py --port 9000 --allow-shell
  python3 hermes_agent.py --set backends.xai.api_key=xai-... --set default_backend=xai
  python3 hermes_agent.py --show-pairing --public-url https://bot.ornek.com
""")
    p.add_argument("--host", default=os.environ.get("HERMES_HOST", "0.0.0.0"))
    p.add_argument("--port", type=int, default=int(os.environ.get("HERMES_PORT", DEFAULT_PORT)))
    p.add_argument("--data-dir", default=os.environ.get("HERMES_DATA", "hermes-data"))
    p.add_argument("--token", default=os.environ.get("HERMES_TOKEN", ""),
                   help="Erişim anahtarı. Verilmezse ilk açılışta üretilir ve kaydedilir.")
    p.add_argument("--allow-shell", action="store_true", help="Kabuk (shell) aracını aç. Dikkatli kullanın.")
    p.add_argument("--no-scheduler", action="store_true", help="Zamanlanmış botları çalıştırma.")
    p.add_argument("--default-backend", default="", help="echo, openai, xai, ollama, anthropic, openrouter...")
    p.add_argument("--public-url", default=os.environ.get("HERMES_PUBLIC_URL", ""),
                   help="Eşleştirme kodunda kullanılacak dış adres.")
    p.add_argument("--set", action="append", default=[], metavar="ANAHTAR=DEĞER",
                   help="Ayar yaz ve çık (tekrarlanabilir).")
    p.add_argument("--show-pairing", action="store_true", help="Eşleştirme kodunu yazdır ve çık.")
    p.add_argument("--version", action="version", version="%s %s" % (AGENT_NAME, VERSION))
    return p


def main(argv=None):
    args = build_parser().parse_args(argv)
    store = Store(args.data_dir)

    if args.set:
        for expression in args.set:
            set_dotted(store.config, expression)
        store.save_config()
        print("Ayarlar kaydedildi: %s" % os.path.join(store.dir, "config.json"))
        return 0

    if args.token:
        store.config["token"] = args.token
    if not store.config.get("token"):
        store.config["token"] = "".join(random.choice(string.ascii_letters + string.digits) for _ in range(40))
        print("Yeni erişim anahtarı üretildi.")
    if args.allow_shell:
        store.config["allow_shell"] = True
    if args.no_scheduler:
        store.config["scheduler_enabled"] = False
    if args.default_backend:
        store.config["default_backend"] = args.default_backend
    store.save_config()

    token = store.config["token"]
    base_url = args.public_url or ("http://%s:%d" % (local_ips()[0] if local_ips() else "127.0.0.1", args.port))

    if args.show_pairing:
        print(pairing_code(base_url, token, socket.gethostname()))
        return 0

    Handler.store = store
    httpd = ThreadingHTTPServer((args.host, args.port), Handler)
    httpd.daemon_threads = True

    stop_event = threading.Event()
    if store.config.get("scheduler_enabled", True):
        threading.Thread(target=scheduler_loop, args=(store, stop_event), daemon=True).start()

    print("")
    print("  ╭──────────────────────────────────────────────────────────╮")
    print("  │  🤖  Hermes Agent %-38s │" % VERSION)
    print("  ╰──────────────────────────────────────────────────────────╯")
    print("  Dinleniyor      : http://%s:%d" % (args.host, args.port))
    for ip in local_ips():
        print("  Yerel adres     : http://%s:%d" % (ip, args.port))
    if args.public_url:
        print("  Dış adres       : %s" % args.public_url)
    print("  Veri klasörü    : %s" % store.dir)
    print("  Varsayılan model: %s" % store.config.get("default_backend"))
    print("  Kabuk aracı     : %s" % ("AÇIK (dikkat!)" if store.config.get("allow_shell") else "kapalı"))
    print("  Kayıtlı bot     : %d" % len(store.bots))
    print("")
    print("  Erişim anahtarı (token):")
    print("      %s" % token)
    print("")
    print("  Uygulamaya yapıştırılacak EŞLEŞTİRME KODU:")
    print("      %s" % pairing_code(base_url, token, socket.gethostname()))
    print("")
    print("  Durdurmak için Ctrl+C.")
    print("")
    log("INFO", "Agent hazır.")

    try:
        httpd.serve_forever(poll_interval=0.5)
    except KeyboardInterrupt:
        print("\nKapatılıyor…")
    finally:
        stop_event.set()
        httpd.shutdown()
        httpd.server_close()
    return 0


if __name__ == "__main__":
    sys.exit(main())
