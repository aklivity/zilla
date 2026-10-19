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
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.sameInstance;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;

import org.junit.Test;

import io.aklivity.zilla.runtime.binding.mcp.eager.McpToolsEager.CompletionCallback;
import io.aklivity.zilla.runtime.binding.mcp.eager.McpToolsEagerRecorder;

public class McpNoneToolsEagerTest
{
    @Test
    public void shouldSelectEveryNameUnchanged()
    {
        McpNoneToolsEager eager = new McpNoneToolsEager(Runnable::run);

        List<CharSequence> selected = eager.select(0L, List.of("alpha", "beta"));

        assertThat(selected, contains("alpha", "beta"));
    }

    @Test
    public void shouldReturnRecorderNone()
    {
        McpNoneToolsEager eager = new McpNoneToolsEager(Runnable::run);

        assertThat(eager.recorder(), sameInstance(McpToolsEagerRecorder.NONE));
    }

    @Test
    public void shouldNeverCompleteIndexOnTheCallingStack()
    {
        Queue<Runnable> dispatched = new ArrayDeque<>();
        McpNoneToolsEager eager = new McpNoneToolsEager(dispatched::add);
        boolean[] completed = { false };

        eager.index(List.of(), new CompletionCallback<Void>()
        {
            @Override
            public void completed(
                Void result)
            {
                completed[0] = true;
            }

            @Override
            public void failed(
                Throwable ex)
            {
                throw new AssertionError(ex);
            }
        });

        assertThat(completed[0], equalTo(false));

        dispatched.poll().run();

        assertThat(completed[0], equalTo(true));
    }
}
