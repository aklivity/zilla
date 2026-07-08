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
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;

import java.util.List;
import java.util.stream.Collectors;

import org.junit.Test;

import io.aklivity.zilla.runtime.binding.mcp.search.McpToolSearchMatch;

public class McpToolSearchRankFusionTest
{
    @Test
    public void shouldPreserveOrderForSingleRanking()
    {
        List<McpToolSearchMatch> ranking = List.of(
            new McpToolSearchMatch("a", 9.0),
            new McpToolSearchMatch("b", 5.0),
            new McpToolSearchMatch("c", 1.0));

        List<McpToolSearchMatch> fused = McpToolSearchRankFusion.fuse(List.of(ranking));

        assertThat(names(fused), contains("a", "b", "c"));
    }

    @Test
    public void shouldBoostItemsAppearingInMultipleRankings()
    {
        List<McpToolSearchMatch> ranking1 = List.of(
            new McpToolSearchMatch("a", 9.0),
            new McpToolSearchMatch("b", 5.0),
            new McpToolSearchMatch("c", 1.0));
        List<McpToolSearchMatch> ranking2 = List.of(
            new McpToolSearchMatch("a", 9.0),
            new McpToolSearchMatch("d", 5.0),
            new McpToolSearchMatch("e", 1.0));

        List<McpToolSearchMatch> fused = McpToolSearchRankFusion.fuse(List.of(ranking1, ranking2));

        assertThat(names(fused).get(0), equalTo("a"));
    }

    @Test
    public void shouldReturnEmptyForNoRankings()
    {
        List<McpToolSearchMatch> fused = McpToolSearchRankFusion.fuse(List.of());

        assertThat(fused, empty());
    }

    @Test
    public void shouldReturnEmptyWhenAllRankingsEmpty()
    {
        List<McpToolSearchMatch> fused = McpToolSearchRankFusion.fuse(List.of(List.of(), List.of()));

        assertThat(fused, empty());
    }

    private static List<String> names(
        List<McpToolSearchMatch> matches)
    {
        return matches.stream().map(match -> match.name).collect(Collectors.toList());
    }
}
