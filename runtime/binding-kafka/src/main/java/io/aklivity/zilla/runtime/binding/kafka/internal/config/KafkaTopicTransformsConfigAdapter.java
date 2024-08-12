/*
 * Copyright 2021-2023 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.runtime.binding.kafka.internal.config;

import java.util.Map;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.binding.kafka.config.KafkaTopicHeaderType;
import io.aklivity.zilla.runtime.binding.kafka.config.KafkaTopicTransformsConfig;
import io.aklivity.zilla.runtime.binding.kafka.config.KafkaTopicTransformsConfigBuilder;

public final class KafkaTopicTransformsConfigAdapter implements JsonbAdapter<KafkaTopicTransformsConfig, JsonObject>
{
    private static final String EXTRACT_KEY_NAME = "extract-key";
    private static final String EXTRACT_HEADERS_NAME = "extract-headers";

    @Override
    public JsonObject adaptToJson(
        KafkaTopicTransformsConfig transforms)
    {
        JsonObjectBuilder object = Json.createObjectBuilder();

        if (transforms.extractKey != null)
        {
            object.add(EXTRACT_KEY_NAME, transforms.extractKey);
        }

        if (transforms.extractHeaders != null && !transforms.extractHeaders.isEmpty())
        {
            JsonObjectBuilder headers = Json.createObjectBuilder();
            for (KafkaTopicHeaderType header : transforms.extractHeaders)
            {
                headers.add(header.name, header.path);
            }
            object.add(EXTRACT_HEADERS_NAME, headers);
        }

        return object.build();
    }

    @Override
    public KafkaTopicTransformsConfig adaptFromJson(
        JsonObject object)
    {
        KafkaTopicTransformsConfigBuilder<KafkaTopicTransformsConfig> topicBuilder = KafkaTopicTransformsConfig.builder();

        String extractKey = object.containsKey(EXTRACT_KEY_NAME)
            ? object.getString(EXTRACT_KEY_NAME)
            : null;
        topicBuilder.extractKey(extractKey);

        JsonObject headers = object.containsKey(EXTRACT_HEADERS_NAME) ? object.getJsonObject(EXTRACT_HEADERS_NAME) : null;

        if (headers != null)
        {
            for (Map.Entry<String, JsonValue> entry : headers.entrySet())
            {
                JsonString jsonString = (JsonString) entry.getValue();
                topicBuilder.extractHeader(entry.getKey(), jsonString.getString());
            }
        }

        return topicBuilder.build();
    }
}
