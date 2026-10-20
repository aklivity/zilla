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
import re

from . import jsonpath

PLACEHOLDER = re.compile(r"\{([A-Za-z0-9_.\[\]\-]+)\}")


def _num(value):
    return isinstance(value, (int, float)) and not isinstance(value, bool)


def _leaf(cond, facts):
    value = jsonpath.get(facts, cond["path"])
    op = cond["op"]
    operand = jsonpath.get(facts, cond["value_path"]) if "value_path" in cond else cond.get("value")
    if op == "exists":
        result = (value is not None and value != "" and value != []) == bool(operand if operand is not None else True)
    elif op == "eq":
        result = value == operand
    elif op == "ne":
        result = value != operand
    elif op in ("lt", "le", "gt", "ge"):
        result = _num(value) and _num(operand) and {
            "lt": value < operand, "le": value <= operand, "gt": value > operand, "ge": value >= operand}[op]
    elif op == "in":
        result = value in operand
    elif op == "not_in":
        result = value not in operand
    elif op == "matches":
        result = isinstance(value, str) and re.search(operand, value, re.IGNORECASE | re.DOTALL) is not None
    elif op == "contains":
        result = isinstance(value, (str, list)) and operand in value
    elif op == "icontains":
        result = isinstance(value, str) and str(operand).lower() in value.lower()
    elif op == "len_eq":
        result = hasattr(value, "__len__") and len(value) == operand
    elif op == "len_ge":
        result = hasattr(value, "__len__") and len(value) >= operand
    elif op == "status_class":
        result = _num(value) and int(value) // 100 == operand
    else:
        raise ValueError(f"unknown op {op}")
    return result


def evaluate(cond, facts):
    if "all" in cond:
        result = all(evaluate(c, facts) for c in cond["all"])
    elif "any" in cond:
        result = any(evaluate(c, facts) for c in cond["any"])
    elif "not" in cond:
        result = not evaluate(cond["not"], facts)
    else:
        result = _leaf(cond, facts)
    return result


def render(template, facts):
    def sub(match):
        value = jsonpath.get(facts, match.group(1))
        if value is None:
            text = "n/a"
        elif isinstance(value, (dict, list)):
            text = json.dumps(value, separators=(",", ":"))[:120]
        else:
            text = str(value)
        return text

    return PLACEHOLDER.sub(sub, template)
