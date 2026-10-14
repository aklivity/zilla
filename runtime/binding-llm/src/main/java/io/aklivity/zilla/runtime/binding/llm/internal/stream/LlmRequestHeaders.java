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

import static java.nio.charset.StandardCharsets.UTF_8;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.HttpBeginExFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEnvelope;

final class LlmRequestHeaders implements JsonEnvelope
{
    private final Map<String, List<DirectBufferEx>> valuesByName;

    private LlmRequestHeaders()
    {
        this.valuesByName = new LinkedHashMap<>();
    }

    static LlmRequestHeaders of(
        HttpBeginExFW httpBeginEx)
    {
        final LlmRequestHeaders headers = new LlmRequestHeaders();

        httpBeginEx.headers().forEach(h ->
            headers.set(h.name().asString(), new UnsafeBufferEx(h.value().asString().getBytes(UTF_8))));

        return headers;
    }

    @Override
    public int count(
        String name)
    {
        final List<DirectBufferEx> values = valuesByName.get(name);
        return values != null ? values.size() : 0;
    }

    @Override
    public DirectBufferEx get(
        String name,
        int index)
    {
        final List<DirectBufferEx> values = valuesByName.get(name);
        return values != null && index < values.size() ? values.get(index) : null;
    }

    @Override
    public void set(
        String name,
        DirectBufferEx value)
    {
        valuesByName.computeIfAbsent(name, n -> new ArrayList<>()).add(value);
    }
}
