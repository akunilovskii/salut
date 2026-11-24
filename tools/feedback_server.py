"""Anonymous feedback outbox. Deploy behind a body-limiting HTTPS proxy.

Only explicitly configured proxy peers may supply X-Salut-Client-IP; that proxy
must overwrite the header and disable IP access logs. Mount /data writable by
1001:1001. Never mount Telegram credentials into the receiver container.
"""
import argparse
import hashlib
import io
import ipaddress
import json
import secrets
import socket
import sqlite3
import struct
import threading
import time
import unicodedata
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

MAX_BODY = 32768
MAX_IMAGES = 3
MAX_IMAGE_BYTES = 5 * 1024 * 1024
MAX_IMAGE_PIXELS = 16_000_000
IMAGE_REQUEST = 'application/vnd.salut.feedback'
IMAGE_TIMEOUT = 60
MAX_IMAGE_BODY = 4 + MAX_BODY + MAX_IMAGES * (4 + MAX_IMAGE_BYTES)


class TextTooLarge(ValueError):
    pass


class InvalidImages(ValueError):
    pass


def unpack_images(body):
    """Read a length-prefixed UTF-8 JSON envelope followed by 1–3 images."""
    def read(limit):
        header = stream.read(4)
        if len(header) != 4:
            raise ValueError('Missing length')
        size, = struct.unpack('>I', header)
        if not 0 < size <= limit:
            raise ValueError('Invalid length')
        data = stream.read(size)
        if len(data) != size:
            raise ValueError('Truncated data')
        return data

    stream = io.BytesIO(body)
    text_length = int.from_bytes(body[:4], 'big')
    if text_length > MAX_BODY:
        raise TextTooLarge()
    message = read(MAX_BODY)
    images = []
    try:
        while stream.tell() < len(body) and len(images) < MAX_IMAGES:
            images.append(check_image(read(MAX_IMAGE_BYTES)))
        if not images or stream.tell() != len(body):
            raise ValueError('Invalid image count')
    except ValueError as error:
        raise InvalidImages() from error
    return message, images


def check_image(data):
    from PIL import Image

    try:
        with Image.open(io.BytesIO(data), formats=('PNG', 'JPEG')) as image:
            if (image.width * image.height > MAX_IMAGE_PIXELS
                    or getattr(image, 'n_frames', 1) != 1
                    or image.get_format_mimetype() == 'image/apng'):
                raise ValueError('Invalid image dimensions or animation')
            fmt = image.format.lower()
            image.verify()
        # verify() checks PNG chunks, but does not decode pixel data.
        with Image.open(io.BytesIO(data), formats=('PNG', 'JPEG')) as image:
            image.load()
    except (OSError, SyntaxError, struct.error, Image.DecompressionBombError) as error:
        raise ValueError('Invalid image') from error
    return fmt, data

LIMITS = dict(screen=80, function=200, description=6000, steps=4000,
              expected=4000, appVersion=64, androidVersion=32)
REQUIRED = {'schemaVersion', 'reportId', 'kind', 'screen', 'description',
            'appVersion', 'androidVersion'}


def connect(database):
    db = sqlite3.connect(database, timeout=5)
    db.execute('''CREATE TABLE IF NOT EXISTS feedback (
        report_id TEXT PRIMARY KEY, kind TEXT NOT NULL, payload TEXT NOT NULL,
        next_part INTEGER NOT NULL DEFAULT 0, delivered INTEGER NOT NULL DEFAULT 0,
        retry_at REAL NOT NULL DEFAULT 0)''')
    db.execute('''CREATE TABLE IF NOT EXISTS feedback_image (
        report_id TEXT NOT NULL REFERENCES feedback(report_id),
        position INTEGER NOT NULL, format TEXT NOT NULL, data BLOB NOT NULL,
        PRIMARY KEY(report_id, position))''')
    db.commit()
    return db


