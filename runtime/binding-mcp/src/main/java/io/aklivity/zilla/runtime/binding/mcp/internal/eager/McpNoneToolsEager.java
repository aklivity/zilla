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
import java.util.function.Consumer;

import io.aklivity.zilla.runtime.binding.mcp.eager.McpToolEagerDocument;
import io.aklivity.zilla.runtime.binding.mcp.eager.McpToolsEager;
import io.aklivity.zilla.runtime.binding.mcp.eager.McpToolsEagerRecorder;

/**
 * No eager/cold distinction at all -- every candidate is admitted, unchanged.
 */
final class McpNoneToolsEager implements McpToolsEager
{
    private final Consumer<Runnable> dispatch;

    McpNoneToolsEager(
        Consumer<Runnable> dispatch)
    {
        this.dispatch = dispatch;
    }

    @Override
    public void index(
        Collection<McpToolEagerDocument> documents,
        CompletionCallback<Void> completed)
    {
        dispatch.accept(() -> completed.completed(null));
    }

    @Override
    public List<CharSequence> select(
        long authorization,
        List<CharSequence> names)
    {
        return names;
    }

    @Override
    public McpToolsEagerRecorder recorder()
    {
        return McpToolsEagerRecorder.NONE;
    }
}
