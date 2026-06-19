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
package io.aklivity.zilla.runtime.model.avro.internal;

import static io.aklivity.zilla.runtime.engine.catalog.CatalogHandler.NO_SCHEMA_ID;

import java.util.List;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.Int2ObjectCache;

import io.aklivity.zilla.runtime.common.avro.AvroDiagnostic;
import io.aklivity.zilla.runtime.common.avro.AvroPipeline;
import io.aklivity.zilla.runtime.common.avro.AvroPipeline.Status;
import io.aklivity.zilla.runtime.common.avro.AvroPipelineResult;
import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.json.JsonGeneratorEx;
import io.aklivity.zilla.runtime.engine.model.ModelPipeline;
import io.aklivity.zilla.runtime.engine.model.ModelPipelineResult;
import io.aklivity.zilla.runtime.engine.model.ModelStatus;
import io.aklivity.zilla.runtime.engine.model.ModelVisitor;

// Per-stream read transform session vended by AvroReadModelHandler: owns its own JSON generator, extractor
// and schema-keyed pipeline cache so concurrent streams on a worker never share in-flight state. transform
// strips the catalog framing on the first fragment, drives the common-avro transform into the caller's
// destination (re-encoding Avro as JSON or canonical Avro), and surfaces extracted fields to the
// ModelVisitor when a value completes.
final class AvroReadModelPipeline implements ModelPipeline
{
    private final AvroReadModelHandler handler;
    private final List<String> paths;
    private final List<String> names;
    private final ModelVisitor visitor;
    private final JsonGeneratorEx generator;
    private final AvroExtractor extractor;
    private final Int2ObjectCache<AvroPipeline> pipelines;
    private final ModelPipelineResult result;

    private AvroPipeline active;
    private String diagnostic;

    AvroReadModelPipeline(
        AvroReadModelHandler handler,
        List<String> paths,
        List<String> names,
        ModelVisitor visitor)
    {
        this.handler = handler;
        this.paths = paths;
        this.names = names;
        this.visitor = visitor;
        this.generator = JsonEx.createGenerator();
        this.extractor = new AvroExtractor();
        for (int i = 0; i < names.size(); i++)
        {
            extractor.register(names.get(i));
        }
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
        int srcLength,
        MutableDirectBuffer dst,
        int dstIndex,
        int dstLength)
    {
        int prefix = 0;
        if ((flags & FLAGS_INIT) != 0)
        {
            // the catalog framing sits at the value start; strip it once on the first fragment and select
            // the schema-bound pipeline, then later fragments stream straight through
            int schemaId = handler.resolveSchemaId(src, srcIndex, srcLength);
            prefix = handler.prefix(src, srcIndex, srcLength);
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
            handler.validationFailure(traceId, bindingId, diagnostic != null ? diagnostic : AvroModel.NAME);
            status = ModelStatus.REJECTED;
            consumed = 0;
            produced = 0;
        }
        else
        {
            boolean last = (flags & FLAGS_FIN) != 0;
            AvroPipelineResult avro =
                active.transform(src, srcIndex + prefix, srcIndex + srcLength, last, dst, dstIndex, dstIndex + dstLength);
            status = map(avro.status());
            consumed = prefix + avro.consumed();
            produced = avro.produced();
            if (status == ModelStatus.COMPLETE)
            {
                visitExtracted();
            }
            else if (status == ModelStatus.REJECTED)
            {
                handler.validationFailure(traceId, bindingId, diagnostic != null ? diagnostic : AvroModel.NAME);
            }
        }
        return result.set(status, consumed, produced);
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
        for (int i = 0; i < names.size(); i++)
        {
            int length = extractor.length(names.get(i));
            if (length != 0)
            {
                visitor.onField(paths.get(i), extractor.value(names.get(i)), 0, length);
            }
        }
    }

    private AvroPipeline supplyPipeline(
        int schemaId)
    {
        return pipelines.computeIfAbsent(schemaId, id -> handler.newPipeline(id, generator, extractor, this::onRejected));
    }

    private void onRejected(
        AvroDiagnostic diagnostic)
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
