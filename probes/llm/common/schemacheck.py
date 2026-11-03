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

TYPES = {
    "object": dict, "array": list, "string": str, "boolean": bool,
    "integer": int, "number": (int, float), "null": type(None),
}


def validate(value, schema, path="$"):
    errors = []
    declared = schema.get("type")
    if declared is not None:
        names = declared if isinstance(declared, list) else [declared]
        ok = any(
            isinstance(value, TYPES[n]) and not (n in ("integer", "number") and isinstance(value, bool))
            for n in names)
        if not ok:
            errors.append(f"{path}: expected {declared}")
    if not errors:
        if "enum" in schema and value not in schema["enum"]:
            errors.append(f"{path}: not in enum")
        if isinstance(value, dict):
            properties = schema.get("properties", {})
            for name in schema.get("required", []):
                if name not in value:
                    errors.append(f"{path}.{name}: required")
            for name, item in value.items():
                if name in properties:
                    errors.extend(validate(item, properties[name], f"{path}.{name}"))
                elif schema.get("additionalProperties") is False:
                    errors.append(f"{path}.{name}: additional property")
        if isinstance(value, list) and "items" in schema:
            for position, item in enumerate(value):
                errors.extend(validate(item, schema["items"], f"{path}[{position}]"))
        if isinstance(value, (int, float)) and not isinstance(value, bool) and "minimum" in schema:
            if value < schema["minimum"]:
                errors.append(f"{path}: below minimum")
    return errors
