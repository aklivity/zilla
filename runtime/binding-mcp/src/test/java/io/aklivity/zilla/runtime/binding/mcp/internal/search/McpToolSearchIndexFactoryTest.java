/*
 * Copyright 2021-2024 Aklivity Inc
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import io.aklivity.zilla.runtime.binding.mcp.config.McpCacheToolsSearchConfig;
import io.aklivity.zilla.runtime.binding.mcp.config.McpKeywordToolSearchIndexConfig;
import io.aklivity.zilla.runtime.binding.mcp.search.McpToolSearchDocument;
import io.aklivity.zilla.runtime.binding.mcp.search.McpToolSearchIndex;
import io.aklivity.zilla.runtime.binding.mcp.search.McpToolSearchMatch;

public class McpToolSearchIndexFactoryTest
{
    private final McpToolSearchIndexFactory factory = new McpToolSearchIndexFactory();

    @Test
    public void shouldReturnNullWhenSearchNotConfigured()
    {
        assertThat(factory.create(null), nullValue());
    }

    @Test
    public void shouldReturnNullWhenNoIndexesConfigured()
    {
        McpCacheToolsSearchConfig search = McpCacheToolsSearchConfig.builder()
            .tool("zilla__search_tools")
            .fields(List.of("name", "description"))
            .build();

        assertThat(factory.create(search), nullValue());
    }

    @Test
    public void shouldCreateWorkingIndexForSingleConfiguredType()
    {
        McpCacheToolsSearchConfig search = McpCacheToolsSearchConfig.builder()
            .tool("zilla__search_tools")
            .fields(List.of("name", "description"))
            .index(new McpKeywordToolSearchIndexConfig())
            .build();

        McpToolSearchIndex index = factory.create(search);

        assertThat(index, not(nullValue()));

        index.index(List.of(document("kafka_tool", "kafka topic manager", "manages kafka topics")));
        List<McpToolSearchMatch> matches = index.query("kafka");

        assertThat(matches, hasSize(1));
        assertThat(matches.get(0).name, equalTo("kafka_tool"));
    }

    @Test
    public void shouldComposeMultipleConfiguredIndexes()
    {
        McpCacheToolsSearchConfig search = McpCacheToolsSearchConfig.builder()
            .tool("zilla__search_tools")
            .fields(List.of("name", "description"))
            .index(new McpKeywordToolSearchIndexConfig())
            .index(new McpKeywordToolSearchIndexConfig())
            .build();

        McpToolSearchIndex index = factory.create(search);

        index.index(List.of(
            document("kafka_tool", "kafka topic manager", "manages kafka topics"),
            document("other_tool", "generic manager", "does something unrelated")));
        List<McpToolSearchMatch> matches = index.query("kafka");

        assertThat(matches, hasSize(1));
        assertThat(matches.get(0).name, equalTo("kafka_tool"));
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
