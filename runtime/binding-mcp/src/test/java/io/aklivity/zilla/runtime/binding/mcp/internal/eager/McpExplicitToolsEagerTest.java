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
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.sameInstance;

import java.util.List;

import org.junit.Test;

import io.aklivity.zilla.runtime.binding.mcp.eager.McpToolsEagerRecorder;

public class McpExplicitToolsEagerTest
{
    @Test
    public void shouldSelectOnlyMatchedNamesPreservingInputOrder()
    {
        McpExplicitToolsEager eager = new McpExplicitToolsEager(Runnable::run, List.of("github__*"));

        List<CharSequence> selected = eager.select(0L,
            List.of("kafka__list_topics", "github__list_repos", "github__create_pr"));

        assertThat(selected, contains("github__list_repos", "github__create_pr"));
    }

    @Test
    public void shouldSelectNothingWhenNoNameMatches()
    {
        McpExplicitToolsEager eager = new McpExplicitToolsEager(Runnable::run, List.of("github__*"));

        List<CharSequence> selected = eager.select(0L, List.of("kafka__list_topics"));

        assertThat(selected, empty());
    }

    @Test
    public void shouldReturnRecorderNone()
    {
        McpExplicitToolsEager eager = new McpExplicitToolsEager(Runnable::run, List.of("github__*"));

        assertThat(eager.recorder(), sameInstance(McpToolsEagerRecorder.NONE));
    }
}
