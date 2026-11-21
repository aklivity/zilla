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
package io.aklivity.zilla.runtime.binding.a2a.internal.discovery;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.config.binding.a2a.A2aDiscoverySearchConfig;
import io.aklivity.zilla.config.binding.a2a.A2aKeywordSearchIndexConfig;
import io.aklivity.zilla.runtime.binding.a2a.internal.cache.A2aAgentCards;
import io.aklivity.zilla.runtime.binding.a2a.internal.cache.A2aCachedAgent;
import io.aklivity.zilla.runtime.binding.a2a.internal.cache.A2aCachedAgents;
import io.aklivity.zilla.runtime.binding.a2a.internal.search.A2aCountingAgentSearchIndex;
import io.aklivity.zilla.runtime.binding.a2a.internal.search.A2aCountingSearchIndexConfig;
import io.aklivity.zilla.runtime.binding.a2a.internal.search.A2aFailingSearchIndexConfig;
import io.aklivity.zilla.runtime.binding.a2a.internal.search.A2aUnregisteredSearchIndexConfig;
import io.aklivity.zilla.runtime.binding.a2a.search.A2aAgentSearchIndex.CompletionCallback;
import io.aklivity.zilla.runtime.engine.EngineContext;

public class A2aDiscoverySearchTest
{
    private static final long RESTRICTED = 0L;
    private static final long PERMITTED = 1L;

    private static final String WEATHER =
        "{\"name\":\"Weather Agent\",\"description\":\"Answers weather questions\"," +
        "\"skills\":[{\"id\":\"weather\",\"name\":\"Weather\",\"description\":\"Current weather\"}]}";
    private static final String WEATHER_WITH_FORECAST =
        "{\"name\":\"Weather Agent\",\"description\":\"Answers weather questions\"," +
        "\"skills\":[{\"id\":\"weather\",\"name\":\"Weather\",\"description\":\"Current weather\"}," +
        "{\"id\":\"forecast\",\"name\":\"Forecast\",\"description\":\"Seven day forecast\",\"tags\":[\"outlook\"]}]}";
    private static final String TRAVEL =
        "{\"name\":\"Travel Agent\",\"description\":\"Plans trips and books flights\"," +
        "\"skills\":[{\"id\":\"flights\",\"name\":\"Flights\",\"examples\":[\"Book a flight\"]}]}";

    private final EngineContext context = mock(EngineContext.class);
    private final A2aAgentCards cards = mock(A2aAgentCards.class);

    private long revision;
    private List<A2aCachedAgent> restricted;
    private List<A2aCachedAgent> permitted;

