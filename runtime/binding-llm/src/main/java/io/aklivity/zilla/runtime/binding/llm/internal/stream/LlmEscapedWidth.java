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

import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.json.JsonGeneratorEx;

final class LlmEscapedWidth
{
    private static final int OPENING_QUOTE = 1;

    private final MutableDirectBufferEx buffer;
    private final JsonGeneratorEx generator;

    LlmEscapedWidth(
        int capacity)
    {
        this.buffer = new UnsafeBufferEx(new byte[capacity]);
        this.generator = JsonEx.createGenerator();
    }

    int of(
        CharSequence chars)
    {
        generator.wrap(buffer, 0, buffer.capacity());
        generator.reset();
        generator.writeStartEscaped();
        generator.writeSegment(chars, 0, chars.length());

        return generator.length() - OPENING_QUOTE;
    }
}
