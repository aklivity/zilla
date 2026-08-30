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
package io.aklivity.zilla.runtime.engine.test.internal.model;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.emptyList;

import java.util.List;

import io.aklivity.zilla.config.engine.ValidateMode;
import io.aklivity.zilla.config.engine.test.internal.model.config.TestModelConfig;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.model.ModelCache;
import io.aklivity.zilla.runtime.engine.model.ModelEnvelope;
import io.aklivity.zilla.runtime.engine.model.ModelHandler;
import io.aklivity.zilla.runtime.engine.model.ModelPipeline;
import io.aklivity.zilla.runtime.engine.model.ModelTransform;

public class TestModelHandler implements ModelHandler
{
    private static final Runnable NOOP = () ->
    {
    };

    private final int length;
    private final int transformLength;
    private final List<String> fields;
    private final boolean decodeLenient;
    private final boolean encodeLenient;
    private final List<Long> transformAuthorizations;
    private final List<String> reject;
    private final boolean suspend;
    private final EngineContext context;
    private final List<Long> discloseAuthorized;
    private final DirectBufferEx discloseRedacted;
    private final String envelopeDiscloseName;

    private int transformAuthorizationIndex;

    public TestModelHandler(
        TestModelConfig config)
    {
        this(config, null);
    }

    public TestModelHandler(
        TestModelConfig config,
        EngineContext context)
    {
        this.length = config.length;
        this.transformLength = config.transformLength;
        this.fields = config.fields != null ? config.fields : emptyList();
        this.decodeLenient = config.validate.decode == ValidateMode.LENIENT;
        this.encodeLenient = config.validate.encode == ValidateMode.LENIENT;
        this.transformAuthorizations = config.transformAuthorizations;
        this.reject = config.reject;
        this.suspend = config.suspend;
        this.context = context;
        this.discloseAuthorized = config.discloseAuthorized;
        this.discloseRedacted = config.discloseRedacted != null
            ? new UnsafeBufferEx(config.discloseRedacted.getBytes(UTF_8))
            : null;
        this.envelopeDiscloseName = config.envelopeDiscloseName;
    }

    @Override
    public ModelPipeline supplyDecoder(
        ModelEnvelope envelope,
        ModelTransform transform,
        ModelCache cache)
    {
        return supplyDecoder(envelope, transform, NOOP, cache);
    }

    @Override
    public ModelPipeline supplyEncoder(
        ModelEnvelope envelope,
        ModelTransform transform)
    {
        return new TestModelPipeline(length, transformLength, fields, encodeLenient, envelope, transform, this,
            null, false, NOOP, null, null, null, null);
    }

    @Override
    public ModelPipeline supplyDecoder(
        ModelEnvelope envelope,
        ModelTransform transform,
        Runnable resumed)
    {
        return supplyDecoder(envelope, transform, resumed, ModelCache.NONE);
    }

    @Override
    public ModelPipeline supplyEncoder(
        ModelEnvelope envelope,
        ModelTransform transform,
        Runnable resumed)
    {
        return new TestModelPipeline(length, transformLength, fields, encodeLenient, envelope, transform, this,
            reject, suspend, resumed, context, null, null, null);
    }

    private ModelPipeline supplyDecoder(
        ModelEnvelope envelope,
        ModelTransform transform,
        Runnable resumed,
        ModelCache cache)
    {
        return cache == ModelCache.WRITE
            ? new TestModelPipeline(length, transformLength, fields, decodeLenient, envelope, transform, this,
                null, false, resumed, context, null, null, null)
            : new TestModelPipeline(length, transformLength, fields, decodeLenient, envelope, transform, this,
                reject, suspend, resumed, context, discloseAuthorized, discloseRedacted, envelopeDiscloseName);
    }

    Long nextTransformAuthorization()
    {
        return transformAuthorizations != null && transformAuthorizationIndex < transformAuthorizations.size()
            ? transformAuthorizations.get(transformAuthorizationIndex++)
            : null;
    }
}
