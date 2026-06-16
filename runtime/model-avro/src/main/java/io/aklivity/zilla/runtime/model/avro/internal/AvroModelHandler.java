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

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.agrona.DirectBuffer;
import org.agrona.ExpandableDirectByteBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.Int2IntHashMap;
import org.agrona.collections.Int2ObjectCache;

import io.aklivity.zilla.runtime.common.avro.Avro;
import io.aklivity.zilla.runtime.common.avro.AvroEvent;
import io.aklivity.zilla.runtime.common.avro.AvroKind;
import io.aklivity.zilla.runtime.common.avro.AvroParser;
import io.aklivity.zilla.runtime.common.avro.AvroSchema;
import io.aklivity.zilla.runtime.common.avro.AvroType;
import io.aklivity.zilla.runtime.common.avro.AvroValidationException;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.config.CatalogedConfig;
import io.aklivity.zilla.runtime.engine.config.SchemaConfig;
import io.aklivity.zilla.runtime.model.avro.config.AvroModelConfig;
import io.aklivity.zilla.runtime.model.avro.internal.types.OctetsFW;

public abstract class AvroModelHandler
{
    protected static final String VIEW_JSON = "json";

    // headroom so the bounded output window admits any single scalar value (worst-case JSON string escaping is
    // ~6x, base64 ~1.34x, a double is 8 bytes); a datum whose total exceeds the window drains in chunks
    private static final int OUT_SCALE = 8;
    private static final int OUT_SLACK = 1024;

    private static final int JSON_FIELD_STRUCTURE_LENGTH = "\"\":\"\",".length();
    private static final int JSON_FIELD_UNION_LENGTH = "\"\":{\"DATA_TYPE\":\"\"},".length();
    private static final int JSON_FIELD_ARRAY_LENGTH = "\"\":[],".length();
    private static final int JSON_FIELD_MAP_LENGTH = "\"\":{},".length();

    protected final SchemaConfig catalog;
    protected final CatalogHandler handler;
    protected final String subject;
    protected final String view;
    protected final AvroModelEventContext event;
    protected final Map<String, AvroField> extracted;
    protected final MutableDirectBuffer out;
    protected final MutableDirectBuffer accumulator;

    private final Int2ObjectCache<AvroSchema> schemas;
    private final Int2ObjectCache<AvroParser> parsers;
    private final Int2IntHashMap paddings;
    private final int paddingMaxItems;

    protected AvroModelHandler(
        AvroModelConfiguration config,
        AvroModelConfig options,
        EngineContext context)
    {
        CatalogedConfig cataloged = options.cataloged.get(0);
        this.handler = context.supplyCatalog(cataloged.id);
        this.catalog = cataloged.schemas.size() != 0 ? cataloged.schemas.get(0) : null;
        this.view = options.view;
        this.subject = catalog != null && catalog.subject != null
                ? catalog.subject
                : options.subject;
        this.schemas = new Int2ObjectCache<>(1, 1024, i -> {});
        this.parsers = new Int2ObjectCache<>(1, 1024, i -> {});
        this.paddings = new Int2IntHashMap(-1);
        this.event = new AvroModelEventContext(context);
        this.extracted = new HashMap<>();
        this.out = new ExpandableDirectByteBuffer();
        this.accumulator = new ExpandableDirectByteBuffer();
        this.paddingMaxItems = config.paddingMaxItems();
    }

    // the bounded output window for a datum decoded/encoded from a payload of the given length, grown so any
    // single scalar value fits; a total exceeding the window drains across successive feeds
    protected final int outLimit(
        int length)
    {
        int limit = length * OUT_SCALE + OUT_SLACK;
        if (out.capacity() < limit)
        {
            out.putByte(limit - 1, (byte) 0);
        }
        return limit;
    }

    protected final boolean validate(
        long traceId,
        long bindingId,
        int schemaId,
        DirectBuffer buffer,
        int index,
        int length)
    {
        boolean status = false;
        try
        {
            AvroParser parser = supplyParser(schemaId);
            if (parser != null)
            {
                parser.reset();
                parser.wrap(buffer, index, length, true);
                walk(parser);
                status = true;
            }
        }
        catch (AvroValidationException ex)
        {
            event.validationFailure(traceId, bindingId, ex.getMessage());
        }
        return status;
    }

    protected final AvroSchema supplySchema(
        int schemaId)
    {
        return schemas.computeIfAbsent(schemaId, this::resolveSchema);
    }

