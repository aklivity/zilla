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
package io.aklivity.zilla.runtime.common.protobuf.internal;

import org.agrona.MutableDirectBuffer;

import io.aklivity.zilla.runtime.common.protobuf.ProtobufGenerator;

/**
 * Buffer-backed {@link ProtobufGenerator} over a single root {@link ProtobufWriter}, which the wire
 * sink borrows for the top-level message body.
 */
public final class ProtobufGeneratorImpl implements ProtobufGenerator
{
    private final ProtobufWriter writer;

    public ProtobufGeneratorImpl()
    {
        this.writer = new ProtobufWriter();
    }

    @Override
    public ProtobufGenerator wrap(
        MutableDirectBuffer buffer,
        int offset)
    {
        writer.wrap(buffer, offset);
        return this;
    }

    @Override
    public int length()
    {
        return writer.length();
    }

    ProtobufWriter writer()
    {
        return writer;
    }
}
