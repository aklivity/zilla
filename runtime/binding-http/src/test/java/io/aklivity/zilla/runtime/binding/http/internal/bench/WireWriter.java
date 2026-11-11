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
package io.aklivity.zilla.runtime.binding.http.internal.bench;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

/**
 * Buffers bytes for a stream and sends them as DATA frames only as far as the stream's credit allows, so a
 * writer can be called from a frame callback without ever overrunning the receiver's window.
 */
final class WireWriter
{
    interface Sink
    {
        int credit();

        void data(
            DirectBufferEx buffer,
            int offset,
            int length);
    }

    private static final int MAX_FRAGMENT_LENGTH = 16 * 1024;

    private final byte[] storage;
    private final UnsafeBufferEx ring;
    private final int mask;

    private Sink sink;
    private long head;
    private long tail;

    WireWriter(
        int capacity)
    {
        this.storage = new byte[capacity];
        this.ring = new UnsafeBufferEx(storage);
        this.mask = capacity - 1;
    }

    void sink(
        Sink sink)
    {
        this.sink = sink;
    }

    int space()
    {
        return storage.length - (int) (tail - head);
    }

    boolean drained()
    {
        return head == tail;
    }

    void write(
        DirectBufferEx source)
    {
        write(source, 0, source.capacity());
    }

    void write(
        DirectBufferEx source,
        int offset,
        int length)
    {
        if (length > space())
        {
            throw new IllegalStateException("wire writer full");
        }

        int copied = 0;
        while (copied < length)
        {
            final int index = (int) (tail & mask);
            final int fragment = Math.min(length - copied, storage.length - index);
            ring.putBytes(index, source, offset + copied, fragment);
            tail += fragment;
            copied += fragment;
        }

        drain();
    }

    void drain()
    {
        while (head != tail)
        {
            final int credit = sink.credit();
            final int index = (int) (head & mask);
            final int contiguous = Math.min((int) (tail - head), storage.length - index);
            final int length = Math.min(contiguous, Math.min(credit, MAX_FRAGMENT_LENGTH));

            if (length <= 0)
            {
                break;
            }

            sink.data(ring, index, length);
            head += length;
        }
    }
}
