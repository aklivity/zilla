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
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;

import java.util.List;

import org.junit.Test;

import io.aklivity.zilla.runtime.binding.mcp.search.McpToolSearchDocument;

public class McpToolSearchDocumentScannerTest
{
    @Test
    public void shouldScanNameAndDescription()
    {
        String json =
                """
                {"tools":[
                    {"name":"get_weather","description":"Get current weather for a location",
                     "inputSchema":{"type":"object"}}
                ]}
                """;

        List<McpToolSearchDocument> documents = McpToolSearchDocumentScanner.scan(json, List.of("name", "description"));

        assertThat(documents, hasSize(1));
        assertThat(documents.get(0).name, equalTo("get_weather"));
        assertThat(documents.get(0).field("name"), equalTo("get_weather"));
        assertThat(documents.get(0).field("description"), equalTo("Get current weather for a location"));
    }

    @Test
    public void shouldFlattenOutputSchemaObjectToText()
    {
        String json =
                """
                {"tools":[
                    {"name":"list_repos","description":"List repositories",
                     "outputSchema":{"type":"object","properties":{"repos":{"type":"array",
                     "description":"matching repositories"}}}}
                ]}
                """;

        List<McpToolSearchDocument> documents =
            McpToolSearchDocumentScanner.scan(json, List.of("name", "output-schema"));

        assertThat(documents, hasSize(1));
        String flattened = documents.get(0).field("output-schema");
        assertThat(flattened, containsString("repos"));
        assertThat(flattened, containsString("matching repositories"));
        // description wasn't requested, so it must not be captured even though present in the JSON
        assertThat(documents.get(0).field("description"), nullValue());
    }

    @Test
    public void shouldIgnoreUnrequestedFields()
    {
        String json =
                """
                {"tools":[
                    {"name":"get_weather","description":"Get current weather for a location"}
                ]}
                """;

        List<McpToolSearchDocument> documents = McpToolSearchDocumentScanner.scan(json, List.of("name"));

        assertThat(documents.get(0).field("name"), equalTo("get_weather"));
        assertThat(documents.get(0).field("description"), nullValue());
    }

    @Test
    public void shouldScanMultipleTools()
    {
        String json =
                """
                {"tools":[
                    {"name":"tool_one","description":"first tool"},
                    {"name":"tool_two","description":"second tool"}
                ]}
                """;

        List<McpToolSearchDocument> documents = McpToolSearchDocumentScanner.scan(json, List.of("name", "description"));

        assertThat(documents, hasSize(2));
        assertThat(documents.get(0).name, equalTo("tool_one"));
        assertThat(documents.get(1).name, equalTo("tool_two"));
    }

    @Test
    public void shouldReturnEmptyForNoToolsArray()
    {
        String json = "{\"resources\":[]}";

        List<McpToolSearchDocument> documents = McpToolSearchDocumentScanner.scan(json, List.of("name", "description"));

        assertThat(documents, empty());
    }

    @Test
    public void shouldReturnEmptyForMalformedJson()
    {
        List<McpToolSearchDocument> documents =
            McpToolSearchDocumentScanner.scan("not valid json at all", List.of("name", "description"));

        assertThat(documents, empty());
    }

    @Test
    public void shouldReturnEmptyForNoFields()
    {
        String json = "{\"tools\":[{\"name\":\"get_weather\"}]}";

        List<McpToolSearchDocument> documents = McpToolSearchDocumentScanner.scan(json, List.of());

        assertThat(documents, empty());
    }
}
