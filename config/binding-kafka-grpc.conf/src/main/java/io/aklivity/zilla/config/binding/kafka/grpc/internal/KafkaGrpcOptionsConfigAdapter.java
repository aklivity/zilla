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
package io.aklivity.zilla.config.binding.kafka.grpc.internal;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.config.binding.kafka.grpc.KafkaGrpcCorrelationConfig;
import io.aklivity.zilla.config.binding.kafka.grpc.KafkaGrpcIdempotencyConfig;
import io.aklivity.zilla.config.binding.kafka.grpc.KafkaGrpcOptionsConfig;
import io.aklivity.zilla.config.engine.OptionsConfig;

public final class KafkaGrpcOptionsConfigAdapter implements JsonbAdapter<OptionsConfig, JsonObject>
{
    private static final String ACKS_NAME = "acks";
    private static final String IDEMPOTENCY_NAME = "idempotency";
    private static final String IDEMPOTENCY_METADATA_NAME = "metadata";
    private static final String CORRELATION_NAME = "correlation";
    private static final String CORRELATION_HEADERS_NAME = "headers";
    private static final String CORRELATION_HEADERS_CORRELATION_ID_NAME = "correlation-id";
    private static final String CORRELATION_HEADERS_SERVICE_NAME = "service";
    private static final String CORRELATION_HEADERS_METHOD_NAME = "method";
    private static final String CORRELATION_HEADERS_REPLY_TO_NAME = "reply-to";

    private static final String ACKS_DEFAULT = KafkaGrpcOptionsConfig.DEFAULT.acks;
    private static final KafkaGrpcIdempotencyConfig IDEMPOTENCY_DEFAULT = KafkaGrpcOptionsConfig.DEFAULT.idempotency;
    private static final String IDEMPOTENCY_METADATA_DEFAULT = IDEMPOTENCY_DEFAULT.metadata;
    private static final KafkaGrpcCorrelationConfig CORRELATION_DEFAULT = KafkaGrpcOptionsConfig.DEFAULT.correlation;
    private static final String CORRELATION_HEADERS_CORRELATION_ID_DEFAULT = CORRELATION_DEFAULT.correlationId;
    private static final String CORRELATION_HEADERS_SERVICE_DEFAULT = CORRELATION_DEFAULT.service;
    private static final String CORRELATION_HEADERS_METHOD_DEFAULT = CORRELATION_DEFAULT.method;
    private static final String CORRELATION_HEADERS_REPLY_TO_DEFAULT = CORRELATION_DEFAULT.replyTo;

    @Override
    public JsonObject adaptToJson(
        OptionsConfig options)
    {
        KafkaGrpcOptionsConfig kafkaGrpcOptions = (KafkaGrpcOptionsConfig) options;

        JsonObjectBuilder object = Json.createObjectBuilder();

        if (!ACKS_DEFAULT.equals(kafkaGrpcOptions.acks))
        {
            object.add(ACKS_NAME, kafkaGrpcOptions.acks);
        }

        KafkaGrpcIdempotencyConfig idempotency = kafkaGrpcOptions.idempotency;
        if (idempotency != null &&
            !IDEMPOTENCY_DEFAULT.equals(idempotency))
        {
            JsonObjectBuilder newIdempotency = Json.createObjectBuilder();

            if (!IDEMPOTENCY_METADATA_DEFAULT.equals(idempotency.metadata))
            {
                newIdempotency.add(IDEMPOTENCY_METADATA_NAME, idempotency.metadata);
            }

            object.add(IDEMPOTENCY_NAME, newIdempotency);
        }

        KafkaGrpcCorrelationConfig correlation = kafkaGrpcOptions.correlation;
        if (correlation != null &&
            !CORRELATION_DEFAULT.equals(correlation))
        {
            JsonObjectBuilder newHeaders = Json.createObjectBuilder();

            if (!CORRELATION_HEADERS_SERVICE_DEFAULT.equals(correlation.service))
            {
                newHeaders.add(CORRELATION_HEADERS_SERVICE_NAME, correlation.service);
            }

            if (!CORRELATION_HEADERS_METHOD_DEFAULT.equals(correlation.method))
            {
                newHeaders.add(CORRELATION_HEADERS_METHOD_NAME, correlation.method);
            }

            if (!CORRELATION_HEADERS_CORRELATION_ID_DEFAULT.equals(correlation.correlationId))
            {
                newHeaders.add(CORRELATION_HEADERS_CORRELATION_ID_NAME, correlation.correlationId);
            }

            if (!CORRELATION_HEADERS_REPLY_TO_DEFAULT.equals(correlation.replyTo))
            {
                newHeaders.add(CORRELATION_HEADERS_REPLY_TO_NAME, correlation.replyTo);
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
        String newProduceAcks = object.containsKey(ACKS_NAME)
            ? object.getString(ACKS_NAME)
            : ACKS_DEFAULT;

        KafkaGrpcIdempotencyConfig newIdempotency = IDEMPOTENCY_DEFAULT;
        if (object.containsKey(IDEMPOTENCY_NAME))
        {
            JsonObject idempotency = object.getJsonObject(IDEMPOTENCY_NAME);

            String newMetadata = IDEMPOTENCY_METADATA_DEFAULT;
            if (idempotency.containsKey(IDEMPOTENCY_METADATA_NAME))
            {
                newMetadata = idempotency.getString(IDEMPOTENCY_METADATA_NAME);
            }

            newIdempotency = new KafkaGrpcIdempotencyConfig(newMetadata);
        }

        KafkaGrpcCorrelationConfig newCorrelation = CORRELATION_DEFAULT;
        if (object.containsKey(CORRELATION_NAME))
        {
            JsonObject correlation = object.getJsonObject(CORRELATION_NAME);
            if (correlation.containsKey(CORRELATION_HEADERS_NAME))
            {
                JsonObject headers = correlation.getJsonObject(CORRELATION_HEADERS_NAME);

                String newService = CORRELATION_HEADERS_SERVICE_DEFAULT;
                if (headers.containsKey(CORRELATION_HEADERS_SERVICE_NAME))
                {
                    newService = headers.getString(CORRELATION_HEADERS_SERVICE_NAME);
                }

                String newMethod = CORRELATION_HEADERS_METHOD_DEFAULT;
                if (headers.containsKey(CORRELATION_HEADERS_METHOD_NAME))
                {
                    newMethod = headers.getString(CORRELATION_HEADERS_METHOD_NAME);
                }

                String newCorrelationId = CORRELATION_HEADERS_CORRELATION_ID_DEFAULT;
                if (headers.containsKey(CORRELATION_HEADERS_CORRELATION_ID_NAME))
                {
                    newCorrelationId = headers.getString(CORRELATION_HEADERS_CORRELATION_ID_NAME);
                }

                String newReplyTo = CORRELATION_HEADERS_REPLY_TO_DEFAULT;
                if (headers.containsKey(CORRELATION_HEADERS_REPLY_TO_NAME))
                {
                    newReplyTo = headers.getString(CORRELATION_HEADERS_REPLY_TO_NAME);
                }

                newCorrelation = new KafkaGrpcCorrelationConfig(newCorrelationId, newService, newMethod, newReplyTo);
            }
        }

        return new KafkaGrpcOptionsConfig(newProduceAcks, newIdempotency, newCorrelation);
    }
}
