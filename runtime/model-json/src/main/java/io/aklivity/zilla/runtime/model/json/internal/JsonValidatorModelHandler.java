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
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.json.JsonGeneratorEx;
import io.aklivity.zilla.runtime.common.json.JsonPipeline;
import io.aklivity.zilla.runtime.common.json.JsonReporter;
import io.aklivity.zilla.runtime.common.json.JsonSchema;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.model.ModelHandler;
import io.aklivity.zilla.runtime.engine.model.ModelPipeline;
import io.aklivity.zilla.runtime.engine.model.ModelVisitor;
import io.aklivity.zilla.runtime.model.json.config.JsonModelConfig;

public final class JsonValidatorModelHandler extends JsonModelHandler implements ModelHandler
{
    private static final int OUTPUT_CAPACITY = 8192;

    // shared per-worker scratch for the discarded validation output; each per-stream pipeline wraps it
    // transiently within a single transform call. Transitional — removed once callers drive validation
    // through the ModelPipeline contract directly rather than re-serializing to validate.
    private final MutableDirectBuffer scratch;

    public JsonValidatorModelHandler(
        JsonModelConfig config,
        EngineContext context)
    {
        super(config, context);
        this.scratch = new UnsafeBuffer(new byte[OUTPUT_CAPACITY]);
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
    public ModelPipeline supplyPipeline(
        ModelVisitor visitor)
    {
        return new JsonValidatorModelPipeline(this, scratch);
    }

    int resolveSchemaId(
        DirectBuffer data,
        int index,
        int length)
    {
        int schemaId = handler.resolve(data, index, length);
        if (schemaId == NO_SCHEMA_ID)
        {
            schemaId = catalog.id != NO_SCHEMA_ID
                ? catalog.id
                : handler.resolve(subject, catalog.version);
        }
        return schemaId;
    }

    JsonPipeline newPipeline(
        int schemaId,
        JsonGeneratorEx generator,
        JsonReporter reporter)
    {
        JsonSchema schema = supplySchema(schemaId);
        return schema != null
            ? JsonEx.stream(JsonEx.createParser())
                .transform(schema.validator())
                .reporting(reporter)
                .into(generator)
            : null;
    }

    void validationFailure(
        long traceId,
        long bindingId,
        String diagnostic)
    {
        event.validationFailure(traceId, bindingId, diagnostic);
    }
}
