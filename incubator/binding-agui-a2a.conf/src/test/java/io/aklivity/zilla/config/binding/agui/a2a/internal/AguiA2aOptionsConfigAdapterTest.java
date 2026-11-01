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
package io.aklivity.zilla.config.binding.agui.a2a.internal;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.config.binding.agui.a2a.AguiA2aOptionsConfig;
import io.aklivity.zilla.config.engine.OptionsConfig;
import io.aklivity.zilla.runtime.common.yaml.json.YamlJson;

public class AguiA2aOptionsConfigAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        jsonb = JsonbBuilder.newBuilder()
            .withProvider(YamlJson.provider())
            .withConfig(new JsonbConfig().withAdapters(new AguiA2aOptionsConfigAdapter()))
            .build();
    }

    @Test
    public void shouldReadOptions()
    {
        OptionsConfig options = jsonb.fromJson("store: memory0", OptionsConfig.class);

        assertThat(((AguiA2aOptionsConfig) options).store, equalTo("memory0"));
    }

    @Test
    public void shouldWriteOptions()
    {
        OptionsConfig options = AguiA2aOptionsConfig.builder()
            .store("memory0")
            .build();

        assertThat(jsonb.toJson(options), equalTo("store: memory0\n"));
    }
}
