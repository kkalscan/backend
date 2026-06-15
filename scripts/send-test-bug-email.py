#!/usr/bin/env python3
"""Send a test bug-report notification email via mail.antonbutov.com."""

from __future__ import annotations

import os
import smtplib
import sys
import uuid
from email.mime.application import MIMEApplication
from email.mime.multipart import MIMEMultipart
from email.mime.text import MIMEText
from io import BytesIO

try:
    from PIL import Image
except ImportError:
    Image = None


def jpeg_bytes(color: tuple[int, int, int]) -> bytes:
    if Image is None:
        return bytes([0xFF, 0xD8, 0xFF, 0xD9])
    buf = BytesIO()
    Image.new("RGB", (120, 80), color).save(buf, format="JPEG")
    return buf.getvalue()


def main() -> int:
    host = os.getenv("SMTP_HOST", "mail.antonbutov.com")
    port = int(os.getenv("SMTP_PORT", "587"))
    user = os.getenv("SMTP_USER", "mail@antonbutov.com")
    password = os.getenv("SMTP_PASSWORD", "")
    mail_from = os.getenv("SMTP_FROM", user)
    mail_to = os.getenv("BUG_REPORT_NOTIFY_TO", "mail@antonbutov.com")

    if not password:
        print("Set SMTP_PASSWORD (and optionally SMTP_USER) before running.", file=sys.stderr)
        return 2

    report_id = str(uuid.uuid4())
    body = (
        f"Тест KkalScan bug report SMTP\n\n"
        f"Report ID: {report_id}\n"
        f"Email автора: qa@kkalscan.test\n\n"
        f"Описание:\n"
        f"Автотест отправки письма с двумя скриншотами через {host}\n"
    )

    msg = MIMEMultipart()
    msg["Subject"] = f"KkalScan bug report {report_id}"
    msg["From"] = mail_from
    msg["To"] = mail_to
    msg["Reply-To"] = "qa@kkalscan.test"
    msg.attach(MIMEText(body, "plain", "utf-8"))

    for index, color in enumerate(((255, 122, 47), (30, 201, 149))):
        part = MIMEApplication(jpeg_bytes(color), _subtype="jpeg")
        part.add_header("Content-Disposition", "attachment", filename=f"screenshot-{index}.jpg")
        msg.attach(part)

    with smtplib.SMTP(host, port, timeout=20) as smtp:
        smtp.ehlo()
        smtp.starttls()
        smtp.ehlo()
        smtp.login(user, password)
        smtp.sendmail(mail_from, [mail_to], msg.as_string())

    print(f"Sent test bug-report email to {mail_to} (report_id={report_id})")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
