import json
import unittest
from unittest import mock

from scripts.lib.client import MemindClient, MemindClientError, is_success_envelope


class _FakeResponse:
    def __init__(self, body):
        self.body = body

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc, tb):
        return False

    def read(self):
        return self.body.encode("utf-8")


class ClientTest(unittest.TestCase):
    def test_success_codes_match_api_result_variants(self):
        self.assertTrue(is_success_envelope({"code": "200"}))
        self.assertTrue(is_success_envelope({"code": "success"}))
        self.assertFalse(is_success_envelope({"code": "500"}))

    @mock.patch("urllib.request.urlopen")
    def test_add_message_sends_source_client(self, urlopen):
        urlopen.return_value = _FakeResponse(json.dumps({"code": "200"}))
        client = MemindClient("http://127.0.0.1:8366")
        client.add_message("u", "a", {"role": "USER", "content": []}, "codex")
        payload = json.loads(urlopen.call_args.args[0].data.decode("utf-8"))
        self.assertEqual(payload["sourceClient"], "codex")
        self.assertEqual(payload["message"]["role"], "USER")

    @mock.patch("urllib.request.urlopen")
    def test_commit_sends_source_client(self, urlopen):
        urlopen.return_value = _FakeResponse(json.dumps({"code": "200"}))
        client = MemindClient("http://127.0.0.1:8366")
        client.commit("u", "a", "codex")
        payload = json.loads(urlopen.call_args.args[0].data.decode("utf-8"))
        self.assertEqual(payload, {"userId": "u", "agentId": "a", "sourceClient": "codex"})

    @mock.patch("urllib.request.urlopen")
    def test_retrieve_requires_data(self, urlopen):
        urlopen.return_value = _FakeResponse(json.dumps({"code": "success"}))
        client = MemindClient("http://127.0.0.1:8366")
        with self.assertRaises(MemindClientError):
            client.retrieve("u", "a", "q")


if __name__ == "__main__":
    unittest.main()
