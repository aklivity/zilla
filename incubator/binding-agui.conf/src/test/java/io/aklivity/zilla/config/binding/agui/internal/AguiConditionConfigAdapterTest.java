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
package io.aklivity.zilla.config.binding.agui.internal;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.config.binding.agui.AguiConditionConfig;
import io.aklivity.zilla.config.engine.ConditionConfig;
import io.aklivity.zilla.runtime.common.yaml.json.YamlJson;

public class AguiConditionConfigAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        JsonbConfig config = new JsonbConfig()
                .withAdapters(new AguiConditionConfigAdapter());
        jsonb = JsonbBuilder.newBuilder()
                .withProvider(YamlJson.provider())
                .withConfig(config)
                .build();
    }

    @Test
    public void shouldReadWithBothMembers()
    {
        String text =
                """
                path: "/agents/a"
                agent: "agent-0"
                """;

        AguiConditionConfig config = (AguiConditionConfig) jsonb.fromJson(text, ConditionConfig.class);

        assertThat(config, not(nullValue()));
        assertThat(config.path, equalTo("/agents/a"));
        assertThat(config.agent, equalTo("agent-0"));
    }

    @Test
    public void shouldReadWithoutMembers()
    {
        AguiConditionConfig config = (AguiConditionConfig) jsonb.fromJson("{}", ConditionConfig.class);

        assertThat(config.path, nullValue());
        assertThat(config.agent, nullValue());
    }

    @Test
    public void shouldWriteOnlyNonNullMembers()
    {
        AguiConditionConfig config = AguiConditionConfig.builder()
            .path("/agents/a")
            .build();

        String text = jsonb.toJson(config, ConditionConfig.class);

        assertThat(text, equalTo("path: /agents/a\n"));
    }

    @Test
    public void shouldWriteBothMembers()
    {
        AguiConditionConfig config = AguiConditionConfig.builder()
            .path("/agents/a")
            .agent("agent-0")
            .build();

        String text = jsonb.toJson(config, ConditionConfig.class);

        assertThat(text, equalTo("path: /agents/a\nagent: agent-0\n"));
    }
}
