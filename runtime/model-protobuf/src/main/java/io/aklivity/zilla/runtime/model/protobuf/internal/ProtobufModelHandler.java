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

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import org.agrona.BitUtil;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.Int2IntHashMap;
import org.agrona.collections.Int2ObjectCache;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.common.protobuf.Protobuf;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufField;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufGenerator;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufMessage;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufPipeline;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufSchema;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.config.CatalogedConfig;
import io.aklivity.zilla.runtime.engine.config.SchemaConfig;
import io.aklivity.zilla.runtime.model.protobuf.config.ProtobufModelConfig;

public class ProtobufModelHandler
{
    protected static final byte[] ZERO_INDEX = new byte[]{0x0};
    protected static final String VIEW_JSON = "json";

    private static final int JSON_FIELD_STRUCTURE_LENGTH = "\"\":\"\",".length();
    private static final int JSON_OBJECT_CURLY_BRACES = 2;

    // the streaming output window the pipeline generator fills: sized to OUT_SCALE * input + OUT_SLACK so a
    // whole message ordinarily transcodes in one feed, and grown-and-retried if a feed reports SUSPENDED
    private static final int OUT_SCALE = 8;
    private static final int OUT_SLACK = 1024;
    private static final int OUT_INITIAL = 8192;

    protected final SchemaConfig catalog;
    protected final CatalogHandler handler;
    protected final String subject;
    protected final String view;
    protected final List<Integer> indexes;
    protected final MutableDirectBuffer out;
    protected final ProtobufModelEventContext event;

    private final Int2ObjectCache<ProtobufSchema> schemas;
    private final Int2IntHashMap paddings;

    private byte[] outBytes;

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
        this.view = config.view;
        this.schemas = new Int2ObjectCache<>(1, 1024, i -> {});
        this.indexes = new LinkedList<>();
        this.paddings = new Int2IntHashMap(-1);
        this.outBytes = new byte[OUT_INITIAL];
        this.out = new UnsafeBuffer(outBytes);
        this.event = new ProtobufModelEventContext(context);
    }

    // Drives one whole message through the pipeline into the reused output window {@code out}, returning the
    // number of bytes produced (in {@code out[0, len)}) or -1 when the message is rejected. The window is sized
    // up front; if a feed reports SUSPENDED (output exceeded the estimate) the window is doubled and the feed is
    // retried from the start, so a valid message of any size completes in a single feed rather than being
    // wrongly rejected. On rejection {@code pipeline.reason()} carries why.
    protected final int project(
        ProtobufPipeline pipeline,
        ProtobufGenerator generator,
        DirectBuffer data,
        int index,
        int length)
    {
        int produced = -1;
        int limit = length * OUT_SCALE + OUT_SLACK;
        try
        {
            ProtobufPipeline.Status status;
            do
            {
                ensureOut(limit);
                generator.wrap(out, 0, limit);
                pipeline.reset();
                status = pipeline.feed(data, index, index + length);
                if (status == ProtobufPipeline.Status.SUSPENDED)
                {
                    limit *= 2;
                }
            }
            while (status == ProtobufPipeline.Status.SUSPENDED);

            if (status == ProtobufPipeline.Status.COMPLETED)
            {
                generator.flush();
                produced = generator.length();
            }
        }
        catch (Exception ex)
        {
            produced = -1;
        }
        return produced;
    }

    private void ensureOut(
        int capacity)
    {
        if (outBytes.length < capacity)
        {
            outBytes = new byte[capacity];
            out.wrap(outBytes);
        }
    }

    protected ProtobufSchema supplySchema(
        int schemaId)
    {
        return schemas.computeIfAbsent(schemaId, this::createSchema);
    }

    protected byte[] encodeIndexes()
    {
        int size = indexes.size();

        byte[] indexes = new byte[size * 5];

        int index = 0;
        for (int i = 0; i < size; i++)
        {
            int entry = this.indexes.get(i);
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
        DirectBuffer data,
        int index,
        int length)
    {
        int progress = 0;
        indexes.clear();
        int encodedLength = decodeIndex(data.getByte(index));
        progress += BitUtil.SIZE_OF_BYTE;
        if (encodedLength == 0)
        {
            indexes.add(encodedLength);
        }
        for (int i = 0; i < encodedLength; i++)
        {
            indexes.add(decodeIndex(data.getByte(index + progress)));
            progress += BitUtil.SIZE_OF_BYTE;
        }
        return progress;
    }

    protected int[] decodedPath()
    {
        int[] path = new int[indexes.size()];
        for (int i = 0; i < indexes.size(); i++)
        {
            path[i] = indexes.get(i);
        }
        return path;
    }

    protected void encodeIndexes(
        int[] path)
    {
        indexes.clear();
        indexes.add(path.length);
        for (int entry : path)
        {
            indexes.add(entry);
        }
    }

    protected int supplyIndexPadding(
        int schemaId)
    {
        return paddings.computeIfAbsent(schemaId, this::calculateIndexPadding);
    }

    protected int supplyJsonFormatPadding(
        int schemaId)
    {
        return paddings.computeIfAbsent(schemaId, this::calculateJsonFormatPadding);
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
        int schemaId)
    {
        ProtobufSchema schema = null;

        String schemaText = handler.resolve(schemaId);
        if (schemaText != null)
        {
            schema = Protobuf.schema(schemaText);
        }
        return schema;
    }
}
