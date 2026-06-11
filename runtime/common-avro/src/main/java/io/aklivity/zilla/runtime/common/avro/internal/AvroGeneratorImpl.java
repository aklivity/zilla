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
package io.aklivity.zilla.runtime.common.avro.internal;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

import io.aklivity.zilla.runtime.common.avro.AvroEvent;
import io.aklivity.zilla.runtime.common.avro.AvroGeneratorEx;
import io.aklivity.zilla.runtime.common.avro.AvroSource;

public final class AvroGeneratorImpl implements AvroGeneratorEx
{
    private final AvroEncoder encoder;

    public AvroGeneratorImpl(
        AvroNode root,
        MutableDirectBuffer buffer,
        int offset)
    {
        this.encoder = new AvroEncoder(root, buffer, offset);
        this.encoder.reset();
    }

    @Override
    public void wrap(
        MutableDirectBuffer buffer,
        int offset)
    {
        encoder.wrap(buffer, offset);
    }

    @Override
    public void encode(
        AvroEvent event,
        AvroSource source)
    {
        encoder.feed(event, source);
    }

    @Override
    public void writeSegment(
        DirectBuffer buffer,
        int offset,
        int length)
    {
        encoder.writeRaw(buffer, offset, length);
    }

    @Override
    public int length()
    {
        return encoder.length();
    }
}
