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
package io.aklivity.zilla.runtime.binding.a2a.internal.search;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import io.aklivity.zilla.config.binding.a2a.A2aDiscoverySearchConfig;
import io.aklivity.zilla.config.binding.a2a.A2aKeywordSearchIndexConfig;
import io.aklivity.zilla.runtime.binding.a2a.search.A2aAgentSearchDocument;
import io.aklivity.zilla.runtime.binding.a2a.search.A2aAgentSearchIndex;
import io.aklivity.zilla.runtime.binding.a2a.search.A2aAgentSearchMatch;
import io.aklivity.zilla.runtime.engine.EngineContext;

public class A2aAgentSearchIndexFactoryTest
{
    private final EngineContext context = mock(EngineContext.class);
    private final A2aAgentSearchIndexFactory factory = new A2aAgentSearchIndexFactory();

    public A2aAgentSearchIndexFactoryTest()
    {
        doAnswer(invocation ->
        {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(context).dispatch(any());
    }

    @Test
    public void shouldReturnNullWhenSearchNotConfigured()
    {
        assertThat(factory.create(context, null), nullValue());
    }

    @Test
    public void shouldReturnNullWhenNoIndexesConfigured()
    {
        A2aDiscoverySearchConfig search = A2aDiscoverySearchConfig.builder()
            .fields(List.of("name", "description"))
            .build();

        assertThat(factory.create(context, search), nullValue());
    }

    @Test
    public void shouldCreateWorkingIndexForSingleConfiguredType()
    {
        A2aDiscoverySearchConfig search = A2aDiscoverySearchConfig.builder()
            .fields(List.of("name", "description"))
            .index(A2aKeywordSearchIndexConfig.builder().build())
            .build();

        A2aAgentSearchIndex index = factory.create(context, search);

        assertThat(index, not(nullValue()));

        rebuild(index, List.of(document("kafka_agent", "kafka topic manager", "manages kafka topics")));
        List<A2aAgentSearchMatch> matches = search(index, "kafka");

        assertThat(matches, hasSize(1));
        assertThat(matches.get(0).agent, equalTo("kafka_agent"));
    }

    @Test
    public void shouldComposeMultipleConfiguredIndexes()
    {
        A2aDiscoverySearchConfig search = A2aDiscoverySearchConfig.builder()
            .fields(List.of("name", "description"))
            .index(A2aKeywordSearchIndexConfig.builder().build())
            .index(A2aKeywordSearchIndexConfig.builder().build())
            .build();

        A2aAgentSearchIndex index = factory.create(context, search);

        rebuild(index, List.of(
            document("kafka_agent", "kafka topic manager", "manages kafka topics"),
            document("other_agent", "generic manager", "does something unrelated")));
        List<A2aAgentSearchMatch> matches = search(index, "kafka");

        assertThat(matches, hasSize(1));
        assertThat(matches.get(0).agent, equalTo("kafka_agent"));
    }

    @Test
    public void shouldDiscoverFactoryByTypeAndUseIt()
    {
        A2aDiscoverySearchConfig search = A2aDiscoverySearchConfig.builder()
            .fields(List.of("name", "description"))
            .index(new A2aFailingSearchIndexConfig())
            .build();

        A2aAgentSearchIndex index = factory.create(context, search);

        Throwable[] failure = new Throwable[1];
        index.query("kafka", new A2aAgentSearchIndex.CompletionCallback<List<A2aAgentSearchMatch>>()
        {
            @Override
            public void completed(
                List<A2aAgentSearchMatch> matches)
            {
            }

            @Override
            public void failed(
                Throwable ex)
            {
                failure[0] = ex;
            }
        });

        assertThat(failure[0], not(nullValue()));
    }

    @Test
    public void shouldFuseRankingFromKeywordAndDiscoveredFactory()
    {
        A2aDiscoverySearchConfig search = A2aDiscoverySearchConfig.builder()
            .fields(List.of("name", "description"))
            .index(A2aKeywordSearchIndexConfig.builder().build())
            .index(new A2aFailingSearchIndexConfig())
            .build();

        A2aAgentSearchIndex index = factory.create(context, search);

        rebuild(index, List.of(
            document("kafka_agent", "kafka topic manager", "manages kafka topics"),
            document("other_agent", "generic manager", "does something unrelated")));
        List<A2aAgentSearchMatch> matches = search(index, "kafka");

        assertThat(matches, hasSize(1));
        assertThat(matches.get(0).agent, equalTo("kafka_agent"));
    }

    @Test
    public void shouldIgnoreIndexTypeWithoutFactory()
    {
        A2aDiscoverySearchConfig search = A2aDiscoverySearchConfig.builder()
            .fields(List.of("name", "description"))
            .index(new A2aUnregisteredSearchIndexConfig())
            .build();

        assertThat(factory.create(context, search), nullValue());
    }

    private static void rebuild(
        A2aAgentSearchIndex index,
        List<A2aAgentSearchDocument> documents)
    {
        boolean[] done = { false };
        index.index(documents, new A2aAgentSearchIndex.CompletionCallback<Void>()
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
    }

    @SuppressWarnings("unchecked")
    private static List<A2aAgentSearchMatch> search(
        A2aAgentSearchIndex index,
        String text)
    {
        Object[] result = new Object[1];
        index.query(text, new A2aAgentSearchIndex.CompletionCallback<List<A2aAgentSearchMatch>>()
        {
            @Override
            public void completed(
                List<A2aAgentSearchMatch> matches)
            {
                result[0] = matches;
            }

            @Override
            public void failed(
                Throwable ex)
            {
                throw new AssertionError(ex);
            }
        });
        return (List<A2aAgentSearchMatch>) result[0];
    }

    private static A2aAgentSearchDocument document(
        String name,
        String nameField,
        String descriptionField)
    {
        Map<String, String> fields = new HashMap<>();
        fields.put("name", nameField);
        fields.put("description", descriptionField);
        return new A2aAgentSearchDocument(name, fields);
    }
}
