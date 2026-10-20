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
import re
import sys
from pathlib import Path
from urllib.parse import urlsplit

from . import runner

SERVER_URL = re.compile(r"^https?://[A-Za-z0-9.\-]+(:[0-9]+)?(/[A-Za-z0-9._~/\-]*)?$")
DEFAULT_PORTS = {"http": 80, "https": 443}
TEMPLATE = """---
name: llm-probe
guards:
  credentials:
    type: inline
bindings:
  north_tcp_server:
    type: tcp
    kind: server
    options:
      host: 0.0.0.0
      port: {port}
    exit: north_http_server
  north_http_server:
    type: http
    kind: server
    exit: north_llm_server
  north_llm_server:
    type: llm
    kind: server
    options:
      dialect: {dialect}
      authorization:
        credentials:
          credentials: "{credentials}"
    exit: south_llm_client
  south_llm_client:
    type: llm
    kind: client
    options:
      dialect: {dialect}
      server: {server}
      authorization:
        credentials:
          credentials: "{credentials}"
    exit: sys:http_client
"""
CREDENTIALS = {"openai": "Bearer {credentials}"}


def build(dialect, server, port=7161):
    if not SERVER_URL.match(server):
        raise ValueError("server URL has unexpected characters")
    parts = urlsplit(server)
    origin = f"{parts.scheme}://{parts.hostname}:{parts.port or DEFAULT_PORTS[parts.scheme]}"
    return TEMPLATE.format(port=port, dialect=dialect, server=origin + parts.path.rstrip("/"),
                           credentials=CREDENTIALS[dialect])


def main(argv=None):
    parser = argparse.ArgumentParser()
    parser.add_argument("--dialect", required=True)
    parser.add_argument("--server")
    parser.add_argument("--provider")
    parser.add_argument("--root", default=str(Path(__file__).resolve().parents[1]))
    parser.add_argument("--port", type=int, default=7161)
    args = parser.parse_args(argv)
    server = args.server
    if args.provider:
        provider = runner.load_provider(Path(args.root) / args.dialect / "providers.json", args.provider)
        server, missing = runner.expand(provider["base_url"], runner.apply_injected(os.environ))
        if missing:
            raise ValueError(f"missing environment: {', '.join(missing)}")
    sys.stdout.write(build(args.dialect, server, args.port))


if __name__ == "__main__":
    main()
