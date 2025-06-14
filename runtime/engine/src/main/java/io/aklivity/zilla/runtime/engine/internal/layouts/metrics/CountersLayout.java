/*
 * Copyright 2021-2024 Aklivity Inc.
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
package io.aklivity.zilla.runtime.engine.internal.layouts.metrics;

import java.nio.file.Path;
import java.util.function.LongConsumer;

import org.agrona.concurrent.AtomicBuffer;

public final class CountersLayout extends ScalarsLayout
{
    private CountersLayout(
        AtomicBuffer buffer)
    {
        super(buffer);
    }

    @Override
    public LongConsumer supplyWriter(
        long bindingId,
        long metricId)
    {
        int index = findOrSetPosition(bindingId, metricId);
        return delta -> buffer.getAndAddLong(index + VALUE_OFFSET, delta);
    }

    public static final class Builder extends ScalarsLayout.Builder
    {
        @Override
        public Builder capacity(
            long capacity)
        {
            super.capacity(capacity);
            return this;
        }

        @Override
        public Builder path(
            Path path)
        {
            super.path(path);
            return this;
        }

        @Override
        public Builder readonly(
            boolean readonly)
        {
            super.readonly(readonly);
            return this;
        }

        @Override
        public Builder label(
            String label)
        {
            super.label(label);
            return this;
        }

        public CountersLayout build()
        {
            return build(CountersLayout::new);
        }
    }
}
