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

import io.aklivity.zilla.config.binding.mcp.McpCacheToolsSearchConfig;
import io.aklivity.zilla.config.binding.mcp.McpKeywordToolSearchIndexConfig;
import io.aklivity.zilla.runtime.binding.mcp.search.McpToolSearchDocument;
import io.aklivity.zilla.runtime.binding.mcp.search.McpToolSearchIndex;
import io.aklivity.zilla.runtime.binding.mcp.search.McpToolSearchMatch;
import io.aklivity.zilla.runtime.engine.EngineContext;

public class McpToolSearchIndexFactoryTest
{
    // dispatches inline -- fine for a unit test asserting ranking behavior, not timing;
    // McpKeywordToolSearchIndexTest.shouldNeverCompleteOnTheCallingStack covers the
    // "never on the caller's stack" contract itself
    private final EngineContext context = mock(EngineContext.class);
    private final McpToolSearchIndexFactory factory = new McpToolSearchIndexFactory();

    public McpToolSearchIndexFactoryTest()
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
        McpCacheToolsSearchConfig search = McpCacheToolsSearchConfig.builder()
            .toolkit("zilla")
            .fields(List.of("name", "description"))
            .build();

        assertThat(factory.create(context, search), nullValue());
    }

    @Test
    public void shouldCreateWorkingIndexForSingleConfiguredType()
    {
        McpCacheToolsSearchConfig search = McpCacheToolsSearchConfig.builder()
            .toolkit("zilla")
            .fields(List.of("name", "description"))
            .index(McpKeywordToolSearchIndexConfig.builder().build())
            .build();

        McpToolSearchIndex index = factory.create(context, search);

        assertThat(index, not(nullValue()));

        rebuild(index, List.of(document("kafka_tool", "kafka topic manager", "manages kafka topics")));
        List<McpToolSearchMatch> matches = search(index, "kafka");

        assertThat(matches, hasSize(1));
        assertThat(matches.get(0).name, equalTo("kafka_tool"));
    }

    @Test
    public void shouldComposeMultipleConfiguredIndexes()
    {
        McpCacheToolsSearchConfig search = McpCacheToolsSearchConfig.builder()
            .toolkit("zilla")
            .fields(List.of("name", "description"))
            .index(McpKeywordToolSearchIndexConfig.builder().build())
            .index(McpKeywordToolSearchIndexConfig.builder().build())
            .build();

        McpToolSearchIndex index = factory.create(context, search);

        rebuild(index, List.of(
            document("kafka_tool", "kafka topic manager", "manages kafka topics"),
            document("other_tool", "generic manager", "does something unrelated")));
        List<McpToolSearchMatch> matches = search(index, "kafka");

        assertThat(matches, hasSize(1));
        assertThat(matches.get(0).name, equalTo("kafka_tool"));
    }

    private static void rebuild(
        McpToolSearchIndex index,
        List<McpToolSearchDocument> documents)
    {
        boolean[] done = { false };
        index.index(documents, new McpToolSearchIndex.CompletionCallback<Void>()
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
    private static List<McpToolSearchMatch> search(
        McpToolSearchIndex index,
        String text)
    {
        Object[] result = new Object[1];
        index.query(text, new McpToolSearchIndex.CompletionCallback<List<McpToolSearchMatch>>()
        {
            @Override
            public void completed(
                List<McpToolSearchMatch> matches)
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
        return (List<McpToolSearchMatch>) result[0];
    }

    private static McpToolSearchDocument document(
        String name,
        String nameField,
        String descriptionField)
    {
        Map<String, String> fields = new HashMap<>();
        fields.put("name", nameField);
        fields.put("description", descriptionField);
        return new McpToolSearchDocument(name, fields);
    }
}
