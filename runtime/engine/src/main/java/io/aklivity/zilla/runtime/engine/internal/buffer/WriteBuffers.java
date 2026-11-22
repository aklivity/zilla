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

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

public final class WriteBuffers
{
    private final int capacity;
    private final Map<String, MutableDirectBufferEx> buffersByName;

    public WriteBuffers(
        int capacity)
    {
        this.capacity = capacity;
        this.buffersByName = new HashMap<>();
    }

    public MutableDirectBufferEx supply(
        String name)
    {
        return buffersByName.computeIfAbsent(name, this::newBuffer);
    }

    private MutableDirectBufferEx newBuffer(
        String name)
    {
        return new UnsafeBufferEx(ByteBuffer.allocateDirect(capacity)).asNative();
    }
}
