/*
 * Copyright 2021-2021 Aklivity Inc.
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
package io.aklivity.zilla.runtime.cog.http2.internal.types;

import static io.aklivity.zilla.runtime.cog.http2.internal.types.Http2ErrorCode.PROTOCOL_ERROR;
import static io.aklivity.zilla.runtime.cog.http2.internal.types.Http2FrameType.RST_STREAM;
import static org.junit.Assert.assertEquals;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

public class Http2RstStreamFWTest
{

    @Test
    public void encode()
    {
        byte[] bytes = new byte[100];
        MutableDirectBuffer buf = new UnsafeBuffer(bytes);

        Http2RstStreamFW reset = new Http2RstStreamFW.Builder()
                .wrap(buf, 1, buf.capacity())       // non-zero offset
                .streamId(3)
                .errorCode(PROTOCOL_ERROR)
                .build();

        assertEquals(4, reset.length());
        assertEquals(1, reset.offset());
        assertEquals(14, reset.limit());
        assertEquals(RST_STREAM, reset.type());
        assertEquals(0, reset.flags());
        assertEquals(3, reset.streamId());
        Http2ErrorCode errorCode = Http2ErrorCode.from(reset.errorCode());
        assertEquals(PROTOCOL_ERROR, errorCode);
    }

}
