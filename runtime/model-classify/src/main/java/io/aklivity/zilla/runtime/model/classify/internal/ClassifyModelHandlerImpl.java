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

import java.util.List;

import io.aklivity.zilla.config.model.classify.ClassifyModelConfig;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.classifier.Detector;
import io.aklivity.zilla.runtime.engine.model.ModelCache;
import io.aklivity.zilla.runtime.engine.model.ModelEnvelope;
import io.aklivity.zilla.runtime.engine.model.ModelHandler;
import io.aklivity.zilla.runtime.engine.model.ModelPipeline;
import io.aklivity.zilla.runtime.engine.model.ModelTransform;

final class ClassifyModelHandlerImpl implements ModelHandler
{
    private static final Runnable NOOP = () ->
    {
    };

    private final List<Detector> detectors;
    private final int maxLength;

    ClassifyModelHandlerImpl(
        EngineContext context,
        ClassifyModelConfig config,
        int maxLength)
    {
        this.detectors = config.reject.stream()
            .map(entry -> context.supplyClassifier(entry.id).initDetector(entry.labels))
            .toList();
        this.maxLength = maxLength;
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
        return new ClassifyModelPipeline(detectors, maxLength, resumed);
    }

    @Override
    public ModelPipeline supplyEncoder(
        ModelEnvelope envelope,
        ModelTransform transform,
        Runnable resumed)
    {
        return new ClassifyModelPipeline(detectors, maxLength, resumed);
    }
}
