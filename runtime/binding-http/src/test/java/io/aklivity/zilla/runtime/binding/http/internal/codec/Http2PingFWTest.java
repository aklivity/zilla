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

import static io.aklivity.zilla.runtime.binding.http.internal.codec.Http2FrameType.PING;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

public class Http2PingFWTest
{

    @Test
    public void encode()
    {
        byte[] bytes = new byte[100];
        MutableDirectBuffer buf = new UnsafeBuffer(bytes);

        DirectBuffer payload = new UnsafeBuffer(new byte[] {1, 2, 3, 4, 5, 6, 7, 8});
        Http2PingFW ping = new Http2PingFW.Builder()
                .wrap(buf, 1, buf.capacity())       // non-zero offset
                .ack()
                .payload(payload, 0, payload.capacity())
                .build();

        assertEquals(8, ping.length());
        assertEquals(1, ping.offset());
        assertEquals(18, ping.limit());
        assertEquals(PING, ping.type());
        assertTrue(ping.ack());
        assertEquals(0, ping.streamId());
        assertEquals(payload, ping.payload());
    }

}