def normalize(value):
    if not isinstance(value, dict) or not REQUIRED <= value.keys():
        raise ValueError('Missing fields')
    if value.keys() - (REQUIRED | LIMITS.keys()):
        raise ValueError('Unknown fields')
    if type(value['schemaVersion']) is not int or value['schemaVersion'] != 1:
        raise ValueError('Invalid schema')
    if not isinstance(value['reportId'], str):
        raise ValueError('Invalid UUID')
    result = dict(schemaVersion=1, reportId=str(uuid.UUID(value['reportId'])))
    if value['kind'] not in ('bug', 'enhancement'):
        raise ValueError('Invalid kind')
    result['kind'] = value['kind']
    for key, limit in LIMITS.items():
        text = value.get(key, '')
        if not isinstance(text, str):
            raise ValueError('Invalid text')
        text = unicodedata.normalize('NFC', text.replace('\r\n', '\n').replace('\r', '\n')).strip()
        if len(text) > limit or any(0xD800 <= ord(c) <= 0xDFFF for c in text):
            raise ValueError('Invalid length or Unicode')
        if key == 'description' and not text:
            raise ValueError('Empty description')
        result[key] = text
    return result


def images_for(db, report_id):
    return db.execute('SELECT format,data FROM feedback_image WHERE report_id=? ORDER BY position',
                      (report_id,)).fetchall()


def accept(db, value, images=()):
    payload = json.dumps(value, ensure_ascii=False, sort_keys=True)
    with db:
        inserted = db.execute('INSERT OR IGNORE INTO feedback(report_id,kind,payload) VALUES(?,?,?)',
                   (value['reportId'], value['kind'], payload)).rowcount
        old = db.execute('SELECT payload FROM feedback WHERE report_id=?',
                         (value['reportId'],)).fetchone()[0]
        if old != payload:
            return 409
        if inserted:
            db.executemany('INSERT INTO feedback_image VALUES(?,?,?,?)', (
                (value['reportId'], position, fmt, data)
                for position, (fmt, data) in enumerate(images)))
        elif images_for(db, value['reportId']) != list(images):
            return 409
    return 202


class RateLimit:
    """Fixed short windows; no raw IPs, bounded memory, restart-random hash key."""
    def __init__(self, per_ip=10, total=120, window=60):
        self.per_ip, self.total, self.window = per_ip, total, window
        self.secret = secrets.token_bytes(32)
        self.entries = {}
        self.start = time.monotonic()
        self.count = 0
        self.lock = threading.Lock()

    def allow(self, address):
        key = hashlib.blake2b(address.encode(), key=self.secret, digest_size=16).digest()
        with self.lock:
            if time.monotonic() - self.start >= self.window:
                self.entries.clear()
                self.count = 0
                self.start = time.monotonic()
            if self.count >= self.total or self.entries.get(key, 0) >= self.per_ip:
                return False
            self.count += 1
            self.entries[key] = self.entries.get(key, 0) + 1
            return True


class Server(ThreadingHTTPServer):
    daemon_threads = True

    def __init__(self, address, database, concurrency=16, timeout=10,
                 per_ip=10, total=120, trusted_proxies=()):
        self.database = database
        self.slots = threading.BoundedSemaphore(concurrency)
        self.image_slots = threading.BoundedSemaphore(min(2, concurrency))
        self.timeout_seconds = timeout
        self.rate = RateLimit(per_ip, total)
        self.trusted_proxies = {str(ipaddress.ip_address(p)) for p in trusted_proxies}
        super().__init__(address, Handler)

    def get_request(self):
        request, address = super().get_request()
        request.settimeout(self.timeout_seconds)
        return request, address

    def process_request(self, request, address):
        if not self.slots.acquire(False):
            request.close()
            return
        try:
            super().process_request(request, address)
        except BaseException:
            self.slots.release()
            raise

    def process_request_thread(self, request, address):
        try:
            super().process_request_thread(request, address)
        finally:
            self.slots.release()

    def handle_error(self, request, client_address):
        pass  # Never log addresses, request contents or exception payloads.


