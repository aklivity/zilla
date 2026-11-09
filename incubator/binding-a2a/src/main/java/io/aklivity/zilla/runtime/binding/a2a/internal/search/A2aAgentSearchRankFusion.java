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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.aklivity.zilla.runtime.binding.a2a.search.A2aAgentSearchMatch;

/**
 * Reciprocal Rank Fusion: combines any number of independently-ranked match lists by rank
 * position rather than raw score, so heterogeneous ranking algorithms (e.g. BM25 alongside a
 * future embedding-based backend) compose without needing comparable score scales. A single
 * input list degenerates to its own order, since the fused score is a strictly monotonic
 * function of rank.
 */
final class A2aAgentSearchRankFusion
{
    private static final double K = 60.0;

    private A2aAgentSearchRankFusion()
    {
    }

    static List<A2aAgentSearchMatch> fuse(
        List<List<A2aAgentSearchMatch>> rankings)
    {
        Map<String, Double> fusedScores = new LinkedHashMap<>();

        for (List<A2aAgentSearchMatch> ranking : rankings)
        {
            for (int rank = 0; rank < ranking.size(); rank++)
            {
                String agent = ranking.get(rank).agent;
                double contribution = 1.0 / (K + rank + 1);
                fusedScores.merge(agent, contribution, Double::sum);
            }
        }

        List<A2aAgentSearchMatch> fused = new ArrayList<>(fusedScores.size());
        fusedScores.forEach((agent, score) -> fused.add(new A2aAgentSearchMatch(agent, score)));
        fused.sort((a, b) -> Double.compare(b.score, a.score));

        return fused;
    }
}