    protected final int supplyPadding(
        int schemaId)
    {
        return paddings.computeIfAbsent(schemaId, id ->
        {
            AvroSchema schema = supplySchema(id);
            return calculatePadding(schema != null ? schema.type() : null);
        });
    }

    private AvroParser supplyParser(
        int schemaId)
    {
        return parsers.computeIfAbsent(schemaId, id ->
        {
            AvroSchema schema = supplySchema(id);
            return schema != null ? Avro.parser(schema) : null;
        });
    }

    private AvroSchema resolveSchema(
        int schemaId)
    {
        AvroSchema schema = null;
        String schemaText = handler.resolve(schemaId);
        if (schemaText != null)
        {
            schema = Avro.schema(schemaText);
        }
        return schema;
    }

    private void walk(
        AvroParser parser)
    {
        boolean extracting = !extracted.isEmpty();
        int depth = 0;
        AvroField current = null;
        while (parser.hasNext())
        {
            AvroEvent next = parser.nextEvent();
            if (next == null)
            {
                break;
            }
            switch (next)
            {
            case START_RECORD:
            case START_ARRAY:
            case START_MAP:
                depth++;
                current = null;
                break;
            case END_RECORD:
            case END_ARRAY:
            case END_MAP:
                depth--;
                current = null;
                break;
            case FIELD_NAME:
                current = extracting && depth == 1 ? extracted.get(parser.getField()) : null;
                break;
            case UNION_BRANCH:
            case MAP_KEY:
            case START_MESSAGE:
            case END_MESSAGE:
                break;
            default:
                if (current != null)
                {
                    writeExtract(current, next, parser);
                }
                current = null;
                break;
            }
        }
    }

    private void writeExtract(
        AvroField field,
        AvroEvent next,
        AvroParser parser)
    {
        OctetsFW value = field.value;
        switch (next)
        {
        case STRING:
        case BYTES:
        case FIXED:
        {
            DirectBuffer segment = parser.getSegment();
            int length = segment.capacity();
            MutableDirectBuffer buffer = field.buffer(length);
            buffer.putBytes(0, segment, 0, length);
            value.wrap(buffer, 0, length);
            break;
        }
        case ENUM:
        case INT:
        {
            MutableDirectBuffer buffer = field.buffer(32);
            int length = buffer.putIntAscii(0, parser.getInt());
            value.wrap(buffer, 0, length);
            break;
        }
        case LONG:
        {
            MutableDirectBuffer buffer = field.buffer(32);
            int length = buffer.putLongAscii(0, parser.getLong());
            value.wrap(buffer, 0, length);
            break;
        }
        case FLOAT:
        {
            String text = String.valueOf(parser.getFloat());
            MutableDirectBuffer buffer = field.buffer(text.length());
            int length = buffer.putStringWithoutLengthAscii(0, text);
            value.wrap(buffer, 0, length);
            break;
        }
        case DOUBLE:
        {
            String text = String.valueOf(parser.getDouble());
            MutableDirectBuffer buffer = field.buffer(text.length());
            int length = buffer.putStringWithoutLengthAscii(0, text);
            value.wrap(buffer, 0, length);
            break;
        }
        case BOOLEAN:
        {
            String text = String.valueOf(parser.getBoolean());
            MutableDirectBuffer buffer = field.buffer(text.length());
            int length = buffer.putStringWithoutLengthAscii(0, text);
            value.wrap(buffer, 0, length);
            break;
        }
        default:
            break;
        }
    }

    private int calculatePadding(
        AvroType type)
    {
        int padding = 0;

        if (type != null)
        {
            padding = 10;
            if (type.kind() == AvroKind.RECORD)
            {
                for (io.aklivity.zilla.runtime.common.avro.AvroField field : type.fields())
                {
                    padding += field.name().getBytes(StandardCharsets.UTF_8).length;

                    AvroType fieldType = field.type();
                    switch (fieldType.kind())
                    {
                    case RECORD:
                        padding += calculatePadding(fieldType);
                        break;
                    case UNION:
                        padding += JSON_FIELD_UNION_LENGTH;
                        break;
                    case MAP:
                        padding += JSON_FIELD_MAP_LENGTH + paddingMaxItems + calculatePadding(fieldType.values());
                        break;
                    case ARRAY:
                        padding += JSON_FIELD_ARRAY_LENGTH + paddingMaxItems + calculatePadding(fieldType.items());
                        break;
                    default:
                        padding += JSON_FIELD_STRUCTURE_LENGTH;
                        break;
                    }
                }
            }
        }
        return padding;
    }
}