class Handler(BaseHTTPRequestHandler):
    def setup(self):
        super().setup()
        self.start_deadline(self.server.timeout_seconds)

    def start_deadline(self, seconds):
        self.deadline_timer = threading.Timer(seconds, self.expire)
        self.deadline_timer.daemon = True
        self.deadline_timer.start()

    def expire(self):
        try:
            self.connection.shutdown(socket.SHUT_RDWR)
        except OSError:
            pass

    def finish(self):
        self.deadline_timer.cancel()
        super().finish()

    def log_message(self, *args):
        pass

    def reply(self, code, value):
        body = json.dumps(value).encode()
        self.send_response(code)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', str(len(body)))
        self.send_header('Connection', 'close')
        if code == 429:
            self.send_header('Retry-After', '60')
        self.end_headers()
        self.wfile.write(body)
        self.close_connection = True

    def do_GET(self):
        self.reply(200 if self.path == '/healthz' else 400,
                   {'status': 'ok'} if self.path == '/healthz' else {'error': 'Invalid path'})

    def do_POST(self):
        image_slot = False
        try:
            if self.path != '/v1/feedback':
                raise ValueError()
            address = str(ipaddress.ip_address(self.client_address[0]))
            if address in self.server.trusted_proxies:
                address = str(ipaddress.ip_address(self.headers.get('X-Salut-Client-IP', '')))
            if not self.server.rate.allow(address):
                self.reply(429, {'error': 'Rate limit'})
                return
            lengths = self.headers.get_all('Content-Length', [])
            if len(lengths) != 1 or not lengths[0].isascii() or not lengths[0].isdigit():
                raise ValueError()
            length = int(lengths[0])
            content_type = self.headers.get_content_type()
            if self.headers.get('Transfer-Encoding') or content_type not in ('application/json', IMAGE_REQUEST):
                raise ValueError()
            with_images = content_type == IMAGE_REQUEST
            if length > (MAX_IMAGE_BODY if with_images else MAX_BODY):
                self.reply(413, {'error': 'Body too large'})
                return
            timeout = self.server.timeout_seconds
            if with_images:
                image_slot = self.server.image_slots.acquire(False)
                if not image_slot:
                    self.reply(503, {'error': 'Image receiver busy'})
                    return
                timeout = max(timeout, IMAGE_TIMEOUT)
                self.deadline_timer.cancel()
                self.start_deadline(timeout)
            # One absolute deadline also bounds clients that trickle body bytes.
            deadline = time.monotonic() + timeout
            body = bytearray()
            while len(body) < length:
                self.connection.settimeout(max(0.001, deadline - time.monotonic()))
                chunk = self.rfile.read1(min(65536, length - len(body)))
                if not chunk or time.monotonic() > deadline:
                    raise ValueError()
                body.extend(chunk)
            def unique(pairs):
                obj = {}
                for key, item in pairs:
                    if key in obj:
                        raise ValueError()
                    obj[key] = item
                return obj
            message, images = unpack_images(body) if with_images else (body, ())
            value = normalize(json.loads(message.decode('utf-8'), object_pairs_hook=unique))
            db = connect(self.server.database)
            try:
                code = accept(db, value, images)
            finally:
                db.close()
            self.reply(code, {'reportId': value['reportId'], 'status': 'accepted'}
                       if code == 202 else {'error': 'Conflicting reportId'})
        except TextTooLarge:
            self.reply(413, {'error': 'Text body too large'})
        except InvalidImages:
            self.reply(400, {'error': 'Invalid images'})
        except (ValueError, TypeError, UnicodeError, RecursionError, socket.timeout):
            self.reply(400, {'error': 'Invalid request'})
        except sqlite3.Error:
            self.reply(503, {'error': 'Storage unavailable'})
        finally:
            if image_slot:
                self.server.image_slots.release()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--bind', default='0.0.0.0')
    parser.add_argument('--port', type=int, default=8080)
    parser.add_argument('--database', default='/data/feedback.sqlite')
    parser.add_argument('--concurrency', type=int, default=16)
    parser.add_argument('--timeout', type=float, default=10)
    parser.add_argument('--per-ip', type=int, default=10)
    parser.add_argument('--global-limit', type=int, default=120)
    parser.add_argument('--trusted-proxy', action='append', default=[])
    args = parser.parse_args()
    if min(args.concurrency, args.timeout, args.per_ip, args.global_limit) <= 0:
        parser.error('Limits must be positive')
    connect(args.database).close()
    with Server((args.bind, args.port), args.database, args.concurrency, args.timeout,
                args.per_ip, args.global_limit, args.trusted_proxy) as server:
        server.serve_forever()


if __name__ == '__main__':
    main()
