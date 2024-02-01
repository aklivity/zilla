/*
 * Copyright 2021-2023 Aklivity Inc
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
import java.util.function.LongFunction;
import java.util.zip.CRC32C;

import org.agrona.BitUtil;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableDirectByteBuffer;
import org.agrona.collections.Int2IntHashMap;
import org.agrona.collections.Int2ObjectCache;
import org.agrona.collections.Object2ObjectHashMap;
import org.agrona.io.DirectBufferInputStream;
import org.agrona.io.ExpandableDirectBufferOutputStream;
import org.antlr.v4.runtime.BailErrorStrategy;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTreeWalker;

import com.google.protobuf.Descriptors;
import com.google.protobuf.Descriptors.DescriptorValidationException;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.DynamicMessage;

import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.config.CatalogedConfig;
import io.aklivity.zilla.runtime.engine.config.SchemaConfig;
import io.aklivity.zilla.runtime.model.protobuf.config.ProtobufModelConfig;
import io.aklivity.zilla.runtime.model.protobuf.internal.parser.Protobuf3Lexer;
import io.aklivity.zilla.runtime.model.protobuf.internal.parser.Protobuf3Parser;

public class ProtobufModelHandler
{
    protected static final byte[] ZERO_INDEX = new byte[]{0x0};
    protected static final String VIEW_JSON = "json";

    private static final int JSON_FIELD_STRUCTURE_LENGTH = "\"\":\"\",".length();
    private static final int JSON_OBJECT_CURLY_BRACES = 2;

    protected final SchemaConfig catalog;
    protected final CatalogHandler handler;
    protected final String subject;
    protected final String view;
    protected final List<Integer> indexes;
    protected final DirectBufferInputStream in;
    protected final ExpandableDirectBufferOutputStream out;

    private final Int2ObjectCache<FileDescriptor> descriptors;
    private final Int2ObjectCache<DescriptorTree> tree;
    private final Object2ObjectHashMap<String, DynamicMessage.Builder> builders;
    private final FileDescriptor[] dependencies;
    private final Int2IntHashMap paddings;
    private final Int2IntHashMap crcCache;
    private final CRC32C crc32c;

    protected ProtobufModelHandler(
        ProtobufModelConfig config,
        LongFunction<CatalogHandler> supplyCatalog)
    {
        CatalogedConfig cataloged = config.cataloged.get(0);
        this.handler = supplyCatalog.apply(cataloged.id);
        this.catalog = cataloged.schemas.size() != 0 ? cataloged.schemas.stream().findFirst().get() : null;
        this.subject = catalog != null && catalog.subject != null
                ? catalog.subject
                : config.subject;
        this.view = config.view;
        this.descriptors = new Int2ObjectCache<>(1, 1024, i -> {});
        this.tree = new Int2ObjectCache<>(1, 1024, i -> {});
        this.builders = new Object2ObjectHashMap<>();
        this.in = new DirectBufferInputStream();
        this.dependencies = new FileDescriptor[0];
        this.indexes = new LinkedList<>();
        this.paddings = new Int2IntHashMap(-1);
        this.out = new ExpandableDirectBufferOutputStream(new ExpandableDirectByteBuffer());
        this.crc32c = new CRC32C();
        this.crcCache = new Int2IntHashMap(0);
    }

    protected FileDescriptor supplyDescriptor(
        int schemaId)
    {
        return descriptors.computeIfAbsent(schemaId, this::createDescriptors);
    }

    protected DescriptorTree supplyDescriptorTree(
        int schemaId)
    {
        return tree.computeIfAbsent(schemaId, this::createDescriptorTree);
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

    protected int supplyIndexPadding(
        int schemaId)
    {
        return paddings.computeIfAbsent(schemaId, this::calculateIndexPadding);
    }

    protected int supplyJsonFormatPadding(
        int schemaId)
    {
        return paddings.computeIfAbsent(schemaId, id -> calculateJsonFormatPadding(supplyDescriptor(id)));
    }

    protected DynamicMessage.Builder supplyDynamicMessageBuilder(
        Descriptors.Descriptor descriptor,
        boolean cacheUpdate)
    {
        DynamicMessage.Builder builder;
        if (builders.containsKey(descriptor.getFullName()) && !cacheUpdate)
        {
            builder = builders.get(descriptor.getFullName());
        }
        else
        {
            builder = createDynamicMessageBuilder(descriptor);
            builders.put(descriptor.getFullName(), builder);
        }
        return builder;
    }

    protected boolean invalidateCacheOnSchemaUpdate(
        int schemaId)
    {
        boolean update = false;
        if (crcCache.containsKey(schemaId))
        {
            String schemaText = handler.resolve(schemaId);
            int checkSum = generateCRC32C(schemaText);
            if (schemaText != null && crcCache.get(schemaId) != checkSum)
            {
                crcCache.remove(schemaId);
                descriptors.remove(schemaId);
                tree.remove(schemaId);
                paddings.remove(schemaId);
                update = true;
            }
        }
        return update;
    }

    private DynamicMessage.Builder createDynamicMessageBuilder(
        Descriptors.Descriptor descriptor)
    {
        return DynamicMessage.newBuilder(descriptor);
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
        DescriptorTree trees = supplyDescriptorTree(schemaId);
        if (trees != null && catalog.record != null)
        {
            DescriptorTree tree = trees.findByName(catalog.record);
            if (tree != null)
            {
                padding = tree.indexes.size() + 1;
            }
        }
        return padding;
    }

    private int calculateJsonFormatPadding(
        FileDescriptor descriptor)
    {
        int padding = 0;

        if (descriptor != null)
        {
            for (Descriptors.Descriptor message : descriptor.getMessageTypes())
            {
                padding += JSON_OBJECT_CURLY_BRACES;
                for (Descriptors.FieldDescriptor field : message.getFields())
                {
                    padding += field.getName().getBytes().length + JSON_FIELD_STRUCTURE_LENGTH;
                }
            }

        }
        return padding;
    }

    private FileDescriptor createDescriptors(
        int schemaId)
    {
        FileDescriptor descriptor = null;

        String schemaText = handler.resolve(schemaId);
        if (schemaText != null)
        {
            crcCache.put(schemaId, generateCRC32C(schemaText));
            CharStream input = CharStreams.fromString(schemaText);
            Protobuf3Lexer lexer = new Protobuf3Lexer(input);
            CommonTokenStream tokens = new CommonTokenStream(lexer);

            Protobuf3Parser parser = new Protobuf3Parser(tokens);
            parser.setErrorHandler(new BailErrorStrategy());
            ParseTreeWalker walker = new ParseTreeWalker();

            ProtoListener listener = new ProtoListener();
            walker.walk(listener, parser.proto());

            try
            {
                descriptor = FileDescriptor.buildFrom(listener.build(), dependencies);
            }
            catch (DescriptorValidationException ex)
            {
                ex.printStackTrace();
            }
        }
        return descriptor;
    }

    private DescriptorTree createDescriptorTree(
        int schemaId)
    {
        DescriptorTree tree = null;
        FileDescriptor descriptor = supplyDescriptor(schemaId);

        if (descriptor != null)
        {
            tree = new DescriptorTree(descriptor);
        }
        return tree;
    }

    private int generateCRC32C(
        String schemaText)
    {
        byte[] bytes = schemaText.getBytes();
        crc32c.reset();
        crc32c.update(bytes, 0, bytes.length);
        return (int) crc32c.getValue();
    }
}
