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

import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

import io.aklivity.zilla.runtime.binding.mcp.search.McpToolSearchDocument;
import io.aklivity.zilla.runtime.binding.mcp.search.McpToolSearchIndex;
import io.aklivity.zilla.runtime.binding.mcp.search.McpToolSearchMatch;

public final class McpFailingToolSearchIndex implements McpToolSearchIndex
{
    private final Consumer<Runnable> dispatcher;

    public McpFailingToolSearchIndex(
        Consumer<Runnable> dispatcher)
    {
        this.dispatcher = dispatcher;
    }

    @Override
    public void index(
        Collection<McpToolSearchDocument> documents,
        CompletionCallback<Void> completed)
    {
        dispatcher.accept(() -> completed.failed(new IllegalStateException("embedding provider unavailable")));
    }

    @Override
    public void query(
        String text,
        CompletionCallback<List<McpToolSearchMatch>> completed)
    {
        dispatcher.accept(() -> completed.failed(new IllegalStateException("embedding provider unavailable")));
    }
}
