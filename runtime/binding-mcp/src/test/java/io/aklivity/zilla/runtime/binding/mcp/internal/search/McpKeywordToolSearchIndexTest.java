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
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import io.aklivity.zilla.runtime.binding.mcp.search.McpToolSearchDocument;
import io.aklivity.zilla.runtime.binding.mcp.search.McpToolSearchMatch;

public class McpKeywordToolSearchIndexTest
{
    private static final List<String> FIELDS = List.of("name", "description");

    @Test
    public void shouldReturnEmptyForNoDocuments()
    {
        McpKeywordToolSearchIndex index = new McpKeywordToolSearchIndex(FIELDS, Map.of());

        index.index(List.of());

        assertThat(index.query("kafka"), empty());
    }

    @Test
    public void shouldReturnEmptyWhenNoMatch()
    {
        McpKeywordToolSearchIndex index = new McpKeywordToolSearchIndex(FIELDS, Map.of());

        index.index(List.of(document("github_create_pr", "create pr", "opens a pull request")));

        assertThat(index.query("kafka"), empty());
    }

    @Test
    public void shouldMatchExactTermInName()
    {
        McpKeywordToolSearchIndex index = new McpKeywordToolSearchIndex(FIELDS, Map.of());

        index.index(List.of(
            document("github_create_pr", "github create pr", "opens a pull request"),
            document("kafka_list_topics", "kafka list topics", "lists kafka topics")));

        List<McpToolSearchMatch> matches = index.query("kafka");

        assertThat(matches, not(empty()));
        assertThat(matches.get(0).name, equalTo("kafka_list_topics"));
    }

    @Test
    public void shouldRankHigherWeightFieldHigher()
    {
        Map<String, Double> weights = Map.of("name", 3.0, "description", 1.0);
        McpKeywordToolSearchIndex index = new McpKeywordToolSearchIndex(FIELDS, weights);

        index.index(List.of(
            document("kafka_tool", "kafka topic manager", "manages message queues"),
            document("other_tool", "generic manager", "manages kafka message queues")));

        List<McpToolSearchMatch> matches = index.query("kafka");

        assertThat(matches, hasSize(2));
        assertThat(matches.get(0).name, equalTo("kafka_tool"));
        assertThat(matches.get(0).score, greaterThan(matches.get(1).score));
    }

    @Test
    public void shouldFindTermInsideCamelCaseCompoundToken()
    {
        McpKeywordToolSearchIndex index = new McpKeywordToolSearchIndex(FIELDS, Map.of());

        index.index(List.of(document("createKafkaTopic", "createKafkaTopic", null)));

        List<McpToolSearchMatch> matches = index.query("kafka");

        assertThat(matches, hasSize(1));
        assertThat(matches.get(0).name, equalTo("createKafkaTopic"));
    }

    @Test
    public void shouldReturnResultsSortedByScoreDescending()
    {
        McpKeywordToolSearchIndex index = new McpKeywordToolSearchIndex(FIELDS, Map.of());

        index.index(List.of(
            document("weak_match", "list resources", "kafka appears once here"),
            document("strong_match", "kafka kafka kafka", "kafka topic tool"),
            document("no_match", "unrelated tool", "does something else entirely")));

        List<McpToolSearchMatch> matches = index.query("kafka");

        assertThat(matches, hasSize(2));
        assertThat(matches.get(0).name, equalTo("strong_match"));
        assertThat(matches.get(0).score, greaterThan(matches.get(1).score));
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
