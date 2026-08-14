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
package io.aklivity.zilla.runtime.model.protobuf.internal;

import static io.aklivity.zilla.runtime.engine.catalog.CatalogHandler.NO_SCHEMA_ID;

import java.io.StringReader;
import java.util.Arrays;

import jakarta.json.Json;
import jakarta.json.JsonValue;

import org.agrona.BitUtil;
import org.agrona.collections.Int2IntHashMap;
import org.agrona.collections.IntArrayList;
import org.agrona.collections.Long2ObjectCache;

import io.aklivity.zilla.config.engine.CatalogedConfig;
import io.aklivity.zilla.config.engine.OverlayConfig;
import io.aklivity.zilla.config.engine.SchemaConfig;
import io.aklivity.zilla.config.engine.ValidateMode;
import io.aklivity.zilla.config.model.protobuf.ProtobufModelConfig;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.protobuf.Protobuf;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufField;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufMessage;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufOverlay;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufSchema;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;

public class ProtobufModelHandler
{
    protected static final byte[] ZERO_INDEX = new byte[]{0x0};
    protected static final String VIEW_JSON = "json";

    private static final int JSON_FIELD_STRUCTURE_LENGTH = "\"\":\"\",".length();
    private static final int JSON_OBJECT_CURLY_BRACES = 2;

    protected final SchemaConfig catalog;
    protected final CatalogHandler handler;
    protected final String subject;
    protected final CatalogHandler overlayHandler;
    protected final String overlaySubject;
    protected final String overlayVersion;
    protected final String view;
    // a scratch path buffer, reused (not boxed) across every decode/encode call: IntArrayList backs its
    // elements with a primitive int[] (no per-add Node/Integer allocation, unlike List<Integer>)
    protected final IntArrayList indexes;
    protected final ProtobufModelEventContext event;
    // LENIENT per direction: a semantic-validation failure passes through (inert today — no protobuf
    // semantic validation stage throws yet, so the wired branch is unreached)
    protected final boolean decodeLenient;
    protected final boolean encodeLenient;

    private final Long2ObjectCache<ProtobufSchema> schemas;
    private final Int2IntHashMap paddings;
    // decodedPath()'s reused result buffer: resized only when the path depth actually changes, which is
    // fixed per message shape, so a decode stream settles into zero allocation after its first message
    private int[] pathScratch;

    protected ProtobufModelHandler(
        ProtobufModelConfig config,
        EngineContext context)
    {
        CatalogedConfig cataloged = config.cataloged.get(0);
        this.handler = context.supplyCatalog(cataloged.id);
        this.catalog = cataloged.schemas.size() != 0 ? cataloged.schemas.get(0) : null;
        this.subject = catalog != null && catalog.subject != null
                ? catalog.subject
                : config.subject;
        OverlayConfig overlay = catalog != null ? catalog.overlay : null;
        this.overlayHandler = overlay != null ? context.supplyCatalog(overlay.id) : null;
        this.overlaySubject = overlay != null ? overlay.schema.subject : null;
        this.overlayVersion = overlay != null ? overlay.schema.version : null;
        this.view = config.view;
        this.decodeLenient = config.validate.decode == ValidateMode.LENIENT;
        this.encodeLenient = config.validate.encode == ValidateMode.LENIENT;
        this.schemas = new Long2ObjectCache<>(1, 1024, i -> {});
        this.indexes = new IntArrayList();
        this.paddings = new Int2IntHashMap(-1);
        this.event = new ProtobufModelEventContext(context);
        this.pathScratch = new int[0];
    }

    // called on every decode/encode call, so this checks the cache directly rather than through
    // computeIfAbsent: a capturing lambda argument is allocated fresh on every evaluation of that
    // argument expression, regardless of whether the cache already has an entry, so computeIfAbsent
    // would pay that cost on every call instead of once per distinct (schemaId, overlaySchemaId) pair
    protected ProtobufSchema supplySchema(
        int schemaId)
    {
        int overlaySchemaId = overlayHandler != null
            ? overlayHandler.resolve(overlaySubject, overlayVersion)
            : NO_SCHEMA_ID;
        long key = cacheKey(schemaId, overlaySchemaId);
        ProtobufSchema schema = schemas.get(key);
        if (schema == null)
        {
            schema = createSchema(schemaId, overlaySchemaId);
            if (schema != null)
            {
                schemas.put(key, schema);
            }
        }
        return schema;
    }

