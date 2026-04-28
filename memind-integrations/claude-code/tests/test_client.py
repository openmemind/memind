#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

import json
import threading
import unittest
from http.server import BaseHTTPRequestHandler, HTTPServer

from scripts.lib.client import MemindClient, MemindClientError, is_success_envelope


class Handler(BaseHTTPRequestHandler):
    responses = []
    requests = []

    def do_GET(self):
        self._handle()

    def do_POST(self):
        self._handle()

    def _handle(self):
        length = int(self.headers.get("Content-Length", "0"))
        body = self.rfile.read(length).decode("utf-8") if length else ""
        Handler.requests.append((self.command, self.path, json.loads(body) if body else None))
        status, payload = Handler.responses.pop(0)
        encoded = json.dumps(payload).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(encoded)))
        self.end_headers()
        self.wfile.write(encoded)

    def log_message(self, format, *args):
        return


class ClientTest(unittest.TestCase):
    def setUp(self):
        Handler.responses = []
        Handler.requests = []
        self.server = HTTPServer(("127.0.0.1", 0), Handler)
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()
        self.base_url = f"http://127.0.0.1:{self.server.server_port}"

    def tearDown(self):
        self.server.shutdown()
        self.server.server_close()
        self.thread.join(timeout=2)

    def test_success_envelope_accepts_200_and_success(self):
        self.assertTrue(is_success_envelope({"code": "200"}))
        self.assertTrue(is_success_envelope({"code": "success"}))
        self.assertFalse(is_success_envelope({"code": "500"}))

    def test_health(self):
        Handler.responses.append((200, {"code": "success", "data": {"status": "UP"}}))
        client = MemindClient(self.base_url, timeout=2)
        self.assertEqual(client.health()["data"]["status"], "UP")
        self.assertEqual(Handler.requests[0][1], "/open/v1/health")

    def test_add_message_and_commit(self):
        Handler.responses.append((200, {"code": "200", "data": None}))
        Handler.responses.append((200, {"code": "200", "data": None}))
        client = MemindClient(self.base_url, timeout=2)
        message = {"role": "USER", "content": [{"type": "text", "text": "hello"}], "timestamp": None}
        client.add_message("u", "a", message, "claude-code")
        client.commit("u", "a", "claude-code")
        self.assertEqual(Handler.requests[0][1], "/open/v1/memory/add-message")
        self.assertEqual(Handler.requests[0][2]["sourceClient"], "claude-code")
        self.assertEqual(Handler.requests[1][1], "/open/v1/memory/commit")
        self.assertEqual(Handler.requests[1][2]["sourceClient"], "claude-code")

    def test_retrieve_requires_data(self):
        Handler.responses.append((200, {"code": "success", "data": {"items": [], "insights": []}}))
        client = MemindClient(self.base_url, timeout=2)
        response = client.retrieve("u", "a", "query", "SIMPLE", False)
        self.assertEqual(response["data"]["items"], [])

    def test_bad_status_raises(self):
        Handler.responses.append((500, {"code": "500"}))
        client = MemindClient(self.base_url, timeout=2)
        with self.assertRaises(MemindClientError):
            client.health()


if __name__ == "__main__":
    unittest.main()
