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
package io.aklivity.zilla.runtime.binding.grpc.kafka.internal.config;


import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.types.String8FW;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.GrpcKafkaBinding;
import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.types.String16FW;
import io.aklivity.zilla.runtime.engine.config.OptionsConfig;
import io.aklivity.zilla.runtime.engine.config.OptionsConfigAdapterSpi;

public final class GrpcKafkaOptionsConfigAdapter implements OptionsConfigAdapterSpi, JsonbAdapter<OptionsConfig, JsonObject>
{
    private static final String LAST_MESSAGE_ID_NAME = "last_message_id";
    private static final String LAST_MESSAGE_ID_METADATA_NAME_NAME = "last_message_id_metadata_name";
    private static final String CORRELATION_NAME = "correlation";
    private static final String CORRELATION_HEADERS_NAME = "headers";
    private static final String CORRELATION_HEADERS_CORRELATION_ID_NAME = "correlation-id";
    private static final String CORRELATION_HEADERS_SERVICE_NAME = "service";
    private static final String CORRELATION_HEADERS_METHOD_NAME = "method";

    private static final String16FW CORRELATION_HEADERS_CORRELATION_ID_DEFAULT = new String16FW("zilla:correlation-id");
    private static final String16FW CORRELATION_HEADERS_SERVICE_DEFAULT = new String16FW("zilla:service");
    private static final String16FW CORRELATION_HEADERS_METHOD_DEFAULT = new String16FW("zilla:method");

    private static final int LAST_MESSAGE_ID_DEFAULT = 32767;
    private static final String8FW LAST_MESSAGE_ID_METADATA_NAME_DEFAULT = new String8FW("lastMessageId");
    private static final GrpcKafkaCorrelationConfig CORRELATION_DEFAULT =
        new GrpcKafkaCorrelationConfig(CORRELATION_HEADERS_CORRELATION_ID_DEFAULT,
            CORRELATION_HEADERS_SERVICE_DEFAULT, CORRELATION_HEADERS_METHOD_DEFAULT);
    public static final GrpcKafkaOptionsConfig DEFAULT =
        new GrpcKafkaOptionsConfig(LAST_MESSAGE_ID_DEFAULT, LAST_MESSAGE_ID_METADATA_NAME_DEFAULT, CORRELATION_DEFAULT);

    @Override
    public Kind kind()
    {
        return Kind.BINDING;
    }

    @Override
    public String type()
    {
        return GrpcKafkaBinding.NAME;
    }

    @Override
    public JsonObject adaptToJson(
        OptionsConfig options)
    {
        GrpcKafkaOptionsConfig grpcKafkaOptions = (GrpcKafkaOptionsConfig) options;

        JsonObjectBuilder object = Json.createObjectBuilder();

        if (LAST_MESSAGE_ID_DEFAULT != grpcKafkaOptions.lastMessageId)
        {
            object.add(LAST_MESSAGE_ID_NAME, grpcKafkaOptions.lastMessageId);
        }

        if (!LAST_MESSAGE_ID_METADATA_NAME_DEFAULT.equals(grpcKafkaOptions.lastMessageIdMetadataName))
        {
            object.add(LAST_MESSAGE_ID_METADATA_NAME_NAME, grpcKafkaOptions.lastMessageIdMetadataName.asString());
        }

        GrpcKafkaCorrelationConfig correlation = grpcKafkaOptions.correlation;
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
        int newLastMessageId = LAST_MESSAGE_ID_DEFAULT;
        if (object.containsKey(CORRELATION_NAME))
        {
            int lastMessageId = object.getInt(LAST_MESSAGE_ID_NAME);
            if (lastMessageId < 19000 || lastMessageId > 19999)
            {
                newLastMessageId = lastMessageId;
            }
        }

        String8FW newLastMessageIdMetadataName = LAST_MESSAGE_ID_METADATA_NAME_DEFAULT;
        if (object.containsKey(LAST_MESSAGE_ID_METADATA_NAME_NAME))
        {
            newLastMessageIdMetadataName = new String8FW(object.getString(LAST_MESSAGE_ID_METADATA_NAME_NAME));
        }

        GrpcKafkaCorrelationConfig newCorrelation = CORRELATION_DEFAULT;
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

                newCorrelation = new GrpcKafkaCorrelationConfig(newCorrelationId, newService, newMethod);
            }
        }

        return new GrpcKafkaOptionsConfig(newLastMessageId, newLastMessageIdMetadataName, newCorrelation);
    }
}
