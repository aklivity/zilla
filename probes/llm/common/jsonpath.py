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

TOKEN = re.compile(r"([^.\[\]]+)|\[(-?\d+)\]")


def get(obj, path, default=None):
    current = obj
    for key, index in TOKEN.findall(path):
        if index != "":
            position = int(index)
            if isinstance(current, list) and -len(current) <= position < len(current):
                current = current[position]
            else:
                current = default
                break
        elif isinstance(current, dict) and key in current:
            current = current[key]
        else:
            current = default
            break
    return current


def walk(obj, prefix=""):
    if isinstance(obj, dict):
        for key, value in obj.items():
            yield from walk(value, f"{prefix}.{key}" if prefix else key)
    elif isinstance(obj, list):
        for position, value in enumerate(obj):
            yield from walk(value, f"{prefix}[{position}]")
    else:
        yield prefix, obj
