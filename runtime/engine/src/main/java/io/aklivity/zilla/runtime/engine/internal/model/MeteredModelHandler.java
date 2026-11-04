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

import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.model.ModelCache;
import io.aklivity.zilla.runtime.engine.model.ModelEnvelope;
import io.aklivity.zilla.runtime.engine.model.ModelHandler;
import io.aklivity.zilla.runtime.engine.model.ModelPipeline;
import io.aklivity.zilla.runtime.engine.model.ModelTransform;

public final class MeteredModelHandler implements ModelHandler
{
    private final ModelHandler handler;
    private final ModelMetrics metrics;
    private final LongSupplier clock;

    public MeteredModelHandler(
        ModelHandler handler,
        EngineContext context)
    {
        this(handler, context, System::nanoTime);
    }

    MeteredModelHandler(
        ModelHandler handler,
        EngineContext context,
        LongSupplier clock)
    {
        this.handler = handler;
        this.metrics = new ModelMetrics(context);
        this.clock = clock;
    }

    @Override
    public ModelPipeline supplyDecoder(
        ModelEnvelope envelope,
        ModelTransform transform,
        ModelCache cache)
    {
        final MeteredModelTransform metered = meter(transform);
        return new MeteredModelPipeline(
            handler.supplyDecoder(envelope, supplied(transform, metered), cache),
            metered, metrics, clock);
    }

    @Override
    public ModelPipeline supplyEncoder(
        ModelEnvelope envelope,
        ModelTransform transform)
    {
        final MeteredModelTransform metered = meter(transform);
        return new MeteredModelPipeline(
            handler.supplyEncoder(envelope, supplied(transform, metered)),
            metered, metrics, clock);
    }

    @Override
    public ModelPipeline supplyDecoder(
        ModelEnvelope envelope,
        ModelTransform transform,
        Runnable resumed)
    {
        final MeteredModelTransform metered = meter(transform);
        return new MeteredModelPipeline(
            handler.supplyDecoder(envelope, supplied(transform, metered), resumed),
            metered, metrics, clock);
    }

    @Override
    public ModelPipeline supplyEncoder(
        ModelEnvelope envelope,
        ModelTransform transform,
        Runnable resumed)
    {
        final MeteredModelTransform metered = meter(transform);
        return new MeteredModelPipeline(
            handler.supplyEncoder(envelope, supplied(transform, metered), resumed),
            metered, metrics, clock);
    }

    private static MeteredModelTransform meter(
        ModelTransform transform)
    {
        return transform != ModelTransform.NONE ? new MeteredModelTransform(transform) : null;
    }

    private static ModelTransform supplied(
        ModelTransform transform,
        MeteredModelTransform metered)
    {
        return metered != null ? metered : transform;
    }
}
