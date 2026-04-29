import json
import urllib.error
import urllib.request
from urllib.parse import urljoin


class MemindClientError(Exception):
    pass


def is_success_envelope(envelope):
    return isinstance(envelope, dict) and str(envelope.get("code")) in {"200", "success"}


class MemindClient:
    def __init__(self, base_url, token=None, timeout=10):
        self.base_url = base_url.rstrip("/") + "/"
        self.token = token
        self.timeout = timeout

    def _headers(self):
        headers = {"Content-Type": "application/json"}
        if self.token:
            headers["Authorization"] = f"Bearer {self.token}"
        return headers

    def _request(self, method, path, payload=None, require_data=False):
        url = urljoin(self.base_url, path.lstrip("/"))
        data = None if payload is None else json.dumps(payload).encode("utf-8")
        request = urllib.request.Request(url, data=data, method=method, headers=self._headers())
        try:
            with urllib.request.urlopen(request, timeout=self.timeout) as response:
                body = response.read().decode("utf-8")
        except (urllib.error.URLError, TimeoutError) as exc:
            raise MemindClientError(str(exc)) from exc
        try:
            envelope = json.loads(body) if body else {}
        except json.JSONDecodeError as exc:
            raise MemindClientError("invalid JSON response") from exc
        if not is_success_envelope(envelope):
            raise MemindClientError(f"unsuccessful ApiResult code: {envelope.get('code')}")
        if require_data and envelope.get("data") is None:
            raise MemindClientError("missing data in ApiResult")
        return envelope

    def health(self):
        return self._request("GET", "/open/v1/health", require_data=True)

    def retrieve(self, user_id, agent_id, query, strategy="SIMPLE", trace=False):
        return self._request(
            "POST",
            "/open/v1/memory/retrieve",
            {"userId": user_id, "agentId": agent_id, "query": query, "strategy": strategy, "trace": trace},
            require_data=True,
        )

    def add_message(self, user_id, agent_id, message, source_client=None):
        payload = {"userId": user_id, "agentId": agent_id, "message": message}
        if source_client:
            payload["sourceClient"] = source_client
        return self._request("POST", "/open/v1/memory/add-message", payload)

    def commit(self, user_id, agent_id, source_client=None):
        payload = {"userId": user_id, "agentId": agent_id}
        if source_client:
            payload["sourceClient"] = source_client
        return self._request("POST", "/open/v1/memory/commit", payload)
