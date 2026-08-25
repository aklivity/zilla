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
import io.aklivity.zilla.runtime.engine.model.ModelEnvelope;
import io.aklivity.zilla.runtime.engine.model.ModelHandler;
import io.aklivity.zilla.runtime.engine.model.ModelPipeline;
import io.aklivity.zilla.runtime.engine.model.ModelTransform;
import io.aklivity.zilla.runtime.model.core.ext.BytesModelExtHandler;
import io.aklivity.zilla.runtime.model.core.ext.BytesTransform;

// Per-worker factory for a bytes model with at least one installed extension. Each direction is extended
// independently, so an extension applying on only one of them leaves the other exactly as it would be with
// no extension installed at all -- the identity pipeline a plain CoreModelHandler vends. A stage holds the
// in-flight state of one value, so the chain is folded per stream rather than shared across the streams
// this handler serves.
final class BytesExtModelHandler implements ModelHandler
{
    private final CoreModelHandler plain;
    private final Supplier<CoreModelValidator> supplier;
    private final boolean decodeLenient;
    private final boolean encodeLenient;
    private final List<BytesModelExtHandler> handlers;
    private final int decodePadding;
    private final int encodePadding;

    BytesExtModelHandler(
        EngineContext context,
        Supplier<CoreModelValidator> supplier,
        boolean decodeLenient,
        boolean encodeLenient,
        List<BytesModelExtHandler> handlers)
    {
        this.plain = new CoreModelHandler(context, BytesModel.NAME, supplier, decodeLenient, encodeLenient);
        this.supplier = supplier;
        this.decodeLenient = decodeLenient;
        this.encodeLenient = encodeLenient;
        this.handlers = handlers;
        this.decodePadding = handlers.stream().mapToInt(BytesModelExtHandler::decodePadding).sum();
        this.encodePadding = handlers.stream().mapToInt(BytesModelExtHandler::encodePadding).sum();
    }

    @Override
    public ModelPipeline supplyCacheable(
        ModelEnvelope envelope,
        ModelTransform transform)
    {
        assert envelope != null;

        BytesTransformStream stream = new BytesTransformStream();
        for (BytesModelExtHandler handler : handlers)
        {
            stream = handler.cacheable(stream);
        }

        List<BytesTransform> transforms = stream.transforms();

        return transforms.isEmpty()
            ? plain.supplyCacheable(envelope, transform)
            : new BytesExtModelPipeline(plain, supplier.get(), decodeLenient, transforms, envelope, decodePadding);
    }

    @Override
    public ModelPipeline supplyDecoder(
        ModelEnvelope envelope,
        ModelTransform transform)
    {
        assert envelope != null;

        BytesTransformStream stream = new BytesTransformStream();
        for (BytesModelExtHandler handler : handlers)
        {
            stream = handler.decode(stream);
        }

        List<BytesTransform> transforms = stream.transforms();

        return transforms.isEmpty()
            ? plain.supplyDecoder(envelope, transform)
            : new BytesExtModelPipeline(plain, supplier.get(), decodeLenient, transforms, envelope, decodePadding);
    }

    @Override
    public ModelPipeline supplyEncoder(
        ModelEnvelope envelope,
        ModelTransform transform)
    {
        assert envelope != null;

        BytesTransformStream stream = new BytesTransformStream();
        for (BytesModelExtHandler handler : handlers)
        {
            stream = handler.encode(stream);
        }

        List<BytesTransform> transforms = stream.transforms();

        return transforms.isEmpty()
            ? plain.supplyEncoder(envelope, transform)
            : new BytesExtModelPipeline(plain, supplier.get(), encodeLenient, transforms, envelope, encodePadding);
    }
}
