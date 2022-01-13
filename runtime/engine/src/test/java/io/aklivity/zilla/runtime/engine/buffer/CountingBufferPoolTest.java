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
package io.aklivity.zilla.runtime.engine.buffer;

import static io.aklivity.zilla.runtime.engine.buffer.BufferPool.NO_SLOT;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.sameInstance;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.nio.ByteBuffer;
import java.util.function.LongSupplier;

import org.agrona.MutableDirectBuffer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class CountingBufferPoolTest
{
    private LongSupplier acquires;
    private LongSupplier releases;
    private BufferPool delegate;
    private BufferPool bufferPool;

    @Before
    public void initMocks()
    {
        acquires = mock(LongSupplier.class);
        releases = mock(LongSupplier.class);
        delegate = mock(BufferPool.class);
        bufferPool = new CountingBufferPool(delegate, acquires, releases);
    }

    @Test
    public void shouldDelegateSlotCapacity()
    {
        when(delegate.slotCapacity()).thenReturn(1024);

        int slotCapacity = bufferPool.slotCapacity();

        assertThat(slotCapacity, equalTo(1024));

        verify(delegate).slotCapacity();
    }

    @Test
    public void shouldDelegateAcquiredSlots()
    {
        when(delegate.acquiredSlots()).thenReturn(32);

        int acquiredSlots = bufferPool.acquiredSlots();

        assertThat(acquiredSlots, equalTo(32));

        verify(delegate).acquiredSlots();
    }

    @Test
    public void shouldDelegateBuffer()
    {
        MutableDirectBuffer expected = mock(MutableDirectBuffer.class);

        when(delegate.buffer(7)).thenReturn(expected);

        MutableDirectBuffer actual = bufferPool.buffer(7);

        assertThat(actual, sameInstance(expected));

        verify(delegate).buffer(7);
        verifyNoMoreInteractions(expected);
    }

    @Test
    public void shouldDelegateBufferAtOffset()
    {
        MutableDirectBuffer expected = mock(MutableDirectBuffer.class);

        when(delegate.buffer(7, 128)).thenReturn(expected);

        MutableDirectBuffer actual = bufferPool.buffer(7, 128);

        assertThat(actual, sameInstance(expected));

        verify(delegate).buffer(7, 128);
        verifyNoMoreInteractions(expected);
    }

    @Test
    public void shouldDelegateByteBuffer()
    {
        ByteBuffer expected = mock(ByteBuffer.class);

        when(delegate.byteBuffer(7)).thenReturn(expected);

        ByteBuffer actual = bufferPool.byteBuffer(7);

        assertThat(actual, sameInstance(expected));

        verify(delegate).byteBuffer(7);
        verifyNoMoreInteractions(expected);
    }

    @Test
    public void shouldDelegateAcquire()
    {
        when(delegate.acquire(1L)).thenReturn(0xcafe);

        int slot = bufferPool.acquire(1L);

        assertThat(slot, equalTo(0xcafe));

        verify(delegate).acquire(1L);
        verify(acquires).getAsLong();
    }

    @Test
    public void shouldDelegateAcquireNoSlot()
    {
        when(delegate.acquire(1L)).thenReturn(NO_SLOT);

        int slot = bufferPool.acquire(1L);

        assertThat(slot, equalTo(NO_SLOT));

        verify(delegate).acquire(1L);
    }

    @Test
    public void shouldDelegateRelease()
    {
        bufferPool.release(0xcafe);

        verify(delegate).release(0xcafe);
        verify(releases).getAsLong();
    }

    @Test
    public void shouldDelegateReleaseNoSlot()
    {
        bufferPool.release(NO_SLOT);

        verify(delegate).release(NO_SLOT);
    }

    @Test
    public void shouldDelegateDuplicate()
    {
        BufferPool actual = bufferPool.duplicate();

        assertThat(actual, instanceOf(CountingBufferPool.class));
        assertThat(actual, not(sameInstance(bufferPool)));

        verify(delegate).duplicate();
    }

    @After
    public void verifyMocks()
    {
        verifyNoMoreInteractions(delegate);
        verifyNoMoreInteractions(acquires);
        verifyNoMoreInteractions(releases);
    }
}
