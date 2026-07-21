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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.aklivity.zilla.runtime.binding.mcp.search.McpToolSearchMatch;

/**
 * Reciprocal Rank Fusion: combines any number of independently-ranked match lists by rank
 * position rather than raw score, so heterogeneous ranking algorithms (e.g. BM25 alongside a
 * future embedding-based backend) compose without needing comparable score scales. A single
 * input list degenerates to its own order, since the fused score is a strictly monotonic
 * function of rank.
 */
final class McpToolSearchRankFusion
{
    private static final double K = 60.0;

    private McpToolSearchRankFusion()
    {
    }

    static List<McpToolSearchMatch> fuse(
        List<List<McpToolSearchMatch>> rankings)
    {
        Map<String, Double> fusedScores = new LinkedHashMap<>();

        for (List<McpToolSearchMatch> ranking : rankings)
        {
            for (int rank = 0; rank < ranking.size(); rank++)
            {
                String name = ranking.get(rank).name;
                double contribution = 1.0 / (K + rank + 1);
                fusedScores.merge(name, contribution, Double::sum);
            }
        }

        List<McpToolSearchMatch> fused = new ArrayList<>(fusedScores.size());
        fusedScores.forEach((name, score) -> fused.add(new McpToolSearchMatch(name, score)));
        fused.sort((a, b) -> Double.compare(b.score, a.score));

        return fused;
    }
}
