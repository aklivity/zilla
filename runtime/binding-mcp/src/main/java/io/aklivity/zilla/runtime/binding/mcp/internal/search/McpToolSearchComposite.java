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
import java.util.Collection;
import java.util.List;

import io.aklivity.zilla.runtime.binding.mcp.search.McpToolSearchDocument;
import io.aklivity.zilla.runtime.binding.mcp.search.McpToolSearchIndex;
import io.aklivity.zilla.runtime.binding.mcp.search.McpToolSearchMatch;

/**
 * Composes any number of configured {@link McpToolSearchIndex} backends behind a single
 * {@link McpToolSearchIndex}, fusing their rankings with Reciprocal Rank Fusion. A single
 * backend is queried directly, with no fusion overhead.
 */
public final class McpToolSearchComposite implements McpToolSearchIndex
{
    private final List<McpToolSearchIndex> indexes;

    public McpToolSearchComposite(
        List<McpToolSearchIndex> indexes)
    {
        this.indexes = indexes;
    }

    @Override
    public void index(
        Collection<McpToolSearchDocument> documents)
    {
        indexes.forEach(index -> index.index(documents));
    }

    @Override
    public List<McpToolSearchMatch> query(
        String text)
    {
        List<McpToolSearchMatch> result;

        if (indexes.size() == 1)
        {
            result = indexes.get(0).query(text);
        }
        else
        {
            List<List<McpToolSearchMatch>> rankings = new ArrayList<>(indexes.size());
            indexes.forEach(index -> rankings.add(index.query(text)));
            result = McpToolSearchRankFusion.fuse(rankings);
        }

        return result;
    }
}
