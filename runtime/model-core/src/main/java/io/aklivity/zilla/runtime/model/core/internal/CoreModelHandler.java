/*
 * Copyright 2021-2024 Aklivity Inc
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
package io.aklivity.zilla.runtime.model.core.internal;

import java.util.function.Supplier;

import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.model.ModelHandler;
import io.aklivity.zilla.runtime.engine.model.ModelPipeline;
import io.aklivity.zilla.runtime.engine.model.ModelVisitor;

// Per-worker factory for a core model. The decode and encode conversions are an identity copy gated by
// validation, so one handler serves both directions; it owns the config-derived event reporter and
// vends a fresh per-stream CoreModelPipeline with its own validation state on each supplyDecoder/supplyEncoder.
final class CoreModelHandler implements ModelHandler
{
    private final CoreModelEventContext event;
    private final String model;
    private final Supplier<CoreModelValidator> supplier;

    CoreModelHandler(
        EngineContext context,
        String model,
        Supplier<CoreModelValidator> supplier)
    {
        this.event = new CoreModelEventContext(context);
        this.model = model;
        this.supplier = supplier;
    }

    @Override
    public ModelPipeline supplyDecoder(
        ModelVisitor visitor)
    {
        return new CoreModelPipeline(this, supplier.get());
    }

    @Override
    public ModelPipeline supplyEncoder(
        ModelVisitor visitor)
    {
        return new CoreModelPipeline(this, supplier.get());
    }

    void validationFailure(
        long traceId,
        long bindingId)
    {
        event.validationFailure(traceId, bindingId, model);
    }
}
