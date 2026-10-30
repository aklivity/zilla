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

import io.aklivity.zilla.config.binding.agui.AguiWithConfig;
import io.aklivity.zilla.config.engine.WithConfig;
import io.aklivity.zilla.runtime.common.yaml.json.YamlJson;

public class AguiWithConfigAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        JsonbConfig config = new JsonbConfig()
                .withAdapters(new AguiWithConfigAdapter());
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
                agent: "agent-0"
                path: "/routed"
                """;

        AguiWithConfig config = (AguiWithConfig) jsonb.fromJson(text, WithConfig.class);

        assertThat(config, not(nullValue()));
        assertThat(config.agent, equalTo("agent-0"));
        assertThat(config.path, equalTo("/routed"));
    }

    @Test
    public void shouldReadWithoutMembers()
    {
        AguiWithConfig config = (AguiWithConfig) jsonb.fromJson("{}", WithConfig.class);

        assertThat(config.agent, nullValue());
        assertThat(config.path, nullValue());
    }

    @Test
    public void shouldWriteOnlyNonNullMembers()
    {
        AguiWithConfig config = AguiWithConfig.builder()
            .agent("agent-0")
            .build();

        String text = jsonb.toJson(config, WithConfig.class);

        assertThat(text, equalTo("agent: agent-0\n"));
    }

    @Test
    public void shouldWriteBothMembers()
    {
        AguiWithConfig config = AguiWithConfig.builder()
            .agent("agent-0")
            .path("/routed")
            .build();

        String text = jsonb.toJson(config, WithConfig.class);

        assertThat(text, equalTo("agent: agent-0\npath: /routed\n"));
    }
}
