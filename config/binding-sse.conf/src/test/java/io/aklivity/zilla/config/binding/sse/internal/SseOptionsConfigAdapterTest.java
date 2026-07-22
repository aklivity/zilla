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
package io.aklivity.zilla.config.binding.sse.internal;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.config.binding.sse.SseOptionsConfig;
import io.aklivity.zilla.config.binding.sse.SseRequestConfig;
import io.aklivity.zilla.runtime.common.yaml.json.YamlJson;
import io.aklivity.zilla.runtime.engine.test.internal.model.config.TestModelConfig;

public class SseOptionsConfigAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        JsonbConfig config = new JsonbConfig()
                .withAdapters(new SseOptionsConfigAdapter());
        jsonb = JsonbBuilder.newBuilder()
                .withProvider(YamlJson.provider())
                .withConfig(config)
                .build();
    }

    @Test
    public void shouldReadOptions()
    {
        String text =
                """
                retry: 3000
                """;

        SseOptionsConfig options = jsonb.fromJson(text, SseOptionsConfig.class);

        assertThat(options, not(nullValue()));
        assertThat(options.retry, equalTo(3000));
    }

    @Test
    public void shouldWriteOptions()
    {
        SseOptionsConfig options = SseOptionsConfig.builder().retry(3000).build();

        String text = jsonb.toJson(options);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo(
                """
                retry: 3000
                """));
    }

    @Test
    public void shouldReadOptionsWithRequests()
    {
        String text =
                """
                retry: 3000
                requests:
                  - path: /events
                    content: test
                """;

        SseOptionsConfig options = jsonb.fromJson(text, SseOptionsConfig.class);

        assertThat(options, not(nullValue()));
        assertThat(options.requests, not(nullValue()));
        assertThat(options.requests, hasSize(1));
        SseRequestConfig request = options.requests.get(0);
        assertThat(request.path, equalTo("/events"));
        assertThat(request.content, instanceOf(TestModelConfig.class));
        assertThat(request.content.model, equalTo("test"));
    }

    @Test
    public void shouldWriteOptionsWithRequests()
    {
        SseOptionsConfig options = SseOptionsConfig.builder()
            .retry(3000)
            .request()
                .path("/events")
                .content(TestModelConfig.builder()
                    .length(0)
                    .build())
                .build()
            .build();

        String text = jsonb.toJson(options);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo(
                """
                retry: 3000
                requests:
                  - path: /events
                    content: test
                """));
    }
}
