/*
 * Copyright 2021-2023 Aklivity Inc.
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
package io.aklivity.zilla.runtime.engine.metrics.layout;

import static io.aklivity.zilla.runtime.engine.internal.layouts.Layout.Mode.READ_ONLY;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.function.LongSupplier;

import io.aklivity.zilla.runtime.engine.internal.layouts.metrics.HistogramsLayout;

public final class HistogramsLayoutRO extends MetricsLayoutRO
{
    public static final int BUCKETS = 63;
    public static final Map<Integer, Long> BUCKET_LIMITS = generateBucketLimits();

    private static final int ARRAY_SIZE = BUCKETS * FIELD_SIZE;
    private static final int RECORD_SIZE = 2 * FIELD_SIZE + ARRAY_SIZE;

    private final HistogramsLayout layout;

    private HistogramsLayoutRO(
        HistogramsLayout layout)
    {
        super(null); // we don't use the buffer here
        this.layout = layout;
    }

    @Override
    public void close()
    {
        layout.close();
    }

    @Override
    public long[][] getIds()
    {
        return layout.getIds();
    }

    @Override
    public LongSupplier supplyReader(
        long bindingId,
        long metricId)
    {
        throw new RuntimeException("not implemented");
    }

    @Override
    public LongSupplier[] supplyReaders(
        long bindingId,
        long metricId)
    {
        return layout.supplyReaders(bindingId, metricId);
    }

    @Override
    protected int recordSize()
    {
        return RECORD_SIZE;
    }

    // exclusive upper limits of each bucket
    private static Map<Integer, Long> generateBucketLimits()
    {
        Map<Integer, Long> limits = new HashMap<>();
        for (int i = 0; i < BUCKETS; i++)
        {
            limits.put(i, 1L << (i + 1));
        }
        return limits;
    }

    public static final class Builder extends LayoutRO.Builder<HistogramsLayoutRO>
    {
        private Path path;

        public Builder path(
            Path path)
        {
            this.path = path;
            return this;
        }

        @Override
        public HistogramsLayoutRO build()
        {
            HistogramsLayout layout = new HistogramsLayout.Builder().path(path).mode(READ_ONLY).build();
            return new HistogramsLayoutRO(layout);
        }
    }
}
