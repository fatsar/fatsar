import logging
import threading
from datetime import datetime, timezone
from typing import List, Dict

import feedparser
from apscheduler.schedulers.background import BackgroundScheduler
from flask import Flask, render_template, jsonify

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

app = Flask(__name__)

NEWS_SOURCES = {
    "BBC": "http://feeds.bbci.co.uk/news/rss.xml",
    "Reuters": "http://feeds.reuters.com/reuters/topNews",
    "Al Jazeera": "https://www.aljazeera.com/xml/rss/all.xml",
}

_refresh_lock = threading.Lock()
_latest_articles: List[Dict[str, str]] = []
_last_updated: datetime | None = None


def summarise_text(text: str, max_length: int = 200) -> str:
    if not text:
        return ""
    text = " ".join(text.split())
    if len(text) <= max_length:
        return text
    cutoff = text.rfind(" ", 0, max_length)
    if cutoff == -1:
        cutoff = max_length
    return text[:cutoff].rstrip() + "…"


def fetch_news() -> None:
    global _latest_articles, _last_updated
    logger.info("Fetching latest news items")
    articles: List[Dict[str, str]] = []
    for source, url in NEWS_SOURCES.items():
        try:
            feed = feedparser.parse(url)
        except Exception as exc:
            logger.exception("Failed to parse feed %s: %s", source, exc)
            continue
        for entry in feed.entries[:5]:
            summary = summarise_text(getattr(entry, "summary", "") or getattr(entry, "description", ""))
            published = getattr(entry, "published", "")
            link = getattr(entry, "link", "")
            title = getattr(entry, "title", "Unnamed article")
            articles.append(
                {
                    "source": source,
                    "title": title,
                    "summary": summary,
                    "link": link,
                    "published": published,
                }
            )
    with _refresh_lock:
        _latest_articles = articles
        _last_updated = datetime.now(timezone.utc)
    logger.info("News refresh completed with %d articles", len(articles))


def ensure_data_loaded() -> None:
    with _refresh_lock:
        needs_refresh = not _latest_articles
    if needs_refresh:
        fetch_news()


@app.route("/")
def index():
    ensure_data_loaded()
    with _refresh_lock:
        articles = list(_latest_articles)
        last_updated = _last_updated
    return render_template("index.html", articles=articles, last_updated=last_updated)


@app.route("/api/articles")
def articles_api():
    ensure_data_loaded()
    with _refresh_lock:
        data = {
            "articles": list(_latest_articles),
            "last_updated": _last_updated.isoformat() if _last_updated else None,
        }
    return jsonify(data)


@app.route("/refresh", methods=["POST"])
def trigger_refresh():
    fetch_news()
    return ("", 204)


def start_scheduler() -> BackgroundScheduler:
    scheduler = BackgroundScheduler()
    scheduler.add_job(fetch_news, "interval", hours=1, id="fetch_news", replace_existing=True)
    scheduler.start()
    return scheduler


scheduler = start_scheduler()


@app.teardown_appcontext
def shutdown_scheduler(exception=None):
    if scheduler.running:
        scheduler.shutdown()


if __name__ == "__main__":
    fetch_news()
    app.run(host="0.0.0.0", port=5000)
