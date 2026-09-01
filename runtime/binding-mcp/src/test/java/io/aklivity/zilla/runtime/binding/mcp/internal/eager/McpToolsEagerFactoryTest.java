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
package io.aklivity.zilla.runtime.binding.mcp.internal.eager;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import java.util.List;

import org.junit.Test;

import io.aklivity.zilla.config.binding.mcp.McpAllToolsEagerConfig;
import io.aklivity.zilla.config.binding.mcp.McpExplicitToolsEagerConfig;
import io.aklivity.zilla.config.binding.mcp.McpNoneToolsEagerConfig;
import io.aklivity.zilla.config.binding.mcp.McpToolsEagerConfig;
import io.aklivity.zilla.runtime.binding.mcp.eager.McpToolsEager;
import io.aklivity.zilla.runtime.engine.EngineContext;

public class McpToolsEagerFactoryTest
{
    // dispatches inline -- fine for a unit test asserting selection behavior, not timing;
    // McpNoneToolsEagerTest.shouldNeverCompleteIndexOnTheCallingStack covers the
    // "never on the caller's stack" contract itself
    private final EngineContext context = mock(EngineContext.class);
    private final McpToolsEagerFactory factory = new McpToolsEagerFactory();

    public McpToolsEagerFactoryTest()
    {
        doAnswer(invocation ->
        {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(context).dispatch(any());
    }

    @Test
    public void shouldReturnNullWhenEagerNotConfigured()
    {
        assertThat(factory.create(context, null), nullValue());
    }

    @Test
    public void shouldReturnNullWhenEagerListEmpty()
    {
        assertThat(factory.create(context, List.of()), nullValue());
    }

    @Test
    public void shouldReturnSingleInstanceWhenOneConfigured()
    {
        List<McpToolsEagerConfig> eager = List.of(McpNoneToolsEagerConfig.builder().build());

        McpToolsEager instance = factory.create(context, eager);

        assertThat(instance, instanceOf(McpNoneToolsEager.class));
    }

    @Test
    public void shouldReturnCompositeWhenMultipleConfigured()
    {
        List<McpToolsEagerConfig> eager = List.of(
            McpExplicitToolsEagerConfig.builder().match(List.of("github__*")).build(),
            McpAllToolsEagerConfig.builder().build());

        McpToolsEager instance = factory.create(context, eager);

        assertThat(instance, instanceOf(McpToolsEagerComposite.class));

        List<CharSequence> selected = instance.select(0L, List.of("github__list_repos", "kafka__list_topics"));

        assertThat(selected, not(contains("kafka__list_topics")));
    }

    @Test
    public void shouldSkipUnregisteredType()
    {
        McpToolsEagerConfig unregistered = new McpToolsEagerConfig("unregistered")
        {
        };

        assertThat(factory.create(context, List.of(unregistered)), nullValue());
    }
}
