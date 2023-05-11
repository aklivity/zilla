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

import static io.aklivity.zilla.runtime.binding.http.internal.codec.Http2FrameType.WINDOW_UPDATE;
import static org.junit.Assert.assertEquals;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

public class Http2WindowUpdateFWTest
{

    @Test
    public void encode()
    {
        byte[] bytes = new byte[100];
        MutableDirectBuffer buf = new UnsafeBuffer(bytes);

        Http2WindowUpdateFW window = new Http2WindowUpdateFW.Builder()
                .wrap(buf, 1, buf.capacity())   // non-zero offset
                .streamId(3)
                .size(100)
                .build();

        assertEquals(4, window.length());
        assertEquals(1, window.offset());
        assertEquals(14, window.limit());
        assertEquals(WINDOW_UPDATE, window.type());
        assertEquals(0, window.flags());
        assertEquals(3, window.streamId());
        assertEquals(100, window.size());
    }

}
