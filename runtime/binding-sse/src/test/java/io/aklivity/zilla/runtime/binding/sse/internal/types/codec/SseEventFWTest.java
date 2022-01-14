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
package io.aklivity.zilla.runtime.binding.sse.internal.types.codec;

import static io.aklivity.zilla.runtime.binding.sse.internal.types.codec.SseEventFW.putHexLong;
import static java.lang.Integer.toHexString;
import static java.nio.ByteBuffer.allocateDirect;
import static org.junit.Assert.assertEquals;

import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

public class SseEventFWTest
{
    private static final int BUFFER_SIZE = 1024;

    private final UnsafeBuffer actual = new UnsafeBuffer(allocateDirect(BUFFER_SIZE))
    {
        {
            // Make sure the code is not secretly relying upon memory being initialized to 0
            setMemory(0, capacity(), (byte) 0xab);
        }
    };

    private final UnsafeBuffer expected = new UnsafeBuffer(allocateDirect(BUFFER_SIZE))
    {
        {
            // Make sure the code is not secretly relying upon memory being initialized to 0
            setMemory(0, capacity(), (byte) 0xab);
        }
    };

    final int offset = 123;

    @Test
    public void shouldPutLong1()
    {
        final int testValue = 1;
        expected.putStringWithoutLengthUtf8(offset, toHexString(testValue));
        int length = putHexLong(testValue, actual, offset);
        assertEquals(expected.byteBuffer(), actual.byteBuffer());
        assertEquals(1, length);
    }

    @Test
    public void shouldPutLong0()
    {
        final int testValue = 1;
        expected.putStringWithoutLengthUtf8(offset, toHexString(testValue));
        int length = putHexLong(testValue, actual, offset);
        assertEquals(expected.byteBuffer(), actual.byteBuffer());
        assertEquals(1, length);
    }

    @Test
    public void shouldPutLong15()
    {
        final int testValue = 15;
        final String expectedHexString = toHexString(testValue);
        expected.putStringWithoutLengthUtf8(offset, expectedHexString);
        int length = putHexLong(testValue, actual, offset);
        assertEquals(expected.byteBuffer(), actual.byteBuffer());
        assertEquals(expectedHexString.length(), length);
    }

    @Test
    public void shouldPutLong9999999()
    {
        final int testValue = 9999999;
        final String expectedHexString = toHexString(testValue);
        expected.putStringWithoutLengthUtf8(offset, expectedHexString);
        int length = putHexLong(testValue, actual, offset);
        assertEquals(expected.byteBuffer(), actual.byteBuffer());
        assertEquals(expectedHexString.length(), length);
    }


    @Test
    public void shouldPutLong5124095576030447()
    {
        final long testValue = 5124095576030447L;
        final String expectedHexString = Long.toHexString(testValue);
        expected.putStringWithoutLengthUtf8(offset, expectedHexString);
        int length = putHexLong(testValue, actual, offset);
        assertEquals(expected.byteBuffer(), actual.byteBuffer());
        assertEquals(expectedHexString.length(), length);
    }

}
