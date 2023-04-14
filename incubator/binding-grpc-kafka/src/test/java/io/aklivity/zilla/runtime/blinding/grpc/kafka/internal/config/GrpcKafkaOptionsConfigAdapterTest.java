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
package io.aklivity.zilla.runtime.blinding.grpc.kafka.internal.config;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.config.GrpcKafkaCorrelationConfig;
import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.config.GrpcKafkaOptionsConfig;
import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.config.GrpcKafkaOptionsConfigAdapter;
import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.types.String16FW;

public class GrpcKafkaOptionsConfigAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        JsonbConfig config = new JsonbConfig()
                .withAdapters(new GrpcKafkaOptionsConfigAdapter());
        jsonb = JsonbBuilder.create(config);
    }

    @Test
    public void shouldReadOptions()
    {
        String text =
                "{" +
                    "\"correlation\":" +
                    "{" +
                        "\"headers\":" +
                        "{" +
                            "\"service\":\"zilla:service\"," +
                            "\"method\":\"zilla:method\"," +
                            "\"correlation-id\":\"zilla:correlation-id\"" +
                        "}" +
                    "}" +
                "}";

        GrpcKafkaOptionsConfig options = jsonb.fromJson(text, GrpcKafkaOptionsConfig.class);

        assertThat(options, not(nullValue()));
        assertThat(options.correlation, not(nullValue()));
        assertThat(options.correlation.service.asString(), equalTo("zilla:service"));
        assertThat(options.correlation.method.asString(), equalTo("zilla:method"));
        assertThat(options.correlation.correlationId.asString(), equalTo("zilla:correlation-id"));
    }

    @Test
    public void shouldWriteOptions()
    {
        GrpcKafkaOptionsConfig options = new GrpcKafkaOptionsConfig(
                new GrpcKafkaCorrelationConfig(
                    new String16FW("zilla:x-correlation-id"),
                    new String16FW("zilla:x-service"),
                    new String16FW("zilla:x-method")));

        String text = jsonb.toJson(options);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo(
                "{" +
                    "\"correlation\":" +
                    "{" +
                        "\"headers\":" +
                        "{" +
                            "\"service\":\"zilla:x-service\"," +
                            "\"method\":\"zilla:x-method\"," +
                            "\"correlation-id\":\"zilla:x-correlation-id\"" +
                        "}" +
                    "}" +
                "}"));
    }
}
