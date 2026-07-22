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
package io.aklivity.zilla.config.binding.grpc.internal;

import static java.util.Collections.singletonMap;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import java.util.Base64;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.config.binding.grpc.GrpcConditionConfig;
import io.aklivity.zilla.config.binding.grpc.GrpcMetadataValueConfig;

public class GrpcConditionConfigAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        JsonbConfig config = new JsonbConfig()
                .withAdapters(new GrpcConditionConfigAdapter());
        jsonb = JsonbBuilder.create(config);
    }

    @Test
    public void shouldReadConditionWithMethod()
    {
        String text =
                "{" +
                    "\"method\": \"example.EchoService/Echo\"" +
                "}";

        GrpcConditionConfig condition = jsonb.fromJson(text, GrpcConditionConfig.class);

        assertThat(condition, not(nullValue()));
        assertThat(condition.method, equalTo("example.EchoService/Echo"));
    }

    @Test
    public void shouldWriteConditionWithMethod()
    {
        GrpcConditionConfig condition = new GrpcConditionConfig("example.EchoService/Echo", null);

        String text = jsonb.toJson(condition);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"method\":\"example.EchoService/Echo\"}"));
    }

    @Test
    public void shouldReadConditionWithTextMetadata()
    {
        String text =
                "{" +
                    "\"metadata\":" +
                    "{" +
                        "\"x-test\": \"value\"" +
                    "}" +
                "}";

        GrpcConditionConfig condition = jsonb.fromJson(text, GrpcConditionConfig.class);

        assertThat(condition, not(nullValue()));
        assertThat(condition.metadata.get("x-test").textValue, equalTo("value"));
        assertThat(condition.metadata.get("x-test").base64Value,
            equalTo(Base64.getUrlEncoder().encodeToString("value".getBytes())));
    }

    @Test
    public void shouldReadConditionWithBase64Metadata()
    {
        String base64Value = Base64.getUrlEncoder().encodeToString("value".getBytes());
        String text =
                "{" +
                    "\"metadata\":" +
                    "{" +
                        "\"x-test\": { \"base64\": \"" + base64Value + "\" }" +
                    "}" +
                "}";

        GrpcConditionConfig condition = jsonb.fromJson(text, GrpcConditionConfig.class);

        assertThat(condition, not(nullValue()));
        assertThat(condition.metadata.get("x-test").textValue, nullValue());
        assertThat(condition.metadata.get("x-test").base64Value, equalTo(base64Value));
    }

    @Test
    public void shouldWriteConditionWithMetadata()
    {
        String base64Value = Base64.getUrlEncoder().encodeToString("value".getBytes());
        GrpcConditionConfig condition = new GrpcConditionConfig(null,
            singletonMap("x-test", new GrpcMetadataValueConfig("value", base64Value)));

        String text = jsonb.toJson(condition);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"metadata\":{\"x-test\":\"value\"}}"));
    }
}
