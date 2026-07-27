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
package io.aklivity.zilla.config.engine;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.config.engine.test.internal.binding.TestBindingInfo;
import io.aklivity.zilla.config.engine.test.internal.binding.config.TestBindingOptionsConfig;
import io.aklivity.zilla.runtime.common.yaml.json.YamlJson;

public class OptionsConfigAdapterTest
{
    private OptionsConfigAdapter adapter;
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        adapter = new OptionsConfigAdapter(type -> TestBindingInfo.TYPE.equals(type) ? new TestBindingInfo() : null);
        adapter.adaptType("test");
        JsonbConfig config = new JsonbConfig()
                .withAdapters(adapter);
        jsonb = JsonbBuilder.newBuilder()
                .withProvider(YamlJson.provider())
                .withConfig(config)
                .build();
    }

    @Test
    public void shouldReadOptions()
    {
        String yaml =
                "mode: test";

        TestBindingOptionsConfig options = (TestBindingOptionsConfig) jsonb.fromJson(yaml, OptionsConfig.class);

        assertThat(options, not(nullValue()));
        assertThat(options.mode, equalTo("test"));
    }

    @Test
    public void shouldWriteOptions()
    {
        OptionsConfig options = TestBindingOptionsConfig.builder()
            .mode("test")
            .build();

        String yaml = jsonb.toJson(options);

        assertThat(yaml, not(nullValue()));
        assertThat(yaml, equalTo("mode: test\n"));
    }

    @Test
    public void shouldReadNullWhenNotAdapting()
    {
        String yaml =
                "mode: test";

        adapter.adaptType(null);
        TestBindingOptionsConfig options = (TestBindingOptionsConfig) jsonb.fromJson(yaml, OptionsConfig.class);

        assertThat(options, nullValue());
    }

    @Test
    public void shouldWriteNullWhenNotAdapting()
    {
        OptionsConfig options = TestBindingOptionsConfig.builder()
                .mode("test")
                .build();

        adapter.adaptType(null);
        String yaml = jsonb.toJson(options);

        assertThat(yaml, not(nullValue()));
        assertThat(yaml, equalTo("null\n"));
    }
}
