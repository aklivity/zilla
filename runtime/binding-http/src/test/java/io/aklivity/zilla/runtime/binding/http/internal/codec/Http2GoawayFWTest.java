/*
 * Copyright 2021-2022 Aklivity Inc.
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

import static io.aklivity.zilla.runtime.binding.http.internal.codec.Http2ErrorCode.PROTOCOL_ERROR;
import static io.aklivity.zilla.runtime.binding.http.internal.codec.Http2FrameType.GO_AWAY;
import static org.junit.Assert.assertEquals;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

public class Http2GoawayFWTest
{

    @Test
    public void encode()
    {
        byte[] bytes = new byte[100];
        MutableDirectBuffer buf = new UnsafeBuffer(bytes);

        Http2GoawayFW goaway = new Http2GoawayFW.Builder()
                .wrap(buf, 1, buf.capacity())       // non-zero offset
                .lastStreamId(3)
                .errorCode(PROTOCOL_ERROR)
                .build();

        assertEquals(8, goaway.length());
        assertEquals(1, goaway.offset());
        assertEquals(18, goaway.limit());
        assertEquals(GO_AWAY, goaway.type());
        assertEquals(0, goaway.flags());
        assertEquals(0, goaway.streamId());
        assertEquals(3, goaway.lastStreamId());
        Http2ErrorCode errorCode = Http2ErrorCode.from(goaway.errorCode());
        assertEquals(PROTOCOL_ERROR, errorCode);
    }

}
