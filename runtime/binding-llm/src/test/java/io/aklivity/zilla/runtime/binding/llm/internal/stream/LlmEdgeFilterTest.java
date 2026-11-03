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
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

import java.io.ByteArrayOutputStream;

import org.junit.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

public class LlmEdgeFilterTest
{
    private final ByteArrayOutputStream output = new ByteArrayOutputStream();
    private final boolean[] closed = new boolean[1];
    private final LlmEdgeFilter filter =
        new LlmEdgeFilter((buffer, offset, length, last) -> collect(buffer, offset, length, last));

    @Test
    public void shouldDropFirstAndLastByteOfSingleChunk()
    {
        put("{\"a\":1}", true);

        assertThat(output.toString(UTF_8), equalTo("\"a\":1"));
        assertThat(closed[0], is(true));
    }

    @Test
    public void shouldDropFirstAndLastByteAcrossChunks()
    {
        put("{", false);
        put("\"a\":", false);
        put("1", false);
        put("}", false);
        put("", true);

        assertThat(output.toString(UTF_8), equalTo("\"a\":1"));
    }

    @Test
    public void shouldForwardEmptyObjectAsEmpty()
    {
        put("{}", true);

        assertThat(output.toString(UTF_8), equalTo(""));
        assertThat(closed[0], is(true));
    }

    @Test
    public void shouldRestartAfterReset()
    {
        put("{x}", true);
        filter.reset();
        put("{y}", true);

        assertThat(output.toString(UTF_8), equalTo("xy"));
    }

    private void put(
        String text,
        boolean last)
    {
        final byte[] bytes = text.getBytes(UTF_8);

        filter.put(new UnsafeBufferEx(bytes), 0, bytes.length, last);
    }

    private void collect(
        DirectBufferEx buffer,
        int offset,
        int length,
        boolean last)
    {
        final byte[] bytes = new byte[length];

        buffer.getBytes(offset, bytes);
        output.write(bytes, 0, length);
        closed[0] |= last;
    }
}
