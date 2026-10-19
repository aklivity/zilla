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

import java.util.HashMap;
import java.util.Map;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEnvelope;

final class LlmSectionEnvelope implements JsonEnvelope
{
    private final Map<String, EncodedValue> encoded;

    private LlmSectionAttributes attributes;

    LlmSectionEnvelope()
    {
        this.encoded = new HashMap<>();
    }

    void wrap(
        LlmSectionAttributes attributes)
    {
        this.attributes = attributes;
    }

    @Override
    public int count(
        String name)
    {
        return attributes != null && attributes.get(name) != null ? 1 : 0;
    }

    @Override
    public DirectBufferEx get(
        String name,
        int index)
    {
        final String value = index == 0 && attributes != null ? attributes.get(name) : null;

        return value != null ? encoded.computeIfAbsent(name, key -> new EncodedValue()).encode(value) : null;
    }

    @Override
    public void set(
        String name,
        DirectBufferEx value)
    {
    }

    private static final class EncodedValue
    {
        private final UnsafeBufferEx buffer = new UnsafeBufferEx();

        private String value;

        DirectBufferEx encode(
            String value)
        {
            if (this.value != value)
            {
                this.value = value;
                buffer.wrap(value.getBytes(UTF_8));
            }

            return buffer;
        }
    }
}
