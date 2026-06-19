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

import java.util.Map;

import org.agrona.DirectBuffer;

import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.protobuf.Protobuf;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufMessage;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufParser;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufPipeline;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufReporter;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufSchema;
import io.aklivity.zilla.runtime.common.protobuf.json.ProtobufJson;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.model.ModelHandler;
import io.aklivity.zilla.runtime.engine.model.ModelPipeline;
import io.aklivity.zilla.runtime.engine.model.ModelVisitor;
import io.aklivity.zilla.runtime.engine.model.function.ValueConsumer;
import io.aklivity.zilla.runtime.model.protobuf.config.ProtobufModelConfig;

public final class ProtobufWriteModelHandler extends ProtobufModelHandler implements ModelHandler
{
    // a no-op encoder so encode() emits only the catalog framing into the destination, never the body
    private static final CatalogHandler.Encoder NONE_ENCODER =
        (traceId, bindingId, schemaId, data, index, length, next) -> 0;

    public ProtobufWriteModelHandler(
        ProtobufModelConfig config,
        EngineContext context)
    {
        super(config, context);
    }

    @Override
    public int padding(
        DirectBuffer data,
        int index,
        int length)
    {
        return handler.encodePadding(length) + supplyIndexPadding(resolveSchemaId());
    }

    @Override
    public ModelPipeline supplyPipeline(
        ModelVisitor visitor)
    {
        return new ProtobufWriteModelPipeline(this);
    }

    int resolveSchemaId()
    {
        return catalog != null && catalog.id > 0
            ? catalog.id
            : handler.resolve(subject, catalog.version);
    }

    int[] messagePath(
        int schemaId)
    {
        ProtobufSchema schema = supplySchema(schemaId);
        return schema != null && catalog.record != null ? schema.messageIndexes(catalog.record) : null;
    }

    ProtobufMessage message(
        int schemaId,
        int[] path)
    {
        ProtobufSchema schema = supplySchema(schemaId);
        return schema != null && path != null ? schema.messageByIndexes(path) : null;
    }

    // writes the catalog framing prefix for the resolved schema id into next, returning the bytes written
    int encodePrefix(
        long traceId,
        long bindingId,
        int schemaId,
        DirectBuffer data,
        int index,
        int length,
        ValueConsumer next)
    {
        return handler.encode(traceId, bindingId, schemaId, data, index, length, next, NONE_ENCODER);
    }

    // the message-index framing the wire value carries ahead of its payload, the single zero byte for the
    // first top-level message and the zigzag-varint index path otherwise
    byte[] indexFraming(
        int[] path)
    {
        encodeIndexes(path);
        byte[] framing;
        if (indexes.size() == 2 && indexes.get(0) == 1 && indexes.get(1) == 0)
        {
            framing = ZERO_INDEX;
        }
        else
        {
            framing = encodeIndexes();
        }
        indexes.clear();
        return framing;
    }

    ProtobufPipeline newPipeline(
        int schemaId,
        String messageName,
        ProtobufReporter reporter)
    {
        ProtobufSchema schema = supplySchema(schemaId);
        ProtobufPipeline pipeline = null;
        if (schema != null && messageName != null)
        {
            // a json view parses JSON into the wire message; any other view re-encodes the incoming wire value,
            // validating it against the schema in both cases
            ProtobufParser parser = VIEW_JSON.equals(view)
                ? ProtobufJson.parser(JsonEx.createParser(), schema, messageName,
                    Map.of(ProtobufJson.REJECT_UNKNOWN_FIELDS, Boolean.TRUE))
                : Protobuf.parser(schema, messageName);
            pipeline = Protobuf.stream(parser)
                .reporting(reporter)
                .into(Protobuf.generator(), schema, messageName);
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
