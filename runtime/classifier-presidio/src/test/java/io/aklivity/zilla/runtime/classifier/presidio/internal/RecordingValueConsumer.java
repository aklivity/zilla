/*
 * Copyright 2021-2026 Aklivity Inc
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
package io.aklivity.zilla.runtime.classifier.presidio.internal;


import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import org.agrona.DirectBuffer;

import io.aklivity.zilla.runtime.engine.classifier.ValueConsumerEx;

final class RecordingValueConsumer implements ValueConsumerEx
{
    private final ByteArrayOutputStream output;
    private final List<String> spans;

    private int capacity;

    RecordingValueConsumer()
    {
        this(Integer.MAX_VALUE);
    }

    RecordingValueConsumer(
        int capacity)
    {
        this.output = new ByteArrayOutputStream();
        this.spans = new ArrayList<>();
        this.capacity = capacity;
    }

    @Override
    public int write(
        DirectBuffer buffer,
        int index,
        int length)
    {
        int accepted = Math.min(length, capacity);
        append(buffer, index, accepted);
        return accepted;
    }

    @Override
    public boolean writeSpan(
        String label,
        DirectBuffer buffer,
        int index,
        int length)
    {
        boolean accepted = length <= capacity;

        if (accepted)
        {
            append(buffer, index, length);
            spans.add(label);
        }

        return accepted;
    }

    void drain(
        int additional)
    {
        capacity += additional;
    }

    String text()
    {
        return output.toString(UTF_8);
    }

    int size()
    {
        return output.size();
    }

    List<String> spans()
    {
        return spans;
    }

    private void append(
        DirectBuffer buffer,
        int index,
        int length)
    {
        byte[] bytes = new byte[length];
        buffer.getBytes(index, bytes);
        output.writeBytes(bytes);
        capacity -= length;
    }
}
