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
package io.aklivity.zilla.runtime.metrics.llm.internal;

import java.util.function.ToIntFunction;

import io.aklivity.zilla.runtime.metrics.llm.internal.types.stream.LlmUsageFW;

enum LlmTokens
{
    INPUT("input", "input", LlmUsageFW::inputTokens),
    OUTPUT("output", "output", LlmUsageFW::outputTokens),
    TOTAL("total", "total", LlmUsageFW::totalTokens),
    CACHE_READ("cache.read", "cache read input", LlmUsageFW::cacheReadTokens),
    CACHE_WRITE("cache.write", "cache write input", LlmUsageFW::cacheWriteTokens),
    REASONING("reasoning", "reasoning output", LlmUsageFW::reasoningTokens);

    static final int ABSENT = -1;

    private final String segment;
    private final String summary;
    private final ToIntFunction<LlmUsageFW> count;

    LlmTokens(
        String segment,
        String summary,
        ToIntFunction<LlmUsageFW> count)
    {
        this.segment = segment;
        this.summary = summary;
        this.count = count;
    }

    String segment()
    {
        return segment;
    }

    String summary()
    {
        return summary;
    }

    int count(
        LlmUsageFW usage)
    {
        return count.applyAsInt(usage);
    }
}
