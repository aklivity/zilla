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
package io.aklivity.zilla.specs.binding.asyncapi.internal;

import static org.junit.Assert.assertEquals;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

import io.aklivity.zilla.specs.binding.asyncapi.internal.types.OctetsFW;
import io.aklivity.zilla.specs.binding.asyncapi.internal.types.stream.AsyncapiBeginExFW;

public class AsyncapiFunctionsTest
{
    @Test
    public void shouldGetMapper()
    {
        AsyncapiFunctions.Mapper mapper = new AsyncapiFunctions.Mapper();
        assertEquals("asyncapi", mapper.getPrefixName());
    }

    @Test
    public void shouldEncodeAsyncapiBeginExt()
    {
        final byte[] array = AsyncapiFunctions.beginEx()
            .typeId(0)
            .operationId("operationId")
            .extension(new byte[] {1})
            .build();

        DirectBuffer buffer = new UnsafeBuffer(array);
        AsyncapiBeginExFW asyncapiBeginEx = new AsyncapiBeginExFW().wrap(buffer, 0, buffer.capacity());
        MutableDirectBuffer writeBuffer = new UnsafeBuffer(new byte[1]);

        assertEquals("operationId", asyncapiBeginEx.operationId().asString());
        assertEquals(new OctetsFW.Builder().wrap(writeBuffer, 0, 1).set(new byte[] {1}).build(),
            asyncapiBeginEx.extension());
    }
}
