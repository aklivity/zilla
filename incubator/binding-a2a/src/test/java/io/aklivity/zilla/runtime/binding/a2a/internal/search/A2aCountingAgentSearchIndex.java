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

import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import io.aklivity.zilla.runtime.binding.a2a.search.A2aAgentSearchDocument;
import io.aklivity.zilla.runtime.binding.a2a.search.A2aAgentSearchIndex;
import io.aklivity.zilla.runtime.binding.a2a.search.A2aAgentSearchMatch;

public final class A2aCountingAgentSearchIndex implements A2aAgentSearchIndex
{
    public static final AtomicInteger INDEXED = new AtomicInteger();

    @Override
    public void index(
        Collection<A2aAgentSearchDocument> documents,
        CompletionCallback<Void> completed)
    {
        INDEXED.incrementAndGet();
        completed.completed(null);
    }

    @Override
    public void query(
        String text,
        CompletionCallback<List<A2aAgentSearchMatch>> completed)
    {
        completed.completed(List.of());
    }
}
