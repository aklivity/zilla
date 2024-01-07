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
package io.aklivity.zilla.runtime.validator.protobuf;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.function.LongFunction;

import org.agrona.BitUtil;
import org.agrona.DirectBuffer;
import org.agrona.collections.Int2ObjectCache;
import org.agrona.io.DirectBufferInputStream;
import org.antlr.v4.runtime.BailErrorStrategy;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTreeWalker;

import com.google.protobuf.Descriptors.DescriptorValidationException;
import com.google.protobuf.Descriptors.FileDescriptor;

import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.config.CatalogedConfig;
import io.aklivity.zilla.runtime.engine.config.SchemaConfig;
import io.aklivity.zilla.runtime.validator.protobuf.config.ProtobufValidatorConfig;
import io.aklivity.zilla.runtime.validator.protobuf.internal.parser.Protobuf3Lexer;
import io.aklivity.zilla.runtime.validator.protobuf.internal.parser.Protobuf3Parser;

public class ProtobufValidator
{
    protected static final byte ZERO_INDEX = 0x0;

    protected final SchemaConfig catalog;
    protected final CatalogHandler handler;
    protected final String subject;
    protected final List<Integer> indexes;
    protected final DirectBufferInputStream in;

    private final Int2ObjectCache<FileDescriptor> descriptors;
    private final FileDescriptor[] dependencies;

    protected ProtobufValidator(
        ProtobufValidatorConfig config,
        LongFunction<CatalogHandler> supplyCatalog)
    {
        CatalogedConfig cataloged = config.cataloged.get(0);
        this.handler = supplyCatalog.apply(cataloged.id);
        this.catalog = cataloged.schemas.size() != 0 ? cataloged.schemas.get(0) : null;
        this.subject = catalog != null && catalog.subject != null
                ? catalog.subject
                : config.subject;
        this.descriptors = new Int2ObjectCache<>(1, 1024, i -> {});
        this.in = new DirectBufferInputStream();
        this.dependencies = new FileDescriptor[0];
        this.indexes = new LinkedList<>();
    }

    protected FileDescriptor supplyDescriptor(
        int schemaId)
    {
        return descriptors.computeIfAbsent(schemaId, this::createDescriptors);
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

    private FileDescriptor createDescriptors(
        int schemaId)
    {
        FileDescriptor descriptor = null;

        String schemaText = handler.resolve(schemaId);
        if (schemaText != null)
        {
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
            catch (DescriptorValidationException e)
            {
                e.printStackTrace();
            }
        }
        return descriptor;
    }
}
