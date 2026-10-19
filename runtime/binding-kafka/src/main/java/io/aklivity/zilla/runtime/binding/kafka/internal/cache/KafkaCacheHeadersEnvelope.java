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
package io.aklivity.zilla.runtime.binding.kafka.internal.cache;

import io.aklivity.zilla.runtime.binding.kafka.internal.types.Array32FW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.ArrayFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaHeaderFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.model.ModelEnvelope;

// A read-only ModelEnvelope backed by a fetched record's real headers, for the per-consumer
// decode path: headers() then trailers(), concatenated in that order to match what the consumer
// sees on the wire (KafkaCacheClientFetchFactory writes them out in that same order). There is
// nowhere on this path for a write to go, so set() discards.
public final class KafkaCacheHeadersEnvelope implements ModelEnvelope
{
    private static final Array32FW<KafkaHeaderFW> EMPTY =
        new Array32FW.Builder<>(new KafkaHeaderFW.Builder(), new KafkaHeaderFW())
            .wrap(new UnsafeBufferEx(new byte[8]), 0, 8)
            .build();

    private final MutableDirectBufferEx nameBuffer = new UnsafeBufferEx(new byte[256]);
    private final MutableDirectBufferEx valueBuffer = new UnsafeBufferEx(new byte[256]);
    private final DirectBufferEx nameView = new UnsafeBufferEx(new byte[0]);
    private final DirectBufferEx valueView = new UnsafeBufferEx(new byte[0]);

    private ArrayFW<KafkaHeaderFW> headers = EMPTY;
    private ArrayFW<KafkaHeaderFW> trailers = EMPTY;

    private int nameLength;
    private int matchTarget;
    private int matchSeen;

    public void wrap(
        ArrayFW<KafkaHeaderFW> headers,
        ArrayFW<KafkaHeaderFW> trailers)
    {
        this.headers = headers;
        this.trailers = trailers;
    }

    @Override
    public int count(
        String name)
    {
        nameLength = nameBuffer.putStringWithoutLengthUtf8(0, name);
        nameView.wrap(nameBuffer, 0, nameLength);
        matchTarget = -1;
        matchSeen = 0;
        headers.forEach(this::countIfMatches);
        trailers.forEach(this::countIfMatches);
        return matchSeen;
    }

    @Override
    public DirectBufferEx get(
        String name,
        int index)
    {
        nameLength = nameBuffer.putStringWithoutLengthUtf8(0, name);
        nameView.wrap(nameBuffer, 0, nameLength);
        matchTarget = index;
        matchSeen = 0;

        KafkaHeaderFW match = headers.matchFirst(this::matchesTarget);
        if (match == null)
        {
            match = trailers.matchFirst(this::matchesTarget);
        }

        DirectBufferEx value = null;
        if (match != null)
        {
            final OctetsFW matchValue = match.value();
            final int length = matchValue.sizeof();
            valueBuffer.putBytes(0, matchValue.buffer(), matchValue.offset(), length);
            valueView.wrap(valueBuffer, 0, length);
            value = valueView;
        }
        return value;
    }

    @Override
    public void set(
        String name,
        DirectBufferEx value)
    {
    }

    private void countIfMatches(
        KafkaHeaderFW header)
    {
        if (nameEquals(header))
        {
            matchSeen++;
        }
    }

    private boolean matchesTarget(
        KafkaHeaderFW header)
    {
        boolean matches = false;
        if (nameEquals(header))
        {
            matches = matchSeen == matchTarget;
            matchSeen++;
        }
        return matches;
    }

    private boolean nameEquals(
        KafkaHeaderFW header)
    {
        final OctetsFW headerName = header.name();
        return headerName.sizeof() == nameLength && headerName.value().equals(nameView);
    }
}