    @Before
    public void init()
    {
        doAnswer(invocation ->
        {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(context).dispatch(any());

        doAnswer(invocation ->
        {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(cards).ready(any());

        when(cards.revision()).thenAnswer(invocation -> revision);
        when(cards.list(anyLong())).thenAnswer(invocation ->
            (long) invocation.getArgument(0) == PERMITTED ? permitted : restricted);

        restricted = List.of(A2aCachedAgents.of("weather", WEATHER), A2aCachedAgents.of("travel", TRAVEL));
        permitted = List.of(A2aCachedAgents.of("weather", WEATHER_WITH_FORECAST), A2aCachedAgents.of("travel", TRAVEL));
    }

    @Test
    public void shouldFindAgentBySkillExample()
    {
        final Outcome outcome = find(null, PERMITTED, "book flight", 0);

        assertThat(outcome.slugs(), contains("travel"));
    }

    @Test
    public void shouldRankMoreRelevantAgentFirst()
    {
        final Outcome outcome = find(null, PERMITTED, "weather flights", 0);

        assertThat(outcome.slugs(), hasSize(2));
    }

    @Test
    public void shouldReturnNoMatches()
    {
        assertThat(find(null, PERMITTED, "astronomy", 0).slugs(), empty());
    }

    @Test
    public void shouldLimitToConfiguredMaximum()
    {
        final A2aDiscoverySearchConfig search = keyword(1);

        assertThat(find(search, PERMITTED, "agent", 0).slugs(), hasSize(1));
        assertThat(find(search, PERMITTED, "agent", 5).slugs(), hasSize(1));
    }

    @Test
    public void shouldLimitToRequestedLimitBelowMaximum()
    {
        assertThat(find(keyword(5), PERMITTED, "agent", 1).slugs(), hasSize(1));
    }

    @Test
    public void shouldNotMatchSkillTheCallerCannotSee()
    {
        assertThat(find(null, RESTRICTED, "forecast outlook", 0).slugs(), empty());
        assertThat(find(null, PERMITTED, "forecast outlook", 0).slugs(), contains("weather"));
    }

    @Test
    public void shouldRebuildWhenCardsChange()
    {
        final A2aDiscoverySearch search = new A2aDiscoverySearch(context, cards, null);
        final Outcome before = find(search, "flights");

        permitted = List.of(A2aCachedAgents.of("travel", TRAVEL.replace("flights", "cruises")));
        revision++;
        final Outcome after = find(search, "flights");

        assertThat(before.slugs(), contains("travel"));
        assertThat(after.slugs(), contains("travel"));

        permitted = List.of(A2aCachedAgents.of("travel",
            "{\"name\":\"Travel Agent\",\"description\":\"Plans cruises\"}"));
        revision++;

        assertThat(find(search, "flights").slugs(), empty());
        assertThat(find(search, "cruises").slugs(), contains("travel"));
    }

    @Test
    public void shouldFailWhenIndexFails()
    {
        final A2aDiscoverySearchConfig search = A2aDiscoverySearchConfig.builder()
            .limit(5)
            .fields(List.of("name"))
            .index(new A2aFailingSearchIndexConfig())
            .build();

        final Outcome first = find(search, PERMITTED, "weather", 0);
        final Outcome second = find(search, PERMITTED, "weather", 0);

        assertThat(first.failure == null, equalTo(false));
        assertThat(second.failure == null, equalTo(false));
    }

    @Test
    public void shouldNotRestartBuildWhenCompletionReentersTheView()
    {
        A2aCountingAgentSearchIndex.INDEXED.set(0);

        final A2aDiscoverySearchConfig config = A2aDiscoverySearchConfig.builder()
            .limit(5)
            .fields(List.of("name"))
            .index(new A2aCountingSearchIndexConfig())
            .build();
        final A2aDiscoverySearch search = new A2aDiscoverySearch(context, cards, config);
        final Outcome nested = new Outcome();

        search.find(PERMITTED, "weather", 0, new CompletionCallback<>()
        {
            @Override
            public void completed(
                List<A2aDiscoveryMatch> matches)
            {
                search.find(PERMITTED, "travel", 0, nested);
            }

            @Override
            public void failed(
                Throwable ex)
            {
                nested.failed(ex);
            }
        });

        assertThat(nested.slugs(), empty());
        assertThat(A2aCountingAgentSearchIndex.INDEXED.get(), equalTo(1));
    }

    @Test
    public void shouldFailWhenNoIndexFactoryRegistered()
    {
        final A2aDiscoverySearchConfig search = A2aDiscoverySearchConfig.builder()
            .limit(5)
            .fields(List.of("name"))
            .index(new A2aUnregisteredSearchIndexConfig())
            .build();

        assertThat(find(search, PERMITTED, "weather", 0).failure == null, equalTo(false));
    }

    private static A2aDiscoverySearchConfig keyword(
        int limit)
    {
        return A2aDiscoverySearchConfig.builder()
            .limit(limit)
            .fields(List.of("name", "description", "skills"))
            .index(A2aKeywordSearchIndexConfig.builder().build())
            .build();
    }

    private Outcome find(
        A2aDiscoverySearchConfig config,
        long authorization,
        String query,
        int limit)
    {
        final Outcome outcome = new Outcome();
        new A2aDiscoverySearch(context, cards, config).find(authorization, query, limit, outcome);
        return outcome;
    }

    private Outcome find(
        A2aDiscoverySearch search,
        String query)
    {
        final Outcome outcome = new Outcome();
        search.find(PERMITTED, query, 0, outcome);
        return outcome;
    }

    private static final class Outcome implements CompletionCallback<List<A2aDiscoveryMatch>>
    {
        private List<A2aDiscoveryMatch> matches;
        private Throwable failure;

        @Override
        public void completed(
            List<A2aDiscoveryMatch> matches)
        {
            this.matches = matches;
        }

        @Override
        public void failed(
            Throwable ex)
        {
            this.failure = ex;
        }

        List<String> slugs()
        {
            assertThat(failure, nullValue());
            return matches.stream().map(match -> match.agent.agent).toList();
        }
    }
}
