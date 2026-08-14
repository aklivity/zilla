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
package io.aklivity.zilla.runtime.model.core.internal;

import java.util.List;
import java.util.function.Supplier;

import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.model.ModelHandler;
import io.aklivity.zilla.runtime.engine.model.ModelPipeline;
import io.aklivity.zilla.runtime.engine.model.ModelTransform;

// Per-worker factory for a bytes/string model with at least one installed extension. The composed
// extension chain is folded once here, at handler construction, and reused for every stream this handler
// serves; supplyDecoder vends a CoreExtModelPipeline that applies it. Extensions are decode-only: encode is
// the write path into the broker and must keep the source of truth intact, so supplyEncoder delegates to a
// plain CoreModelHandler exactly as if no extension were installed.
final class CoreExtModelHandler implements ModelHandler
{
    private final CoreModelHandler plain;
    private final Supplier<CoreModelValidator> supplier;
    private final boolean decodeLenient;
    private final List<ValueTransform> transforms;
    private final int padding;

    CoreExtModelHandler(
        EngineContext context,
        String model,
        Supplier<CoreModelValidator> supplier,
        boolean decodeLenient,
        boolean encodeLenient,
        List<ValueTransform> transforms)
    {
        this.plain = new CoreModelHandler(context, model, supplier, decodeLenient, encodeLenient);
        this.supplier = supplier;
        this.decodeLenient = decodeLenient;
        this.transforms = transforms;

        int total = 0;
        for (ValueTransform transform : transforms)
        {
            total += transform.padding();
        }
        this.padding = total;
    }

    @Override
    public ModelPipeline supplyDecoder(
        ModelTransform transform)
    {
        return new CoreExtModelPipeline(plain, supplier.get(), decodeLenient, transforms, padding);
    }

    @Override
    public ModelPipeline supplyEncoder(
        ModelTransform transform)
    {
        return plain.supplyEncoder(transform);
    }
}
