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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import org.junit.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.json.JsonGeneratorEx;

public class LlmEscapedWidthTest
{
    private static final int CAPACITY = 64 * 1024;
    private static final int ENVELOPE = "{\"id\":\"\"}".length();

    private final MutableDirectBufferEx target = new UnsafeBufferEx(new byte[CAPACITY]);
    private final JsonGeneratorEx generator = JsonEx.createGenerator();
    private final LlmEscapedWidth width = new LlmEscapedWidth(CAPACITY);

    @Test
    public void shouldMeasurePlainText()
    {
        assertWritten("call_55063");
    }

    @Test
    public void shouldMeasureQuoteAndBackslash()
    {
        assertWritten("a\"b\\c");
    }

    @Test
    public void shouldMeasureControlCharacters()
    {
        assertWritten("\u0001\n\t\u001f");
    }

    @Test
    public void shouldMeasureMultiByteCharacters()
    {
        assertWritten("é€😀");
    }

    @Test
    public void shouldMeasureLoneSurrogate()
    {
        assertWritten("a\ud800b");
    }

    @Test
    public void shouldMeasureLongEscapedText()
    {
        assertWritten("\u0001".repeat(4096));
    }

    @Test
    public void shouldMeasureEmptyText()
    {
        assertWritten("");
    }

    @Test
    public void shouldMeasureBuilderContent()
    {
        final StringBuilder builder = new StringBuilder("call_").append("\"x\"");

        generator.wrap(target, 0, CAPACITY);
        generator.reset();
        generator.writeStartObject().write("id", builder.toString()).writeEnd();

        assertThat(width.of(builder), equalTo(generator.length() - ENVELOPE));
    }

    private void assertWritten(
        String value)
    {
        generator.wrap(target, 0, CAPACITY);
        generator.reset();
        generator.writeStartObject().write("id", value).writeEnd();

        assertThat(width.of(value), equalTo(generator.length() - ENVELOPE));
    }
}
