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
package io.aklivity.zilla.runtime.binding.mcp.internal.search;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.sameInstance;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.Test;

import io.aklivity.zilla.runtime.binding.mcp.search.McpToolSearchDocument;
import io.aklivity.zilla.runtime.binding.mcp.search.McpToolSearchIndex;
import io.aklivity.zilla.runtime.binding.mcp.search.McpToolSearchMatch;

public class McpToolSearchCompositeTest
{
    @Test
    public void shouldWaitForAllBackendsBeforeCompletingIndex()
    {
        FakeIndex first = new FakeIndex();
        FakeIndex second = new FakeIndex();
        McpToolSearchComposite composite = new McpToolSearchComposite(List.of(first, second));
        int[] completions = { 0 };

        composite.index(List.of(), new McpToolSearchIndex.CompletionCallback<Void>()
        {
            @Override
            public void completed(
                Void result)
            {
                completions[0]++;
            }

            @Override
            public void failed(
                Throwable ex)
            {
                throw new AssertionError(ex);
            }
        });

        assertThat(completions[0], equalTo(0));

        first.completeIndex();
        assertThat(completions[0], equalTo(0));

        second.completeIndex();
        assertThat(completions[0], equalTo(1));
    }

    @Test
    public void shouldFuseResultsFromAllBackendsRegardlessOfCompletionOrder()
    {
        FakeIndex first = new FakeIndex();
        FakeIndex second = new FakeIndex();
        McpToolSearchComposite composite = new McpToolSearchComposite(List.of(first, second));
        List<McpToolSearchMatch>[] fused = new List[1];

        composite.query("kafka", new McpToolSearchIndex.CompletionCallback<List<McpToolSearchMatch>>()
        {
            @Override
            public void completed(
                List<McpToolSearchMatch> matches)
            {
                fused[0] = matches;
            }

            @Override
            public void failed(
                Throwable ex)
            {
                throw new AssertionError(ex);
            }
        });

        // completes out of order -- second backend settles before first
        second.completeQuery(List.of(new McpToolSearchMatch("b", 9.0)));
        assertThat(fused[0], equalTo(null));

        first.completeQuery(List.of(new McpToolSearchMatch("a", 9.0)));

        assertThat(names(fused[0]), contains("a", "b"));
    }

    @Test
    public void shouldCompleteIndexWhenOneBackendFails()
    {
        FakeIndex first = new FakeIndex();
        FakeIndex second = new FakeIndex();
        McpToolSearchComposite composite = new McpToolSearchComposite(List.of(first, second));
        int[] completions = { 0 };

        composite.index(List.of(), new McpToolSearchIndex.CompletionCallback<Void>()
        {
            @Override
            public void completed(
                Void result)
            {
                completions[0]++;
            }

            @Override
            public void failed(
                Throwable ex)
            {
                throw new AssertionError(ex);
            }
        });

        first.failIndex(new RuntimeException("embedding provider unavailable"));
        second.completeIndex();

        assertThat(completions[0], equalTo(1));
    }

    @Test
    public void shouldFailIndexOnlyWhenEveryBackendFails()
    {
        FakeIndex first = new FakeIndex();
        FakeIndex second = new FakeIndex();
        McpToolSearchComposite composite = new McpToolSearchComposite(List.of(first, second));
        RuntimeException firstFailure = new RuntimeException("embedding provider unavailable");
        RuntimeException secondFailure = new RuntimeException("keyword index not ready");
        int[] failures = { 0 };

        composite.index(List.of(), new McpToolSearchIndex.CompletionCallback<Void>()
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
                assertThat(ex, sameInstance(secondFailure));
                failures[0]++;
            }
        });

        first.failIndex(firstFailure);
        second.failIndex(secondFailure);

        assertThat(failures[0], equalTo(1));
    }

    @Test
    public void shouldFuseSuccessfulResultsWhenOneBackendFails()
    {
        FakeIndex first = new FakeIndex();
        FakeIndex second = new FakeIndex();
        McpToolSearchComposite composite = new McpToolSearchComposite(List.of(first, second));
        RuntimeException failure = new RuntimeException("embedding provider unavailable");
        List<McpToolSearchMatch>[] fused = new List[1];

        composite.query("kafka", new McpToolSearchIndex.CompletionCallback<List<McpToolSearchMatch>>()
        {
            @Override
            public void completed(
                List<McpToolSearchMatch> matches)
            {
                fused[0] = matches;
            }

            @Override
            public void failed(
                Throwable ex)
            {
                throw new AssertionError(ex);
            }
        });

        first.failQuery(failure);
        second.completeQuery(List.of(new McpToolSearchMatch("b", 9.0)));

        assertThat(names(fused[0]), contains("b"));
    }

    @Test
    public void shouldFailOnlyWhenEveryBackendFails()
    {
        FakeIndex first = new FakeIndex();
        FakeIndex second = new FakeIndex();
        McpToolSearchComposite composite = new McpToolSearchComposite(List.of(first, second));
        RuntimeException firstFailure = new RuntimeException("embedding provider unavailable");
        RuntimeException secondFailure = new RuntimeException("keyword index not ready");
        int[] failures = { 0 };

        composite.query("kafka", new McpToolSearchIndex.CompletionCallback<List<McpToolSearchMatch>>()
        {
            @Override
            public void completed(
                List<McpToolSearchMatch> matches)
            {
                throw new AssertionError("expected failure, not " + matches);
            }

            @Override
            public void failed(
                Throwable ex)
            {
                assertThat(ex, sameInstance(secondFailure));
                failures[0]++;
            }
        });

        first.failQuery(firstFailure);
        second.failQuery(secondFailure);

        assertThat(failures[0], equalTo(1));
    }

    @Test
    public void shouldDelegateDirectlyForSingleBackend()
    {
        FakeIndex only = new FakeIndex();
        McpToolSearchComposite composite = new McpToolSearchComposite(List.of(only));
        McpToolSearchIndex.CompletionCallback<List<McpToolSearchMatch>> callback =
            new McpToolSearchIndex.CompletionCallback<List<McpToolSearchMatch>>()
            {
                @Override
                public void completed(
                    List<McpToolSearchMatch> matches)
                {
                }

                @Override
                public void failed(
                    Throwable ex)
                {
                }
            };

        composite.query("kafka", callback);

        assertThat(only.queryCallback, sameInstance(callback));
    }

    private static List<String> names(
        List<McpToolSearchMatch> matches)
    {
        return matches.stream().map(match -> match.name).collect(Collectors.toList());
    }

    private static final class FakeIndex implements McpToolSearchIndex
    {
        private CompletionCallback<Void> indexCallback;
        private CompletionCallback<List<McpToolSearchMatch>> queryCallback;

        @Override
        public void index(
            Collection<McpToolSearchDocument> documents,
            CompletionCallback<Void> completed)
        {
            this.indexCallback = completed;
        }

        @Override
        public void query(
            String text,
            CompletionCallback<List<McpToolSearchMatch>> completed)
        {
            this.queryCallback = completed;
        }

        private void completeIndex()
        {
            indexCallback.completed(null);
        }

        private void completeQuery(
            List<McpToolSearchMatch> matches)
        {
            queryCallback.completed(matches);
        }

        private void failQuery(
            Throwable ex)
        {
            queryCallback.failed(ex);
        }

        private void failIndex(
            Throwable ex)
        {
            indexCallback.failed(ex);
        }
    }
}
