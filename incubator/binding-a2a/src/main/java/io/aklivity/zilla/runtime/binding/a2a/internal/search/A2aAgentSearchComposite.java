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
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import io.aklivity.zilla.runtime.binding.a2a.search.A2aAgentSearchDocument;
import io.aklivity.zilla.runtime.binding.a2a.search.A2aAgentSearchIndex;
import io.aklivity.zilla.runtime.binding.a2a.search.A2aAgentSearchMatch;

/**
 * Composes any number of configured {@link A2aAgentSearchIndex} backends behind a single
 * {@link A2aAgentSearchIndex}, fusing their rankings with Reciprocal Rank Fusion. A single
 * backend is queried directly, with no fusion overhead.
 * <p>
 * Fans an {@link #index(Collection, CompletionCallback)} or {@link #query(String,
 * CompletionCallback)} call out to every configured backend and joins once all have
 * completed. A backend that fails contributes no results rather than failing the
 * composite; the composite itself fails only once every backend has failed, forwarding
 * the last-reported failure.
 * </p>
 */
public final class A2aAgentSearchComposite implements A2aAgentSearchIndex
{
    private final List<A2aAgentSearchIndex> indexes;

    public A2aAgentSearchComposite(
        List<A2aAgentSearchIndex> indexes)
    {
        this.indexes = indexes;
    }

    @Override
    public void index(
        Collection<A2aAgentSearchDocument> documents,
        CompletionCallback<Void> completed)
    {
        if (indexes.size() == 1)
        {
            indexes.get(0).index(documents, completed);
        }
        else
        {
            final int[] remaining = { indexes.size() };
            final int[] failures = { 0 };
            final Throwable[] lastFailure = { null };

            for (A2aAgentSearchIndex index : indexes)
            {
                index.index(documents, new CompletionCallback<>()
                {
                    @Override
                    public void completed(
                        Void result)
                    {
                        onSettled();
                    }

                    @Override
                    public void failed(
                        Throwable ex)
                    {
                        failures[0]++;
                        lastFailure[0] = ex;
                        onSettled();
                    }

                    private void onSettled()
                    {
                        if (--remaining[0] == 0)
                        {
                            if (failures[0] == indexes.size())
                            {
                                completed.failed(lastFailure[0]);
                            }
                            else
                            {
                                completed.completed(null);
                            }
                        }
                    }
                });
            }
        }
    }

    @Override
    public void query(
        String text,
        CompletionCallback<List<A2aAgentSearchMatch>> completed)
    {
        if (indexes.size() == 1)
        {
            indexes.get(0).query(text, completed);
        }
        else
        {
            final List<List<A2aAgentSearchMatch>> rankings = new ArrayList<>(Collections.nCopies(indexes.size(), List.of()));
            final int[] remaining = { indexes.size() };
            final int[] failures = { 0 };
            final Throwable[] lastFailure = { null };

            for (int i = 0; i < indexes.size(); i++)
            {
                final int slot = i;
                indexes.get(i).query(text, new CompletionCallback<>()
                {
                    @Override
                    public void completed(
                        List<A2aAgentSearchMatch> matches)
                    {
                        rankings.set(slot, matches);
                        onSettled();
                    }

                    @Override
                    public void failed(
                        Throwable ex)
                    {
                        failures[0]++;
                        lastFailure[0] = ex;
                        onSettled();
                    }

                    private void onSettled()
                    {
                        if (--remaining[0] == 0)
                        {
                            if (failures[0] == indexes.size())
                            {
                                completed.failed(lastFailure[0]);
                            }
                            else
                            {
                                completed.completed(A2aAgentSearchRankFusion.fuse(rankings));
                            }
                        }
                    }
                });
            }
        }
    }
}
