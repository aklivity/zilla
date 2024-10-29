/*
 * Copyright 2021-2024 Aklivity Inc.
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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.binding.kafka.config.KafkaOptionsConfig;
import io.aklivity.zilla.runtime.binding.kafka.config.KafkaOptionsConfigBuilder;
import io.aklivity.zilla.runtime.binding.kafka.internal.KafkaBinding;
import io.aklivity.zilla.runtime.engine.config.OptionsConfig;
import io.aklivity.zilla.runtime.engine.config.OptionsConfigAdapterSpi;

public final class KafkaOptionsConfigAdapter implements OptionsConfigAdapterSpi, JsonbAdapter<OptionsConfig, JsonObject>
{
    private static final Pattern SERVER_PATTERN = Pattern.compile("([^\\:]+):(\\d+)");
    private static final String BOOTSTRAP_NAME = "bootstrap";
    private static final String SERVERS_NAME = "servers";
    private static final String TOPICS_NAME = "topics";
    private static final String SASL_NAME = "sasl";
    private static final String SASL_MECHANISM_NAME = "mechanism";
    private static final String SASL_PLAIN_USERNAME_NAME = "username";
    private static final String SASL_PLAIN_PASSWORD_NAME = "password";

    private final KafkaTopicConfigAdapter topic = new KafkaTopicConfigAdapter();

    @Override
    public Kind kind()
    {
        return Kind.BINDING;
    }

    @Override
    public String type()
    {
        return KafkaBinding.NAME;
    }

    @Override
    public JsonObject adaptToJson(
        OptionsConfig options)
    {
        KafkaOptionsConfig kafkaOptions = (KafkaOptionsConfig) options;

        JsonObjectBuilder object = Json.createObjectBuilder();

        if (kafkaOptions.bootstrap != null &&
            !kafkaOptions.bootstrap.isEmpty())
        {
            JsonArrayBuilder entries = Json.createArrayBuilder();
            kafkaOptions.bootstrap.forEach(b -> entries.add(b));

            object.add(BOOTSTRAP_NAME, entries);
        }

        if (kafkaOptions.topics != null &&
            !kafkaOptions.topics.isEmpty())
        {
            JsonArrayBuilder entries = Json.createArrayBuilder();
            kafkaOptions.topics.forEach(t -> entries.add(topic.adaptToJson(t)));

            object.add(TOPICS_NAME, entries);
        }

        if (kafkaOptions.servers != null &&
            !kafkaOptions.servers.isEmpty())
        {
            JsonArrayBuilder entries = Json.createArrayBuilder();
            kafkaOptions.servers.forEach(s -> entries.add(String.format("%s:%d", s.host, s.port)));

            object.add(SERVERS_NAME, entries);
        }

        if (kafkaOptions.sasl != null)
        {
            JsonObjectBuilder sasl = Json.createObjectBuilder();

            sasl.add(SASL_MECHANISM_NAME, kafkaOptions.sasl.mechanism);
            sasl.add(SASL_PLAIN_USERNAME_NAME, kafkaOptions.sasl.username);
            sasl.add(SASL_PLAIN_PASSWORD_NAME, kafkaOptions.sasl.password);

            object.add(SASL_NAME, sasl);
        }

        return object.build();
    }

    @Override
    public OptionsConfig adaptFromJson(
        JsonObject object)
    {
        KafkaOptionsConfigBuilder<KafkaOptionsConfig> options = KafkaOptionsConfig.builder();

        if (object.containsKey(BOOTSTRAP_NAME))
        {
            object.getJsonArray(BOOTSTRAP_NAME).stream()
                .map(JsonString.class::cast)
                .map(JsonString::getString)
                .forEach(options::bootstrap);
        }

        if (object.containsKey(TOPICS_NAME))
        {
            object.getJsonArray(TOPICS_NAME).stream()
                .map(JsonValue::asJsonObject)
                .map(topic::adaptFromJson)
                .forEach(options::topic);
        }

        if (object.containsKey(SERVERS_NAME))
        {
            object.getJsonArray(SERVERS_NAME).stream()
                .map(JsonString.class::cast)
                .map(JsonString::getString)
                .map(SERVER_PATTERN::matcher)
                .filter(Matcher::matches)
                .forEach(m -> options
                    .server()
                        .host(m.group(1))
                        .port(Integer.parseInt(m.group(2)))
                        .build());
        }

        if (object.containsKey(SASL_NAME))
        {
            JsonObject sasl = object.getJsonObject(SASL_NAME);
            options.sasl()
                .mechanism(sasl.getString(SASL_MECHANISM_NAME))
                .username(sasl.getString(SASL_PLAIN_USERNAME_NAME))
                .password(sasl.getString(SASL_PLAIN_PASSWORD_NAME))
                .build();
        }

        return options.build();
    }
}
