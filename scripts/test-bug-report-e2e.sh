#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

DEVICE_ID="${DEVICE_ID:-$(uuidgen | tr '[:upper:]' '[:lower:]')}"
DB_PATH="${DATABASE_URL:-jdbc:sqlite:./data/kkalscan-e2e.db}"
DB_FILE="${DB_PATH#jdbc:sqlite:}"
PORT="${PORT:-9091}"
BASE="http://127.0.0.1:${PORT}"

mkdir -p "$(dirname "$DB_FILE")"
rm -f "$DB_FILE"

SHOT1="$(mktemp --suffix=.jpg)"
SHOT2="$(mktemp --suffix=.jpg)"
python3 - <<PY
from PIL import Image
Image.new("RGB", (120, 80), (255, 122, 47)).save("$SHOT1", format="JPEG")
Image.new("RGB", (120, 80), (30, 201, 149)).save("$SHOT2", format="JPEG")
PY

echo "Starting backend on :$PORT with DB $DB_FILE"
DATABASE_URL="$DB_PATH" PORT="$PORT" VISION_PROVIDER=stub \
  ./gradlew -q run > /tmp/kkalscan-bug-e2e.log 2>&1 &
SERVER_PID=$!
trap 'kill "$SERVER_PID" 2>/dev/null || true; rm -f "$SHOT1" "$SHOT2"' EXIT

for _ in $(seq 1 60); do
  if curl -sf "$BASE/health" >/dev/null; then
    break
  fi
  sleep 1
done

echo "Submitting bug report from device $DEVICE_ID"
RESPONSE="$(curl -sS -w '\n%{http_code}' -X POST "$BASE/api/v1/feedback/bug" \
  -H "X-Device-Id: $DEVICE_ID" \
  -F "email=bug-test@example.com" \
  -F "description=Автотест: баг-репорт с двумя скриншотами для проверки БД и SMTP" \
  -F "screenshot=@${SHOT1};type=image/jpeg" \
  -F "screenshot=@${SHOT2};type=image/jpeg")"

BODY="${RESPONSE%$'\n'*}"
STATUS="${RESPONSE##*$'\n'}"
echo "HTTP $STATUS"
echo "$BODY"

if [[ "$STATUS" != "200" ]]; then
  echo "Bug report failed" >&2
  tail -50 /tmp/kkalscan-bug-e2e.log >&2 || true
  exit 1
fi

REPORT_ID="$(python3 -c 'import json,sys; print(json.loads(sys.argv[1])["report_id"])' "$BODY")"

echo "Checking sqlite..."
python3 - <<PY
import sqlite3, sys
db = "${DB_FILE#jdbc:sqlite:}" if False else "$DB_FILE"
conn = sqlite3.connect("$DB_FILE")
cur = conn.cursor()
cur.execute("SELECT id, device_id, email, length(description) FROM bug_reports WHERE id = ?", ("$REPORT_ID",))
print("bug_reports:", cur.fetchall())
cur.execute("SELECT report_id, position, content_type, length(data) FROM bug_report_screenshots WHERE report_id = ? ORDER BY position", ("$REPORT_ID",))
rows = cur.fetchall()
print("screenshots:", rows)
count = len(rows)
if count != 2:
    sys.exit(1)
PY

echo "OK: bug report persisted with 2 screenshots"

if [[ -n "${SMTP_USER:-}" && -n "${SMTP_PASSWORD:-}" ]]; then
  echo "SMTP configured — email should be sent to ${BUG_REPORT_NOTIFY_TO:-mail@antonbutov.com}"
else
  echo "SMTP_USER/SMTP_PASSWORD not set — email was logged only (LoggingBugReportMailer)"
fi
