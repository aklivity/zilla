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
package io.aklivity.zilla.runtime.classifier.presidio.internal;

import java.io.StringReader;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonStructure;
import jakarta.json.JsonValue;
import jakarta.json.JsonValue.ValueType;

final class PresidioResponse
{
    static boolean detected(
        String body)
    {
        final boolean detected;

        try (JsonReader reader = Json.createReader(new StringReader(body)))
        {
            JsonStructure structure = reader.read();

            if (structure.getValueType() != ValueType.ARRAY)
            {
                throw new IllegalArgumentException("Expected an array of findings");
            }

            JsonArray findings = structure.asJsonArray();
            findings.forEach(PresidioResponse::validate);

            detected = !findings.isEmpty();
        }

        return detected;
    }

    private static void validate(
        JsonValue finding)
    {
        if (finding.getValueType() != ValueType.OBJECT || !locatable(finding.asJsonObject()))
        {
            throw new IllegalArgumentException("Expected a located finding");
        }
    }

    private static boolean locatable(
        JsonObject finding)
    {
        return hasType(finding, "entity_type", ValueType.STRING) &&
            hasType(finding, "start", ValueType.NUMBER) &&
            hasType(finding, "end", ValueType.NUMBER);
    }

    private static boolean hasType(
        JsonObject finding,
        String name,
        ValueType type)
    {
        JsonValue value = finding.get(name);

        return value != null && value.getValueType() == type;
    }

    private PresidioResponse()
    {
    }
}
