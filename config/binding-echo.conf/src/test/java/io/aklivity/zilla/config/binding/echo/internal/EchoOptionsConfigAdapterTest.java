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
package io.aklivity.zilla.config.binding.echo.internal;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.config.binding.echo.EchoOptionsConfig;
import io.aklivity.zilla.config.engine.test.internal.model.config.TestModelConfig;
import io.aklivity.zilla.runtime.common.yaml.json.YamlJson;

public class EchoOptionsConfigAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        JsonbConfig config = new JsonbConfig()
            .withAdapters(new EchoOptionsConfigAdapter());
        jsonb = JsonbBuilder.newBuilder()
            .withProvider(YamlJson.provider())
            .withConfig(config)
            .build();
    }

    @Test
    public void shouldReadOptions()
    {
        String yaml = "value: test";

        EchoOptionsConfig options = jsonb.fromJson(yaml, EchoOptionsConfig.class);

        assertThat(options, not(nullValue()));
        assertThat(options.value, instanceOf(TestModelConfig.class));
        assertThat(options.value.model, equalTo("test"));
    }

    @Test
    public void shouldReadOptionsWithNullFields()
    {
        String yaml = "{}";

        EchoOptionsConfig options = jsonb.fromJson(yaml, EchoOptionsConfig.class);

        assertThat(options, not(nullValue()));
        assertThat(options.value, nullValue());
    }

    @Test
    public void shouldWriteOptions()
    {
        EchoOptionsConfig options = EchoOptionsConfig.builder()
            .value(TestModelConfig.builder().length(0).build())
            .build();

        String yaml = jsonb.toJson(options);

        assertThat(yaml, not(nullValue()));
        assertThat(yaml, equalTo("value: test\n"));
    }

    @Test
    public void shouldWriteOptionsWithNullFields()
    {
        EchoOptionsConfig options = EchoOptionsConfig.builder()
            .build();

        String yaml = jsonb.toJson(options);

        assertThat(yaml, not(nullValue()));
        assertThat(yaml, equalTo("{}\n"));
    }
}
