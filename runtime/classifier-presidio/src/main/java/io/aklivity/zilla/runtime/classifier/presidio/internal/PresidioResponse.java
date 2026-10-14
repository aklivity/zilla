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
import java.util.ArrayList;
import java.util.List;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonNumber;
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
        return !read(body).isEmpty();
    }

    static List<PresidioFinding> findings(
        String body)
    {
        JsonArray array = read(body);
        List<PresidioFinding> findings = new ArrayList<>(array.size());

        array.forEach(finding -> findings.add(finding(finding.asJsonObject())));

        return findings;
    }

    private static JsonArray read(
        String body)
    {
        final JsonArray findings;

        try (JsonReader reader = Json.createReader(new StringReader(body)))
        {
            JsonStructure structure = reader.read();

            if (structure.getValueType() != ValueType.ARRAY)
            {
                throw new IllegalArgumentException("Expected an array of findings");
            }

            findings = structure.asJsonArray();
            findings.forEach(PresidioResponse::validate);
        }

        return findings;
    }

    private static PresidioFinding finding(
        JsonObject finding)
    {
        JsonValue score = finding.get("score");

        return new PresidioFinding(
            finding.getString("entity_type"),
            finding.getJsonNumber("start").intValue(),
            finding.getJsonNumber("end").intValue(),
            score != null && score.getValueType() == ValueType.NUMBER ? ((JsonNumber) score).doubleValue() : 0.0);
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
