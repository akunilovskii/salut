"""Single delivery worker per database; only this process receives a token file.

Delivery is at-least-once: a crash after Telegram accepts a part but before the
SQLite commit may repeat that part. Report ID and part numbers identify repeats.
Do not run multiple workers on the same database.
"""
import argparse
import email.utils
import http.client
import json
import sqlite3
import time
import urllib.error
import urllib.request
from pathlib import Path

try:
    from .feedback_server import connect
except ImportError:
    from feedback_server import connect


def parts(value):
    report_id = value['reportId']
    text = '\n'.join(f'{key}: {value[key]}' for key in (
        'kind', 'screen', 'function', 'appVersion', 'androidVersion',
        'description', 'steps', 'expected') if value[key])
    # Telegram counts UTF-16 units. Never split a Python Unicode code point.
    chunks, chunk, units = [], '', 0
    for char in text:
        size = 2 if ord(char) > 0xFFFF else 1
        if units + size > 3500:
            chunks.append(chunk)
            chunk, units = '', 0
        chunk += char
        units += size
    if chunk:
        chunks.append(chunk)
    return [f'Report {report_id} [{i}/{len(chunks)}]\n{chunk}'
            for i, chunk in enumerate(chunks, 1)]


def retry_after(headers, data, now):
    try:
        return max(1, float(data['parameters']['retry_after']))
    except (KeyError, TypeError, ValueError):
        pass
    value = headers.get('Retry-After', '')
    try:
        return max(1, float(value))
    except ValueError:
        try:
            return max(1, email.utils.parsedate_to_datetime(value).timestamp() - now)
        except (ValueError, TypeError, OverflowError):
            return 30


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def send(token, chat_id, text):
    body = json.dumps({'chat_id': chat_id, 'text': text,
                       'link_preview_options': {'is_disabled': True}}).encode()
    request = urllib.request.Request(f'https://api.telegram.org/bot{token}/sendMessage',
                                     data=body, headers={'Content-Type': 'application/json'})
    opener = urllib.request.build_opener(NoRedirect())
    try:
        with opener.open(request, timeout=20) as response:
            data = json.loads(response.read(65536))
            return data.get('ok') is True, retry_after(response.headers, data, time.time())
    except urllib.error.HTTPError as error:
        try:
            data = json.loads(error.read(65536))
        except (OSError, ValueError, http.client.HTTPException):
            data = {}
        return False, retry_after(error.headers, data, time.time())
    except (OSError, ValueError, http.client.HTTPException):
        return False, 30


def deliver_one(db, token, chat_id, sender=send, clock=None):
    clock = time.time if clock is None else clock
    # Worker-owned singleton persists the shared bot/chat cooldown across restart.
    # Conservatively pause the entire queue on any delivery failure, not just 429.
    with db:
        db.execute("""CREATE TABLE IF NOT EXISTS delivery_state (
            id INTEGER PRIMARY KEY CHECK(id=1), retry_at REAL NOT NULL)""")
        db.execute('INSERT OR IGNORE INTO delivery_state VALUES(1,0)')
    now = clock()
    if db.execute('SELECT retry_at FROM delivery_state WHERE id=1').fetchone()[0] > now:
        return False
    row = db.execute('''SELECT report_id,payload,next_part FROM feedback
        WHERE delivered=0 AND retry_at<=? ORDER BY rowid LIMIT 1''', (now,)).fetchone()
    if not row:
        return False
    report_id, payload, index = row
    messages = parts(json.loads(payload))
    ok, delay = sender(token, chat_id, messages[index])
    completed_at = clock()
    with db:
        if ok:
            db.execute('UPDATE feedback SET next_part=?,delivered=?,retry_at=0 WHERE report_id=?',
                       (index + 1, int(index + 1 == len(messages)), report_id))
        else:
            retry_at = completed_at + max(1, delay)
            db.execute('UPDATE feedback SET retry_at=? WHERE report_id=?',
                       (retry_at, report_id))
            db.execute('UPDATE delivery_state SET retry_at=? WHERE id=1', (retry_at,))
    return True


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--database', required=True)
    parser.add_argument('--token-file', required=True)
    parser.add_argument('--chat-id', required=True)
    args = parser.parse_args()
    token = Path(args.token_file).read_text(encoding='utf-8').strip()
    if not token or any(c.isspace() for c in token):
        parser.error('Invalid token file')
    db = connect(args.database)
    try:
        while True:
            try:
                worked = deliver_one(db, token, args.chat_id)
            except sqlite3.Error:
                worked = False
            if not worked:
                time.sleep(1)
    finally:
        db.close()


if __name__ == '__main__':
    main()
