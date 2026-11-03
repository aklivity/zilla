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

import json
import shutil
import tempfile
from pathlib import Path
from types import SimpleNamespace

from .. import runner
from .mock_provider import CHAT_MODEL, REASONING_MODEL, MockProvider

REAL = Path(__file__).resolve().parents[2] / "openai"


def run_against(profile, provider_overrides=None, probes="all", via="direct", zilla_url=None, secret="sk-test-secret-0123456789"):
    with MockProvider(profile) as mock, tempfile.TemporaryDirectory() as tmp:
        root = Path(tmp) / "root"
        shutil.copytree(REAL / "probes", root / "openai" / "probes")
        shutil.copy(REAL / "constants.json", root / "openai" / "constants.json")
        provider = {
            "id": "mock", "name": "Mock", "tier": "p1", "hosting": "hosted", "secret": "MOCK_API_KEY",
            "base_url": mock.base_url, "auth": {"scheme": "bearer"}, "extra_env": [],
            "models": {"chat": CHAT_MODEL, "reasoning": REASONING_MODEL}, "endpoints": {"responses": True},
            "price_usd_per_mtok": {"input": 1.0, "output": 2.0}, "deviations": [], "verification": "verified",
        }
        provider.update(provider_overrides or {})
        (root / "openai" / "providers.json").write_text(json.dumps({"providers": [provider]}))
        out = Path(tmp) / "out.json"
        args = SimpleNamespace(root=str(root), dialect="openai", provider=provider["id"], probes=probes, via=via,
                               zilla_url=zilla_url or "http://127.0.0.1:1/v1", zilla_ref="test", budget_usd=None,
                               timeout=20, skip_reason=None, out=str(out))
        meta, records = runner.run(args, {"MOCK_API_KEY": secret})
        text = out.read_text()
        by_probe = {r["probe"]: r for r in records if r["via"] == "direct"}
        return meta, records, by_probe, text, mock
