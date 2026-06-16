/*
 * Copyright 2021-2024 Aklivity Inc
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
package io.aklivity.zilla.runtime.model.avro.internal;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.model.avro.internal.types.OctetsFW;

public final class AvroField
{
    private static final int INITIAL_CAPACITY = 24;

    public final OctetsFW value;

    private MutableDirectBuffer buffer;

    public AvroField()
    {
        this.value = new OctetsFW();
        this.buffer = new UnsafeBuffer(new byte[INITIAL_CAPACITY]);
    }

    public MutableDirectBuffer buffer(
        int capacity)
    {
        if (capacity > buffer.capacity())
        {
            buffer = new UnsafeBuffer(new byte[Math.max(buffer.capacity() * 2, capacity)]);
        }
        return buffer;
    }
}
