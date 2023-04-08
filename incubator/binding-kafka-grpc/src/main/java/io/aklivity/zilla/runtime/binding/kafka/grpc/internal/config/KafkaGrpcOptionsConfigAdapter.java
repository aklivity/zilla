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
import io.aklivity.zilla.runtime.engine.config.OptionsConfig;
import io.aklivity.zilla.runtime.engine.config.OptionsConfigAdapterSpi;

public class KafkaGrpcOptionsConfigAdapter implements OptionsConfigAdapterSpi, JsonbAdapter<OptionsConfig, JsonObject>
{
    public static final KafkaAckMode ACKS_DEFAULT = KafkaAckMode.IN_SYNC_REPLICAS;
    private static final String ENTRY_DEFAULT = "grpc_client0";
    private static final String ENTRY_NAME = "entry";
    private static final String ACKS_NAME = "acks";

    private static final String CORRELATION_NAME = "correlation";
    private static final String CORRELATION_HEADERS_NAME = "headers";
    private static final String CORRELATION_HEADERS_CORRELATION_ID_NAME = "correlation-id";
    private static final String CORRELATION_HEADERS_SERVICE_NAME = "service";
    private static final String CORRELATION_HEADERS_METHOD_NAME = "method";
    private static final String CORRELATION_HEADERS_REQUEST_NAME = "request";
    private static final String CORRELATION_HEADERS_RESPONSE_NAME = "response";

    private static final String16FW CORRELATION_HEADERS_CORRELATION_ID_DEFAULT = new String16FW("zilla:correlation-id");
    private static final String16FW CORRELATION_HEADERS_SERVICE_DEFAULT = new String16FW("zilla:service");
    private static final String16FW CORRELATION_HEADERS_METHOD_DEFAULT = new String16FW("zilla:method");
    private static final String16FW CORRELATION_HEADERS_REQUEST_DEFAULT = new String16FW("zilla:request");
    private static final String16FW CORRELATION_HEADERS_RESPONSE_DEFAULT = new String16FW("zilla:response");

    private static final KafkaGrpcCorrelationConfig CORRELATION_DEFAULT =
        new KafkaGrpcCorrelationConfig(CORRELATION_HEADERS_CORRELATION_ID_DEFAULT,
            CORRELATION_HEADERS_SERVICE_DEFAULT, CORRELATION_HEADERS_METHOD_DEFAULT,
            CORRELATION_HEADERS_REQUEST_DEFAULT, CORRELATION_HEADERS_RESPONSE_DEFAULT);
    public static final KafkaGrpcOptionsConfig DEFAULT =
        new KafkaGrpcOptionsConfig(ENTRY_DEFAULT, ACKS_DEFAULT, CORRELATION_DEFAULT);

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

            if (!CORRELATION_HEADERS_REQUEST_DEFAULT.equals(correlation.request))
            {
                newHeaders.add(CORRELATION_HEADERS_REQUEST_NAME, correlation.request.asString());
            }

            if (!CORRELATION_HEADERS_RESPONSE_DEFAULT.equals(correlation.response))
            {
                newHeaders.add(CORRELATION_HEADERS_RESPONSE_NAME, correlation.response.asString());
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
        KafkaAckMode newProduceAcks = object.containsKey(ACKS_NAME)
            ? KafkaAckMode.valueOf(object.getString(ACKS_NAME).toUpperCase())
            : ACKS_DEFAULT;

        String newEntry = object.getString(ENTRY_NAME);

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

                String16FW newRequest = CORRELATION_HEADERS_REQUEST_DEFAULT;
                if (headers.containsKey(CORRELATION_HEADERS_REQUEST_NAME))
                {
                    newRequest = new String16FW(headers.getString(CORRELATION_HEADERS_REQUEST_NAME));
                }

                String16FW newResponse = CORRELATION_HEADERS_RESPONSE_DEFAULT;
                if (headers.containsKey(CORRELATION_HEADERS_RESPONSE_NAME))
                {
                    newResponse = new String16FW(headers.getString(CORRELATION_HEADERS_RESPONSE_NAME));
                }

                String16FW newCorrelationId = CORRELATION_HEADERS_CORRELATION_ID_DEFAULT;
                if (headers.containsKey(CORRELATION_HEADERS_CORRELATION_ID_NAME))
                {
                    newCorrelationId = new String16FW(headers.getString(CORRELATION_HEADERS_CORRELATION_ID_NAME));
                }

                newCorrelation = new KafkaGrpcCorrelationConfig(newCorrelationId, newService, newMethod, newRequest, newResponse);
            }
        }

        return new KafkaGrpcOptionsConfig(newEntry, newProduceAcks, newCorrelation);
    }
}
