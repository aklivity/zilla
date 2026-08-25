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
import io.aklivity.zilla.runtime.engine.model.ModelEnvelope;
import io.aklivity.zilla.runtime.engine.model.ModelHandler;
import io.aklivity.zilla.runtime.engine.model.ModelPipeline;
import io.aklivity.zilla.runtime.engine.model.ModelTransform;

public class TestModelHandler implements ModelHandler
{
    private final int length;
    private final int transformLength;
    private final List<String> fields;
    private final boolean decodeLenient;
    private final boolean encodeLenient;
    private final List<Long> transformAuthorizations;
    private final List<Long> discloseAuthorized;
    private final DirectBufferEx discloseRedacted;

    private int transformAuthorizationIndex;

    public TestModelHandler(
        TestModelConfig config)
    {
        this.length = config.length;
        this.transformLength = config.transformLength;
        this.fields = config.fields != null ? config.fields : emptyList();
        this.decodeLenient = config.validate.decode == ValidateMode.LENIENT;
        this.encodeLenient = config.validate.encode == ValidateMode.LENIENT;
        this.transformAuthorizations = config.transformAuthorizations;
        this.discloseAuthorized = config.discloseAuthorized;
        this.discloseRedacted = config.discloseRedacted != null
            ? new UnsafeBufferEx(config.discloseRedacted.getBytes(UTF_8))
            : null;
    }

    @Override
    public ModelPipeline supplyCacheable(
        ModelEnvelope envelope,
        ModelTransform transform)
    {
        return new TestModelPipeline(length, transformLength, fields, decodeLenient, envelope, transform, this,
            null, null);
    }

    @Override
    public ModelPipeline supplyDecoder(
        ModelEnvelope envelope,
        ModelTransform transform)
    {
        return new TestModelPipeline(length, transformLength, fields, decodeLenient, envelope, transform, this,
            discloseAuthorized, discloseRedacted);
    }

    @Override
    public ModelPipeline supplyEncoder(
        ModelEnvelope envelope,
        ModelTransform transform)
    {
        return new TestModelPipeline(length, transformLength, fields, encodeLenient, envelope, transform, this,
            null, null);
    }

    Long nextTransformAuthorization()
    {
        return transformAuthorizations != null && transformAuthorizationIndex < transformAuthorizations.size()
            ? transformAuthorizations.get(transformAuthorizationIndex++)
            : null;
    }
}
