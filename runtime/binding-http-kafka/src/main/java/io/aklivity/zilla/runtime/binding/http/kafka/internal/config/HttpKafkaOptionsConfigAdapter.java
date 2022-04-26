/*
 * Copyright 2021-2022 Aklivity Inc
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
package io.aklivity.zilla.runtime.binding.http.kafka.internal.config;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.binding.http.kafka.internal.HttpKafkaBinding;
import io.aklivity.zilla.runtime.binding.http.kafka.internal.types.String16FW;
import io.aklivity.zilla.runtime.binding.http.kafka.internal.types.String8FW;
import io.aklivity.zilla.runtime.engine.config.OptionsConfig;
import io.aklivity.zilla.runtime.engine.config.OptionsConfigAdapterSpi;

public final class HttpKafkaOptionsConfigAdapter implements OptionsConfigAdapterSpi, JsonbAdapter<OptionsConfig, JsonObject>
{
    private static final String IDEMPOTENCY_NAME = "idempotency";
    private static final String IDEMPOTENCY_HEADER_NAME = "header";

    private static final String8FW IDEMPOTENCY_HEADER_DEFAULT = new String8FW("idempotency-key");
    private static final HttpKafkaIdempotencyConfig IDEMPOTENCY_DEFAULT =
        new HttpKafkaIdempotencyConfig(
            IDEMPOTENCY_HEADER_DEFAULT);

    private static final String CORRELATION_NAME = "correlation";
    private static final String CORRELATION_HEADERS_NAME = "headers";
    private static final String CORRELATION_HEADERS_REPLY_TO_NAME = "reply-to";
    private static final String CORRELATION_HEADERS_CORRELATION_ID_NAME = "correlation-id";

    private static final String16FW CORRELATION_HEADERS_REPLY_TO_DEFAULT = new String16FW("zilla:reply-to");
    private static final String16FW CORRELATION_HEADERS_CORRELATION_ID_DEFAULT = new String16FW("zilla:correlation-id");
    private static final HttpKafkaCorrelationConfig CORRELATION_DEFAULT =
        new HttpKafkaCorrelationConfig(
            CORRELATION_HEADERS_REPLY_TO_DEFAULT,
            CORRELATION_HEADERS_CORRELATION_ID_DEFAULT);

    public static final HttpKafkaOptionsConfig DEFAULT =
        new HttpKafkaOptionsConfig(IDEMPOTENCY_DEFAULT, CORRELATION_DEFAULT);

    @Override
    public Kind kind()
    {
        return Kind.BINDING;
    }

    @Override
    public String type()
    {
        return HttpKafkaBinding.NAME;
    }

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
            newIdempotency.add(IDEMPOTENCY_HEADER_NAME, idempotency.header.asString());

            object.add(IDEMPOTENCY_NAME, newIdempotency);
        }

        HttpKafkaCorrelationConfig correlation = httpKafkaOptions.correlation;
        if (correlation != null &&
            !CORRELATION_DEFAULT.equals(correlation))
        {
            JsonObjectBuilder newHeaders = Json.createObjectBuilder();
            if (!CORRELATION_HEADERS_REPLY_TO_DEFAULT.equals(correlation.replyTo))
            {
                newHeaders.add(CORRELATION_HEADERS_REPLY_TO_NAME, correlation.replyTo.asString());
            }

            if (!CORRELATION_HEADERS_CORRELATION_ID_DEFAULT.equals(correlation.correlationId))
            {
                newHeaders.add(CORRELATION_HEADERS_CORRELATION_ID_NAME, correlation.correlationId.asString());
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
        HttpKafkaIdempotencyConfig newIdempotency = IDEMPOTENCY_DEFAULT;

        if (object.containsKey(IDEMPOTENCY_NAME))
        {
            String8FW newIdempotencyKey = IDEMPOTENCY_HEADER_DEFAULT;

            JsonObject idempotency = object.getJsonObject(IDEMPOTENCY_NAME);
            if (idempotency.containsKey(IDEMPOTENCY_HEADER_NAME))
            {
                newIdempotencyKey = new String8FW(idempotency.getString(IDEMPOTENCY_HEADER_NAME));
            }

            newIdempotency = new HttpKafkaIdempotencyConfig(newIdempotencyKey);
        }

        HttpKafkaCorrelationConfig newCorrelation = CORRELATION_DEFAULT;
        if (object.containsKey(CORRELATION_NAME))
        {
            JsonObject correlation = object.getJsonObject(CORRELATION_NAME);
            if (correlation.containsKey(CORRELATION_HEADERS_NAME))
            {
                JsonObject headers = correlation.getJsonObject(CORRELATION_HEADERS_NAME);

                String16FW newReplyTo = CORRELATION_HEADERS_REPLY_TO_DEFAULT;
                if (headers.containsKey(CORRELATION_HEADERS_REPLY_TO_NAME))
                {
                    newReplyTo = new String16FW(headers.getString(CORRELATION_HEADERS_REPLY_TO_NAME));
                }

                String16FW newCorrelationId = CORRELATION_HEADERS_CORRELATION_ID_DEFAULT;
                if (headers.containsKey(CORRELATION_HEADERS_CORRELATION_ID_NAME))
                {
                    newCorrelationId = new String16FW(headers.getString(CORRELATION_HEADERS_CORRELATION_ID_NAME));
                }

                newCorrelation = new HttpKafkaCorrelationConfig(newReplyTo, newCorrelationId);
            }
        }

        return new HttpKafkaOptionsConfig(newIdempotency, newCorrelation);
    }
}
