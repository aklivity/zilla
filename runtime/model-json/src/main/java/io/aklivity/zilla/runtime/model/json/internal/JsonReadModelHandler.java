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

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.agrona.DirectBuffer;

import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.json.JsonGeneratorEx;
import io.aklivity.zilla.runtime.common.json.JsonPipeline;
import io.aklivity.zilla.runtime.common.json.JsonReporter;
import io.aklivity.zilla.runtime.common.json.JsonSchema;
import io.aklivity.zilla.runtime.common.json.JsonTransform;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.model.ModelHandler;
import io.aklivity.zilla.runtime.engine.model.ModelPipeline;
import io.aklivity.zilla.runtime.engine.model.ModelVisitor;
import io.aklivity.zilla.runtime.model.json.config.JsonModelConfig;

public final class JsonReadModelHandler extends JsonModelHandler implements ModelHandler
{
    private static final String PATH = "^\\$\\.([A-Za-z_][A-Za-z0-9_]*)$";
    private static final Pattern PATH_PATTERN = Pattern.compile(PATH);

    private final Matcher matcher;
    private final List<String> paths;
    private final List<String> names;

    public JsonReadModelHandler(
        JsonModelConfig config,
        EngineContext context)
    {
        super(config, context);
        this.matcher = PATH_PATTERN.matcher("");
        this.paths = new ArrayList<>();
        this.names = new ArrayList<>();
    }

    @Override
    public void extract(
        String path)
    {
        if (matcher.reset(path).matches() && !paths.contains(path))
        {
            paths.add(path);
            names.add(matcher.group(1));
        }
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
        return new JsonReadModelPipeline(this, paths, names, visitor);
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
        JsonTransform extractor,
        JsonReporter reporter)
    {
        JsonSchema schema = supplySchema(schemaId);
        return schema != null
            ? JsonEx.stream(JsonEx.createParser())
                .transform(schema.validator())
                .transform(extractor)
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
