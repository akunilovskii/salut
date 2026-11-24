import contextlib
import http.client
import io
import json
import socket
import struct
import threading
import unittest
import uuid
import zlib
from unittest.mock import patch

from tools import feedback_server as server
from tools import feedback_delivery as delivery


def payload(**changes):
    result = dict(schemaVersion=1, reportId=str(uuid.uuid4()), kind='bug',
                  screen='Player', description='Problem', appVersion='1', androidVersion='16')
    result.update(changes)
    return result


def picture(fmt='PNG', animated=False):
    from PIL import Image

    buffer = io.BytesIO()
    first = Image.new('RGB', (2, 2), 'red')
    if animated:
        first.save(buffer, fmt, save_all=True, append_images=[Image.new('RGB', (2, 2), 'blue')])
    else:
        first.save(buffer, fmt)
    return buffer.getvalue()


def image_body(message, *images):
    return b''.join(struct.pack('>I', len(data)) + data for data in (message, *images))


class FeedbackTest(unittest.TestCase):
    def setUp(self):
        self.db = server.connect(':memory:')

    def tearDown(self):
        self.db.close()

    def test_commit_idempotency_and_conflict(self):
        for kind in ('bug', 'enhancement'):
            value = server.normalize(payload(kind=kind))
            self.assertEqual(server.accept(self.db, value), 202)
            self.assertFalse(self.db.in_transaction)
            self.assertEqual(server.accept(self.db, value), 202)
            self.assertEqual(server.accept(self.db, dict(value, description='other')), 409)
        self.assertEqual(self.db.execute('SELECT count(*) FROM feedback').fetchone()[0], 2)

    def test_attachments_are_atomic_and_part_of_deduplication(self):
        import sqlite3

        value = server.normalize(payload())
        images = [server.check_image(picture()), server.check_image(picture('JPEG'))]
        with self.db:
            self.db.execute("""CREATE TRIGGER fail_image BEFORE INSERT ON feedback_image
                WHEN NEW.position=1 BEGIN SELECT RAISE(ABORT, 'failed image'); END""")
        with self.assertRaises(sqlite3.IntegrityError):
            server.accept(self.db, value, images)
        self.assertEqual(self.db.execute('SELECT count(*) FROM feedback').fetchone()[0], 0)
        self.assertEqual(server.images_for(self.db, value['reportId']), [])
        with self.db:
            self.db.execute('DROP TRIGGER fail_image')
        self.assertEqual(server.accept(self.db, value, images), 202)
        self.assertEqual(server.accept(self.db, value, images), 202)
        self.assertEqual(server.accept(self.db, value, images[::-1]), 409)
        self.assertEqual(server.accept(self.db, value), 409)

    def test_retry_resumes_at_failed_image(self):
        value = server.normalize(payload())
        images = [server.check_image(picture()), server.check_image(picture('JPEG'))]
        server.accept(self.db, value, images)
        text = unittest.mock.Mock(return_value=(True, 0))
        send_image = unittest.mock.Mock(side_effect=[(True, 0), (False, 5), (True, 0)])
        for _ in range(3):
            self.assertTrue(delivery.deliver_one(self.db, 'token', 'chat', text,
                                                 clock=lambda: 10, image_sender=send_image))
        restarted = server.connect(':memory:')
        self.addCleanup(restarted.close)
        self.db.backup(restarted)
        self.assertFalse(delivery.deliver_one(restarted, 'token', 'chat', text,
                                              clock=lambda: 14, image_sender=send_image))
        self.assertTrue(delivery.deliver_one(restarted, 'token', 'chat', text,
                                             clock=lambda: 15, image_sender=send_image))
        text.assert_called_once()
        args = [call.args for call in send_image.call_args_list]
        self.assertEqual([arg[-2:] for arg in args], [images[0], images[1], images[1]])
        self.assertIn(value['reportId'], args[-1][2])
        self.assertEqual(restarted.execute('SELECT next_part,delivered FROM feedback').fetchone(), (3, 1))

    def test_image_framing_limits_and_types(self):
        png, jpeg = picture(), picture('JPEG')
        message = b'x' * server.MAX_BODY
        self.assertEqual(server.unpack_images(image_body(message, png, jpeg)),
                         (message, [('png', png), ('jpeg', jpeg)]))
        with self.assertRaises(ValueError):
            server.unpack_images(image_body(message + b'x', png))
        for images in ((), (png,) * 4, (b'not an image',), (png[:20],),
                       (picture('PNG', animated=True),), (b'x' * (server.MAX_IMAGE_BYTES + 1),)):
            with self.subTest(lengths=list(map(len, images))), self.assertRaises(server.InvalidImages):
                server.unpack_images(image_body(message, *images))

    def test_normalization(self):
        value = payload(description=' e\u0301\r\n ')
        normalized = server.normalize(value)
        self.assertEqual(normalized['description'], 'é')
        self.assertEqual(server.normalize(dict(value, function='')), normalized)

    def test_validation(self):
        for changes in ({'schemaVersion': True}, {'kind': []}, {'reportId': 'x'},
                        {'userId': 'x'}, {'deviceId': 'x'}, {'description': ''},
                        {'description': None}, {'screen': 5}, {'description': '\ud800'}):
            with self.subTest(changes=changes), self.assertRaises((ValueError, TypeError)):
                server.normalize(payload(**changes))
        for key, maximum in server.LIMITS.items():
            server.normalize(payload(**{key: 'a' * maximum}))
            with self.assertRaises(ValueError):
                server.normalize(payload(**{key: 'a' * (maximum + 1)}))
        with self.assertRaises(ValueError):
            server.normalize({})

    def test_rate_bounds_and_expiry(self):
        rate = server.RateLimit(1, 2, 60)
        self.assertTrue(rate.allow('first'))
        self.assertFalse(rate.allow('first'))
        self.assertTrue(rate.allow('second'))
        self.assertFalse(rate.allow('third'))
        self.assertEqual(len(rate.entries), 2)
        self.assertNotIn('first', rate.entries)
        rate.start -= 61
        self.assertTrue(rate.allow('third'))
        self.assertEqual(len(rate.entries), 1)

    def test_delivery_resume_unicode_and_retry(self):
        value = server.normalize(payload(description='😀' * 6000, steps='s' * 4000))
        server.accept(self.db, value)
        messages = delivery.parts(value)
        for message in messages:
            self.assertLessEqual(len(message.encode('utf-16-le')) // 2, 4096)
            self.assertIn(value['reportId'], message)
        self.assertTrue(delivery.deliver_one(self.db, 'token', 'chat',
                                           lambda *a: (False, 45), clock=lambda: 10))
        self.assertFalse(delivery.deliver_one(self.db, 'token', 'chat', clock=lambda: 54))
        sent = []
        def sender(token, chat, text):
            sent.append(text)
            return True, 0
        for _ in messages:
            delivery.deliver_one(self.db, 'token', 'chat', sender, clock=lambda: 55)
        self.assertEqual(sent, messages)
        self.assertEqual(self.db.execute('SELECT delivered FROM feedback').fetchone()[0], 1)

    def test_retry_headers(self):
        self.assertEqual(delivery.retry_after({'Retry-After': '22'}, {}, 0), 22)
        self.assertEqual(delivery.retry_after({}, {'parameters': {'retry_after': 42}}, 0), 42)

    def test_telegram_429_and_network_failure(self):
        from urllib.error import HTTPError, URLError
        error = HTTPError('https://example.invalid', 429, 'rate',
                          {'Retry-After': '12'}, io.BytesIO(b'{"parameters":{"retry_after":25}}'))
        with patch.object(delivery.urllib.request, 'build_opener') as factory:
            factory.return_value.open.side_effect = error
            self.assertEqual(delivery.send('test', 'chat', 'text'), (False, 25))
            factory.return_value.open.side_effect = URLError('offline')
            self.assertEqual(delivery.send('test', 'chat', 'text'), (False, 30))

    def test_truncated_http_responses_retry_without_losing_outbox(self):
        from urllib.error import HTTPError
        from unittest.mock import MagicMock
        server.accept(self.db, server.normalize(payload()))
        for is_error_response in (False, True):
            with self.subTest(is_error_response=is_error_response):
                response = MagicMock()
                response.read.side_effect = http.client.IncompleteRead(b'partial')
                response.__enter__.return_value = response
                with patch.object(delivery.urllib.request, 'build_opener') as factory:
                    if is_error_response:
                        factory.return_value.open.side_effect = HTTPError(
                            'https://example.invalid', 503, 'temporary', {}, response)
                    else:
                        factory.return_value.open.return_value = response
                    self.assertTrue(delivery.deliver_one(
                        self.db, 'token', 'chat', clock=lambda: 100))
                self.assertEqual(self.db.execute(
                    'SELECT next_part,delivered,retry_at FROM feedback').fetchone(), (0, 0, 130))
                # The process can continue and retry instead of exiting.
                self.assertFalse(delivery.deliver_one(
                    self.db, 'token', 'chat', clock=lambda: 129))
                with self.db:
                    self.db.execute('UPDATE delivery_state SET retry_at=0')
                    self.db.execute('UPDATE feedback SET retry_at=0')
        self.assertTrue(delivery.deliver_one(
            self.db, 'token', 'chat', lambda *a: (True, 0), clock=lambda: 130))
        self.assertEqual(self.db.execute('SELECT delivered FROM feedback').fetchone()[0], 1)

    def test_delayed_429_cools_entire_queue_and_persists(self):
        from urllib.error import HTTPError
        for kind in ('bug', 'enhancement'):
            server.accept(self.db, server.normalize(payload(kind=kind)))
        current = [100]
        def delayed_response(*args, **kwargs):
            current[0] = 120  # Network request took twenty seconds.
            raise HTTPError('https://example.invalid', 429, 'rate',
                            {'Retry-After': '45'}, io.BytesIO(b'{}'))
        with patch.object(delivery.urllib.request, 'build_opener') as factory:
            factory.return_value.open.side_effect = delayed_response
            self.assertTrue(delivery.deliver_one(
                self.db, 'token', 'chat', clock=lambda: current[0]))
            self.assertEqual(factory.return_value.open.call_count, 1)
        self.assertEqual(self.db.execute('SELECT retry_at FROM delivery_state').fetchone()[0], 165)
        self.assertFalse(self.db.in_transaction)
        # Copy committed state to simulate a restarted worker without disk files.
        restarted = server.connect(':memory:')
        try:
            self.db.backup(restarted)
            from unittest.mock import Mock
            sender = Mock(return_value=(True, 0))
            for now in (120, 145, 164.99):
                self.assertFalse(delivery.deliver_one(
                    restarted, 'token', 'chat', sender, clock=lambda: now))
            sender.assert_not_called()
            for _ in range(2):
                self.assertTrue(delivery.deliver_one(
                    restarted, 'token', 'chat', sender, clock=lambda: 165))
            self.assertEqual(sender.call_count, 2)
            self.assertEqual(restarted.execute(
                'SELECT count(*) FROM feedback WHERE delivered=1').fetchone()[0], 2)
        finally:
            restarted.close()

    def test_telegram_request(self):
        class Response:
            headers = {}
            def __enter__(self): return self
            def __exit__(self, *args): pass
            def read(self, size): return b'{"ok": true}'
        with patch.object(delivery.urllib.request, 'build_opener') as factory:
            factory.return_value.open.return_value = Response()
            self.assertTrue(delivery.send('test', 'chat', 'hello')[0])
            request = factory.return_value.open.call_args.args[0]
            body = json.loads(request.data)
            self.assertNotIn('parse_mode', body)
            self.assertTrue(body['link_preview_options']['is_disabled'])

    def test_telegram_document_request(self):
        from email import policy
        from email.parser import BytesParser

        png = picture()
        with patch.object(delivery, 'telegram', return_value=(True, 0)) as telegram:
            self.assertEqual(delivery.send_image('token', 'chat', 'Report 1', 'png', png),
                             (True, 0))
        token, method, body, content_type = telegram.call_args.args
        self.assertEqual((token, method), ('token', 'sendDocument'))
        message = BytesParser(policy=policy.default).parsebytes(
            f'Content-Type: {content_type}\r\n\r\n'.encode() + body)
        chat, caption, document = list(message.iter_parts())
        self.assertEqual(chat.get_payload(decode=True), b'chat')
        self.assertEqual(caption.get_payload(decode=True), b'Report 1')
        self.assertEqual(document.get_filename(), 'image.png')
        self.assertEqual(document.get_content_type(), 'image/png')
        self.assertEqual(document.get_payload(decode=True), png)


class HttpTest(unittest.TestCase):
    def setUp(self):
        self.db = server.connect(':memory:')
        self.http = server.Server(('127.0.0.1', 0), ':memory:', timeout=0.15)
        self.thread = threading.Thread(target=self.http.serve_forever)
        self.thread.start()

    def tearDown(self):
        self.http.shutdown()
        self.http.server_close()
        self.thread.join()
        self.db.close()

    def request(self, path='/v1/feedback', body=None, headers=None, method='POST'):
        connection = http.client.HTTPConnection(*self.http.server_address, timeout=2)
        connection.request(method, path, body=body, headers=headers or {'Content-Type': 'application/json'})
        response = connection.getresponse()
        result = response.status, response.read()
        connection.close()
        return result

    def test_paths_body_limits_types_and_health(self):
        self.assertEqual(self.request('/healthz', method='GET')[0], 200)
        self.assertEqual(self.request('/other')[0], 400)
        self.assertEqual(self.request(body='x' * 32769)[0], 413)
        for body in ('{}', '[]', '{"schemaVersion":1,"schemaVersion":1}', 'invalid'):
            self.assertEqual(self.request(body=body)[0], 400)
        self.assertEqual(self.request(body='{}', headers={'Transfer-Encoding': 'chunked'})[0], 400)

    def test_same_text_byte_boundary_with_and_without_images(self):
        uri = 'file:feedback-image-boundary?mode=memory&cache=shared'
        import sqlite3
        real_connect = sqlite3.connect
        with patch.object(server.sqlite3, 'connect', side_effect=lambda name, **kw: real_connect(name, uri=True, **kw)):
            keeper = server.connect(uri)
        value = payload(description='界' * 6000, steps='界' * 4000, expected='界' * 500)
        message = json.dumps(value, ensure_ascii=False, separators=(',', ':')).encode()
        self.assertLess(len(message), server.MAX_BODY)
        message = message.ljust(server.MAX_BODY, b' ')
        png = picture()
        headers = {'Content-Type': server.IMAGE_REQUEST}
        try:
            with patch.object(server, 'connect', wraps=lambda _: self.connect_uri(uri)):
                self.assertEqual(self.request(body=message)[0], 202)
                self.assertEqual(self.request(body=message + b' ')[0], 413)
                value['reportId'] = str(uuid.uuid4())
                with_image = json.dumps(value, ensure_ascii=False, separators=(',', ':')).encode()
                with_image = with_image.ljust(server.MAX_BODY, b' ')
                for _ in range(2):
                    status, receipt = self.request(body=image_body(with_image, png), headers=headers)
                    self.assertEqual(status, 202)
                    self.assertEqual(json.loads(receipt)['reportId'], value['reportId'])
                self.assertEqual(self.request(body=image_body(with_image + b' ', png), headers=headers)[0], 413)
                self.assertEqual(server.images_for(keeper, value['reportId']), [('png', png)])
        finally:
            keeper.close()

    @staticmethod
    def connect_uri(uri):
        import sqlite3
        return sqlite3.connect(uri, uri=True)

    def test_maximum_combined_request_and_malformed_frames(self):
        import sqlite3

        uri = 'file:feedback-image-maximum?mode=memory&cache=shared'
        real_connect = sqlite3.connect
        self.http.database = uri
        self.http.timeout_seconds = 2
        with patch.object(server.sqlite3, 'connect',
                          side_effect=lambda name, **kw: real_connect(name, uri=True, **kw)):
            keeper = server.connect(uri)
            try:
                value = payload(description='界' * 6000, steps='界' * 4000)
                message = json.dumps(value, ensure_ascii=False).encode().ljust(server.MAX_BODY, b' ')
                png = picture()
                # A valid private ancillary PNG chunk pads the image to exactly 5 MiB.
                data = b'\0' * (server.MAX_IMAGE_BYTES - len(png) - 12)
                chunk = b'saLt' + data
                png = png[:-12] + struct.pack('>I', len(data)) + chunk + struct.pack('>I', zlib.crc32(chunk)) + png[-12:]
                body = image_body(message, png, png, png)
                self.assertEqual(len(body), 15_761_424)
                headers = {'Content-Type': server.IMAGE_REQUEST}
                self.assertEqual(self.request(body=body, headers=headers)[0], 202)
                headers_over = dict(headers, **{'Content-Length': str(len(body) + 1)})
                self.assertEqual(self.request(body=b'', headers=headers_over)[0], 413)
                self.assertEqual(server.images_for(keeper, value['reportId']), [('png', png)] * 3)
                for malformed in (body[:3], image_body(message, png)[:-1],
                                  image_body(message, png) + b'x', image_body(message, b'')):
                    self.assertEqual(self.request(body=malformed, headers=headers)[0], 400)
            finally:
                keeper.close()

    def test_deeply_nested_json_returns_400(self):
        # Decoder depth limits vary; a nested array is not a valid report either way.
        body = '[' * 2000 + '0' + ']' * 2000
        self.assertLessEqual(len(body.encode('utf-8')), server.MAX_BODY)
        status, response = self.request(body=body)
        self.assertEqual(status, 400)
        self.assertEqual(json.loads(response), {'error': 'Invalid request'})

    def test_json_decoder_recursion_error_returns_400(self):
        with patch.object(server.json, 'loads', side_effect=RecursionError) as loads:
            status, response = self.request(body='{}')
            loads.assert_called_once()
        self.assertEqual(status, 400)
        self.assertEqual(json.loads(response), {'error': 'Invalid request'})

    def test_accepted_receipt_after_commit_no_logs(self):
        # A fresh connection per request, backed by a shared in-memory database.
        uri = 'file:feedback-test?mode=memory&cache=shared'
        import sqlite3
        keeper = sqlite3.connect(uri, uri=True)
        schema = "\n".join(self.db.iterdump())
        def connect(_):
            db = sqlite3.connect(uri, uri=True)
            # Initialize with the production schema without creating files.
            if not db.execute("SELECT name FROM sqlite_master WHERE name='feedback'").fetchone():
                db.executescript(schema)
            return db
        value = payload()
        try:
            with patch.object(server, 'connect', side_effect=connect), contextlib.redirect_stderr(io.StringIO()) as log:
                self.assertEqual(self.request(body=json.dumps(value))[0], 202)
                self.assertEqual(self.request(body=json.dumps(value))[0], 202)
                self.assertEqual(self.request(body=json.dumps(dict(value, description='changed')))[0], 409)
                self.assertEqual(log.getvalue(), '')
            self.assertEqual(keeper.execute('SELECT count(*) FROM feedback').fetchone()[0], 1)
        finally:
            keeper.close()

    def test_rate_untrusted_header(self):
        self.http.rate = server.RateLimit(1, 100)
        self.request(body='{}', headers={'Content-Type': 'application/json', 'X-Salut-Client-IP': '1.1.1.1'})
        self.assertEqual(self.request(body='{}', headers={'Content-Type': 'application/json',
                                                       'X-Salut-Client-IP': '2.2.2.2'})[0], 429)

    def test_trusted_proxy_separates_clients(self):
        self.http.trusted_proxies = {'127.0.0.1'}
        self.http.rate = server.RateLimit(1, 100)
        for ip in ('1.1.1.1', '2.2.2.2'):
            self.assertEqual(self.request(body='{}', headers={
                'Content-Type': 'application/json', 'X-Salut-Client-IP': ip})[0], 400)
        self.assertEqual(self.request(body='{}', headers={
            'Content-Type': 'application/json', 'X-Salut-Client-IP': '1.1.1.1'})[0], 429)

    def test_storage_failure_is_not_accepted(self):
        import sqlite3
        with patch.object(server, 'connect', side_effect=sqlite3.OperationalError('unavailable')):
            self.assertEqual(self.request(body=json.dumps(payload()))[0], 503)

    def test_socket_timeout_and_concurrency(self):
        self.http.slots = threading.BoundedSemaphore(1)
        connection = socket.create_connection(self.http.server_address)
        connection.sendall(b'POST /v1/feedback HTTP/1.0\r\nContent-Type: application/json\r\nContent-Length: 10\r\n\r\nx')
        response = connection.recv(4096)
        self.assertTrue(not response or b'400' in response)
        connection.close()
        self.http.slots.acquire()
        try:
            other = socket.create_connection(self.http.server_address)
            self.assertEqual(other.recv(1), b'')
            other.close()
        finally:
            self.http.slots.release()
