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
package io.aklivity.zilla.runtime.binding.kafka.grpc.internal.config;

import static io.aklivity.zilla.runtime.engine.config.OptionsConfigAdapterSpi.Kind.BINDING;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.binding.kafka.grpc.internal.KafkaGrpcBinding;
import io.aklivity.zilla.runtime.binding.kafka.grpc.internal.types.KafkaAckMode;
import io.aklivity.zilla.runtime.binding.kafka.grpc.internal.types.String16FW;
import io.aklivity.zilla.runtime.binding.kafka.grpc.internal.types.String8FW;
import io.aklivity.zilla.runtime.engine.config.OptionsConfig;
import io.aklivity.zilla.runtime.engine.config.OptionsConfigAdapterSpi;

public class KafkaGrpcOptionsConfigAdapter implements OptionsConfigAdapterSpi, JsonbAdapter<OptionsConfig, JsonObject>
{
    private static final KafkaAckMode ACKS_DEFAULT = KafkaAckMode.IN_SYNC_REPLICAS;
    private static final String ACKS_NAME = "acks";
    private static final String IDEMPOTENCY_NAME = "idempotency";
    private static final String IDEMPOTENCY_METADATA_NAME = "metadata";
    private static final String CORRELATION_NAME = "correlation";
    private static final String CORRELATION_HEADERS_NAME = "headers";
    private static final String CORRELATION_HEADERS_CORRELATION_ID_NAME = "correlation-id";
    private static final String CORRELATION_HEADERS_SERVICE_NAME = "service";
    private static final String CORRELATION_HEADERS_METHOD_NAME = "method";
    private static final String CORRELATION_HEADERS_REPLY_TO_NAME = "reply-to";

    private static final String8FW IDEMPOTENCY_METADATA_DEFAULT = new String8FW("idempotency-key");
    private static final String16FW CORRELATION_HEADERS_CORRELATION_ID_DEFAULT = new String16FW("zilla:correlation-id");
    private static final String16FW CORRELATION_HEADERS_SERVICE_DEFAULT = new String16FW("zilla:service");
    private static final String16FW CORRELATION_HEADERS_METHOD_DEFAULT = new String16FW("zilla:method");
    private static final String16FW CORRELATION_HEADERS_REPLY_TO_DEFAULT = new String16FW("zilla:reply-to");

    private static final KafkaGrpcIdempotencyConfig IDEMPOTENCY_DEFAULT =
        new KafkaGrpcIdempotencyConfig(IDEMPOTENCY_METADATA_DEFAULT);
    private static final KafkaGrpcCorrelationConfig CORRELATION_DEFAULT =
        new KafkaGrpcCorrelationConfig(CORRELATION_HEADERS_CORRELATION_ID_DEFAULT,
            CORRELATION_HEADERS_SERVICE_DEFAULT, CORRELATION_HEADERS_METHOD_DEFAULT,
            CORRELATION_HEADERS_REPLY_TO_DEFAULT);
    public static final KafkaGrpcOptionsConfig DEFAULT =
        new KafkaGrpcOptionsConfig(ACKS_DEFAULT, IDEMPOTENCY_DEFAULT, CORRELATION_DEFAULT);

    @Override
    public OptionsConfigAdapterSpi.Kind kind()
    {
        return BINDING;
    }

    @Override
    public String type()
    {
        return KafkaGrpcBinding.NAME;
    }

    @Override
    public JsonObject adaptToJson(
        OptionsConfig options)
    {
        KafkaGrpcOptionsConfig kafkaGrpcOptions = (KafkaGrpcOptionsConfig) options;

        JsonObjectBuilder object = Json.createObjectBuilder();

        if (kafkaGrpcOptions.acks != ACKS_DEFAULT)
        {
            object.add(ACKS_NAME, kafkaGrpcOptions.acks.name().toLowerCase());
        }

        KafkaGrpcIdempotencyConfig idempotency = kafkaGrpcOptions.idempotency;
        if (idempotency != null &&
            !IDEMPOTENCY_DEFAULT.equals(idempotency))
        {
            JsonObjectBuilder newIdempotency = Json.createObjectBuilder();

            if (!IDEMPOTENCY_METADATA_DEFAULT.equals(idempotency.metadata))
            {
                newIdempotency.add(IDEMPOTENCY_METADATA_NAME, idempotency.metadata.asString());
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
                newHeaders.add(CORRELATION_HEADERS_SERVICE_NAME, correlation.service.asString());
            }

            if (!CORRELATION_HEADERS_METHOD_DEFAULT.equals(correlation.method))
            {
                newHeaders.add(CORRELATION_HEADERS_METHOD_NAME, correlation.method.asString());
            }

            if (!CORRELATION_HEADERS_CORRELATION_ID_DEFAULT.equals(correlation.correlationId))
            {
                newHeaders.add(CORRELATION_HEADERS_CORRELATION_ID_NAME, correlation.correlationId.asString());
            }

            if (!CORRELATION_HEADERS_REPLY_TO_DEFAULT.equals(correlation.replyTo))
            {
                newHeaders.add(CORRELATION_HEADERS_REPLY_TO_NAME, correlation.replyTo.asString());
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
        KafkaAckMode newProduceAcks = object.containsKey(ACKS_NAME)
            ? KafkaAckMode.valueOf(object.getString(ACKS_NAME).toUpperCase())
            : ACKS_DEFAULT;

        KafkaGrpcIdempotencyConfig newIdempotency = IDEMPOTENCY_DEFAULT;
        if (object.containsKey(IDEMPOTENCY_NAME))
        {
            JsonObject idempotency = object.getJsonObject(IDEMPOTENCY_NAME);

            String8FW newMetadata = IDEMPOTENCY_METADATA_DEFAULT;
            if (idempotency.containsKey(IDEMPOTENCY_METADATA_NAME))
            {
                newMetadata = new String8FW(idempotency.getString(IDEMPOTENCY_METADATA_NAME));
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

                String16FW newService = CORRELATION_HEADERS_SERVICE_DEFAULT;
                if (headers.containsKey(CORRELATION_HEADERS_SERVICE_NAME))
                {
                    newService = new String16FW(headers.getString(CORRELATION_HEADERS_SERVICE_NAME));
                }

                String16FW newMethod = CORRELATION_HEADERS_METHOD_DEFAULT;
                if (headers.containsKey(CORRELATION_HEADERS_METHOD_NAME))
                {
                    newMethod = new String16FW(headers.getString(CORRELATION_HEADERS_METHOD_NAME));
                }

                String16FW newCorrelationId = CORRELATION_HEADERS_CORRELATION_ID_DEFAULT;
                if (headers.containsKey(CORRELATION_HEADERS_CORRELATION_ID_NAME))
                {
                    newCorrelationId = new String16FW(headers.getString(CORRELATION_HEADERS_CORRELATION_ID_NAME));
                }

                String16FW newReplyTo = CORRELATION_HEADERS_REPLY_TO_DEFAULT;
                if (headers.containsKey(CORRELATION_HEADERS_REPLY_TO_NAME))
                {
                    newReplyTo = new String16FW(headers.getString(CORRELATION_HEADERS_REPLY_TO_NAME));
                }

                newCorrelation = new KafkaGrpcCorrelationConfig(newCorrelationId, newService, newMethod, newReplyTo);
            }
        }

        return new KafkaGrpcOptionsConfig(newProduceAcks, newIdempotency, newCorrelation);
    }
}
