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
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import org.junit.Test;

import io.aklivity.zilla.runtime.binding.a2a.search.A2aAgentSearchDocument;
import io.aklivity.zilla.runtime.binding.a2a.search.A2aAgentSearchIndex.CompletionCallback;
import io.aklivity.zilla.runtime.binding.a2a.search.A2aAgentSearchMatch;

public class A2aKeywordAgentSearchIndexTest
{
    private static final List<String> FIELDS = List.of("name", "description");

    @Test
    public void shouldReturnEmptyForNoDocuments()
    {
        A2aKeywordAgentSearchIndex index = new A2aKeywordAgentSearchIndex(Runnable::run, FIELDS, Map.of());

        rebuild(index, List.of());

        assertThat(search(index, "kafka"), empty());
    }

    @Test
    public void shouldReturnEmptyWhenNoMatch()
    {
        A2aKeywordAgentSearchIndex index = new A2aKeywordAgentSearchIndex(Runnable::run, FIELDS, Map.of());

        rebuild(index, List.of(document("github_create_pr", "create pr", "opens a pull request")));

        assertThat(search(index, "kafka"), empty());
    }

    @Test
    public void shouldMatchExactTermInName()
    {
        A2aKeywordAgentSearchIndex index = new A2aKeywordAgentSearchIndex(Runnable::run, FIELDS, Map.of());

        rebuild(index, List.of(
            document("github_create_pr", "github create pr", "opens a pull request"),
            document("kafka_list_topics", "kafka list topics", "lists kafka topics")));

        List<A2aAgentSearchMatch> matches = search(index, "kafka");

        assertThat(matches, not(empty()));
        assertThat(matches.get(0).agent, equalTo("kafka_list_topics"));
    }

    @Test
    public void shouldRankHigherWeightFieldHigher()
    {
        Map<String, Double> weights = Map.of("name", 3.0, "description", 1.0);
        A2aKeywordAgentSearchIndex index = new A2aKeywordAgentSearchIndex(Runnable::run, FIELDS, weights);

        rebuild(index, List.of(
            document("kafka_agent", "kafka topic manager", "manages message queues"),
            document("other_agent", "generic manager", "manages kafka message queues")));

        List<A2aAgentSearchMatch> matches = search(index, "kafka");

        assertThat(matches, hasSize(2));
        assertThat(matches.get(0).agent, equalTo("kafka_agent"));
        assertThat(matches.get(0).score, greaterThan(matches.get(1).score));
    }

    @Test
    public void shouldFindTermInsideCamelCaseCompoundToken()
    {
        A2aKeywordAgentSearchIndex index = new A2aKeywordAgentSearchIndex(Runnable::run, FIELDS, Map.of());

        rebuild(index, List.of(document("createKafkaTopic", "createKafkaTopic", null)));

        List<A2aAgentSearchMatch> matches = search(index, "kafka");

        assertThat(matches, hasSize(1));
        assertThat(matches.get(0).agent, equalTo("createKafkaTopic"));
    }

    @Test
    public void shouldReturnResultsSortedByScoreDescending()
    {
        A2aKeywordAgentSearchIndex index = new A2aKeywordAgentSearchIndex(Runnable::run, FIELDS, Map.of());

        rebuild(index, List.of(
            document("weak_match", "list resources", "kafka appears once here"),
            document("strong_match", "kafka kafka kafka", "kafka topic agent"),
            document("no_match", "unrelated agent", "does something else entirely")));

        List<A2aAgentSearchMatch> matches = search(index, "kafka");

        assertThat(matches, hasSize(2));
        assertThat(matches.get(0).agent, equalTo("strong_match"));
        assertThat(matches.get(0).score, greaterThan(matches.get(1).score));
    }

    @Test
    public void shouldNeverCompleteOnTheCallingStack()
    {
        Queue<Runnable> dispatched = new ArrayDeque<>();
        A2aKeywordAgentSearchIndex index = new A2aKeywordAgentSearchIndex(dispatched::add, FIELDS, Map.of());
        boolean[] completed = { false };

        index.index(
            List.of(document("kafka_agent", "kafka topic manager", "manages kafka topics")),
            new CompletionCallback<Void>()
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

    private static void rebuild(
        A2aKeywordAgentSearchIndex index,
        List<A2aAgentSearchDocument> documents)
    {
        boolean[] done = { false };
        index.index(documents, new CompletionCallback<Void>()
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
        A2aKeywordAgentSearchIndex index,
        String text)
    {
        Object[] result = new Object[1];
        index.query(text, new CompletionCallback<List<A2aAgentSearchMatch>>()
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
