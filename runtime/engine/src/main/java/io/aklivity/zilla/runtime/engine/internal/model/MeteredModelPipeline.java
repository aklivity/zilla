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
package io.aklivity.zilla.runtime.engine.internal.model;

import java.util.function.LongSupplier;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.engine.internal.model.ModelMetrics.Recorders;
import io.aklivity.zilla.runtime.engine.model.ModelPipeline;
import io.aklivity.zilla.runtime.engine.model.ModelPipelineResult;
import io.aklivity.zilla.runtime.engine.model.ModelRejection;
import io.aklivity.zilla.runtime.engine.model.ModelStatus;

final class MeteredModelPipeline implements ModelPipeline
{
    private static final long NOT_STARTED = Long.MIN_VALUE;

    private final ModelPipeline pipeline;
    private final MeteredModelTransform transform;
    private final ModelMetrics metrics;
    private final LongSupplier clock;

    private long bindingId;
    private Recorders recorders;
    private long startedAt = NOT_STARTED;

    MeteredModelPipeline(
        ModelPipeline pipeline,
        MeteredModelTransform transform,
        ModelMetrics metrics,
        LongSupplier clock)
    {
        this.pipeline = pipeline;
        this.transform = transform;
        this.metrics = metrics;
        this.clock = clock;
    }

    @Override
    public ModelPipelineResult transform(
        long traceId,
        long bindingId,
        long authorization,
        int flags,
        DirectBufferEx src,
        int srcIndex,
        int srcLimit,
        MutableDirectBufferEx dst,
        int dstIndex,
        int dstLimit)
    {
        if (recorders == null || this.bindingId != bindingId)
        {
            this.bindingId = bindingId;
            this.recorders = metrics.supply(bindingId);
        }

        if (startedAt == NOT_STARTED)
        {
            startedAt = clock.getAsLong();
        }

        transform.recorders(recorders);

        final ModelPipelineResult result = pipeline.transform(traceId, bindingId, authorization, flags,
            src, srcIndex, srcLimit, dst, dstIndex, dstLimit);

        final ModelStatus status = result.status();

        if (status == ModelStatus.REJECTED)
        {
            onRejected(authorization, result.rejection());
        }

        if (status == ModelStatus.COMPLETE || status == ModelStatus.REJECTED)
        {
            recorders.duration.record(authorization, clock.getAsLong() - startedAt);
            startedAt = NOT_STARTED;
        }

        return result;
    }

    @Override
    public boolean identity()
    {
        return pipeline.identity();
    }

    @Override
    public boolean deterministic()
    {
        return pipeline.deterministic();
    }

    @Override
    public int padding(
        DirectBufferEx data,
        int index,
        int length)
    {
        return pipeline.padding(data, index, length);
    }

    @Override
    public void reset()
    {
        startedAt = NOT_STARTED;
        pipeline.reset();
    }

    private void onRejected(
        long authorization,
        ModelRejection rejection)
    {
        if (rejection == ModelRejection.INVALID)
        {
            recorders.invalid.record(authorization, 1L);
        }
        else if (rejection == ModelRejection.WITHHELD)
        {
            recorders.withheld.record(authorization, 1L);
        }
    }
}
