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

import argparse
import os
import sys
from pathlib import Path
from urllib.parse import quote

from .redact import MASK, MIN_SECRET_LENGTH, TOKEN_PATTERNS


def secret_values(env, names):
    values = {env[n] for n in names if env.get(n)}
    values |= {v for k, v in env.items() if k.startswith("PROBE_ENV_VALUE_") and v}
    values |= {v for k, v in env.items() if k.endswith("_API_KEY") and v}
    return {v for v in values if len(v) >= MIN_SECRET_LENGTH}


FIXTURE_PREFIX = "sk-test-"


def looks_real(text):
    token = text.split()[-1]
    placeholder = token.replace("_", "").isalpha() and token.upper() == token
    return not (FIXTURE_PREFIX in token or any(c in token for c in "{}$<>[]") or token.isalpha() or placeholder)


def scan(paths, values):
    findings = []
    candidates = values | {quote(v, safe="") for v in values}
    for root in paths:
        for path in sorted(Path(root).rglob("*")) if Path(root).is_dir() else [Path(root)]:
            if not path.is_file() or "__pycache__" in path.parts:
                continue
            text = path.read_text(errors="replace")
            if any(v in text for v in candidates):
                findings.append((str(path), "known secret value"))
            for pattern in TOKEN_PATTERNS:
                for match in pattern.finditer(text):
                    if MASK not in match.group(0) and looks_real(match.group(0)):
                        findings.append((str(path), f"credential pattern {pattern.pattern[:24]}"))
                        break
    return findings


def main(argv=None):
    parser = argparse.ArgumentParser()
    parser.add_argument("paths", nargs="+")
    parser.add_argument("--names", default="")
    args = parser.parse_args(argv)
    findings = scan(args.paths, secret_values(os.environ, [n for n in args.names.split(",") if n]))
    for path, kind in findings:
        print(f"{path}: {kind}")
    return 1 if findings else 0


if __name__ == "__main__":
    sys.exit(main())
