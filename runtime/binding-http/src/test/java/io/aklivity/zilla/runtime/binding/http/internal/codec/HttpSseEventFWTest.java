/*
 * Copyright 2021-2026 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.runtime.binding.http.internal.codec;

import static io.aklivity.zilla.runtime.engine.util.Flags.COMPLETE;
import static io.aklivity.zilla.runtime.engine.util.Flags.FIN;
import static io.aklivity.zilla.runtime.engine.util.Flags.INIT;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

import io.aklivity.zilla.runtime.binding.http.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

public class HttpSseEventFWTest
{
    private final MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[1024]);
    private final HttpSseEventFW.Builder eventRW = new HttpSseEventFW.Builder();
    private final OctetsFW dataRO = new OctetsFW();

    @Test
    public void shouldEncodeCompleteEventWithoutFields()
    {
        assertEquals("data:Hello, world\n\n", encode(COMPLETE, null, null, "Hello, world"));
    }

    @Test
    public void shouldEncodeCompleteEventWithIdAndType()
    {
        assertEquals("id:105\nevent:one\ndata:Hello, world\n\n", encode(COMPLETE, "105", "one", "Hello, world"));
    }

    @Test
    public void shouldEncodeCompleteEventWithEmptyIdAndEmptyType()
    {
        assertEquals("id:\nevent:\ndata:Hello, world\n\n", encode(COMPLETE, "", "", "Hello, world"));
    }

    @Test
    public void shouldEncodeCompleteEventWithEmptyData()
    {
        assertEquals("data:\n\n", encode(COMPLETE, null, null, ""));
    }

    @Test
    public void shouldEncodeCompleteEventWithMultipleLines()
    {
        assertEquals("data:Hello\ndata:world\n\n", encode(COMPLETE, null, null, "Hello\nworld"));
    }

    @Test
    public void shouldEncodeInitialFragment()
    {
        assertEquals("id:105\ndata:Hel", encode(INIT, "105", null, "Hel"));
    }

    @Test
    public void shouldEncodeFinalFragment()
    {
        assertEquals("lo\n\n", encode(FIN, null, null, "lo"));
    }

    @Test
    public void shouldEncodeIdOnlyEvent()
    {
        assertEquals("id:105\n\n", encode(COMPLETE, null, "105", null, null));
    }

    @Test
    public void shouldEncodeFinalFragmentWithTrailingType()
    {
        assertEquals("lo\nevent:one\n\n", encode(FIN, null, null, "one", "lo"));
    }

    @Test
    public void shouldEncodeRetryOnlyEvent()
    {
        assertEquals("retry:5000\n\n", encodeRetry(COMPLETE, "5000", null));
    }

    @Test
    public void shouldEncodeCompleteEventWithRetry()
    {
        assertEquals("retry:5000\ndata:Hello, world\n\n", encodeRetry(COMPLETE, "5000", "Hello, world"));
    }

    @Test
    public void shouldEncodeCommentOnlyEvent()
    {
        assertEquals(":\n\n", encode(COMPLETE, "", null, null, null));
    }

    private String encode(
        int flags,
        String comment,
        String id,
        String type,
        String data)
    {
        final byte[] bytes = data != null ? data.getBytes(UTF_8) : null;
        final HttpSseEventFW event = eventRW.wrap(buffer, 0, buffer.capacity())
            .flags(flags)
            .comment(comment != null ? field(comment) : null)
            .id(id != null ? field(id) : null)
            .type(type != null ? field(type) : null)
            .data(bytes != null ? dataRO.wrap(new UnsafeBufferEx(bytes), 0, bytes.length) : null)
            .build();

        return buffer.getStringWithoutLengthUtf8(event.offset(), event.sizeof());
    }

    private String encode(
        int flags,
        String id,
        String type,
        String data)
    {
        final byte[] bytes = data.getBytes(UTF_8);
        final MutableDirectBufferEx dataBuffer = new UnsafeBufferEx(bytes);
        final HttpSseEventFW event = eventRW.wrap(buffer, 0, buffer.capacity())
            .flags(flags)
            .id(id != null ? field(id) : null)
            .type(type != null ? field(type) : null)
            .data(dataRO.wrap(dataBuffer, 0, bytes.length))
            .build();

        return buffer.getStringWithoutLengthUtf8(event.offset(), event.sizeof());
    }

    private String encodeRetry(
        int flags,
        String retry,
        String data)
    {
        final byte[] bytes = data != null ? data.getBytes(UTF_8) : null;
        final HttpSseEventFW event = eventRW.wrap(buffer, 0, buffer.capacity())
            .flags(flags)
            .retry(field(retry))
            .data(bytes != null ? dataRO.wrap(new UnsafeBufferEx(bytes), 0, bytes.length) : null)
            .build();

        return buffer.getStringWithoutLengthUtf8(event.offset(), event.sizeof());
    }

    private DirectBufferEx field(
        String value)
    {
        return new UnsafeBufferEx(value.getBytes(UTF_8));
    }
}
