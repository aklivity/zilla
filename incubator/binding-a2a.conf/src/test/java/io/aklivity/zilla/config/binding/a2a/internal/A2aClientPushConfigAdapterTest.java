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
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import java.util.List;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.config.binding.a2a.A2aClientPushConfig;
import io.aklivity.zilla.runtime.common.yaml.json.YamlJson;

public class A2aClientPushConfigAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        jsonb = JsonbBuilder.newBuilder()
            .withProvider(YamlJson.provider())
            .withConfig(new JsonbConfig().withAdapters(new A2aClientPushConfigAdapter()))
            .build();
    }

    @Test
    public void shouldReadPush()
    {
        String text =
            """
            allow:
              - https://*.example.com/hooks/*
              - http://10.0.0.5/hooks/*
            maxLength: 65536
            """;

        A2aClientPushConfig push = jsonb.fromJson(text, A2aClientPushConfig.class);

        assertThat(push.allow, contains("https://*.example.com/hooks/*", "http://10.0.0.5/hooks/*"));
        assertThat(push.maxLength, equalTo(65536));
    }

    @Test
    public void shouldReadEmptyPush()
    {
        A2aClientPushConfig push = jsonb.fromJson("{}", A2aClientPushConfig.class);

        assertThat(push.allow, nullValue());
        assertThat(push.maxLength, equalTo(-1));
    }

    @Test
    public void shouldWritePush()
    {
        A2aClientPushConfig push = A2aClientPushConfig.builder()
            .allow(List.of("https://*.example.com/hooks/*"))
            .maxLength(65536)
            .build();

        assertThat(jsonb.toJson(push), equalTo(
            """
            allow:
              - "https://*.example.com/hooks/*"
            maxLength: 65536
            """));
    }
}
