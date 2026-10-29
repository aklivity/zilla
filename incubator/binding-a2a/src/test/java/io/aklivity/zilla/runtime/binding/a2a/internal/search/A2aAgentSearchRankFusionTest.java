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
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;

import java.util.List;
import java.util.stream.Collectors;

import org.junit.Test;

import io.aklivity.zilla.runtime.binding.a2a.search.A2aAgentSearchMatch;

public class A2aAgentSearchRankFusionTest
{
    @Test
    public void shouldPreserveOrderForSingleRanking()
    {
        List<A2aAgentSearchMatch> ranking = List.of(
            new A2aAgentSearchMatch("a", 9.0),
            new A2aAgentSearchMatch("b", 5.0),
            new A2aAgentSearchMatch("c", 1.0));

        List<A2aAgentSearchMatch> fused = A2aAgentSearchRankFusion.fuse(List.of(ranking));

        assertThat(names(fused), contains("a", "b", "c"));
    }

    @Test
    public void shouldBoostItemsAppearingInMultipleRankings()
    {
        List<A2aAgentSearchMatch> ranking1 = List.of(
            new A2aAgentSearchMatch("a", 9.0),
            new A2aAgentSearchMatch("b", 5.0),
            new A2aAgentSearchMatch("c", 1.0));
        List<A2aAgentSearchMatch> ranking2 = List.of(
            new A2aAgentSearchMatch("a", 9.0),
            new A2aAgentSearchMatch("d", 5.0),
            new A2aAgentSearchMatch("e", 1.0));

        List<A2aAgentSearchMatch> fused = A2aAgentSearchRankFusion.fuse(List.of(ranking1, ranking2));

        assertThat(names(fused).get(0), equalTo("a"));
    }

    @Test
    public void shouldReturnEmptyForNoRankings()
    {
        List<A2aAgentSearchMatch> fused = A2aAgentSearchRankFusion.fuse(List.of());

        assertThat(fused, empty());
    }

    @Test
    public void shouldReturnEmptyWhenAllRankingsEmpty()
    {
        List<A2aAgentSearchMatch> fused = A2aAgentSearchRankFusion.fuse(List.of(List.of(), List.of()));

        assertThat(fused, empty());
    }

    private static List<String> names(
        List<A2aAgentSearchMatch> matches)
    {
        return matches.stream().map(match -> match.agent).collect(Collectors.toList());
    }
}
