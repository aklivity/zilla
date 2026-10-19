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

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.ExpandableArrayBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.model.ModelEnvelope;

// A write-collecting ModelEnvelope for the key's own WRITE-context model: a composed transform
// narrowing a structured key down to one field calls set(":key", value) to override what gets
// persisted as the key, in place of KafkaPipeline's old SWITCH_KEY lane. ":key" is a reserved,
// colon-prefixed pseudo-name (HTTP/2-pseudo-header style) so it can never collide with a real,
// wire-materialized header of the same name (e.g. "zilla:key", the vault key identifier from the
// field-encryption design). There is only ever one key, so unlike the header/trailer envelopes
// there is no repeated-entry bookkeeping -- just a single reused buffer, matching the bounded,
// reused-not-growable-per-message shape already used for key extraction on this path.
public final class KafkaCacheKeyEnvelope implements ModelEnvelope
{
    public static final String NAME = ":key";

    private final MutableDirectBufferEx buffer = new ExpandableArrayBufferEx();
    private final DirectBufferEx view = new UnsafeBufferEx(new byte[0]);

    private int length = -1;

    public void reset()
    {
        length = -1;
    }

    public boolean isEmpty()
    {
        return length < 0;
    }

    @Override
    public int count(
        String name)
    {
        return NAME.equals(name) && length >= 0 ? 1 : 0;
    }

    @Override
    public DirectBufferEx get(
        String name,
        int index)
    {
        DirectBufferEx value = null;
        if (NAME.equals(name) && index == 0 && length >= 0)
        {
            view.wrap(buffer, 0, length);
            value = view;
        }
        return value;
    }

    @Override
    public void set(
        String name,
        DirectBufferEx value)
    {
        if (NAME.equals(name))
        {
            length = value.capacity();
            buffer.putBytes(0, value, 0, length);
        }
    }
}
