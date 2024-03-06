/*
 * Copyright 2021-2023 Aklivity Inc
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
package io.aklivity.zilla.runtime.exporter.otlp.internal.serializer;

import java.io.StringReader;
import java.util.List;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;

import io.aklivity.zilla.runtime.engine.config.AttributeConfig;

public class OtlpLogsSerializer
{
    private static final String SCOPE_NAME = "OtlpLogsSerializer";
    private static final String SCOPE_VERSION = "1.0.0";

    private final List<AttributeConfig> attributes;

    public OtlpLogsSerializer(
        List<AttributeConfig> attributes)
    {
        this.attributes = attributes;
    }

    public String serializeAll()
    {
        JsonArrayBuilder attributesArray = Json.createArrayBuilder();
        attributes.forEach(attr -> attributesArray.add(attributeToJson(attr)));
        //JsonArrayBuilder logsArray = Json.createArrayBuilder();
        String logRecords =
            "[\n" +
            "            {\n" +
            "              \"timeUnixNano\": \"1544712660300000000\",\n" +
            "              \"observedTimeUnixNano\": \"1544712660300000000\",\n" +
            "              \"severityNumber\": 10,\n" +
            "              \"severityText\": \"Information\",\n" +
            "              \"traceId\": \"5B8EFFF798038103D269B633813FC60C\",\n" +
            "              \"spanId\": \"EEE19B7EC3C1B174\",\n" +
            "              \"body\": {\n" +
            "                \"stringValue\": \"1st Example log record\"\n" +
            "              },\n" +
            "              \"attributes\": [\n" +
            "                {\n" +
            "                  \"key\": \"string.attribute\",\n" +
            "                  \"value\": {\n" +
            "                    \"stringValue\": \"jo napot\"\n" +
            "                  }\n" +
            "                },\n" +
            "                {\n" +
            "                  \"key\": \"int.attribute\",\n" +
            "                  \"value\": {\n" +
            "                    \"intValue\": \"77\"\n" +
            "                  }\n" +
            "                }\n" +
            "              ]\n" +
            "            }\n" +
            "]";
        JsonReader reader = Json.createReader(new StringReader(logRecords));
        JsonArray logsArray = reader.readArray();
        // TODO: Ati - serialize logs to logsArray
        return createJson(attributesArray, logsArray);
    }

    private JsonObject attributeToJson(
        AttributeConfig attributeConfig)
    {
        JsonObject value = Json.createObjectBuilder()
            .add("stringValue", attributeConfig.value)
            .build();
        return Json.createObjectBuilder()
            .add("key", attributeConfig.name)
            .add("value", value)
            .build();
    }

    private String createJson(
        JsonArrayBuilder attributes,
        //JsonArrayBuilder logsArray)
        JsonArray logsArray)
    {
        JsonObject resource = Json.createObjectBuilder()
            .add("attributes", attributes)
            .build();
        JsonObject scope = Json.createObjectBuilder()
            .add("name", SCOPE_NAME)
            .add("version", SCOPE_VERSION)
            .build();
        JsonObject scopeLogs = Json.createObjectBuilder()
            .add("scope", scope)
            .add("logRecords", logsArray)
            .build();
        JsonArray scopeLogsArray = Json.createArrayBuilder()
            .add(scopeLogs)
            .build();
        JsonObject resourceLogs = Json.createObjectBuilder()
            .add("resource", resource)
            .add("scopeLogs", scopeLogsArray)
            .build();
        JsonArray resourceLogsArray = Json.createArrayBuilder()
            .add(resourceLogs)
            .build();
        JsonObject jsonObject = Json.createObjectBuilder()
            .add("resourceLogs", resourceLogsArray)
            .build();
        return jsonObject.toString();
    }
}