    private static long cacheKey(
        int schemaId,
        int overlaySchemaId)
    {
        return (long) schemaId << 32 | overlaySchemaId & 0xFFFFFFFFL;
    }

    protected byte[] encodeIndexes()
    {
        int size = indexes.size();

        byte[] indexes = new byte[size * 5];

        int index = 0;
        for (int i = 0; i < size; i++)
        {
            int entry = this.indexes.getInt(i);
            int value = (entry << 1) ^ (entry >> 31);
            while ((value & ~0x7F) != 0)
            {
                indexes[index++] = (byte) ((value & 0x7F) | 0x80);
                value >>>= 7;
            }
            indexes[index++] = (byte) value;
        }

        return Arrays.copyOf(indexes, index);
    }

    protected int decodeIndexes(
        DirectBufferEx data,
        int index,
        int length)
    {
        int progress = 0;
        indexes.clear();
        int encodedLength = decodeIndex(data.getByte(index));
        progress += BitUtil.SIZE_OF_BYTE;
        if (encodedLength == 0)
        {
            indexes.addInt(encodedLength);
        }
        for (int i = 0; i < encodedLength; i++)
        {
            indexes.addInt(decodeIndex(data.getByte(index + progress)));
            progress += BitUtil.SIZE_OF_BYTE;
        }
        return progress;
    }

    protected int[] decodedPath()
    {
        int size = indexes.size();
        if (pathScratch.length != size)
        {
            pathScratch = new int[size];
        }
        for (int i = 0; i < size; i++)
        {
            pathScratch[i] = indexes.getInt(i);
        }
        return pathScratch;
    }

    protected void encodeIndexes(
        int[] path)
    {
        indexes.clear();
        indexes.addInt(path.length);
        for (int entry : path)
        {
            indexes.addInt(entry);
        }
    }

    // avoids computeIfAbsent for the same reason as supplySchema above: a capturing method reference
    // argument is allocated on every call, not just on a cache miss
    protected int supplyIndexPadding(
        int schemaId)
    {
        int padding = paddings.get(schemaId);
        if (padding == paddings.missingValue())
        {
            padding = calculateIndexPadding(schemaId);
            paddings.put(schemaId, padding);
        }
        return padding;
    }

    protected int supplyJsonFormatPadding(
        int schemaId)
    {
        int padding = paddings.get(schemaId);
        if (padding == paddings.missingValue())
        {
            padding = calculateJsonFormatPadding(schemaId);
            paddings.put(schemaId, padding);
        }
        return padding;
    }

    private int decodeIndex(
        byte encodedByte)
    {
        int result = 0;
        int shift = 0;
        do
        {
            result |= (encodedByte & 0x7F) << shift;
            shift += 7;
        }
        while ((encodedByte & 0x80) != 0);
        return (result >>> 1) ^ -(result & 1);
    }

    private int calculateIndexPadding(
        int schemaId)
    {
        int padding = 0;
        ProtobufSchema schema = supplySchema(schemaId);
        if (schema != null && catalog.record != null)
        {
            int[] path = schema.messageIndexes(catalog.record);
            if (path != null)
            {
                padding = path.length + 1;
            }
        }
        return padding;
    }

    private int calculateJsonFormatPadding(
        int schemaId)
    {
        int padding = 0;
        ProtobufSchema schema = supplySchema(schemaId);

        if (schema != null)
        {
            for (int i = 0; ; i++)
            {
                ProtobufMessage message = schema.messageByIndexes(new int[]{i});
                if (message == null)
                {
                    break;
                }
                padding += JSON_OBJECT_CURLY_BRACES;
                for (ProtobufField field : message.fields())
                {
                    padding += field.name().getBytes().length + JSON_FIELD_STRUCTURE_LENGTH;
                }
            }
        }
        return padding;
    }

    private ProtobufSchema createSchema(
        int schemaId,
        int overlaySchemaId)
    {
        ProtobufSchema schema = null;

        String schemaText = handler.resolve(schemaId);
        String overlayText = overlayHandler != null ? overlayHandler.resolve(overlaySchemaId) : null;
        if (schemaText != null && (overlayHandler == null || overlayText != null))
        {
            schema = Protobuf.schema(schemaText);
            if (overlayText != null)
            {
                JsonValue overlay = Json.createReader(new StringReader(overlayText)).readValue();
                schema = ProtobufOverlay.of(overlay).apply(schema);
            }
        }
        return schema;
    }
}
