/*
 * Copyright 2021-2023 Aklivity Inc.
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

import static io.aklivity.zilla.runtime.binding.http.internal.codec.Http2FrameType.DATA;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

public class Http2DataFWTest
{
    @Test
    public void encode()
    {
        DirectBuffer payload = new UnsafeBuffer(new byte[] {0, 1, 2, 3, 4, 5});
        byte[] bytes = new byte[1 + 9 + payload.capacity()];
        MutableDirectBuffer buf = new UnsafeBuffer(bytes);

        Http2DataFW fw = new Http2DataFW.Builder()
                .wrap(buf, 1, buf.capacity())   // non-zero offset
                .endStream()
                .streamId(3)
                .payload(payload)
                .build();

        assertEquals(6, fw.length());
        assertEquals(1, fw.offset());
        assertEquals(16, fw.limit());
        assertTrue(fw.endStream());
        assertEquals(DATA, fw.type());
        assertEquals(3, fw.streamId());
    }

    @Test
    public void encodeLarge()
    {
        DirectBuffer payload = new UnsafeBuffer(new byte[0x010203]);
        byte[] bytes = new byte[1 + 9 + payload.capacity()];
        MutableDirectBuffer buf = new UnsafeBuffer(bytes);

        Http2DataFW fw = new Http2DataFW.Builder()
                .wrap(buf, 1, buf.capacity())   // non-zero offset
                .endStream()
                .streamId(3)
                .payload(payload)
                .build();

        assertEquals(payload.capacity(), fw.length());
        assertEquals(1, fw.offset());
        assertEquals(1 + 9 + 0x010203, fw.limit());
        assertTrue(fw.endStream());
        assertEquals(DATA, fw.type());
        assertEquals(3, fw.streamId());
    }

    @Test
    public void encodeEmpty()
    {
        DirectBuffer payload = new UnsafeBuffer(new byte[0]);
        byte[] bytes = new byte[1 + 9 + payload.capacity()];
        MutableDirectBuffer buf = new UnsafeBuffer(bytes);

        Http2DataFW fw = new Http2DataFW.Builder()
                .wrap(buf, 1, buf.capacity())   // non-zero offset
                .endStream()
                .streamId(3)
                .payload(payload)
                .build();

        assertEquals(0, fw.length());
        assertEquals(1, fw.offset());
        assertEquals(10, fw.limit());
        assertTrue(fw.endStream());
        assertEquals(DATA, fw.type());
        assertEquals(3, fw.streamId());
    }
}
