/*
 * Copyright 2021-2026 Aklivity Inc
 *
 * Licensed under the Aklivity Community License (the "License"); you may not use
 * this file except in compliance with the License.  You may obtain a copy of the
 * License at
 *
 *   https://www.aklivity.io/aklivity-community-license/
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */
package io.aklivity.zilla.runtime.binding.mcp.internal.codec;

import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;

public record McpInitializeParams(
    String protocolVersion,
    JsonObject capabilities)
{
    public static McpInitializeParams parse(
        JsonObject params)
    {
        final JsonValue protocolVersionValue = params.get("protocolVersion");
        final JsonValue capabilitiesValue = params.get("capabilities");
        final boolean capabilitiesValid = capabilitiesValue == null ||
            capabilitiesValue.getValueType() == JsonValue.ValueType.OBJECT;

        final String protocolVersion = protocolVersionValue != null &&
            protocolVersionValue.getValueType() == JsonValue.ValueType.STRING
                ? ((JsonString) protocolVersionValue).getString()
                : null;
        final JsonObject capabilities = capabilitiesValid && capabilitiesValue != null
            ? capabilitiesValue.asJsonObject()
            : null;

        return capabilitiesValid ? new McpInitializeParams(protocolVersion, capabilities) : null;
    }
}
