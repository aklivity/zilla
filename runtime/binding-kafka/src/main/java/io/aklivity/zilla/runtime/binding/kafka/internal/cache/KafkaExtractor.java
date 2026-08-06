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

import java.util.Set;

import org.agrona.collections.Object2LongHashMap;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.ExpandableArrayBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.engine.model.FieldEvent;
import io.aklivity.zilla.runtime.engine.model.FieldStatus;
import io.aklivity.zilla.runtime.engine.model.ModelController;
import io.aklivity.zilla.runtime.engine.model.ModelSink;
import io.aklivity.zilla.runtime.engine.model.ModelSource;
import io.aklivity.zilla.runtime.engine.model.ModelTransform;
import io.aklivity.zilla.runtime.engine.model.function.ValueConsumer;

// An observing per-field stage: every field of a decoded value passes through unchanged, while the value of
// each configured extraction path is copied aside for KafkaCachePartition to replay into the cache entry's
// key or trailing headers. The accumulation is read back through extractedLength / extracted, outside the
// ModelTransform contract, and outlives the pipeline that produced it until the owner resets.
final class KafkaExtractor implements ModelTransform
{
    private static final long MISSING_REGION = -1L;

    private final Set<String> paths;
    private final MutableDirectBufferEx captures;
    private final Object2LongHashMap<String> regionByPath;

    private int capturesOffset;

    KafkaExtractor(
        Set<String> paths)
    {
        this.paths = paths;
        this.captures = new ExpandableArrayBufferEx();
        this.regionByPath = new Object2LongHashMap<>(MISSING_REGION);
    }

    @Override
    public FieldStatus transform(
        ModelController control,
        ModelSource source,
        FieldEvent event,
        ModelSink sink)
    {
        if (event == FieldEvent.FIELD)
        {
            capture(source);
        }
        return sink.transform(control, source, event);
    }

    @Override
    public boolean identity()
    {
        return true;
    }

    int extractedLength(
        String path)
    {
        final long region = regionByPath.getValue(path);
        return region == MISSING_REGION ? 0 : (int)(region & 0xFFFF_FFFFL);
    }

    void extracted(
        String path,
        ValueConsumer consumer)
    {
        final long region = regionByPath.getValue(path);
        if (region != MISSING_REGION)
        {
            final int offset = (int)(region >>> Integer.SIZE);
            final int length = (int)(region & 0xFFFF_FFFFL);
            consumer.accept(captures, offset, length);
        }
    }

    @Override
    public void reset()
    {
        regionByPath.clear();
        capturesOffset = 0;
    }

    private void capture(
        ModelSource source)
    {
        final String path = source.getPath();
        // the model surfaces every top-level field; capture only the configured extraction paths
        if (paths.contains(path))
        {
            final DirectBufferEx value = source.getValue();
            final int length = value.capacity();
            final int offset = capturesOffset;
            captures.putBytes(offset, value, 0, length);
            capturesOffset += length;
            regionByPath.put(path, (long) offset << Integer.SIZE | (length & 0xFFFF_FFFFL));
        }
    }
}
