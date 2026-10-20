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
package io.aklivity.zilla.runtime.model.classify.internal;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.util.List;

import io.aklivity.zilla.config.model.classify.ClassifyModelConfig;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.classifier.Detector;
import io.aklivity.zilla.runtime.engine.model.ModelCache;
import io.aklivity.zilla.runtime.engine.model.ModelEnvelope;
import io.aklivity.zilla.runtime.engine.model.ModelHandler;
import io.aklivity.zilla.runtime.engine.model.ModelPipeline;
import io.aklivity.zilla.runtime.engine.model.ModelTransform;

final class ClassifyModelHandlerImpl implements ModelHandler
{
    private static final String ENVELOPE_DIRECTION = "direction";
    private static final byte[] DIRECTION_RESPONSE = "response".getBytes(UTF_8);
    private static final Runnable NOOP = () ->
    {
    };

    private final List<Detector> detectors;
    private final int maxLength;
    private final int overlap;

    ClassifyModelHandlerImpl(
        EngineContext context,
        ClassifyModelConfig config,
        int maxLength,
        int overlap)
    {
        this.detectors = config.reject.stream()
            .map(entry -> context.supplyClassifier(entry.id).initDetector(entry.labels))
            .toList();
        this.maxLength = maxLength;
        this.overlap = overlap;
    }

    @Override
    public ModelPipeline supplyDecoder(
        ModelEnvelope envelope,
        ModelTransform transform,
        ModelCache cache)
    {
        return supplyDecoder(envelope, transform, NOOP);
    }

    @Override
    public ModelPipeline supplyEncoder(
        ModelEnvelope envelope,
        ModelTransform transform)
    {
        return supplyEncoder(envelope, transform, NOOP);
    }

    @Override
    public ModelPipeline supplyDecoder(
        ModelEnvelope envelope,
        ModelTransform transform,
        Runnable resumed)
    {
        return supplyPipeline(envelope, resumed);
    }

    @Override
    public ModelPipeline supplyEncoder(
        ModelEnvelope envelope,
        ModelTransform transform,
        Runnable resumed)
    {
        return supplyPipeline(envelope, resumed);
    }

    private ModelPipeline supplyPipeline(
        ModelEnvelope envelope,
        Runnable resumed)
    {
        return new ClassifyModelPipeline(detectors, maxLength, responding(envelope) ? Math.min(overlap, maxLength) : maxLength,
            resumed);
    }

    private static boolean responding(
        ModelEnvelope envelope)
    {
        final DirectBufferEx direction = envelope.count(ENVELOPE_DIRECTION) > 0 ? envelope.get(ENVELOPE_DIRECTION, 0) : null;

        boolean responding = direction != null && direction.capacity() == DIRECTION_RESPONSE.length;

        for (int index = 0; responding && index < DIRECTION_RESPONSE.length; index++)
        {
            responding = direction.getByte(index) == DIRECTION_RESPONSE[index];
        }

        return responding;
    }
}
