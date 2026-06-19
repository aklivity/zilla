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
package io.aklivity.zilla.runtime.model.protobuf.internal;

import static io.aklivity.zilla.runtime.engine.catalog.CatalogHandler.NO_SCHEMA_ID;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.agrona.DirectBuffer;

import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.protobuf.Protobuf;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufGenerator;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufMessage;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufPipeline;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufReporter;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufSchema;
import io.aklivity.zilla.runtime.common.protobuf.json.ProtobufJson;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.model.ModelHandler;
import io.aklivity.zilla.runtime.engine.model.ModelPipeline;
import io.aklivity.zilla.runtime.engine.model.ModelVisitor;
import io.aklivity.zilla.runtime.model.protobuf.config.ProtobufModelConfig;

public final class ProtobufReadModelHandler extends ProtobufModelHandler implements ModelHandler
{
    private static final String PATH = "^\\$\\.([A-Za-z_][A-Za-z0-9_]*)$";
    private static final Pattern PATH_PATTERN = Pattern.compile(PATH);

    private final Matcher matcher;
    private final List<String> paths;
    private final List<String> names;
    private final Map<String, Object> jsonConfig;

    public ProtobufReadModelHandler(
        ProtobufModelConfig config,
        EngineContext context)
    {
        super(config, context);
        this.matcher = PATH_PATTERN.matcher("");
        this.paths = new ArrayList<>();
        this.names = new ArrayList<>();
        this.jsonConfig = new HashMap<>();
        jsonConfig.put(ProtobufJson.FIELD_NAMES, ProtobufJson.FieldNames.PROTO);
        jsonConfig.put(ProtobufJson.INCLUDE_DEFAULTS, Boolean.TRUE);
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
        int padding = handler.decodePadding(data, index, length);
        if (VIEW_JSON.equals(view))
        {
            padding += supplyJsonFormatPadding(resolveSchemaId(data, index, length));
        }
        return padding;
    }

    @Override
    public ModelPipeline supplyPipeline(
        ModelVisitor visitor)
    {
        return new ProtobufReadModelPipeline(this, paths, names, visitor);
    }

    // the catalog framing the value carries on the wire, stripped once at the start of the first fragment
    int prefix(
        DirectBuffer data,
        int index,
        int length)
    {
        return handler.decodePadding(data, index, length);
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

    // consumes the message-index varints at the value start (after the catalog framing), returning the number
    // of bytes they occupy; the decoded path is then read by message(int)
    int messageProgress(
        DirectBuffer data,
        int index,
        int length)
    {
        return decodeIndexes(data, index, length);
    }

    ProtobufMessage message(
        int schemaId)
    {
        ProtobufSchema schema = supplySchema(schemaId);
        return schema != null ? schema.messageByIndexes(decodedPath()) : null;
    }

    ProtobufPipeline newPipeline(
        int schemaId,
        String messageName,
        ProtobufExtractor extractor,
        ProtobufReporter reporter)
    {
        ProtobufSchema schema = supplySchema(schemaId);
        ProtobufPipeline pipeline = null;
        if (schema != null && messageName != null)
        {
            // a json view re-encodes the wire message into JSON; any other view re-encodes it into canonical
            // wire, so the bytes the parser validated are reproduced for the caller
            ProtobufGenerator generator = VIEW_JSON.equals(view)
                ? ProtobufJson.generator(JsonEx.createGenerator(), schema, messageName, jsonConfig)
                : Protobuf.generator();
            pipeline = Protobuf.stream(Protobuf.parser(schema, messageName))
                .transform(extractor)
                .reporting(reporter)
                .into(generator, schema, messageName);
        }
        return pipeline;
    }

    void validationFailure(
        long traceId,
        long bindingId,
        String diagnostic)
    {
        event.validationFailure(traceId, bindingId, diagnostic);
    }
}
