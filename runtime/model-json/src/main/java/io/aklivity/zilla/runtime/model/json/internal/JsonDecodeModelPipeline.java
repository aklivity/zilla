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
package io.aklivity.zilla.runtime.model.json.internal;

import static io.aklivity.zilla.runtime.engine.catalog.CatalogHandler.NO_SCHEMA_ID;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.Int2ObjectCache;

import io.aklivity.zilla.runtime.common.json.JsonDiagnostic;
import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.json.JsonGeneratorEx;
import io.aklivity.zilla.runtime.common.json.JsonPipeline;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;
import io.aklivity.zilla.runtime.common.json.JsonPipelineResult;
import io.aklivity.zilla.runtime.engine.model.ModelPipeline;
import io.aklivity.zilla.runtime.engine.model.ModelPipelineResult;
import io.aklivity.zilla.runtime.engine.model.ModelStatus;
import io.aklivity.zilla.runtime.engine.model.ModelVisitor;

// Per-stream read transform session vended by JsonModelHandlerImpl: owns its own generator, extractor and
// schema-keyed pipeline cache so concurrent streams on a worker never share in-flight state. transform
// strips the catalog framing on the first fragment, drives the common-json transform into the caller's
// destination, and surfaces extracted fields to the ModelVisitor when a value completes.
final class JsonDecodeModelPipeline implements ModelPipeline
{
    private final JsonModelHandlerImpl handler;
    private final ModelVisitor visitor;
    private final JsonGeneratorEx generator;
    private final JsonExtractor extractor;
    private final Int2ObjectCache<JsonPipeline> pipelines;
    private final ModelPipelineResult result;

    private JsonPipeline active;
    private String diagnostic;

    JsonDecodeModelPipeline(
        JsonModelHandlerImpl handler,
        ModelVisitor visitor)
    {
        this.handler = handler;
        this.visitor = visitor;
        this.generator = JsonEx.createGenerator();
        // a NONE visitor keeps the verbatim/SEGMENTED fast path: no extractor stage, no structured field events
        this.extractor = visitor != ModelVisitor.NONE ? new JsonExtractor() : null;
        this.pipelines = new Int2ObjectCache<>(1, 16, p -> {});
        this.result = new ModelPipelineResult();
    }

    @Override
    public ModelPipelineResult transform(
        long traceId,
        long bindingId,
        int flags,
        DirectBuffer src,
        int srcIndex,
        int srcLimit,
        MutableDirectBuffer dst,
        int dstIndex,
        int dstLimit)
    {
        int srcLength = srcLimit - srcIndex;
        int dstLength = dstLimit - dstIndex;
        int prefix = 0;
        if ((flags & FLAGS_INIT) != 0)
        {
            // the catalog framing sits at the value start; strip it once on the first fragment and select
            // the schema-bound pipeline, then later fragments stream straight through
            int schemaId = handler.resolveSchemaId(src, srcIndex, srcLength);
            prefix = handler.decodePadding(src, srcIndex, srcLength);
            active = schemaId != NO_SCHEMA_ID ? supplyPipeline(schemaId) : null;
            if (active != null)
            {
                active.reset();
            }
            diagnostic = null;
        }

        ModelStatus status;
        int consumed;
        int produced;
        if (active == null)
        {
            handler.validationFailure(traceId, bindingId, diagnostic != null ? diagnostic : JsonModel.NAME);
            status = ModelStatus.REJECTED;
            consumed = 0;
            produced = 0;
        }
        else
        {
            boolean last = (flags & FLAGS_FIN) != 0;
            JsonPipelineResult json =
                active.transform(src, srcIndex + prefix, srcIndex + srcLength, last, dst, dstIndex, dstIndex + dstLength);
            status = map(json.status());
            consumed = prefix + json.consumed();
            produced = json.produced();
            if (status == ModelStatus.COMPLETE && extractor != null)
            {
                visitExtracted();
            }
            else if (status == ModelStatus.REJECTED)
            {
                handler.validationFailure(traceId, bindingId, diagnostic != null ? diagnostic : JsonModel.NAME);
            }
        }
        return result.set(status, consumed, produced);
    }

    @Override
    public boolean identity()
    {
        return active != null && active.identity();
    }

    @Override
    public int padding(
        DirectBuffer data,
        int index,
        int length)
    {
        return handler.decodePadding(data, index, length);
    }

    @Override
    public void reset()
    {
        if (active != null)
        {
            active.reset();
        }
        active = null;
        diagnostic = null;
    }

    private void visitExtracted()
    {
        for (int i = 0; i < extractor.captured(); i++)
        {
            visitor.onField("$." + extractor.name(i), extractor.value(i), 0, extractor.length(i));
        }
    }

    private JsonPipeline supplyPipeline(
        int schemaId)
    {
        return pipelines.computeIfAbsent(schemaId, id -> extractor != null
            ? handler.newPipeline(id, generator, extractor, this::onRejected)
            : handler.newPipeline(id, generator, this::onRejected));
    }

    private void onRejected(
        JsonDiagnostic diagnostic)
    {
        this.diagnostic = diagnostic.message();
    }

    private static ModelStatus map(
        Status status)
    {
        return switch (status)
        {
        case COMPLETED -> ModelStatus.COMPLETE;
        case SUSPENDED -> ModelStatus.OVERFLOW;
        case STARVED -> ModelStatus.UNDERFLOW;
        case REJECTED -> ModelStatus.REJECTED;
        default -> ModelStatus.OK;
        };
    }
}
