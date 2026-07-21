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
import static org.hamcrest.Matchers.empty;

import java.util.List;

import org.junit.Test;

public class McpTextTokenizerTest
{
    @Test
    public void shouldTokenizeCamelCase()
    {
        List<String> tokens = McpTextTokenizer.tokenize("createKafkaTopic");

        assertThat(tokens, contains("create", "kafka", "topic"));
    }

    @Test
    public void shouldTokenizeAcronymBoundary()
    {
        List<String> tokens = McpTextTokenizer.tokenize("parseXMLDocument");

        assertThat(tokens, contains("parse", "xml", "document"));
    }

    @Test
    public void shouldTokenizeSnakeCase()
    {
        List<String> tokens = McpTextTokenizer.tokenize("get_weather_data");

        assertThat(tokens, contains("get", "weather", "data"));
    }

    @Test
    public void shouldTokenizeDotNamespace()
    {
        List<String> tokens = McpTextTokenizer.tokenize("github.createPullRequest");

        assertThat(tokens, contains("github", "create", "pull", "request"));
    }

    @Test
    public void shouldLowercaseFullyUppercaseWord()
    {
        List<String> tokens = McpTextTokenizer.tokenize("KAFKA");

        assertThat(tokens, contains("kafka"));
    }

    @Test
    public void shouldRemoveStopwords()
    {
        List<String> tokens = McpTextTokenizer.tokenize("the quick fox");

        assertThat(tokens, contains("quick", "fox"));
    }

    @Test
    public void shouldSplitOnPunctuation()
    {
        List<String> tokens = McpTextTokenizer.tokenize("get-weather.data");

        assertThat(tokens, contains("get", "weather", "data"));
    }

    @Test
    public void shouldReturnEmptyForNull()
    {
        List<String> tokens = McpTextTokenizer.tokenize(null);

        assertThat(tokens, empty());
    }

    @Test
    public void shouldReturnEmptyForEmptyString()
    {
        List<String> tokens = McpTextTokenizer.tokenize("");

        assertThat(tokens, empty());
    }
}
