#
# Copyright 2021-2026 Aklivity Inc
#
# Licensed under the Aklivity Community License (the "License"); you may not use
# this file except in compliance with the License.  You may obtain a copy of the
# License at
#
#   https://www.aklivity.io/aklivity-community-license/
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
# WARRANTIES OF ANY KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations under the License.
#

import re
from urllib.parse import quote

HEADER_NAME = re.compile(r"(?i)(key|token|secret|auth|cookie|credential|password)")
TOKEN_PATTERNS = [
    re.compile(r"(?i)(bearer\s+)[A-Za-z0-9._~+/=\-]{8,}"),
    re.compile(r"\bsk-[A-Za-z0-9_\-]{16,}"),
    re.compile(r"\bgsk_[A-Za-z0-9]{20,}"),
    re.compile(r"\bAIza[0-9A-Za-z_\-]{30,}"),
    re.compile(r"\bhf_[A-Za-z0-9]{20,}"),
    re.compile(r"\bxai-[A-Za-z0-9]{20,}"),
    re.compile(r"\bnvapi-[A-Za-z0-9_\-]{20,}"),
    re.compile(r"\bpplx-[A-Za-z0-9]{20,}"),
    re.compile(r"\bcsk-[A-Za-z0-9]{20,}"),
    re.compile(r"\bfw_[A-Za-z0-9]{20,}"),
    re.compile(r"\bdapi[0-9a-f]{20,}"),
    re.compile(r"(?i)([?&](?:key|api[_-]?key|token)=)[^&\s\"']+"),
]
MASK = "[REDACTED]"
MIN_SECRET_LENGTH = 6


class Scrubber:
    def __init__(self, secrets):
        values = {s for s in secrets if s and len(s) >= MIN_SECRET_LENGTH}
        self.values = sorted(values | {quote(v, safe="") for v in values}, key=len, reverse=True)

    def text(self, value):
        for secret in self.values:
            value = value.replace(secret, MASK)
        for pattern in TOKEN_PATTERNS:
            value = pattern.sub(lambda m: (m.group(1) if m.groups() else "") + MASK, value)
        return value

    def headers(self, headers):
        return {k: (MASK if HEADER_NAME.search(k) else self.text(str(v))) for k, v in headers.items()}

    def obj(self, value):
        if isinstance(value, str):
            result = self.text(value)
        elif isinstance(value, dict):
            result = {self.text(str(k)): self.obj(v) for k, v in value.items()}
        elif isinstance(value, list):
            result = [self.obj(v) for v in value]
        else:
            result = value
        return result
