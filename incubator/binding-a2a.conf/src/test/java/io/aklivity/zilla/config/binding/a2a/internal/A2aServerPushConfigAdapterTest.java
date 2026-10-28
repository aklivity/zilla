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
package io.aklivity.zilla.config.binding.a2a.internal;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.config.binding.a2a.A2aServerPushConfig;
import io.aklivity.zilla.runtime.common.yaml.json.YamlJson;

public class A2aServerPushConfigAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        jsonb = JsonbBuilder.newBuilder()
            .withProvider(YamlJson.provider())
            .withConfig(new JsonbConfig().withAdapters(new A2aServerPushConfigAdapter()))
            .build();
    }

    @Test
    public void shouldReadPush()
    {
        String text =
            """
            url: https://gateway.example.com/agents/push
            path: /agents/push
            """;

        A2aServerPushConfig push = jsonb.fromJson(text, A2aServerPushConfig.class);

        assertThat(push.url, equalTo("https://gateway.example.com/agents/push"));
        assertThat(push.path, equalTo("/agents/push"));
    }

    @Test
    public void shouldReadEmptyPush()
    {
        A2aServerPushConfig push = jsonb.fromJson("{}", A2aServerPushConfig.class);

        assertThat(push.url, nullValue());
        assertThat(push.path, nullValue());
    }

    @Test
    public void shouldWritePush()
    {
        A2aServerPushConfig push = A2aServerPushConfig.builder()
            .url("https://gateway.example.com/agents/push")
            .path("/agents/push")
            .build();

        assertThat(jsonb.toJson(push), equalTo(
            """
            url: "https://gateway.example.com/agents/push"
            path: /agents/push
            """));
    }

    @Test
    public void shouldWriteEmptyPush()
    {
        assertThat(jsonb.toJson(A2aServerPushConfig.builder().build()), equalTo("{}\n"));
    }
}
