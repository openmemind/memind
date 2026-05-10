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

from __future__ import annotations

API_PREFIX = "/open/v1"
DEFAULT_CONNECT_TIMEOUT = 5.0
DEFAULT_MAX_RETRIES = 2
DEFAULT_READ_TIMEOUT = 30.0
ENV_API_TOKEN = "MEMIND_API_TOKEN"
ENV_BASE_URL = "MEMIND_BASE_URL"
RETRYABLE_STATUS_CODES = frozenset({408, 429, 500, 502, 503, 504})
