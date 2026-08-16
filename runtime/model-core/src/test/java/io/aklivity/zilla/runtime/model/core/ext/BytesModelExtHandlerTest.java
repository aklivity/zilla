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
package io.aklivity.zilla.runtime.model.core.ext;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

public class BytesModelExtHandlerTest
{
    @Test
    public void shouldReportNoPaddingByDefault()
    {
        BytesModelExtHandler handler = stream -> stream;

        assertEquals(0, handler.padding());
    }

    @Test
    public void shouldForwardStreamUnchangedByDefault()
    {
        BytesModelExtHandler handler = stream -> stream;
        BytesTransformable stream = transform -> null;

        assertEquals(stream, handler.transform(stream));
    }

    @Test
    public void shouldIdentifyNoneAsIdentity()
    {
        assertTrue(BytesTransform.NONE.identity());
    }

    @Test
    public void shouldCopyValueUnchangedForNoneTransform()
    {
        byte[] bytes = "hello".getBytes();
        UnsafeBufferEx src = new UnsafeBufferEx(bytes);
        UnsafeBufferEx dst = new UnsafeBufferEx(new byte[bytes.length]);

        int produced = BytesTransform.NONE.transform(src, 0, bytes.length, dst, 0);

        assertEquals(bytes.length, produced);
        assertEquals("hello", dst.getStringWithoutLengthUtf8(0, produced));
    }
}
