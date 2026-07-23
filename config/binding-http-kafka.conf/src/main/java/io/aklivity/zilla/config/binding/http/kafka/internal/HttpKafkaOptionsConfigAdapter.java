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
package io.aklivity.zilla.config.binding.http.kafka.internal;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.config.binding.http.kafka.HttpKafkaCorrelationConfig;
import io.aklivity.zilla.config.binding.http.kafka.HttpKafkaCorrelationConfigBuilder;
import io.aklivity.zilla.config.binding.http.kafka.HttpKafkaIdempotencyConfig;
import io.aklivity.zilla.config.binding.http.kafka.HttpKafkaOptionsConfig;
import io.aklivity.zilla.config.binding.http.kafka.HttpKafkaOptionsConfigBuilder;
import io.aklivity.zilla.config.engine.OptionsConfig;

public final class HttpKafkaOptionsConfigAdapter implements JsonbAdapter<OptionsConfig, JsonObject>
{
    private static final String IDEMPOTENCY_NAME = "idempotency";
    private static final String IDEMPOTENCY_HEADER_NAME = "header";

    private static final HttpKafkaIdempotencyConfig IDEMPOTENCY_DEFAULT = HttpKafkaOptionsConfig.DEFAULT.idempotency;

    private static final String CORRELATION_NAME = "correlation";
    private static final String CORRELATION_HEADERS_NAME = "headers";
    private static final String CORRELATION_HEADERS_REPLY_TO_NAME = "reply-to";
    private static final String CORRELATION_HEADERS_CORRELATION_ID_NAME = "correlation-id";

    private static final HttpKafkaCorrelationConfig CORRELATION_DEFAULT = HttpKafkaOptionsConfig.DEFAULT.correlation;

    @Override
    public JsonObject adaptToJson(
        OptionsConfig options)
    {
        HttpKafkaOptionsConfig httpKafkaOptions = (HttpKafkaOptionsConfig) options;

        JsonObjectBuilder object = Json.createObjectBuilder();

        HttpKafkaIdempotencyConfig idempotency = httpKafkaOptions.idempotency;
        if (idempotency != null &&
            !(IDEMPOTENCY_DEFAULT.header.equals(idempotency.header)))
        {
            JsonObjectBuilder newIdempotency = Json.createObjectBuilder();
            newIdempotency.add(IDEMPOTENCY_HEADER_NAME, idempotency.header);

            object.add(IDEMPOTENCY_NAME, newIdempotency);
        }

        HttpKafkaCorrelationConfig correlation = httpKafkaOptions.correlation;
        if (correlation != null &&
            !CORRELATION_DEFAULT.equals(correlation))
        {
            JsonObjectBuilder newHeaders = Json.createObjectBuilder();
            if (!CORRELATION_DEFAULT.replyTo.equals(correlation.replyTo))
            {
                newHeaders.add(CORRELATION_HEADERS_REPLY_TO_NAME, correlation.replyTo);
            }

            if (!CORRELATION_DEFAULT.correlationId.equals(correlation.correlationId))
            {
                newHeaders.add(CORRELATION_HEADERS_CORRELATION_ID_NAME, correlation.correlationId);
            }

            JsonObjectBuilder newCorrelation = Json.createObjectBuilder();
            newCorrelation.add(CORRELATION_HEADERS_NAME, newHeaders);

            object.add(CORRELATION_NAME, newCorrelation);
        }

        return object.build();
    }

    @Override
    public OptionsConfig adaptFromJson(
        JsonObject object)
    {
        HttpKafkaOptionsConfigBuilder<HttpKafkaOptionsConfig> builder = HttpKafkaOptionsConfig.builder();

        if (object.containsKey(IDEMPOTENCY_NAME))
        {
            JsonObject idempotency = object.getJsonObject(IDEMPOTENCY_NAME);
            if (idempotency.containsKey(IDEMPOTENCY_HEADER_NAME))
            {
                builder.idempotency()
                    .header(idempotency.getString(IDEMPOTENCY_HEADER_NAME))
                    .build();
            }
        }

        if (object.containsKey(CORRELATION_NAME))
        {
            JsonObject correlationJson = object.getJsonObject(CORRELATION_NAME);
            if (correlationJson.containsKey(CORRELATION_HEADERS_NAME))
            {
                HttpKafkaCorrelationConfigBuilder<HttpKafkaOptionsConfigBuilder<HttpKafkaOptionsConfig>>
                    correlation = builder.correlation();
                JsonObject headers = correlationJson.getJsonObject(CORRELATION_HEADERS_NAME);

                if (headers.containsKey(CORRELATION_HEADERS_REPLY_TO_NAME))
                {
                    correlation.replyTo(headers.getString(CORRELATION_HEADERS_REPLY_TO_NAME));
                }

                if (headers.containsKey(CORRELATION_HEADERS_CORRELATION_ID_NAME))
                {
                    correlation.correlationId(headers.getString(CORRELATION_HEADERS_CORRELATION_ID_NAME));
                }

                correlation.build();
            }
        }

        return builder.build();
    }
}
