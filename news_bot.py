#!/usr/bin/env python3
"""Simple news bot that fetches headlines from Turkish and English news sites
and optionally emails the summary."""

import argparse
import os
import smtplib
from email.message import EmailMessage

import feedparser

USER_AGENT = "Mozilla/5.0 (news bot)"

NEWS_SOURCES = {
    "tr": [
        ("Internethaber", "https://www.internethaber.com/rss"),
        ("BloombergHT", "https://www.bloomberght.com/rss"),
    ],
    "en": [
        ("Reuters", "https://www.reuters.com/rssFeed/worldNews"),
        ("Al Jazeera", "https://www.aljazeera.com/xml/rss/all.xml"),
    ],
}


def fetch_feed(name: str, url: str, max_items: int):
    """Fetch up to ``max_items`` entries from ``url``.

    Returns a tuple of source name and a list of formatted entries.
    """
    try:
        feed = feedparser.parse(url, agent=USER_AGENT)
    except Exception as exc:  # pragma: no cover - network failures
        return name, [f"Error fetching feed: {exc}"]

    entries = []
    for entry in feed.entries[:max_items]:
        title = entry.get("title", "No title")
        link = entry.get("link", "")
        entries.append(f"- {title}\n  {link}")

    if not entries:
        entries.append("(no entries found)")
    return name, entries


def collect_news(max_per_source: int = 5) -> str:
    """Collect news from all configured sources."""
    sections = []
    for sources in NEWS_SOURCES.values():
        for name, url in sources:
            source_name, items = fetch_feed(name, url, max_per_source)
            sections.append(f"{source_name}:\n" + "\n".join(items))
    return "\n\n".join(sections)


def send_email(subject: str, body: str, smtp_server: str, smtp_port: int,
               username: str, password: str, recipients):
    """Send ``body`` as an email to ``recipients``."""
    msg = EmailMessage()
    msg["Subject"] = subject
    msg["From"] = username
    msg["To"] = ", ".join(recipients)
    msg.set_content(body)

    with smtplib.SMTP_SSL(smtp_server, smtp_port) as smtp:
        smtp.login(username, password)
        smtp.send_message(msg)


def main():
    parser = argparse.ArgumentParser(description="Fetch news and optionally email them.")
    parser.add_argument("--max-per-source", type=int, default=5,
                        help="Maximum number of headlines per source")
    parser.add_argument("--dry-run", action="store_true",
                        help="Print news instead of sending email")
    args = parser.parse_args()

    news_body = collect_news(args.max_per_source)

    if args.dry_run:
        print(news_body)
        return

    smtp_server = os.environ["SMTP_SERVER"]
    smtp_port = int(os.environ.get("SMTP_PORT", 465))
    smtp_user = os.environ["SMTP_USER"]
    smtp_password = os.environ["SMTP_PASSWORD"]
    recipients = [r.strip() for r in os.environ["EMAIL_RECIPIENTS"].split(",")]

    send_email("Güncel Haberler / Latest News", news_body, smtp_server,
               smtp_port, smtp_user, smtp_password, recipients)


if __name__ == "__main__":
    main()
