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

import static java.util.Collections.singletonMap;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertTrue;

import java.util.Base64;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.config.GrpcKafkaConditionConfig;
import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.config.GrpcKafkaConditionConfigAdapter;
import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.config.GrpcKafkaMetadataValue;
import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.types.String16FW;
import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.types.String8FW;

public class GrpcKafkaConditionConfigAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        JsonbConfig config = new JsonbConfig()
                .withAdapters(new GrpcKafkaConditionConfigAdapter());
        jsonb = JsonbBuilder.create(config);
    }

    @Test
    public void shouldReadCondition()
    {
        String text =
                "{\n" +
                "    \"method\": \"example.EchoService/EchoUnary\",\n" +
                "    \"metadata\": {\n" +
                "        \"custom\": \"test\"\n" +
                "    }\n" +
                "}";


        GrpcKafkaConditionConfig condition = jsonb.fromJson(text, GrpcKafkaConditionConfig.class);

        assertThat(condition, not(nullValue()));
        assertThat(condition.service, equalTo("example.EchoService"));
        assertThat(condition.method, equalTo("EchoUnary"));
        assertTrue(!condition.metadata.isEmpty());
    }

    @Test
    public void shouldWriteCondition()
    {
        GrpcKafkaConditionConfig condition = new GrpcKafkaConditionConfig("test", "*",
            singletonMap(new String8FW("custom"), new GrpcKafkaMetadataValue(new String16FW("test"),
                new String16FW(Base64.getUrlEncoder().encodeToString("test".getBytes())))));

        String text = jsonb.toJson(condition);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"method\":\"test/*\",\"metadata\":{\"custom\":\"test\"}}"));
    }
}
