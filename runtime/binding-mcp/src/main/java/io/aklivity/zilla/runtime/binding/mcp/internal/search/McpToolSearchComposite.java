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
import java.util.Collections;
import java.util.List;

import io.aklivity.zilla.runtime.binding.mcp.search.McpToolSearchDocument;
import io.aklivity.zilla.runtime.binding.mcp.search.McpToolSearchIndex;
import io.aklivity.zilla.runtime.binding.mcp.search.McpToolSearchMatch;

/**
 * Composes any number of configured {@link McpToolSearchIndex} backends behind a single
 * {@link McpToolSearchIndex}, fusing their rankings with Reciprocal Rank Fusion. A single
 * backend is queried directly, with no fusion overhead.
 * <p>
 * Fans an {@link #index(Collection, CompletionCallback)} or {@link #query(String,
 * CompletionCallback)} call out to every configured backend and joins once all have
 * completed; the first failure reported by any backend is forwarded as the composite's own
 * failure, discarding any results still outstanding from the others.
 * </p>
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
        Collection<McpToolSearchDocument> documents,
        CompletionCallback<Void> completed)
    {
        if (indexes.size() == 1)
        {
            indexes.get(0).index(documents, completed);
        }
        else
        {
            final int[] remaining = { indexes.size() };
            final boolean[] settled = { false };

            for (McpToolSearchIndex index : indexes)
            {
                index.index(documents, new CompletionCallback<>()
                {
                    @Override
                    public void completed(
                        Void result)
                    {
                        if (--remaining[0] == 0 && !settled[0])
                        {
                            settled[0] = true;
                            completed.completed(null);
                        }
                    }

                    @Override
                    public void failed(
                        Throwable ex)
                    {
                        if (!settled[0])
                        {
                            settled[0] = true;
                            completed.failed(ex);
                        }
                    }
                });
            }
        }
    }

    @Override
    public void query(
        String text,
        CompletionCallback<List<McpToolSearchMatch>> completed)
    {
        if (indexes.size() == 1)
        {
            indexes.get(0).query(text, completed);
        }
        else
        {
            final List<List<McpToolSearchMatch>> rankings = new ArrayList<>(Collections.nCopies(indexes.size(), null));
            final int[] remaining = { indexes.size() };
            final boolean[] settled = { false };

            for (int i = 0; i < indexes.size(); i++)
            {
                final int slot = i;
                indexes.get(i).query(text, new CompletionCallback<>()
                {
                    @Override
                    public void completed(
                        List<McpToolSearchMatch> matches)
                    {
                        rankings.set(slot, matches);
                        if (--remaining[0] == 0 && !settled[0])
                        {
                            settled[0] = true;
                            completed.completed(McpToolSearchRankFusion.fuse(rankings));
                        }
                    }

                    @Override
                    public void failed(
                        Throwable ex)
                    {
                        if (!settled[0])
                        {
                            settled[0] = true;
                            completed.failed(ex);
                        }
                    }
                });
            }
        }
    }
}
