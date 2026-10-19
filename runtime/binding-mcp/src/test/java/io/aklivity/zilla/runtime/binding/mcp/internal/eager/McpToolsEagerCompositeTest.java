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
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.sameInstance;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.UnaryOperator;

import org.junit.Test;

import io.aklivity.zilla.runtime.binding.mcp.eager.McpToolEagerDocument;
import io.aklivity.zilla.runtime.binding.mcp.eager.McpToolsEager;
import io.aklivity.zilla.runtime.binding.mcp.eager.McpToolsEager.CompletionCallback;
import io.aklivity.zilla.runtime.binding.mcp.eager.McpToolsEagerRecorder;

public class McpToolsEagerCompositeTest
{
    @Test
    public void shouldThreadCandidateListThroughEachStageInOrder()
    {
        McpToolsEager reverse = selecting(McpToolsEagerCompositeTest::reversed);
        McpToolsEager firstOnly = selecting(names -> names.isEmpty() ? names : List.of(names.get(0)));
        McpToolsEagerComposite composite = new McpToolsEagerComposite(List.of(reverse, firstOnly));

        List<CharSequence> selected = composite.select(0L, List.of("alpha", "beta", "gamma"));

        assertThat(selected, contains("gamma"));
    }

    @Test
    public void shouldSkipRemainingStagesOnceAStageReturnsEmpty()
    {
        boolean[] secondStageCalled = { false };
        McpToolsEager empty = selecting(names -> List.of());
        McpToolsEager tracking = selecting(names ->
        {
            secondStageCalled[0] = true;
            return names;
        });
        McpToolsEagerComposite composite = new McpToolsEagerComposite(List.of(empty, tracking));

        List<CharSequence> selected = composite.select(0L, List.of("alpha"));

        assertThat(selected, empty());
        assertThat(secondStageCalled[0], equalTo(false));
    }

    @Test
    public void shouldDelegateSelectDirectlyForSingleStage()
    {
        McpToolsEager only = selecting(names -> names);
        McpToolsEagerComposite composite = new McpToolsEagerComposite(List.of(only));

        List<CharSequence> selected = composite.select(0L, List.of("alpha"));

        assertThat(selected, contains("alpha"));
    }

    @Test
    public void shouldFanOutIndexToEveryStageAndJoin()
    {
        List<McpToolEagerDocument> documentsSeenByFirst = new ArrayList<>();
        List<McpToolEagerDocument> documentsSeenBySecond = new ArrayList<>();
        McpToolsEager first = indexing(documentsSeenByFirst);
        McpToolsEager second = indexing(documentsSeenBySecond);
        McpToolsEagerComposite composite = new McpToolsEagerComposite(List.of(first, second));

        McpToolEagerDocument document = new McpToolEagerDocument("tool0", new byte[0], 0, 0);
        boolean[] done = { false };
        composite.index(List.of(document), new CompletionCallback<Void>()
        {
            @Override
            public void completed(
                Void result)
            {
                done[0] = true;
            }

            @Override
            public void failed(
                Throwable ex)
            {
                throw new AssertionError(ex);
            }
        });

        assertThat(done[0], equalTo(true));
        assertThat(documentsSeenByFirst, contains(document));
        assertThat(documentsSeenBySecond, contains(document));
    }

    @Test
    public void shouldForwardFirstIndexFailure()
    {
        McpToolsEager failing = new McpToolsEager()
        {
            @Override
            public void index(
                Collection<McpToolEagerDocument> documents,
                CompletionCallback<Void> completed)
            {
                completed.failed(new RuntimeException("boom"));
            }

            @Override
            public List<CharSequence> select(
                long authorization,
                List<CharSequence> names)
            {
                return names;
            }

            @Override
            public McpToolsEagerRecorder recorder()
            {
                return McpToolsEagerRecorder.NONE;
            }
        };
        McpToolsEager never = selecting(names -> names);
        McpToolsEagerComposite composite = new McpToolsEagerComposite(List.of(failing, never));

        Throwable[] failure = { null };
        composite.index(List.of(), new CompletionCallback<Void>()
        {
            @Override
            public void completed(
                Void result)
            {
                throw new AssertionError("expected failure");
            }

            @Override
            public void failed(
                Throwable ex)
            {
                failure[0] = ex;
            }
        });

        assertThat(failure[0], notNullValue());
    }

    @Test
    public void shouldFoldRecordersInConfigOrder()
    {
        List<CharSequence> recorded = new ArrayList<>();
        McpToolsEager first = recording((authorization, tool) -> recorded.add("first:" + tool));
        McpToolsEager second = recording((authorization, tool) -> recorded.add("second:" + tool));
        McpToolsEagerComposite composite = new McpToolsEagerComposite(List.of(first, second));

        composite.recorder().record(0L, "tool0");

        assertThat(recorded, contains("first:tool0", "second:tool0"));
    }

    @Test
    public void shouldCollapseRecorderToNoneWhenNoStageOverridesIt()
    {
        McpToolsEager first = selecting(names -> names);
        McpToolsEager second = selecting(names -> names);
        McpToolsEagerComposite composite = new McpToolsEagerComposite(List.of(first, second));

        assertThat(composite.recorder(), sameInstance(McpToolsEagerRecorder.NONE));
    }

    private static List<CharSequence> reversed(
        List<CharSequence> names)
    {
        List<CharSequence> result = new ArrayList<>(names);
        Collections.reverse(result);
        return result;
    }

    private static McpToolsEager selecting(
        UnaryOperator<List<CharSequence>> select)
    {
        return new McpToolsEager()
        {
            @Override
            public void index(
                Collection<McpToolEagerDocument> documents,
                CompletionCallback<Void> completed)
            {
                completed.completed(null);
            }

            @Override
            public List<CharSequence> select(
                long authorization,
                List<CharSequence> names)
            {
                return select.apply(names);
            }

            @Override
            public McpToolsEagerRecorder recorder()
            {
                return McpToolsEagerRecorder.NONE;
            }
        };
    }

    private static McpToolsEager indexing(
        List<McpToolEagerDocument> observed)
    {
        return new McpToolsEager()
        {
            @Override
            public void index(
                Collection<McpToolEagerDocument> documents,
                CompletionCallback<Void> completed)
            {
                observed.addAll(documents);
                completed.completed(null);
            }

            @Override
            public List<CharSequence> select(
                long authorization,
                List<CharSequence> names)
            {
                return names;
            }

            @Override
            public McpToolsEagerRecorder recorder()
            {
                return McpToolsEagerRecorder.NONE;
            }
        };
    }

    private static McpToolsEager recording(
        McpToolsEagerRecorder recorder)
    {
        return new McpToolsEager()
        {
            @Override
            public void index(
                Collection<McpToolEagerDocument> documents,
                CompletionCallback<Void> completed)
            {
                completed.completed(null);
            }

            @Override
            public List<CharSequence> select(
                long authorization,
                List<CharSequence> names)
            {
                return names;
            }

            @Override
            public McpToolsEagerRecorder recorder()
            {
                return recorder;
            }
        };
    }
}
