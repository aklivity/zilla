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
package io.aklivity.zilla.runtime.binding.mcp.internal.eager;

import java.util.Collection;
import java.util.List;

import io.aklivity.zilla.runtime.binding.mcp.eager.McpToolEagerDocument;
import io.aklivity.zilla.runtime.binding.mcp.eager.McpToolsEager;
import io.aklivity.zilla.runtime.binding.mcp.eager.McpToolsEagerRecorder;

/**
 * Composes any number of configured {@link McpToolsEager} stages behind a single
 * {@link McpToolsEager}, threading the candidate list through each stage in turn -- a single
 * pipeline, not fan-out-and-fuse like {@code McpToolSearchComposite}, since eager composition
 * combines eligibility constraints and ordering signals, not independent rankings to merge. A
 * single stage is delegated to directly, with no wrapper overhead.
 * <p>
 * {@link #index(Collection, CompletionCallback)} does fan out, over the same full, un-narrowed
 * document set, to every stage in parallel, joining once all have completed; the first failure
 * reported by any stage is forwarded as the composite's own failure, discarding any results
 * still outstanding from the others.
 * </p>
 */
public final class McpToolsEagerComposite implements McpToolsEager
{
    private final List<McpToolsEager> stages;

    public McpToolsEagerComposite(
        List<McpToolsEager> stages)
    {
        this.stages = stages;
    }

    @Override
    public void index(
        Collection<McpToolEagerDocument> documents,
        CompletionCallback<Void> completed)
    {
        if (stages.size() == 1)
        {
            stages.get(0).index(documents, completed);
        }
        else
        {
            final int[] remaining = { stages.size() };
            final boolean[] settled = { false };

            for (McpToolsEager stage : stages)
            {
                stage.index(documents, new CompletionCallback<>()
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
    public List<CharSequence> select(
        long authorization,
        List<CharSequence> names)
    {
        List<CharSequence> selected = names;
        for (int i = 0; i < stages.size() && !selected.isEmpty(); i++)
        {
            selected = stages.get(i).select(authorization, selected);
        }
        return selected;
    }

    @Override
    public McpToolsEagerRecorder recorder()
    {
        McpToolsEagerRecorder recorder = McpToolsEagerRecorder.NONE;
        for (McpToolsEager stage : stages)
        {
            recorder = recorder.andThen(stage.recorder());
        }
        return recorder;
    }
}
