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
package io.aklivity.zilla.runtime.binding.llm.internal.stream;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEnvelope;

// Per-stream metadata channel: seeded from the inbound request's :method / :path pseudo-headers and its
// ordinary headers (LlmDialect.detect(JsonEnvelope) reads these), then read from and written to as the
// dialect's own decode transform observes fields (e.g. extracting a "model" entry) while the request body
// streams through the json pipeline.
final class LlmModelEnvelope implements JsonEnvelope
{
    private final Map<String, List<DirectBufferEx>> valuesByName;

    LlmModelEnvelope()
    {
        this.valuesByName = new LinkedHashMap<>();
    }

    void clear()
    {
        valuesByName.clear();
    }

    @Override
    public int count(
        String name)
    {
        List<DirectBufferEx> values = valuesByName.get(name);
        return values != null ? values.size() : 0;
    }

    @Override
    public DirectBufferEx get(
        String name,
        int index)
    {
        List<DirectBufferEx> values = valuesByName.get(name);
        return values != null && index < values.size() ? values.get(index) : null;
    }

    @Override
    public void set(
        String name,
        DirectBufferEx value)
    {
        byte[] copy = new byte[value.capacity()];
        value.getBytes(0, copy);

        valuesByName.computeIfAbsent(name, n -> new ArrayList<>()).add(new UnsafeBufferEx(copy));
    }
}
