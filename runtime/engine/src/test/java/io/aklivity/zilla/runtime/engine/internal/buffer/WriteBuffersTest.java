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
package io.aklivity.zilla.runtime.engine.internal.buffer;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.sameInstance;

import org.junit.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;

public class WriteBuffersTest
{
    @Test
    public void shouldSupplyBufferWithConfiguredCapacity()
    {
        WriteBuffers buffers = new WriteBuffers(1024);

        MutableDirectBufferEx buffer = buffers.supply("ext");

        assertThat(buffer.capacity(), equalTo(1024));
    }

    @Test
    public void shouldSupplyNativeBuffer()
    {
        WriteBuffers buffers = new WriteBuffers(1024);

        MutableDirectBufferEx buffer = buffers.supply("ext");

        assertThat(buffer.byteBuffer(), notNullValue());
        assertThat(buffer.byteBuffer().isDirect(), equalTo(true));
        assertThat(buffer.byteArray(), equalTo(null));
    }

    @Test
    public void shouldSupplySameBufferForSameName()
    {
        WriteBuffers buffers = new WriteBuffers(1024);

        MutableDirectBufferEx first = buffers.supply("ext");
        MutableDirectBufferEx second = buffers.supply("ext");

        assertThat(second, sameInstance(first));
    }

    @Test
    public void shouldSupplyDistinctBuffersForDistinctNames()
    {
        WriteBuffers buffers = new WriteBuffers(1024);

        MutableDirectBufferEx ext = buffers.supply("ext");
        MutableDirectBufferEx headers = buffers.supply("headers");

        assertThat(headers, not(sameInstance(ext)));
    }

    @Test
    public void shouldNotShareBuffersAcrossInstances()
    {
        WriteBuffers first = new WriteBuffers(1024);
        WriteBuffers second = new WriteBuffers(1024);

        assertThat(second.supply("ext"), not(sameInstance(first.supply("ext"))));
    }

    @Test
    public void shouldRetainBytesWrittenToBufferForSameName()
    {
        WriteBuffers buffers = new WriteBuffers(1024);

        buffers.supply("ext").putInt(0, 0x01020304);

        assertThat(buffers.supply("ext").getInt(0), equalTo(0x01020304));
    }
}
